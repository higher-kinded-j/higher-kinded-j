// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
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

  /** Configuration with various fields that need validation. */
  @GenerateLenses
  @GenerateFocus
  public record Config(String apiKey, String databaseUrl, int timeout, int maxConnections) {}

  /** User profile with fields requiring validation. */
  @GenerateLenses
  @GenerateFocus
  public record UserProfile(String username, String email, int age) {}

  /** Order with line items for aggregation example. */
  @GenerateLenses
  @GenerateFocus
  public record Order(String orderId, List<LineItem> items) {}

  /** Line item in an order. */
  @GenerateLenses
  @GenerateFocus
  public record LineItem(String productId, int quantity, double unitPrice) {
    double total() {
      return quantity * unitPrice;
    }
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

    Config validConfig = new Config("abc123xyz", "jdbc:postgresql://localhost/db", 30, 10);
    Config invalidConfig = new Config("short", "jdbc:postgresql://localhost/db", 30, 10);

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
    FocusPath<Config, String> apiKeyPath = ConfigFocus.apiKey();

    // Validate and transform valid config
    Kind<MaybeKind.Witness, Config> validResult =
        apiKeyPath.modifyF(validateApiKey, validConfig, MaybeMonad.INSTANCE);

    Maybe<Config> validMaybe = MaybeKindHelper.MAYBE.narrow(validResult);
    System.out.println("Valid config result: " + (validMaybe.isJust() ? "Success" : "Failure"));
    if (validMaybe.isJust()) {
      System.out.println("  Transformed API key: " + validMaybe.get().apiKey());
    }

    // Validate invalid config
    Kind<MaybeKind.Witness, Config> invalidResult =
        apiKeyPath.modifyF(validateApiKey, invalidConfig, MaybeMonad.INSTANCE);

    Maybe<Config> invalidMaybe = MaybeKindHelper.MAYBE.narrow(invalidResult);
    System.out.println("Invalid config result: " + (invalidMaybe.isJust() ? "Success" : "Failure"));

    System.out.println();
  }

  /** Demonstrates chaining multiple validations. */
  static void chainedValidationExample() {
    System.out.println("--- Chained Validation ---");

    UserProfile validProfile = new UserProfile("alice_smith", "alice@example.com", 25);
    UserProfile invalidEmail = new UserProfile("bob_jones", "not-an-email", 30);
    UserProfile invalidAge = new UserProfile("charlie", "charlie@test.com", -5);

    // Username validation: alphanumeric and underscores, 3-20 chars
    Function<String, Kind<MaybeKind.Witness, String>> validateUsername =
        username -> {
          if (username != null
              && username.length() >= 3
              && username.length() <= 20
              && username.matches("^[a-zA-Z0-9_]+$")) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(username.toLowerCase()));
          }
          return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
        };

    // Email validation: simple check for @ symbol
    Function<String, Kind<MaybeKind.Witness, String>> validateEmail =
        email -> {
          if (email != null && email.contains("@") && email.contains(".")) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(email.toLowerCase()));
          }
          return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
        };

    // Age validation: must be positive and reasonable
    Function<Integer, Kind<MaybeKind.Witness, Integer>> validateAge =
        age -> {
          if (age >= 0 && age <= 150) {
            return MaybeKindHelper.MAYBE.widen(Maybe.just(age));
          }
          return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
        };

    // Use generated Focus classes for type-safe navigation
    FocusPath<UserProfile, String> usernamePath = UserProfileFocus.username();
    FocusPath<UserProfile, String> emailPath = UserProfileFocus.email();
    FocusPath<UserProfile, Integer> agePath = UserProfileFocus.age();

    // Chain validations manually (fail-fast)
    System.out.println("Validating valid profile:");
    validateProfile(
        validProfile,
        usernamePath,
        emailPath,
        agePath,
        validateUsername,
        validateEmail,
        validateAge);

    System.out.println("\nValidating profile with invalid email:");
    validateProfile(
        invalidEmail,
        usernamePath,
        emailPath,
        agePath,
        validateUsername,
        validateEmail,
        validateAge);

    System.out.println("\nValidating profile with invalid age:");
    validateProfile(
        invalidAge, usernamePath, emailPath, agePath, validateUsername, validateEmail, validateAge);

    System.out.println();
  }

  private static void validateProfile(
      UserProfile profile,
      FocusPath<UserProfile, String> usernamePath,
      FocusPath<UserProfile, String> emailPath,
      FocusPath<UserProfile, Integer> agePath,
      Function<String, Kind<MaybeKind.Witness, String>> validateUsername,
      Function<String, Kind<MaybeKind.Witness, String>> validateEmail,
      Function<Integer, Kind<MaybeKind.Witness, Integer>> validateAge) {

    // Chain validations: username -> email -> age
    Kind<MaybeKind.Witness, UserProfile> step1 =
        usernamePath.modifyF(validateUsername, profile, MaybeMonad.INSTANCE);

    Maybe<UserProfile> afterUsername = MaybeKindHelper.MAYBE.narrow(step1);
    if (afterUsername.isNothing()) {
      System.out.println("  Failed at: username validation");
      return;
    }

    Kind<MaybeKind.Witness, UserProfile> step2 =
        emailPath.modifyF(validateEmail, afterUsername.get(), MaybeMonad.INSTANCE);

    Maybe<UserProfile> afterEmail = MaybeKindHelper.MAYBE.narrow(step2);
    if (afterEmail.isNothing()) {
      System.out.println("  Failed at: email validation");
      return;
    }

    Kind<MaybeKind.Witness, UserProfile> step3 =
        agePath.modifyF(validateAge, afterEmail.get(), MaybeMonad.INSTANCE);

    Maybe<UserProfile> result = MaybeKindHelper.MAYBE.narrow(step3);
    if (result.isJust()) {
      System.out.println("  All validations passed!");
      System.out.println("  Final profile: " + result.get());
    } else {
      System.out.println("  Failed at: age validation");
    }
  }

  /** Demonstrates aggregation using foldMap with various Monoids. */
  static void aggregationWithFoldMapExample() {
    System.out.println("--- Aggregation with foldMap() ---");

    Order order =
        new Order(
            "ORD-001",
            List.of(
                new LineItem("PROD-A", 2, 29.99),
                new LineItem("PROD-B", 1, 149.99),
                new LineItem("PROD-C", 5, 9.99)));

    // Use generated Focus class - items() returns TraversalPath for List fields
    TraversalPath<Order, LineItem> allItemsPath = OrderFocus.items();

    // Navigate to quantities using generated LineItemFocus
    TraversalPath<Order, Integer> quantitiesPath = allItemsPath.via(LineItemFocus.quantity());

    // Navigate to prices
    TraversalPath<Order, Double> pricesPath = allItemsPath.via(LineItemFocus.unitPrice());

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

    // Double sum monoid
    Monoid<Double> doubleSum =
        new Monoid<>() {
          @Override
          public Double empty() {
            return 0.0;
          }

          @Override
          public Double combine(Double a, Double b) {
            return a + b;
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

    // Calculate total using line item totals
    double orderTotal = allItemsPath.foldMap(doubleSum, LineItem::total, order);
    System.out.println("Order total: $" + String.format("%.2f", orderTotal));

    // Find max quantity
    int maxQuantity = quantitiesPath.foldMap(intMax, q -> q, order);
    System.out.println("Max quantity in single item: " + maxQuantity);

    // Count items
    int itemCount = allItemsPath.count(order);
    System.out.println("Number of line items: " + itemCount);

    // Calculate average price
    double sumPrices = pricesPath.foldMap(doubleSum, p -> p, order);
    double avgPrice = sumPrices / itemCount;
    System.out.println("Average unit price: $" + String.format("%.2f", avgPrice));

    System.out.println();
  }

  /** Demonstrates conditional modification with modifyWhen. */
  static void conditionalModificationExample() {
    System.out.println("--- Conditional Modification with modifyWhen() ---");

    Order order =
        new Order(
            "ORD-002",
            List.of(
                new LineItem("BULK-A", 100, 5.00), // Bulk item
                new LineItem("SMALL-B", 2, 25.00), // Small order
                new LineItem("BULK-C", 50, 8.00), // Another bulk item
                new LineItem("TINY-D", 1, 100.00) // Single item
                ));

    // Use generated Focus class for type-safe navigation
    TraversalPath<Order, LineItem> allItemsPath = OrderFocus.items();

    System.out.println("Original items:");
    printOrderItems(order);

    // Apply 10% discount to bulk orders (quantity >= 50)
    Order discountedOrder =
        allItemsPath.modifyWhen(
            item -> item.quantity() >= 50,
            item -> new LineItem(item.productId(), item.quantity(), item.unitPrice() * 0.90),
            order);

    System.out.println("\nAfter bulk discount (10% off for qty >= 50):");
    printOrderItems(discountedOrder);

    // Double the quantity for small orders (quantity < 5)
    Order doubledSmallOrder =
        allItemsPath.modifyWhen(
            item -> item.quantity() < 5,
            item -> new LineItem(item.productId(), item.quantity() * 2, item.unitPrice()),
            order);

    System.out.println("\nAfter doubling small orders (qty < 5):");
    printOrderItems(doubledSmallOrder);

    System.out.println();
  }

  private static void printOrderItems(Order order) {
    for (LineItem item : order.items()) {
      System.out.printf(
          "  %s: qty=%d, price=$%.2f, total=$%.2f%n",
          item.productId(), item.quantity(), item.unitPrice(), item.total());
    }
  }
}
