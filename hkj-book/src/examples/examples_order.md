# Order Processing Workflow

A production-quality example demonstrating how functional patterns solve real business problems.

---

## Overview

The Order Processing Workflow is a comprehensive e-commerce example that processes customer orders through multiple stages: validation, inventory reservation, payment processing, shipment creation, and notification. It showcases how Higher-Kinded-J patterns handle complexity without sacrificing readability.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ORDER PROCESSING PIPELINE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   Request ──▶ Validate ──▶ Customer ──▶ Inventory ──▶ Discount          │
│                  │            │            │            │               │
│                  ▼            ▼            ▼            ▼               │
│              Address?     Exists?      In Stock?    Valid Code?         │
│              Postcode?    Eligible?    Reserved?    Loyalty Tier?       │
│                                                                         │
│   ──▶ Payment ──▶ Shipment ──▶ Notification ──▶ Result                  │
│          │           │             │                                    │
│          ▼           ▼             ▼                                    │
│       Approved?   Created?     Sent?                                    │
│       Funds?      Carrier?     (non-critical)                           │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Patterns Demonstrated

### Typed Error Hierarchies

Domain errors modelled as a sealed interface hierarchy for exhaustive pattern matching:

```java
public sealed interface OrderError {
    record ValidationError(String field, String message) implements OrderError {}
    record CustomerNotFound(CustomerId id) implements OrderError {}
    record InsufficientInventory(ProductId id, int requested, int available) implements OrderError {}
    record PaymentDeclined(String reason) implements OrderError {}
    record ShipmentFailed(String carrier, String reason) implements OrderError {}
}
```

### ForPath Comprehensions

The entire workflow composes into a single flat `ForPath` comprehension (8 steps, well within the arity-12 limit):

```java
public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());

    return ForPath.from(validateShippingAddress(request.shippingAddress()))  // 1. address
        .from(validAddress -> lookupAndValidateCustomer(customerId))         // 2. customer
        .from(t -> buildValidatedOrder(orderId, request, t._2(), t._1()))    // 3. order
        .from(t -> reserveInventory(t._3().orderId(), t._3().lines()))       // 4. reservation
        .from(t -> applyDiscounts(t._3(), t._2()))                           // 5. discount
        .from(t -> processPayment(t._3(), t._5()))                           // 6. payment
        .from(t -> createShipment(t._3(), t._1()))                           // 7. shipment
        .from(t -> sendNotifications(t._3(), t._2(), t._5()))                // 8. notification
        .yield((validAddress, customer, order, reservation, discount,
                payment, shipment, notification) ->
            buildOrderResult(order, discount, payment, shipment, notification));
}
```

### Resilience Patterns

Built-in retry policies and timeouts for external service calls:

```java
private EitherPath<OrderError, PaymentConfirmation> processPaymentWithRetry(
        Customer customer, Money amount) {
    return Path.io(() -> paymentService.charge(customer, amount))
        .retry(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)))
        .timeout(Duration.ofSeconds(5))
        .mapError(e -> new PaymentDeclined(e.getMessage()));
}
```

### Focus DSL Integration

Immutable state updates using generated lenses:

```java
// Update nested order status
Order updated = OrderFocus.status().set(order, OrderStatus.CONFIRMED);

// Modify all line item quantities
Order adjusted = OrderFocus.lines()
    .traverseEach()
    .compose(OrderLineFocus.quantity())
    .modify(order, qty -> qty + 1);
```

---

## Source Files

| File | Description | Run Command |
|------|-------------|-------------|
| [OrderWorkflowDemo.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/runner/OrderWorkflowDemo.java) | Main demo runner | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.runner.OrderWorkflowDemo` |
| [EnhancedOrderWorkflowDemo.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/runner/EnhancedOrderWorkflowDemo.java) | Demo with concurrency | `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.order.runner.EnhancedOrderWorkflowDemo` |
| [OrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflow.java) | Core workflow | View source |
| [ConfigurableOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/ConfigurableOrderWorkflow.java) | Feature flags and resilience | View source |
| [EnhancedOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/EnhancedOrderWorkflow.java) | VTask concurrency patterns | View source |

### Domain Model

| File | Description |
|------|-------------|
| [OrderError.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/error/OrderError.java) | Sealed error hierarchy |
| [OrderRequest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/model/OrderRequest.java) | Input request model |
| [ValidatedOrder.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/model/ValidatedOrder.java) | Post-validation model |
| [OrderResult.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/model/OrderResult.java) | Workflow result |

### Services

| File | Description |
|------|-------------|
| [CustomerService.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/service/CustomerService.java) | Customer lookup |
| [InventoryService.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/service/InventoryService.java) | Stock reservation |
| [PaymentService.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/service/PaymentService.java) | Payment processing |
| [ShippingService.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/service/ShippingService.java) | Shipment creation |

### Extended Workflows

| File | Description |
|------|-------------|
| [PartialFulfilmentWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/PartialFulfilmentWorkflow.java) | Handling partial inventory |
| [SplitShipmentWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/SplitShipmentWorkflow.java) | Multi-warehouse shipping |
| [OrderCancellationWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderCancellationWorkflow.java) | Cancellation with rollback |

---

## Project Structure

```
hkj-examples/src/main/java/org/higherkindedj/example/order/
├── config/
│   └── WorkflowConfig.java        # Feature flags and configuration
├── context/
│   └── OrderContext.java          # Execution context
├── error/
│   └── OrderError.java            # Sealed error hierarchy
├── model/
│   ├── OrderRequest.java          # Input models
│   ├── ValidatedOrder.java        # Domain models
│   ├── OrderResult.java           # Result types
│   └── value/                     # Value objects (Money, OrderId, etc.)
├── resilience/
│   ├── Resilience.java            # Retry utilities
│   └── RetryPolicy.java           # Policy definitions
├── runner/
│   ├── OrderWorkflowDemo.java     # Main runner
│   └── EnhancedOrderWorkflowDemo.java
├── service/
│   ├── CustomerService.java       # Service interfaces
│   ├── InventoryService.java
│   └── impl/                      # In-memory implementations
└── workflow/
    ├── OrderWorkflow.java         # Core workflow
    ├── ConfigurableOrderWorkflow.java
    ├── EnhancedOrderWorkflow.java
    └── FocusDSLExamples.java      # Focus DSL usage
```

---

## Related Documentation

- [Order Walkthrough](../hkts/order-walkthrough.md) – Step-by-step guide to the workflow
- [Effect Composition](../hkts/order-composition.md) – Detailed pattern explanations
- [Production Patterns](../hkts/order-production.md) – Feature flags, retries, and configuration
- [Concurrency and Scale](../hkts/order-concurrency.md) – VTask, Scope, and structured concurrency

---

**Next:** [Draughts Game](examples_draughts.md)
