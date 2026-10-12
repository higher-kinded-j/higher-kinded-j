// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.optics.extensions.TraversalExtensions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * A comprehensive example demonstrating {@link
 * org.higherkindedj.optics.extensions.TraversalExtensions} for bulk operations with error handling.
 *
 * <p>This example showcases how to process collections using traversals whilst handling validation
 * errors, keeping either the first error or every error.
 *
 * <p><b>Scenario:</b> Bulk order processing for the order service. We validate and process the
 * lines of an order, and the statuses of several orders, demonstrating different error handling
 * strategies appropriate for various business requirements.
 *
 * <p><b>Key Methods Demonstrated:</b>
 *
 * <ul>
 *   <li>{@code getAllMaybe} - Extract all values if present
 *   <li>{@code modifyAllMaybe} - All-or-nothing modifications
 *   <li>{@code modifyAllEither} - First error only (every element is still validated)
 *   <li>{@code modifyAllValidated} - Error accumulation (collect all errors)
 *   <li>{@code modifyWherePossible} - Selective modification (best-effort)
 *   <li>{@code countValid} - Count items passing validation
 *   <li>{@code collectErrors} - Gather all validation failures
 * </ul>
 *
 * @see org.higherkindedj.optics.extensions.TraversalExtensions
 */
public class TraversalExtensionsExample {

  // Domain model: the chapter's Order, Customer and LineItem
  private static final Customer ALICE =
      new Customer("Alice", new EmailAddress("alice@example.com"));

  private static final Customer BOB = new Customer("Bob", new EmailAddress("bob@example.com"));

  private static final Customer CAROL =
      new Customer("Carol", new EmailAddress("carol@example.com"));

  // A customer's order in the given status, placed on the same day, in pounds
  private static Order order(String id, Customer customer, OrderStatus status, LineItem... lines) {
    return new Order(
        UUID.fromString(id),
        customer,
        List.of(lines),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  // Validation errors
  sealed interface LineItemError permits PriceError, QuantityError, StatusError {}

  record PriceError(String sku, String message) implements LineItemError {
    @Override
    public String toString() {
      return "Price[" + sku + "]: " + message;
    }
  }

  record QuantityError(String sku, String message) implements LineItemError {
    @Override
    public String toString() {
      return "Quantity[" + sku + "]: " + message;
    }
  }

  record StatusError(String sku, String message) implements LineItemError {
    @Override
    public String toString() {
      return "Status[" + sku + "]: " + message;
    }
  }

  public static void main(String[] args) {
    System.out.println("=== Traversal Extensions Examples ===\n");

    demonstrateGetAllMaybe();
    demonstrateModifyAllMaybe();
    demonstrateModifyAllEither();
    demonstrateModifyAllValidated();
    demonstrateModifyWherePossible();
    demonstrateCountValid();
    demonstrateCollectErrors();
    demonstrateRealWorldScenario();
  }

  private static void demonstrateGetAllMaybe() {
    System.out.println("--- getAllMaybe: Extract All Values ---");

    List<LineItem> items =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
            new LineItem("MOUSE", 2, new BigDecimal("29.99")),
            new LineItem("KEYBOARD", 1, new BigDecimal("79.99")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    // Extract all prices
    Maybe<List<BigDecimal>> prices = getAllMaybe(allPrices, items);
    System.out.println("All prices: " + prices.map(list -> list.toString()).orElse("No prices"));

    // Empty list returns Nothing
    List<LineItem> empty = List.of();
    Maybe<List<BigDecimal>> noPrices = getAllMaybe(allPrices, empty);
    System.out.println("Empty list: " + noPrices.orElse(null));

    System.out.println();
  }

  private static void demonstrateModifyAllMaybe() {
    System.out.println("--- modifyAllMaybe: All-or-Nothing Modifications ---");

    List<LineItem> items =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("100.00")),
            new LineItem("MOUSE", 2, new BigDecimal("20.00")),
            new LineItem("KEYBOARD", 1, new BigDecimal("50.00")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    // Successful modification (all prices > £10)
    Maybe<List<LineItem>> updated =
        modifyAllMaybe(
            allPrices,
            price ->
                price.compareTo(new BigDecimal("10")) >= 0
                    ? Maybe.just(
                        price.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN))
                    : Maybe.nothing(),
            items);
    System.out.println("10% price increase: " + (updated.isJust() ? "Success" : "Failed"));

    // Failed modification (one price too low)
    List<LineItem> withLowPrice =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("100.00")),
            new LineItem("STICKER", 2, new BigDecimal("5.00")));

    Maybe<List<LineItem>> failed =
        modifyAllMaybe(
            allPrices,
            price ->
                price.compareTo(new BigDecimal("10")) >= 0
                    ? Maybe.just(
                        price.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_EVEN))
                    : Maybe.nothing(),
            withLowPrice);
    System.out.println(
        "With invalid price: " + (failed.isJust() ? "Success" : "Failed (rolled back)"));

    System.out.println();
  }

  private static void demonstrateModifyAllEither() {
    System.out.println("--- modifyAllEither: First Error Only ---");

    List<LineItem> items =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
            new LineItem("MOUSE", 2, new BigDecimal("-10.00")),
            new LineItem("KEYBOARD", 1, new BigDecimal("79.99")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    // Keeps only the first error; every price is still validated
    Either<String, List<LineItem>> result =
        modifyAllEither(
            allPrices,
            price -> {
              if (price.compareTo(BigDecimal.ZERO) < 0) {
                return Either.left("Price cannot be negative");
              }
              if (price.compareTo(new BigDecimal("10000")) > 0) {
                return Either.left("Price exceeds maximum");
              }
              return Either.right(price);
            },
            items);

    result.fold(
        error -> {
          System.out.println("❌ Validation failed: " + error);
          return null;
        },
        updated -> {
          System.out.println("✅ All prices valid");
          return null;
        });

    System.out.println();
  }

  private static void demonstrateModifyAllValidated() {
    System.out.println("--- modifyAllValidated: Error Accumulation ---");

    List<LineItem> items =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("-100.00")),
            new LineItem("MOUSE", -5, new BigDecimal("29.99")),
            new LineItem("KEYBOARD", 1, new BigDecimal("-50.00")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    // Accumulate ALL validation errors
    Validated<List<String>, List<LineItem>> result =
        modifyAllValidated(
            allPrices,
            price -> {
              if (price.compareTo(BigDecimal.ZERO) < 0) {
                return Validated.invalid("Price cannot be negative: " + price);
              }
              if (price.compareTo(new BigDecimal("10000")) > 0) {
                return Validated.invalid("Price exceeds maximum: " + price);
              }
              return Validated.valid(price);
            },
            items);

    result.fold(
        errors -> {
          System.out.println("❌ Validation failed with " + errors.size() + " errors:");
          errors.forEach(err -> System.out.println("   • " + err));
          return null;
        },
        updated -> {
          System.out.println("✅ All prices valid");
          return null;
        });

    System.out.println();
  }

  private static void demonstrateModifyWherePossible() {
    System.out.println("--- modifyWherePossible: Selective Modification ---");

    List<Order> orders =
        List.of(
            order(
                "00000000-0000-0000-0000-000000000001",
                ALICE,
                OrderStatus.PAID,
                new LineItem("LAPTOP", 1, new BigDecimal("999.99"))),
            order(
                "00000000-0000-0000-0000-000000000002",
                BOB,
                OrderStatus.SHIPPED,
                new LineItem("MOUSE", 2, new BigDecimal("29.99"))),
            order(
                "00000000-0000-0000-0000-000000000003",
                CAROL,
                OrderStatus.NEW,
                new LineItem("KEYBOARD", 1, new BigDecimal("79.99"))));

    Lens<Order, OrderStatus> statusLens = OrderLenses.status();
    Traversal<List<Order>, OrderStatus> allStatuses =
        Traversals.<Order>forList().andThen(statusLens.asTraversal());

    // Ship only the PAID orders (best-effort)
    List<Order> updated =
        modifyWherePossible(
            allStatuses,
            status ->
                status == OrderStatus.PAID ? Maybe.just(OrderStatus.SHIPPED) : Maybe.nothing(),
            orders);

    System.out.println("Status updates:");
    updated.forEach(
        order -> System.out.println("  " + order.customer().name() + ": " + order.status()));

    System.out.println();
  }

  private static void demonstrateCountValid() {
    System.out.println("--- countValid: Count Passing Validation ---");

    List<LineItem> items =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
            new LineItem("MOUSE", 2, new BigDecimal("-10.00")),
            new LineItem("KEYBOARD", 1, new BigDecimal("79.99")),
            new LineItem("MONITOR", 1, new BigDecimal("-50.00")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    int validCount =
        countValid(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) >= 0
                    ? Either.right(price)
                    : Either.left("Negative price"),
            items);

    System.out.println("Valid items: " + validCount + " out of " + items.size());
    System.out.println("Invalid items: " + (items.size() - validCount));

    System.out.println();
  }

  private static void demonstrateCollectErrors() {
    System.out.println("--- collectErrors: Gather All Validation Failures ---");

    List<LineItem> items =
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
            new LineItem("MOUSE", 2, new BigDecimal("-10.00")),
            new LineItem("KEYBOARD", 1, new BigDecimal("79.99")),
            new LineItem("MONITOR", -1, new BigDecimal("-50.00")));

    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    List<String> errors =
        collectErrors(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) >= 0
                    ? Either.right(price)
                    : Either.left("Negative price: " + price),
            items);

    if (errors.isEmpty()) {
      System.out.println("✅ All prices valid");
    } else {
      System.out.println("❌ Found " + errors.size() + " invalid prices:");
      errors.forEach(err -> System.out.println("   • " + err));
    }

    System.out.println();
  }

  private static void demonstrateRealWorldScenario() {
    System.out.println("--- Real-World Scenario: Order Validation Pipeline ---\n");

    Order order =
        order(
            "00000000-0000-0000-0000-000000012345",
            ALICE,
            OrderStatus.NEW,
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
            new LineItem("MOUSE", 2, new BigDecimal("29.99")),
            new LineItem("USB-CABLE", 5, new BigDecimal("-5.00")),
            new LineItem("KEYBOARD", 0, new BigDecimal("79.99")));

    System.out.println("Processing Order: " + order.id());
    System.out.println("Lines: " + order.lines().size());

    // Step 1: Validate all prices (accumulate errors)
    System.out.println("\n📋 Step 1: Price Validation");
    Lens<LineItem, BigDecimal> priceLens = LineItemLenses.price();
    Traversal<List<LineItem>, BigDecimal> allPrices =
        Traversals.<LineItem>forList().andThen(priceLens.asTraversal());

    List<String> priceErrors =
        collectErrors(allPrices, price -> validatePrice(price), order.lines());

    if (priceErrors.isEmpty()) {
      System.out.println("  ✅ All prices valid");
    } else {
      System.out.println("  ❌ Price validation errors:");
      priceErrors.forEach(err -> System.out.println("     • " + err));
    }

    // Step 2: Validate all quantities
    System.out.println("\n📦 Step 2: Quantity Validation");
    Lens<LineItem, Integer> quantityLens = LineItemLenses.quantity();
    Traversal<List<LineItem>, Integer> allQuantities =
        Traversals.<LineItem>forList().andThen(quantityLens.asTraversal());

    List<String> quantityErrors =
        collectErrors(allQuantities, qty -> validateQuantity(qty), order.lines());

    if (quantityErrors.isEmpty()) {
      System.out.println("  ✅ All quantities valid");
    } else {
      System.out.println("  ❌ Quantity validation errors:");
      quantityErrors.forEach(err -> System.out.println("     • " + err));
    }

    // Step 3: Count valid vs invalid items
    System.out.println("\n📊 Step 3: Validation Summary");
    int validPrices = countValid(allPrices, price -> validatePrice(price), order.lines());
    int validQuantities = countValid(allQuantities, qty -> validateQuantity(qty), order.lines());

    System.out.println("  Valid prices: " + validPrices + "/" + order.lines().size());
    System.out.println("  Valid quantities: " + validQuantities + "/" + order.lines().size());

    // Step 4: Apply discount to valid items only
    System.out.println("\n💰 Step 4: Apply 10% Discount to Valid Items");
    List<LineItem> discounted =
        modifyWherePossible(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) > 0
                    ? Maybe.just(
                        price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN))
                    : Maybe.nothing(),
            order.lines());

    System.out.println("  Discounted items:");
    discounted.forEach(
        item -> {
          if (item.price().compareTo(BigDecimal.ZERO) > 0) {
            System.out.println("     " + item.sku() + ": £" + item.price());
          }
        });

    // Final decision
    System.out.println("\n🎯 Final Decision:");
    int totalErrors = priceErrors.size() + quantityErrors.size();
    if (totalErrors == 0) {
      System.out.println("  ✅ Order approved for processing");
    } else {
      System.out.println("  ❌ Order rejected (" + totalErrors + " validation errors)");
      System.out.println("  Please correct the errors and resubmit");
    }

    System.out.println();
  }

  // Validation helpers
  private static Either<String, BigDecimal> validatePrice(BigDecimal price) {
    if (price.compareTo(BigDecimal.ZERO) < 0) {
      return Either.left("Price cannot be negative");
    }
    if (price.compareTo(new BigDecimal("10000")) > 0) {
      return Either.left("Price exceeds maximum (£10,000)");
    }
    return Either.right(price);
  }

  private static Either<String, Integer> validateQuantity(Integer quantity) {
    if (quantity <= 0) {
      return Either.left("Quantity must be positive");
    }
    if (quantity > 100) {
      return Either.left("Quantity exceeds maximum (100)");
    }
    return Either.right(quantity);
  }
}
