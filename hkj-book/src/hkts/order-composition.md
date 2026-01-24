# Order Workflow: Effect Composition

This page covers the core composition patterns used in the order workflow: typed error hierarchies, the `via()` chain pattern, `ForPath` comprehensions, and recovery strategies.

~~~admonish info title="What You'll Learn"
- Modelling domain errors with sealed interfaces for exhaustive handling
- Composing multi-step workflows with `EitherPath` and `via()` chains
- Using `ForPath` comprehensions for readable sequential composition
- Implementing recovery patterns for non-fatal errors
~~~

~~~admonish example title="See Example Code"
- [OrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflow.java) - Main workflow implementation
- [OrderError.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/error/OrderError.java) - Sealed error hierarchy
~~~

---

## Building Block: The Sealed Error Hierarchy

The foundation of type-safe error handling is a sealed interface:

```java
@GeneratePrisms
public sealed interface OrderError
    permits ValidationError, CustomerError, InventoryError,
            DiscountError, PaymentError, ShippingError,
            NotificationError, SystemError {

    String code();
    String message();
    Instant timestamp();
    Map<String, Object> context();
}
```

Each variant carries domain-specific information:

```java
record CustomerError(
    String code,
    String message,
    Instant timestamp,
    Map<String, Object> context,
    String customerId
) implements OrderError {

    public static CustomerError notFound(String customerId) {
        return new CustomerError(
            "CUSTOMER_NOT_FOUND",
            "Customer not found: " + customerId,
            Instant.now(),
            Map.of("customerId", customerId),
            customerId
        );
    }

    public static CustomerError suspended(String customerId, String reason) {
        return new CustomerError(
            "CUSTOMER_SUSPENDED",
            "Customer account suspended: " + reason,
            Instant.now(),
            Map.of("customerId", customerId, "reason", reason),
            customerId
        );
    }
}
```

The `@GeneratePrisms` annotation creates optics for each variant, enabling type-safe pattern matching in functional pipelines.

### Why Sealed Interfaces Matter

```java
// Exhaustive matching - compiler ensures all cases handled
public String getUserFriendlyMessage(OrderError error) {
    return switch (error) {
        case ValidationError e  -> "Please check your order: " + e.message();
        case CustomerError e    -> "Account issue: " + e.message();
        case InventoryError e   -> "Stock issue: " + e.message();
        case DiscountError e    -> "Discount issue: " + e.message();
        case PaymentError e     -> "Payment issue: " + e.message();
        case ShippingError e    -> "Shipping issue: " + e.message();
        case NotificationError e -> "Order processed (notification pending)";
        case SystemError e      -> "System error - please try again";
    };
}
```

Add a new error type, and the compiler tells you everywhere that needs updating.

---

## Composing the Workflow with `via()`

The `via()` method is the workhorse of Effect Path composition. It chains computations where each step depends on the previous result:

```java
private EitherPath<OrderError, OrderResult> processOrderCore(
    ValidatedOrder order, Customer customer) {

    return reserveInventory(order.orderId(), order.lines())
        .via(reservation ->
            applyDiscounts(order, customer)
                .via(discount ->
                    processPayment(order, discount)
                        .via(payment ->
                            createShipment(order, order.shippingAddress())
                                .via(shipment ->
                                    sendNotifications(order, customer, discount)
                                        .map(notification ->
                                            buildOrderResult(order, discount,
                                                payment, shipment, notification))))));
}
```

Each step:
1. Receives the success value from the previous step
2. Returns a new `EitherPath`
3. Automatically propagates errors (if the previous step failed, this step is skipped)

### Individual Steps Are Simple

```java
private EitherPath<OrderError, InventoryReservation> reserveInventory(
    OrderId orderId, List<ValidatedOrderLine> lines) {
    return Path.either(inventoryService.reserve(orderId, lines));
}

private EitherPath<OrderError, PaymentConfirmation> processPayment(
    ValidatedOrder order, DiscountResult discount) {
    return Path.either(
        paymentService.processPayment(
            order.orderId(),
            discount.finalTotal(),
            order.paymentMethod()));
}
```

The `Path.either()` factory lifts an `Either<E, A>` into an `EitherPath<E, A>`. Your services return `Either`; the workflow composes them with `via()`.

---

## Pattern Spotlight: ForPath Comprehensions

For workflows with several sequential steps, `ForPath` provides a cleaner syntax:

```java
private EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return ForPath.from(lookupCustomer(customerId))
        .from(this::validateCustomerEligibility)
        .yield((found, validated) -> validated);
}
```

This is equivalent to nested `via()` calls but reads more naturally for simple sequences.

### When to Use ForPath vs via()

| Pattern | Best For |
|---------|----------|
| `ForPath` | 2-3 sequential steps with simple dependencies |
| `via()` chains | Longer chains, complex branching, or when intermediate values are reused |

~~~admonish note title="ForPath Limitation"
`ForPath` for `EitherPath` currently supports up to 3 steps. For longer sequences, use nested `via()` chains as shown in the main workflow.
~~~

---

## Recovery Patterns

Not all errors are fatal. Notifications, for instance, should not fail the entire order:

```java
private EitherPath<OrderError, NotificationResult> sendNotifications(
    ValidatedOrder order, Customer customer, DiscountResult discount) {

    return Path.either(
            notificationService.sendOrderConfirmation(
                order.orderId(), customer, discount.finalTotal()))
        .recoverWith(error -> Path.right(NotificationResult.none()));
}
```

The `recoverWith()` method catches errors and provides a fallback. Here, notification failures are swallowed, and processing continues with a "no notification" result.

### Recovery Options

| Method | Use Case |
|--------|----------|
| `recover(f)` | Transform error to success value directly |
| `recoverWith(f)` | Provide alternative `EitherPath` (may itself fail) |
| `mapError(f)` | Transform error type (stays on failure track) |

---

~~~admonish info title="Key Takeaways"
* **Sealed error hierarchies** enable exhaustive pattern matching and type-safe error handling
* **`via()` chains** compose sequential operations with automatic error propagation
* **`ForPath` comprehensions** provide readable syntax for simple sequences (up to 3 steps)
* **Recovery patterns** (`recover`, `recoverWith`) handle non-fatal errors gracefully
~~~

---

~~~admonish tip title="See Also"
- [Effect Path Overview](../effect/effect_path_overview.md) - The railway model and core operations
- [EitherPath](../effect/path_either.md) - Complete reference for EitherPath
- [ForPath Comprehension](../effect/forpath_comprehension.md) - Detailed ForPath documentation
~~~

---

**Previous:** [Order Workflow Overview](order-walkthrough.md)
**Next:** [Production Patterns](order-production.md)
