// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * Demonstrates validation pipelines using the Focus DSL with effectful operations.
 *
 * <p>This example shows how to use {@code modifyF()} for validation that can fail, and {@code
 * foldMap()} for aggregating values with monoids.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Using {@code modifyF()} with Maybe for validation that may fail
 *   <li>Short-circuit validation (fail-fast)
 *   <li>Using {@code foldMap()} with custom Monoids for aggregation
 *   <li>Combining validation with transformation
 *   <li>Using generated Focus classes for type-safe navigation
 * </ul>
 */
public class ValidationPipelineExample {

  // ============= Domain Model =============

  /** An integration's configuration, with various fields that need validation. */
  @GenerateLenses
  @GenerateFocus
  public record IntegrationConfig(
      String apiKey, String databaseUrl, int timeout, int maxConnections) {}

  // The customers and orders are the chapter's Customer, Order and LineItem.

  // An order of Ada's with the given lines, placed on the same day, in pounds
  private static Order order(String id, LineItem... lines) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(lines),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  // A line's total: its price times its quantity
  private static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Validation Pipeline Example ===\n");

    basicValidationExample();
    chainedValidationExample();
    aggregationWithFoldMapExample();
    conditionalModificationExample();
  }

  /** Demonstrates basic validation using modifyF with Maybe. */
  static void basicValidationExample() {
    System.out.println("--- Basic Validation with modifyF() ---");

    IntegrationConfig validConfig =
        new IntegrationConfig("abc123xyz", "jdbc:postgresql://localhost/db", 30, 10);
    IntegrationConfig invalidConfig =
        new IntegrationConfig("short", "jdbc:postgresql://localhost/db", 30, 10);

    // Validation function: API key must be at least 8 characters
    Function<String, Kind<MaybeKind.Witness, String>> validateApiKey =
        key -> {
          if (key != null && key.length() >= 8) {
            // Valid: transform to uppercase
            return MaybeKindHelper.MAYBE.widen(Maybe.just(key.toUpperCase()));
          } else {
            // Invalid: return Nothing
            return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
          }
        };

    // Use generated Focus class for type-safe navigation
    FocusPath<IntegrationConfig, String> apiKeyPath = IntegrationConfigFocus.apiKey();

    // Validate and transform valid config
    Kind<MaybeKind.Witness, IntegrationConfig> validResult =
        apiKeyPath.modifyF(validateApiKey, validConfig, Instances.monadError(maybe()));

    Maybe<IntegrationConfig> validMaybe = MaybeKindHelper.MAYBE.narrow(validResult);
    System.out.println("Valid config result: " + (validMaybe.isJust() ? "Success" : "Failure"));
    if (validMaybe.isJust()) {
      System.out.println("  Transformed API key: " + validMaybe.get().apiKey());
    }

    // Validate invalid config
    Kind<MaybeKind.Witness, IntegrationConfig> invalidResult =
        apiKeyPath.modifyF(validateApiKey, invalidConfig, Instances.monadError(maybe()));

    Maybe<IntegrationConfig> invalidMaybe = MaybeKindHelper.MAYBE.narrow(invalidResult);
    System.out.println("Invalid config result: " + (invalidMaybe.isJust() ? "Success" : "Failure"));

    System.out.println();
  }

  /** Demonstrates chaining multiple validations. */
  static void chainedValidationExample() {
    System.out.println("--- Chained Validation ---");

    Customer validCustomer = new Customer("Alice Smith", new EmailAddress("Alice@Example.com"));
    Customer invalidEmail = new Customer("Bob Jones", new EmailAddress("not-an-email"));
    Customer invalidName = new Customer("  ", new EmailAddress("charlie@test.com"));

    // Name validation: not blank and at most 40 characters, trimmed
    Function<String, Kind<MaybeKind.Witness, String>> validateName =
        name -> {
          String trimmed = name.trim();
          if (!trimmed.isEmpty() && trimmed.length() <= 40) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(trimmed));
          }
          return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
        };

    // Email validation: simple check for @ symbol
    Function<String, Kind<MaybeKind.Witness, String>> validateEmail =
        email -> {
          if (email.contains("@") && email.contains(".")) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(email.toLowerCase()));
          }
          return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
        };

    // Use generated Focus classes for type-safe navigation
    FocusPath<Customer, String> namePath = CustomerFocus.name();
    FocusPath<Customer, String> emailPath = CustomerFocus.email().value();

    // Chain validations manually (fail-fast)
    System.out.println("Validating valid customer:");
    validateCustomer(validCustomer, namePath, emailPath, validateName, validateEmail);

    System.out.println("\nValidating customer with invalid email:");
    validateCustomer(invalidEmail, namePath, emailPath, validateName, validateEmail);

    System.out.println("\nValidating customer with blank name:");
    validateCustomer(invalidName, namePath, emailPath, validateName, validateEmail);

    System.out.println();
  }

  private static void validateCustomer(
      Customer customer,
      FocusPath<Customer, String> namePath,
      FocusPath<Customer, String> emailPath,
      Function<String, Kind<MaybeKind.Witness, String>> validateName,
      Function<String, Kind<MaybeKind.Witness, String>> validateEmail) {

    // Chain validations: name -> email
    Kind<MaybeKind.Witness, Customer> step1 =
        namePath.modifyF(validateName, customer, Instances.monadError(maybe()));

    Maybe<Customer> afterName = MaybeKindHelper.MAYBE.narrow(step1);
    if (afterName.isNothing()) {
      System.out.println("  Failed at: name validation");
      return;
    }

    Kind<MaybeKind.Witness, Customer> step2 =
        emailPath.modifyF(validateEmail, afterName.get(), Instances.monadError(maybe()));

    Maybe<Customer> result = MaybeKindHelper.MAYBE.narrow(step2);
    if (result.isJust()) {
      System.out.println("  All validations passed!");
      System.out.println("  Final customer: " + result.get());
    } else {
      System.out.println("  Failed at: email validation");
    }
  }

  /** Demonstrates aggregation using foldMap with various Monoids. */
  static void aggregationWithFoldMapExample() {
    System.out.println("--- Aggregation with foldMap() ---");

    Order order =
        order(
            "00000000-0000-0000-0000-000000000001",
            new LineItem("LAMP", 2, new BigDecimal("29.99")),
            new LineItem("DESK", 1, new BigDecimal("149.99")),
            new LineItem("BULB", 5, new BigDecimal("9.99")));

    // Use generated Focus class - lines() returns TraversalPath for List fields
    TraversalPath<Order, LineItem> allLinesPath = OrderFocus.lines();

    // Navigate to quantities using generated LineItemFocus
    TraversalPath<Order, Integer> quantitiesPath = allLinesPath.via(LineItemFocus.quantity());

    // Navigate to prices
    TraversalPath<Order, BigDecimal> pricesPath = allLinesPath.via(LineItemFocus.price());

    // Integer sum monoid
    Monoid<Integer> intSum =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };

    // BigDecimal sum monoid
    Monoid<BigDecimal> moneySum =
        new Monoid<>() {
          @Override
          public BigDecimal empty() {
            return BigDecimal.ZERO;
          }

          @Override
          public BigDecimal combine(BigDecimal a, BigDecimal b) {
            return a.add(b);
          }
        };

    // Integer max monoid
    Monoid<Integer> intMax =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return Integer.MIN_VALUE;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return Math.max(a, b);
          }
        };

    // Sum all quantities
    int totalQuantity = quantitiesPath.foldMap(intSum, q -> q, order);
    System.out.println("Total quantity: " + totalQuantity);

    // Calculate total using line totals
    BigDecimal orderTotal =
        allLinesPath.foldMap(moneySum, ValidationPipelineExample::lineTotal, order);
    System.out.println("Order total: £" + orderTotal);

    // Find max quantity
    int maxQuantity = quantitiesPath.foldMap(intMax, q -> q, order);
    System.out.println("Max quantity in single line: " + maxQuantity);

    // Count lines
    int lineCount = allLinesPath.count(order);
    System.out.println("Number of lines: " + lineCount);

    // Calculate average price
    BigDecimal sumPrices = pricesPath.foldMap(moneySum, p -> p, order);
    BigDecimal avgPrice = sumPrices.divide(BigDecimal.valueOf(lineCount), 2, RoundingMode.HALF_UP);
    System.out.println("Average unit price: £" + avgPrice);

    System.out.println();
  }

  /** Demonstrates conditional modification with modifyWhen. */
  static void conditionalModificationExample() {
    System.out.println("--- Conditional Modification with modifyWhen() ---");

    Order order =
        order(
            "00000000-0000-0000-0000-000000000002",
            new LineItem("BULB", 100, new BigDecimal("5.00")), // Bulk line
            new LineItem("LAMP", 2, new BigDecimal("25.00")), // Small line
            new LineItem("FUSE", 50, new BigDecimal("8.00")), // Another bulk line
            new LineItem("DESK", 1, new BigDecimal("100.00")) // Single item
            );

    // Use generated Focus class for type-safe navigation
    TraversalPath<Order, LineItem> allLinesPath = OrderFocus.lines();

    System.out.println("Original lines:");
    printOrderLines(order);

    // Apply 10% discount to bulk lines (quantity >= 50)
    BigDecimal bulkMultiplier = new BigDecimal("0.90");
    Order discountedOrder =
        allLinesPath.modifyWhen(
            line -> line.quantity() >= 50,
            line ->
                new LineItem(
                    line.sku(),
                    line.quantity(),
                    line.price().multiply(bulkMultiplier).setScale(2, RoundingMode.HALF_UP)),
            order);

    System.out.println("\nAfter bulk discount (10% off for qty >= 50):");
    printOrderLines(discountedOrder);

    // Double the quantity for small lines (quantity < 5)
    Order doubledSmallLines =
        allLinesPath.modifyWhen(
            line -> line.quantity() < 5,
            line -> new LineItem(line.sku(), line.quantity() * 2, line.price()),
            order);

    System.out.println("\nAfter doubling small lines (qty < 5):");
    printOrderLines(doubledSmallLines);

    System.out.println();
  }

  private static void printOrderLines(Order order) {
    for (LineItem line : order.lines()) {
      System.out.printf(
          "  %s: qty=%d, price=£%s, total=£%s%n",
          line.sku(), line.quantity(), line.price(), lineTotal(line));
    }
  }
}
