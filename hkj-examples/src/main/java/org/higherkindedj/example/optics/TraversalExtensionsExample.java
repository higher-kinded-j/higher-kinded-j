// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.optics.extensions.TraversalExtensions.*;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A comprehensive example demonstrating {@link
 * org.higherkindedj.optics.extensions.TraversalExtensions} for bulk operations with error handling.
 *
 * <p>This example showcases how to process collections using traversals whilst handling validation
 * errors, with support for fail-fast and error accumulation strategies.
 *
 * <p><b>Scenario:</b> Bulk order processing system for an e-commerce platform. We validate and
 * process multiple order items, demonstrating different error handling strategies appropriate for
 * various business requirements.
 *
 * <p><b>Key Methods Demonstrated:</b>
 *
 * <ul>
 *   <li>{@code getAllMaybe} - Extract all values if present
 *   <li>{@code modifyAllMaybe} - All-or-nothing modifications
 *   <li>{@code modifyAllEither} - Fail-fast validation (stop at first error)
 *   <li>{@code modifyAllValidated} - Error accumulation (collect all errors)
 *   <li>{@code modifyWherePossible} - Selective modification (best-effort)
 *   <li>{@code countValid} - Count items passing validation
 *   <li>{@code collectErrors} - Gather all validation failures
 * </ul>
 *
 * @see org.higherkindedj.optics.extensions.TraversalExtensions
 */
public class TraversalExtensionsExample {

  // Domain model for e-commerce order processing
  @GenerateLenses
  record TEOrderItem(String sku, String name, BigDecimal price, int quantity, String status) {}

  @GenerateLenses
  record TEOrder(String orderId, List<TEOrderItem> items, String customerEmail) {}

  // Validation errors
  sealed interface ValidationError permits PriceError, QuantityError, StatusError {}

  record PriceError(String sku, String message) implements ValidationError {
    @Override
    public String toString() {
      return "Price[" + sku + "]: " + message;
    }
  }

  record QuantityError(String sku, String message) implements ValidationError {
    @Override
    public String toString() {
      return "Quantity[" + sku + "]: " + message;
    }
  }

  record StatusError(String sku, String message) implements ValidationError {
    @Override
    public String toString() {
      return "Status[" + sku + "]: " + message;
    }
  }

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
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

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("29.99"), 2, "pending"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"));

    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

    // Extract all prices
    Maybe<List<BigDecimal>> prices = getAllMaybe(allPrices, items);
    System.out.println("All prices: " + prices.map(list -> list.toString()).orElse("No prices"));

    // Empty list returns Nothing
    List<TEOrderItem> empty = List.of();
    Maybe<List<BigDecimal>> noPrices = getAllMaybe(allPrices, empty);
    System.out.println("Empty list: " + noPrices.orElse(null));

    System.out.println();
  }

  private static void demonstrateModifyAllMaybe() {
    System.out.println("--- modifyAllMaybe: All-or-Nothing Modifications ---");

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("100.00"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("20.00"), 2, "pending"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("50.00"), 1, "pending"));

    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

    // Successful modification (all prices > £10)
    Maybe<List<TEOrderItem>> updated =
        modifyAllMaybe(
            allPrices,
            price ->
                price.compareTo(new BigDecimal("10")) >= 0
                    ? Maybe.just(price.multiply(new BigDecimal("1.1")))
                    : Maybe.nothing(),
            items);
    System.out.println("10% price increase: " + (updated.isJust() ? "Success" : "Failed"));

    // Failed modification (one price too low)
    List<TEOrderItem> withLowPrice =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("100.00"), 1, "pending"),
            new TEOrderItem("SKU002", "Cheap Item", new BigDecimal("5.00"), 2, "pending"));

    Maybe<List<TEOrderItem>> failed =
        modifyAllMaybe(
            allPrices,
            price ->
                price.compareTo(new BigDecimal("10")) >= 0
                    ? Maybe.just(price.multiply(new BigDecimal("1.1")))
                    : Maybe.nothing(),
            withLowPrice);
    System.out.println(
        "With invalid price: " + (failed.isJust() ? "Success" : "Failed (rolled back)"));

    System.out.println();
  }

  private static void demonstrateModifyAllEither() {
    System.out.println("--- modifyAllEither: Fail-Fast Validation ---");

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("-10.00"), 2, "pending"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"));

    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

    // Fail-fast validation (stops at first error)
    Either<String, List<TEOrderItem>> result =
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

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("-100.00"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("29.99"), -5, "pending"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("-50.00"), 1, "pending"));

    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

    // Accumulate ALL validation errors
    Validated<List<String>, List<TEOrderItem>> result =
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

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("29.99"), 2, "shipped"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"));

    Lens<TEOrderItem, String> statusLens = TEOrderItemLenses.status();
    Traversal<List<TEOrderItem>, String> allStatuses =
        Traversals.<TEOrderItem>forList().andThen(statusLens.asTraversal());

    // Update only pending items (best-effort)
    List<TEOrderItem> updated =
        modifyWherePossible(
            allStatuses,
            status -> status.equals("pending") ? Maybe.just("processing") : Maybe.nothing(),
            items);

    System.out.println("Status updates:");
    updated.forEach(item -> System.out.println("  " + item.sku() + ": " + item.status()));

    System.out.println();
  }

  private static void demonstrateCountValid() {
    System.out.println("--- countValid: Count Passing Validation ---");

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("-10.00"), 2, "pending"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"),
            new TEOrderItem("SKU004", "Monitor", new BigDecimal("-50.00"), 1, "pending"));

    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

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

    List<TEOrderItem> items =
        List.of(
            new TEOrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
            new TEOrderItem("SKU002", "Mouse", new BigDecimal("-10.00"), 2, "pending"),
            new TEOrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"),
            new TEOrderItem("SKU004", "Monitor", new BigDecimal("-50.00"), -1, "pending"));

    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

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

    TEOrder order =
        new TEOrder(
            "ORD-12345",
            List.of(
                new TEOrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
                new TEOrderItem("SKU002", "Mouse", new BigDecimal("29.99"), 2, "pending"),
                new TEOrderItem("SKU003", "USB Cable", new BigDecimal("-5.00"), 5, "pending"),
                new TEOrderItem("SKU004", "Keyboard", new BigDecimal("79.99"), 0, "pending")),
            "customer@example.com");

    System.out.println("Processing Order: " + order.orderId());
    System.out.println("Items: " + order.items().size());

    // Step 1: Validate all prices (accumulate errors)
    System.out.println("\n📋 Step 1: Price Validation");
    Lens<TEOrderItem, BigDecimal> priceLens = TEOrderItemLenses.price();
    Traversal<List<TEOrderItem>, BigDecimal> allPrices =
        Traversals.<TEOrderItem>forList().andThen(priceLens.asTraversal());

    List<String> priceErrors =
        collectErrors(allPrices, price -> validatePrice(price), order.items());

    if (priceErrors.isEmpty()) {
      System.out.println("  ✅ All prices valid");
    } else {
      System.out.println("  ❌ Price validation errors:");
      priceErrors.forEach(err -> System.out.println("     • " + err));
    }

    // Step 2: Validate all quantities
    System.out.println("\n📦 Step 2: Quantity Validation");
    Lens<TEOrderItem, Integer> quantityLens = TEOrderItemLenses.quantity();
    Traversal<List<TEOrderItem>, Integer> allQuantities =
        Traversals.<TEOrderItem>forList().andThen(quantityLens.asTraversal());

    List<String> quantityErrors =
        collectErrors(allQuantities, qty -> validateQuantity(qty), order.items());

    if (quantityErrors.isEmpty()) {
      System.out.println("  ✅ All quantities valid");
    } else {
      System.out.println("  ❌ Quantity validation errors:");
      quantityErrors.forEach(err -> System.out.println("     • " + err));
    }

    // Step 3: Count valid vs invalid items
    System.out.println("\n📊 Step 3: Validation Summary");
    int validPrices = countValid(allPrices, price -> validatePrice(price), order.items());
    int validQuantities = countValid(allQuantities, qty -> validateQuantity(qty), order.items());

    System.out.println("  Valid prices: " + validPrices + "/" + order.items().size());
    System.out.println("  Valid quantities: " + validQuantities + "/" + order.items().size());

    // Step 4: Apply discount to valid items only
    System.out.println("\n💰 Step 4: Apply 10% Discount to Valid Items");
    List<TEOrderItem> discounted =
        modifyWherePossible(
            allPrices,
            price ->
                price.compareTo(BigDecimal.ZERO) > 0
                    ? Maybe.just(price.multiply(new BigDecimal("0.9")))
                    : Maybe.nothing(),
            order.items());

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
