// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import module java.base;
import module org.higherkindedj.core;

/**
 * A runnable example demonstrating type-safe state machines using prisms for state transitions.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe state representation with sealed interfaces
 *   <li>State validation using {@code matches()} before transitions
 *   <li>Conditional state transitions with {@code modifyWhen()}
 *   <li>State-specific data access using {@code getOptional()} and {@code mapOptional()}
 *   <li>Complex state transition logic with composed prisms
 * </ul>
 */
public class StateMachineExample {

  // Order processing state machine
  @GeneratePrisms
  public sealed interface OrderState
      permits Draft, Submitted, Processing, Shipped, Delivered, Cancelled {}

  @GenerateLenses
  public record Draft(String orderId, List<String> items) implements OrderState {}

  @GenerateLenses
  public record Submitted(String orderId, List<String> items, Instant submittedAt)
      implements OrderState {}

  @GenerateLenses
  public record Processing(
      String orderId, List<String> items, String warehouseId, Instant startedAt)
      implements OrderState {}

  public record Shipped(
      String orderId, List<String> items, String trackingNumber, String carrier, Instant shippedAt)
      implements OrderState {}

  public record Delivered(String orderId, Instant deliveredAt, String signature)
      implements OrderState {}

  public record Cancelled(String orderId, String reason, Instant cancelledAt)
      implements OrderState {}

  // Prisms for state access
  private static final Prism<OrderState, Draft> DRAFT = OrderStatePrisms.draft();
  private static final Prism<OrderState, Submitted> SUBMITTED = OrderStatePrisms.submitted();
  private static final Prism<OrderState, Processing> PROCESSING = OrderStatePrisms.processing();
  private static final Prism<OrderState, Shipped> SHIPPED = OrderStatePrisms.shipped();
  private static final Prism<OrderState, Delivered> DELIVERED = OrderStatePrisms.delivered();
  private static final Prism<OrderState, Cancelled> CANCELLED = OrderStatePrisms.cancelled();

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== State Machine with Prisms ===\n");

    demonstrateBasicTransitions();
    demonstrateConditionalTransitions();
    demonstrateInvalidTransitions();
    demonstrateStateQueries();
    demonstrateComplexWorkflow();
  }

  private static void demonstrateBasicTransitions() {
    System.out.println("--- Basic State Transitions ---");

    // Create a draft order
    OrderState state = new Draft("ORD-001", List.of("Laptop", "Mouse"));
    System.out.println("Initial state: " + state.getClass().getSimpleName());

    // Transition: Draft -> Submitted
    OrderState submitted = transitionToSubmitted(state);
    System.out.println("After submit: " + submitted.getClass().getSimpleName());

    // Transition: Submitted -> Processing
    OrderState processing = transitionToProcessing(submitted, "WH-LONDON");
    System.out.println("After processing: " + processing.getClass().getSimpleName());

    // Transition: Processing -> Shipped
    OrderState shipped = transitionToShipped(processing, "TRACK-123", "DHL");
    System.out.println("After shipping: " + shipped.getClass().getSimpleName());

    // Transition: Shipped -> Delivered
    OrderState delivered = transitionToDelivered(shipped, "John Doe");
    System.out.println("After delivery: " + delivered.getClass().getSimpleName());

    System.out.println();
  }

  private static void demonstrateConditionalTransitions() {
    System.out.println("--- Conditional State Transitions ---");

    OrderState draft = new Draft("ORD-002", List.of());
    System.out.println("Draft order with empty items: " + draft);

    // Cannot submit an order with no items
    OrderState result = transitionToSubmitted(draft);
    System.out.println("After submit attempt: " + result.getClass().getSimpleName());
    System.out.println("  (Remains in Draft because items list is empty)");

    // Add items and try again
    OrderState draftWithItems = new Draft("ORD-002", List.of("Keyboard"));
    OrderState submitted = transitionToSubmitted(draftWithItems);
    System.out.println("After submit with items: " + submitted.getClass().getSimpleName());

    // Cancel if warehouse is unavailable
    OrderState processing =
        new Processing("ORD-003", List.of("Monitor"), "WH-UNAVAILABLE", Instant.now());
    System.out.println("\nProcessing order at unavailable warehouse: " + processing);

    OrderState maybeCancelled =
        PROCESSING
            .getOptional(processing)
            .filter(proc -> proc.warehouseId().equals("WH-UNAVAILABLE"))
            .map(
                proc ->
                    (OrderState) new Cancelled("ORD-003", "Warehouse unavailable", Instant.now()))
            .orElse(processing);

    if (CANCELLED.matches(maybeCancelled)) {
      System.out.println("Order was cancelled due to warehouse issue");
      System.out.println(
          "  Reason: "
              + CANCELLED.mapOptional(Cancelled::reason, maybeCancelled).orElse("Unknown"));
    }

    System.out.println();
  }

  private static void demonstrateInvalidTransitions() {
    System.out.println("--- Invalid Transition Handling ---");

    // Try to ship a draft order (invalid)
    OrderState draft = new Draft("ORD-004", List.of("Tablet"));
    System.out.println("Attempting to ship a draft order...");

    // This will fail because the order is not in Processing state
    OrderState result = transitionToShipped(draft, "TRACK-456", "UPS");

    if (DRAFT.matches(result)) {
      System.out.println("  Transition rejected: Order still in Draft state");
    }

    // Try to deliver a submitted order (invalid)
    OrderState submitted = new Submitted("ORD-005", List.of("Phone"), Instant.now());
    System.out.println("\nAttempting to deliver a submitted order...");

    OrderState result2 = transitionToDelivered(submitted, "Jane Smith");

    if (SUBMITTED.matches(result2)) {
      System.out.println("  Transition rejected: Order still in Submitted state");
    }

    System.out.println();
  }

  private static void demonstrateStateQueries() {
    System.out.println("--- State Queries ---");

    List<OrderState> orders =
        List.of(
            new Draft("ORD-001", List.of("Item1")),
            new Submitted("ORD-002", List.of("Item2"), Instant.now()),
            new Processing("ORD-003", List.of("Item3"), "WH-001", Instant.now()),
            new Shipped("ORD-004", List.of("Item4"), "TRACK-001", "DHL", Instant.now()),
            new Delivered("ORD-005", Instant.now(), "Customer"),
            new Cancelled("ORD-006", "Out of stock", Instant.now()));

    // Check if orders are in specific states
    System.out.println("Orders by state:");
    System.out.println("  Draft:      " + orders.stream().filter(DRAFT::matches).count());
    System.out.println("  Submitted:  " + orders.stream().filter(SUBMITTED::matches).count());
    System.out.println("  Processing: " + orders.stream().filter(PROCESSING::matches).count());
    System.out.println("  Shipped:    " + orders.stream().filter(SHIPPED::matches).count());
    System.out.println("  Delivered:  " + orders.stream().filter(DELIVERED::matches).count());
    System.out.println("  Cancelled:  " + orders.stream().filter(CANCELLED::matches).count());

    // Extract all tracking numbers for shipped orders
    List<String> trackingNumbers =
        orders.stream()
            .flatMap(order -> SHIPPED.mapOptional(Shipped::trackingNumber, order).stream())
            .toList();

    System.out.println("\nTracking numbers: " + trackingNumbers);

    // Check if any orders are awaiting action (Draft or Submitted)
    List<String> awaitingOrderIds =
        orders.stream()
            .flatMap(
                order ->
                    DRAFT
                        .mapOptional(Draft::orderId, order)
                        .or(() -> SUBMITTED.mapOptional(Submitted::orderId, order))
                        .stream())
            .toList();

    System.out.println("Orders awaiting action: " + awaitingOrderIds);

    System.out.println();
  }

  private static void demonstrateComplexWorkflow() {
    System.out.println("--- Complex Workflow with Branching ---");

    OrderState order = new Draft("ORD-100", List.of("Premium Laptop", "Extended Warranty"));

    System.out.println("Starting order: " + order.getClass().getSimpleName());

    // Submit the order
    order = transitionToSubmitted(order);
    System.out.println("1. Submitted order");

    // Start processing
    order = transitionToProcessing(order, "WH-PREMIUM");
    System.out.println("2. Processing at premium warehouse");

    // Check if high-value order (simulate)
    Optional<Processing> processingDetails = PROCESSING.getOptional(order);
    boolean isHighValue =
        processingDetails
            .map(proc -> proc.items().stream().anyMatch(item -> item.contains("Premium")))
            .orElse(false);

    if (isHighValue) {
      System.out.println("3. High-value order detected - using express shipping");
      order = transitionToShipped(order, "EXPRESS-TRACK-001", "FedEx Express");
    } else {
      System.out.println("3. Standard shipping");
      order = transitionToShipped(order, "STD-TRACK-001", "Standard Post");
    }

    // Deliver with signature required for high-value
    String carrier = SHIPPED.mapOptional(Shipped::carrier, order).orElse("Unknown");

    String signature = carrier.contains("Express") ? "Signature Required" : "Left at door";
    order = transitionToDelivered(order, signature);

    System.out.println("4. Delivered with: " + signature);

    String finalSignature = DELIVERED.mapOptional(Delivered::signature, order).orElse("N/A");

    System.out.println("\nFinal state: " + order.getClass().getSimpleName());
    System.out.println("Delivery signature: " + finalSignature);

    System.out.println();
  }

  // State transition methods

  private static OrderState transitionToSubmitted(OrderState state) {
    return DRAFT
        .getOptional(state)
        .filter(draft -> !draft.items().isEmpty())
        .map(draft -> (OrderState) new Submitted(draft.orderId(), draft.items(), Instant.now()))
        .orElse(state);
  }

  private static OrderState transitionToProcessing(OrderState state, String warehouseId) {
    return SUBMITTED
        .getOptional(state)
        .map(
            submitted ->
                (OrderState)
                    PROCESSING.build(
                        new Processing(
                            submitted.orderId(), submitted.items(), warehouseId, Instant.now())))
        .orElse(state);
  }

  private static OrderState transitionToShipped(
      OrderState state, String trackingNumber, String carrier) {
    return PROCESSING
        .getOptional(state)
        .map(
            processing ->
                (OrderState)
                    SHIPPED.build(
                        new Shipped(
                            processing.orderId(),
                            processing.items(),
                            trackingNumber,
                            carrier,
                            Instant.now())))
        .orElse(state);
  }

  private static OrderState transitionToDelivered(OrderState state, String signature) {
    return SHIPPED
        .getOptional(state)
        .map(
            shipped ->
                (OrderState)
                    DELIVERED.build(new Delivered(shipped.orderId(), Instant.now(), signature)))
        .orElse(state);
  }

  private static OrderState cancelOrder(OrderState state, String reason) {
    // Can cancel from Draft, Submitted, or Processing states
    return DRAFT
        .mapOptional(Draft::orderId, state)
        .or(() -> SUBMITTED.mapOptional(Submitted::orderId, state))
        .or(() -> PROCESSING.mapOptional(Processing::orderId, state))
        .map(orderId -> (OrderState) new Cancelled(orderId, reason, Instant.now()))
        .orElse(state);
  }
}
