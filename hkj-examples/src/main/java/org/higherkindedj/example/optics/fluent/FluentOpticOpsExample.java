// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.example.optics.fluent.generated.OrderItemLenses;
import org.higherkindedj.example.optics.fluent.generated.OrderLenses;
import org.higherkindedj.example.optics.fluent.generated.OrderTraversals;
import org.higherkindedj.example.optics.fluent.generated.ShippingAddressLenses;
import org.higherkindedj.example.optics.fluent.model.Order;
import org.higherkindedj.example.optics.fluent.model.OrderItem;
import org.higherkindedj.example.optics.fluent.model.OrderStatus;
import org.higherkindedj.example.optics.fluent.model.ShippingAddress;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A runnable example demonstrating the fluent API for optic operations. This example shows both the
 * static method style (concise) and the fluent builder style (explicit) for working with optics in
 * a Java-friendly way.
 *
 * <p>The scenario is an e-commerce order processing system where we need to:
 *
 * <ul>
 *   <li>Apply discounts to order items
 *   <li>Update shipping addresses
 *   <li>Modify order status
 *   <li>Calculate totals and validate orders
 * </ul>
 */
public class FluentOpticOpsExample {

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== FLUENT OPTIC OPERATIONS EXAMPLE ===\n");

    // Create a sample order
    Order order = createSampleOrder();
    System.out.println("Original Order:");
    printOrder(order);
    System.out.println("\n" + "=".repeat(60) + "\n");

    staticMethodExamples(order);
    System.out.println("\n" + "=".repeat(60) + "\n");

    fluentBuilderExamples(order);
    System.out.println("\n" + "=".repeat(60) + "\n");

    realWorldWorkflow(order);
    System.out.println("\n" + "=".repeat(60) + "\n");

    queryingExamples(order);
    System.out.println("\n" + "=".repeat(60) + "\n");

    validationExamples(order);
  }

  // ============================================================================
  // PART 1: Static Method Style - Concise and Direct
  // ============================================================================

  private static void staticMethodExamples(Order order) {
    System.out.println("--- Part 1: Static Method Style ---\n");

    // Example 1: Get a value (Lens)
    System.out.println("Example 1: Get order status");
    OrderStatus status = OpticOps.get(order, OrderLenses.status());
    System.out.println("  Current status: " + status);
    System.out.println();

    // Example 2: Set a value (Lens)
    System.out.println("Example 2: Update order status");
    Order processingOrder = OpticOps.set(order, OrderLenses.status(), OrderStatus.PROCESSING);
    System.out.println("  New status: " + OpticOps.get(processingOrder, OrderLenses.status()));
    System.out.println();

    // Example 3: Modify a value (Lens)
    System.out.println("Example 3: Update postcode to uppercase");
    Lens<Order, String> orderToPostCode =
        OrderLenses.address().andThen(ShippingAddressLenses.postCode());
    Order updatedPostCode = OpticOps.modify(order, orderToPostCode, String::toUpperCase);
    System.out.println("  Original: " + OpticOps.get(order, orderToPostCode));
    System.out.println("  Updated:  " + OpticOps.get(updatedPostCode, orderToPostCode));
    System.out.println();

    // Example 4: Get all values (Traversal)
    System.out.println("Example 4: Get all item prices");
    Traversal<Order, BigDecimal> orderToAllPrices =
        OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal());
    List<BigDecimal> allPrices = OpticOps.getAll(order, orderToAllPrices);
    System.out.println("  All prices: " + allPrices);
    System.out.println();

    // Example 5: Modify all values (Traversal)
    System.out.println("Example 5: Apply 10% discount to all items");
    BigDecimal discountMultiplier = new BigDecimal("0.90");
    Order discountedOrder =
        OpticOps.modifyAll(
            order,
            orderToAllPrices,
            price -> price.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP));
    System.out.println("  Original prices: " + OpticOps.getAll(order, orderToAllPrices));
    System.out.println(
        "  Discounted prices: " + OpticOps.getAll(discountedOrder, orderToAllPrices));
    System.out.println();
  }

  // ============================================================================
  // PART 2: Fluent Builder Style - Explicit and Discoverable
  // ============================================================================

  private static void fluentBuilderExamples(Order order) {
    System.out.println("--- Part 2: Fluent Builder Style ---\n");

    // Example 1: Getting values with fluent API
    System.out.println("Example 1: Get order ID");
    String orderId = OpticOps.getting(order).through(OrderLenses.orderId());
    System.out.println("  Order ID: " + orderId);
    System.out.println();

    // Example 2: Setting values with fluent API
    System.out.println("Example 2: Update city");
    Order updatedCity =
        OpticOps.setting(order)
            .through(OrderLenses.address().andThen(ShippingAddressLenses.city()), "Manchester");
    String newCity =
        OpticOps.getting(updatedCity)
            .through(OrderLenses.address().andThen(ShippingAddressLenses.city()));
    System.out.println("  New city: " + newCity);
    System.out.println();

    // Example 3: Modifying with fluent API
    System.out.println("Example 3: Double all quantities");
    Traversal<Order, Integer> orderToQuantities =
        OrderTraversals.items().andThen(OrderItemLenses.quantity().asTraversal());

    Order doubledQuantities =
        OpticOps.modifying(order).allThrough(orderToQuantities, quantity -> quantity * 2);

    System.out.println(
        "  Original quantities: " + OpticOps.getting(order).allThrough(orderToQuantities));
    System.out.println(
        "  Doubled quantities: "
            + OpticOps.getting(doubledQuantities).allThrough(orderToQuantities));
    System.out.println();

    // Example 4: Chaining multiple operations
    System.out.println("Example 4: Complex update chain");
    Order statusUpdated =
        OpticOps.setting(order).through(OrderLenses.status(), OrderStatus.PROCESSING);
    Order complexUpdate =
        OpticOps.modifying(statusUpdated)
            .through(
                OrderLenses.address().andThen(ShippingAddressLenses.postCode()),
                String::toUpperCase);

    System.out.println(
        "  Status: " + OpticOps.getting(complexUpdate).through(OrderLenses.status()));
    System.out.println(
        "  Postcode: "
            + OpticOps.getting(complexUpdate)
                .through(OrderLenses.address().andThen(ShippingAddressLenses.postCode())));
    System.out.println();
  }

  // ============================================================================
  // PART 3: Real-World Workflow - Order Fulfilment
  // ============================================================================

  private static void realWorldWorkflow(Order order) {
    System.out.println("--- Part 3: Real-World Order Fulfilment Workflow ---\n");

    System.out.println("Processing order " + order.orderId() + "...\n");

    // Step 1: Validate order is pending
    OrderStatus currentStatus = OpticOps.get(order, OrderLenses.status());
    if (currentStatus != OrderStatus.PENDING) {
      System.out.println("ERROR: Order is not pending, cannot process");
      return;
    }
    System.out.println("✓ Step 1: Order status validated (PENDING)");

    // Step 2: Apply bulk discount for large orders
    int totalItems =
        OpticOps.getAll(
                order, OrderTraversals.items().andThen(OrderItemLenses.quantity().asTraversal()))
            .stream()
            .mapToInt(Integer::intValue)
            .sum();

    Order processedOrder = order;
    if (totalItems >= 5) {
      System.out.println("✓ Step 2: Applying 15% bulk discount (total items: " + totalItems + ")");
      BigDecimal bulkDiscount = new BigDecimal("0.85");
      processedOrder =
          OpticOps.modifyAll(
              processedOrder,
              OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal()),
              price -> price.multiply(bulkDiscount).setScale(2, RoundingMode.HALF_UP));
    } else {
      System.out.println("✓ Step 2: No bulk discount (total items: " + totalItems + ")");
    }

    // Step 3: Normalise address for shipping system
    System.out.println("✓ Step 3: Normalising shipping address");
    processedOrder =
        OpticOps.modifying(processedOrder)
            .through(
                OrderLenses.address().andThen(ShippingAddressLenses.postCode()),
                String::toUpperCase);
    processedOrder =
        OpticOps.modifying(processedOrder)
            .through(
                OrderLenses.address().andThen(ShippingAddressLenses.city()),
                city -> capitalise(city));

    // Step 4: Update status to PROCESSING
    System.out.println("✓ Step 4: Updating order status to PROCESSING");
    processedOrder = OpticOps.set(processedOrder, OrderLenses.status(), OrderStatus.PROCESSING);

    // Step 5: Calculate final total
    BigDecimal finalTotal =
        OpticOps.getAll(
                processedOrder,
                OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal()))
            .stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    System.out.println("✓ Step 5: Order total calculated: £" + finalTotal);

    System.out.println("\nFinal Order State:");
    printOrder(processedOrder);
  }

  // ============================================================================
  // PART 4: Querying and Validation
  // ============================================================================

  private static void queryingExamples(Order order) {
    System.out.println("--- Part 4: Querying and Validation ---\n");

    Traversal<Order, BigDecimal> orderToPrices =
        OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal());

    // Example 1: Check if any item is expensive
    System.out.println("Example 1: Check for expensive items (> £100)");
    boolean hasExpensiveItems =
        OpticOps.exists(order, orderToPrices, price -> price.compareTo(new BigDecimal("100")) > 0);
    System.out.println("  Has expensive items: " + hasExpensiveItems);
    System.out.println();

    // Example 2: Check if all items are in valid price range
    System.out.println("Example 2: Validate all prices are positive");
    boolean allPositive =
        OpticOps.all(order, orderToPrices, price -> price.compareTo(BigDecimal.ZERO) > 0);
    System.out.println("  All prices positive: " + allPositive);
    System.out.println();

    // Example 3: Count items
    System.out.println("Example 3: Count order items");
    int itemCount = OpticOps.count(order, OrderTraversals.items());
    System.out.println("  Total items: " + itemCount);
    System.out.println();

    // Example 4: Find first item matching condition
    System.out.println("Example 4: Find first item with quantity > 2");
    Optional<OrderItem> bulkItem =
        OpticOps.find(order, OrderTraversals.items(), item -> item.quantity() > 2);
    bulkItem.ifPresentOrElse(
        item ->
            System.out.println(
                "  Found: " + item.productName() + " (qty: " + item.quantity() + ")"),
        () -> System.out.println("  No bulk items found"));
    System.out.println();

    // Example 5: Check if order has items (using fluent API)
    System.out.println("Example 5: Check if order is empty");
    boolean isEmpty = OpticOps.querying(order).isEmpty(OrderTraversals.items());
    System.out.println("  Order is empty: " + isEmpty);
    System.out.println();

    // Example 6: Complex query with fluent API
    System.out.println("Example 6: Check if all items have reasonable quantity (< 10)");
    Traversal<Order, Integer> orderToQuantities =
        OrderTraversals.items().andThen(OrderItemLenses.quantity().asTraversal());
    boolean allReasonable = OpticOps.querying(order).allMatch(orderToQuantities, qty -> qty < 10);
    System.out.println("  All quantities reasonable: " + allReasonable);
  }

  // ============================================================================
  // PART 5: Validation-Aware Modifications
  // ============================================================================

  private static void validationExamples(Order order) {
    System.out.println("--- Part 5: Validation-Aware Modifications ---\n");

    System.out.println(
        "OpticOps provides validation-aware modification methods that integrate with");
    System.out.println("higher-kinded-j core types (Either, Maybe, Validated):\n");

    // Example 1: Validate and modify with Either (short-circuiting)
    System.out.println("Example 1: Validate price with Either (short-circuits on error)");
    Traversal<Order, BigDecimal> orderToPrices =
        OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal());

    // Simulate validation: prices must be positive
    Either<String, Order> validatedOrder =
        OpticOps.modifyAllEither(
            order,
            orderToPrices,
            price -> {
              if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return Either.left("Price must be positive: " + price);
              }
              return Either.right(price);
            });

    validatedOrder.fold(
        error -> {
          System.out.println("  ✗ Validation failed: " + error);
          return null;
        },
        validOrder -> {
          System.out.println("  ✓ All prices validated successfully");
          return null;
        });
    System.out.println();

    // Example 2: Validate and modify with Validated (accumulates all errors)
    System.out.println("Example 2: Validate prices with Validated (accumulates errors)");

    // Create an order with some invalid prices for demonstration
    Order testOrder =
        new Order(
            "TEST-001",
            OrderStatus.PENDING,
            List.of(
                new OrderItem("P1", "Item 1", 1, new BigDecimal("50.00")),
                new OrderItem("P2", "Item 2", 2, new BigDecimal("100.00"))),
            order.address());

    Validated<List<String>, Order> validatedWithAccumulation =
        OpticOps.modifyAllValidated(
            testOrder,
            orderToPrices,
            price -> {
              if (price.compareTo(new BigDecimal("1000")) > 0) {
                return Validated.invalid("Price exceeds maximum: " + price);
              }
              if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return Validated.invalid("Price must be positive: " + price);
              }
              return Validated.valid(price);
            });

    validatedWithAccumulation.fold(
        errors -> {
          System.out.println("  ✗ Validation failed with " + errors.size() + " error(s):");
          errors.forEach(error -> System.out.println("    - " + error));
          return null;
        },
        validOrder -> {
          System.out.println("  ✓ All prices validated (all constraints met)");
          return null;
        });
    System.out.println();

    // Example 3: Fluent builder style
    System.out.println("Example 3: Using fluent builder for validation");

    Either<String, Order> fluentValidation =
        OpticOps.modifyingWithValidation(order)
            .allThroughEither(
                orderToPrices,
                price ->
                    price.compareTo(BigDecimal.ZERO) > 0
                        ? Either.right(price)
                        : Either.left("Invalid price: " + price));

    fluentValidation.fold(
        error -> {
          System.out.println("  ✗ " + error);
          return null;
        },
        validOrder -> {
          System.out.println("  ✓ Validation successful using fluent API");
          return null;
        });
    System.out.println();

    System.out.println("Validation Methods Summary:");
    System.out.println("  • modifyEither / throughEither: Single field, short-circuit on error");
    System.out.println("  • modifyMaybe / throughMaybe: Optional validation, no error details");
    System.out.println(
        "  • modifyAllValidated / allThroughValidated: Multi-field, accumulate all errors");
    System.out.println(
        "  • modifyAllEither / allThroughEither: Multi-field, short-circuit on first error");
    System.out.println();
    System.out.println("For comprehensive validation examples, see FluentValidationExample.java");
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static Order createSampleOrder() {
    return new Order(
        "ORD-2025-001",
        OrderStatus.PENDING,
        List.of(
            new OrderItem("PROD-101", "Laptop", 1, new BigDecimal("899.99")),
            new OrderItem("PROD-202", "Wireless Mouse", 2, new BigDecimal("29.99")),
            new OrderItem("PROD-303", "USB-C Cable", 3, new BigDecimal("12.50")),
            new OrderItem("PROD-404", "Laptop Stand", 1, new BigDecimal("45.00"))),
        new ShippingAddress("123 High Street", "london", "sw1a 1aa", "United Kingdom"));
  }

  private static void printOrder(Order order) {
    System.out.println("  Order ID: " + order.orderId());
    System.out.println("  Status: " + order.status());
    System.out.println("  Items:");
    order
        .items()
        .forEach(
            item ->
                System.out.println(
                    "    - "
                        + item.productName()
                        + " x"
                        + item.quantity()
                        + " @ £"
                        + item.price()));
    System.out.println("  Shipping to:");
    System.out.println("    " + order.address().street());
    System.out.println("    " + order.address().city() + ", " + order.address().postCode());
    System.out.println("    " + order.address().country());
  }

  private static String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
  }
}
