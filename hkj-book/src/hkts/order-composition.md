# Order Workflow: Effect Composition

This page covers the core composition patterns used in the order workflow: typed error hierarchies, `ForPath` comprehensions for flat multi-step composition, sub-comprehensions for encapsulating related steps, and recovery strategies.

~~~admonish info title="What You'll Learn"
- Modelling domain errors with sealed interfaces for exhaustive handling
- Composing multi-step workflows with `ForPath` comprehensions (up to 12 steps)
- Encapsulating sub-workflows as composable building blocks
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

## Composing the Workflow with ForPath

`ForPath` is the primary composition tool for multi-step workflows. It provides a flat, readable syntax where each step chains sequentially, with automatic error propagation and all intermediate values accessible via the accumulated tuple.

### The Full Workflow as a Single Comprehension

With arity-12 support, the entire order processing pipeline — all eight steps — composes into one flat `ForPath` comprehension:

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

Each step:
1. Receives the accumulated tuple of all previous results (or just the first value at step 2)
2. Returns a new `EitherPath`
3. Automatically propagates errors (if any previous step failed, subsequent steps are skipped)

The final `yield` destructures all eight values by name, making the result assembly fully readable.

### Tuple Position Reference

Within each `from` lambda, positions map to earlier steps:

| Position | Value | Type |
|----------|-------|------|
| `_1()` | validAddress | `ValidatedShippingAddress` |
| `_2()` | customer | `Customer` |
| `_3()` | order | `ValidatedOrder` |
| `_4()` | reservation | `InventoryReservation` |
| `_5()` | discount | `DiscountResult` |
| `_6()` | payment | `PaymentConfirmation` |
| `_7()` | shipment | `ShipmentInfo` |

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

The `Path.either()` factory lifts an `Either<E, A>` into an `EitherPath<E, A>`. Your services return `Either`; the workflow composes them within ForPath.

---

## Pattern Spotlight: Sub-Comprehensions

Smaller groups of related steps can use their own `ForPath` and be called as a single step from the main comprehension:

```java
private EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return ForPath.from(lookupCustomer(customerId))
        .from(this::validateCustomerEligibility)
        .yield((found, validated) -> validated);
}
```

This keeps the main comprehension readable while encapsulating sub-workflows.

### When to Use ForPath vs via()

| Pattern | Best For |
|---------|----------|
| `ForPath` | Multi-step sequential workflows (up to 12 steps) |
| `via()` | One-off chaining, conditional branching, or when tuple access would be awkward |

~~~admonish tip title="ForPath supports up to 12 steps"
`ForPath` for all Path types — including `EitherPath`, `MaybePath`, `TryPath`, `IOPath`, and `VTaskPath` — supports up to 12 steps. This is sufficient for virtually any real-world workflow.
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
* **`ForPath` comprehensions** compose multi-step workflows into flat, readable pipelines (up to 12 steps)
* **`via()` chains** remain useful for one-off chaining and conditional branching
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
