// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.AddressLenses;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;

/**
 * A runnable example demonstrating the fluent API for optic operations. This example shows both the
 * static method style (concise) and the fluent builder style (explicit) for working with optics in
 * a Java-friendly way.
 *
 * <p>The scenario is the chapter's order service, where we need to:
 *
 * <ul>
 *   <li>Apply discounts to an order's lines
 *   <li>Update the address its consignment ships to
 *   <li>Modify order status
 *   <li>Calculate totals and validate orders
 * </ul>
 */
public class FluentOpticOpsExample {

  public static void main(String[] args) {
    System.out.println("=== FLUENT OPTIC OPERATIONS EXAMPLE ===\n");

    // Create a sample order and the consignment that will ship it
    Order order = createSampleOrder();
    Consignment consignment = createSampleConsignment(order);
    System.out.println("Original Order:");
    printOrder(order, consignment);
    System.out.println("\n" + "=".repeat(60) + "\n");

    staticMethodExamples(order, consignment);
    System.out.println("\n" + "=".repeat(60) + "\n");

    fluentBuilderExamples(order, consignment);
    System.out.println("\n" + "=".repeat(60) + "\n");

    realWorldWorkflow(order, consignment);
    System.out.println("\n" + "=".repeat(60) + "\n");

    queryingExamples(order);
    System.out.println("\n" + "=".repeat(60) + "\n");

    validationExamples(order);
  }

  // ============================================================================
  // PART 1: Static Method Style - Concise and Direct
  // ============================================================================

  private static void staticMethodExamples(Order order, Consignment consignment) {
    System.out.println("--- Part 1: Static Method Style ---\n");

    // Example 1: Get a value (Lens)
    System.out.println("Example 1: Get order status");
    OrderStatus status = OpticOps.get(order, OrderLenses.status());
    System.out.println("  Current status: " + status);
    System.out.println();

    // Example 2: Set a value (Lens)
    System.out.println("Example 2: Update order status");
    Order paidOrder = OpticOps.set(order, OrderLenses.status(), OrderStatus.PAID);
    System.out.println("  New status: " + OpticOps.get(paidOrder, OrderLenses.status()));
    System.out.println();

    // Example 3: Modify a value (Lens)
    System.out.println("Example 3: Update postcode to uppercase");
    Lens<Consignment, String> consignmentToPostcode =
        ConsignmentLenses.to().andThen(AddressLenses.postcode());
    Consignment updatedPostcode =
        OpticOps.modify(consignment, consignmentToPostcode, String::toUpperCase);
    System.out.println("  Original: " + OpticOps.get(consignment, consignmentToPostcode));
    System.out.println("  Updated:  " + OpticOps.get(updatedPostcode, consignmentToPostcode));
    System.out.println();

    // Example 4: Get all values (Traversal)
    System.out.println("Example 4: Get all line prices");
    Traversal<Order, BigDecimal> orderToAllPrices =
        OrderTraversals.lines().andThen(LineItemLenses.price().asTraversal());
    List<BigDecimal> allPrices = OpticOps.getAll(order, orderToAllPrices);
    System.out.println("  All prices: " + allPrices);
    System.out.println();

    // Example 5: Modify all values (Traversal)
    System.out.println("Example 5: Apply 10% discount to all lines");
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

  private static void fluentBuilderExamples(Order order, Consignment consignment) {
    System.out.println("--- Part 2: Fluent Builder Style ---\n");

    // Example 1: Getting values with fluent API
    System.out.println("Example 1: Get order ID");
    UUID orderId = OpticOps.getting(order).through(OrderLenses.id());
    System.out.println("  Order ID: " + orderId);
    System.out.println();

    // Example 2: Setting values with fluent API
    System.out.println("Example 2: Update city");
    Consignment updatedCity =
        OpticOps.setting(consignment)
            .through(ConsignmentLenses.to().andThen(AddressLenses.city()), "Manchester");
    String newCity =
        OpticOps.getting(updatedCity).through(ConsignmentLenses.to().andThen(AddressLenses.city()));
    System.out.println("  New city: " + newCity);
    System.out.println();

    // Example 3: Modifying with fluent API
    System.out.println("Example 3: Double all quantities");
    Traversal<Order, Integer> orderToQuantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity().asTraversal());

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
    Consignment stateUpdated =
        OpticOps.setting(consignment)
            .through(
                ConsignmentLenses.state(),
                new ConsignmentState.Dispatched(Instant.parse("2026-10-02T08:30:00Z")));
    Consignment complexUpdate =
        OpticOps.modifying(stateUpdated)
            .through(ConsignmentLenses.to().andThen(AddressLenses.postcode()), String::toUpperCase);

    System.out.println(
        "  State: " + OpticOps.getting(complexUpdate).through(ConsignmentLenses.state()));
    System.out.println(
        "  Postcode: "
            + OpticOps.getting(complexUpdate)
                .through(ConsignmentLenses.to().andThen(AddressLenses.postcode())));
    System.out.println();
  }

  // ============================================================================
  // PART 3: Real-World Workflow - Order Fulfilment
  // ============================================================================

  private static void realWorldWorkflow(Order order, Consignment consignment) {
    System.out.println("--- Part 3: Real-World Order Fulfilment Workflow ---\n");

    System.out.println("Processing order " + order.id() + "...\n");

    // Step 1: Validate order is new
    OrderStatus currentStatus = OpticOps.get(order, OrderLenses.status());
    if (currentStatus != OrderStatus.NEW) {
      System.out.println("ERROR: Order is not new, cannot process");
      return;
    }
    System.out.println("✓ Step 1: Order status validated (NEW)");

    // Step 2: Apply bulk discount for large orders
    int totalItems =
        OpticOps.getAll(
                order, OrderTraversals.lines().andThen(LineItemLenses.quantity().asTraversal()))
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
              OrderTraversals.lines().andThen(LineItemLenses.price().asTraversal()),
              price -> price.multiply(bulkDiscount).setScale(2, RoundingMode.HALF_UP));
    } else {
      System.out.println("✓ Step 2: No bulk discount (total items: " + totalItems + ")");
    }

    // Step 3: Normalise address for shipping system
    System.out.println("✓ Step 3: Normalising shipping address");
    Consignment processedConsignment =
        OpticOps.modifying(consignment)
            .through(ConsignmentLenses.to().andThen(AddressLenses.postcode()), String::toUpperCase);
    processedConsignment =
        OpticOps.modifying(processedConsignment)
            .through(
                ConsignmentLenses.to().andThen(AddressLenses.city()), city -> capitalise(city));

    // Step 4: Update status to PAID
    System.out.println("✓ Step 4: Updating order status to PAID");
    processedOrder = OpticOps.set(processedOrder, OrderLenses.status(), OrderStatus.PAID);

    // Step 5: Calculate final total, each line's price times its quantity
    BigDecimal finalTotal =
        OpticOps.getAll(processedOrder, OrderTraversals.lines()).stream()
            .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    System.out.println("✓ Step 5: Order total calculated: £" + finalTotal);

    System.out.println("\nFinal Order State:");
    printOrder(processedOrder, processedConsignment);
  }

  // ============================================================================
  // PART 4: Querying and Validation
  // ============================================================================

  private static void queryingExamples(Order order) {
    System.out.println("--- Part 4: Querying and Validation ---\n");

    Traversal<Order, BigDecimal> orderToPrices =
        OrderTraversals.lines().andThen(LineItemLenses.price().asTraversal());

    // Example 1: Check if any line is expensive
    System.out.println("Example 1: Check for expensive lines (> £100)");
    boolean hasExpensiveItems =
        OpticOps.exists(order, orderToPrices, price -> price.compareTo(new BigDecimal("100")) > 0);
    System.out.println("  Has expensive lines: " + hasExpensiveItems);
    System.out.println();

    // Example 2: Check if all lines are in valid price range
    System.out.println("Example 2: Validate all prices are positive");
    boolean allPositive =
        OpticOps.all(order, orderToPrices, price -> price.compareTo(BigDecimal.ZERO) > 0);
    System.out.println("  All prices positive: " + allPositive);
    System.out.println();

    // Example 3: Count lines
    System.out.println("Example 3: Count order lines");
    int lineCount = OpticOps.count(order, OrderTraversals.lines());
    System.out.println("  Total lines: " + lineCount);
    System.out.println();

    // Example 4: Find first line matching condition
    System.out.println("Example 4: Find first line with quantity > 2");
    Optional<LineItem> bulkLine =
        OpticOps.find(order, OrderTraversals.lines(), line -> line.quantity() > 2);
    bulkLine.ifPresentOrElse(
        line -> System.out.println("  Found: " + line.sku() + " (qty: " + line.quantity() + ")"),
        () -> System.out.println("  No bulk lines found"));
    System.out.println();

    // Example 5: Check if order has items (using fluent API)
    System.out.println("Example 5: Check if order is empty");
    boolean isEmpty = OpticOps.querying(order).isEmpty(OrderTraversals.lines());
    System.out.println("  Order is empty: " + isEmpty);
    System.out.println();

    // Example 6: Complex query with fluent API
    System.out.println("Example 6: Check if all lines have reasonable quantity (< 10)");
    Traversal<Order, Integer> orderToQuantities =
        OrderTraversals.lines().andThen(LineItemLenses.quantity().asTraversal());
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
        OrderTraversals.lines().andThen(LineItemLenses.price().asTraversal());

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
        OpticOps.set(
            order,
            OrderLenses.lines(),
            List.of(
                new LineItem("LAMP", 1, new BigDecimal("0.00")), // Invalid: not positive
                new LineItem("DESK", 2, new BigDecimal("1200.00")))); // Invalid: exceeds max

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
    System.out.println("  • modifyAllEither / allThroughEither: Multi-field, first error only");
    System.out.println();
    System.out.println("For comprehensive validation examples, see FluentValidationExample.java");
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static Order createSampleOrder() {
    return new Order(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(
            new LineItem("LAPTOP", 1, new BigDecimal("899.99")),
            new LineItem("MOUSE", 2, new BigDecimal("29.99")),
            new LineItem("USB-C-CABLE", 3, new BigDecimal("12.50")),
            new LineItem("LAPTOP-STAND", 1, new BigDecimal("45.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  // The consignment that will ship the order, still waiting to leave
  private static Consignment createSampleConsignment(Order order) {
    return new Consignment(
        order.id(),
        new Address("123 High Street", "london", "sw1a 1aa"),
        new ConsignmentState.Pending());
  }

  private static void printOrder(Order order, Consignment consignment) {
    System.out.println("  Order ID: " + order.id());
    System.out.println("  Status: " + order.status());
    System.out.println("  Lines:");
    order
        .lines()
        .forEach(
            line ->
                System.out.println(
                    "    - " + line.sku() + " x" + line.quantity() + " @ £" + line.price()));
    System.out.println("  Shipping to:");
    System.out.println("    " + consignment.to().street());
    System.out.println("    " + consignment.to().city() + ", " + consignment.to().postcode());
  }

  private static String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
  }
}
