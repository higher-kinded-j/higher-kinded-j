# Order Workflow: Effect Composition

This page covers the core composition patterns used in the order workflow: typed error hierarchies, the `For` → `toState()` → `ForState` pattern for named-field multi-step composition, sub-comprehensions for encapsulating related steps, and recovery strategies.

~~~admonish info title="What You'll Learn"
- Modelling domain errors with sealed interfaces for exhaustive handling
- Composing multi-step workflows with `For` → `toState()` → `ForState` for named field access
- Understanding the two-phase gather/enrich workflow pattern
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

## Composing the Workflow with For → toState → ForState

The order workflow uses a two-phase composition pattern. The `For` comprehension gathers initial values (tuple positions are fine at low arity), then `toState()` bridges to `ForState` where lenses provide named field access for the remaining steps.

### The Full Workflow

```java
public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());
    EitherMonad<OrderError> monad = EitherMonad.instance();

    Kind<EitherKind.Witness<OrderError>, OrderResult> result =
        // Phase 1 (Gather): accumulate address, customer, order via For
        For.from(monad, lift(validateShippingAddress(request.shippingAddress())))
            .from(addr -> lift(lookupAndValidateCustomer(customerId)))
            .from(t -> lift(buildValidatedOrder(orderId, request, t._2(), t._1())))

            // Bridge: construct named state from the three gathered values
            .toState((address, customer, order) ->
                ProcessingState.initial(address, customer, order))

            // Phase 2 (Enrich): named field access via ForState + lenses
            .fromThen(s -> lift(reserveInventory(s.order().orderId(), s.order().lines())),
                reservationLens)
            .fromThen(s -> lift(applyDiscounts(s.order(), s.customer())),
                discountLens)
            .fromThen(s -> lift(processPayment(s.order(), s.discount())),
                paymentLens)
            .fromThen(s -> lift(createShipment(s.order(), s.address())),
                shipmentLens)
            .fromThen(s -> lift(sendNotifications(s.order(), s.customer(), s.discount())),
                notificationLens)
            .yield(OrderWorkflow::toOrderResult);

    return Path.either(EITHER.narrow(result));
}
```

### Two-Phase Design

The workflow has a natural two-phase shape:

1. **Gather phase** (For comprehension, steps 1-3): Accumulate address, customer, and order. At arity 3, tuple positions `t._1()` and `t._2()` are still clear.
2. **Enrich phase** (ForState, steps 4-8): The `toState()` bridge constructs a `ProcessingState` record from the gathered values. Each subsequent `fromThen()` reads its inputs by name and stores its output via a lens.

This split is what makes the pattern powerful. The gather phase uses `For` for concise accumulation. The enrich phase uses `ForState` for self-documenting named access. The bridge connects them seamlessly.

### Named State Replaces Tuple Positions

The `ProcessingState` record gives every intermediate value a name:

```java
record ProcessingState(
    ValidatedShippingAddress address,
    Customer customer,
    ValidatedOrder order,
    InventoryReservation reservation,
    DiscountResult discount,
    PaymentConfirmation payment,
    ShipmentInfo shipment,
    NotificationResult notification) {

    static ProcessingState initial(
        ValidatedShippingAddress address, Customer customer, ValidatedOrder order) {
      return new ProcessingState(address, customer, order, null, null, null, null, null);
    }
}
```

After `toState()`, the `fromThen()` steps access earlier results by name — `s.order()`, `s.customer()`, `s.discount()` — instead of by tuple position. This makes the code self-documenting: you can read the workflow without a position reference table.

### Lenses Connect ForState to the State Record

Each `fromThen(function, lens)` call runs a monadic operation and stores the result via a lens:

```java
// In production: @GenerateLenses on ProcessingState generates these automatically
static final Lens<ProcessingState, DiscountResult> discountLens =
    Lens.of(
        ProcessingState::discount,
        (s, v) -> new ProcessingState(
            s.address(), s.customer(), s.order(), s.reservation(),
            v, s.payment(), s.shipment(), s.notification()));
```

~~~admonish tip title="Use @GenerateLenses in production"
Annotate your state record with `@GenerateLenses` and the annotation processor generates all lenses automatically. The manual definitions shown here are for clarity.
~~~

### Kind Lifting

Service methods return `Either<OrderError, T>`. The `For`/`ForState` comprehension works with `Kind<EitherKind.Witness<OrderError>, T>`. A simple `lift()` helper bridges the two:

```java
private static <A> Kind<EitherKind.Witness<OrderError>, A> lift(Either<OrderError, A> either) {
    return EITHER.widen(either);
}
```

### Individual Steps Are Simple

```java
private Either<OrderError, InventoryReservation> reserveInventory(
    OrderId orderId, List<ValidatedOrderLine> lines) {
    return inventoryService.reserve(orderId, lines);
}

private Either<OrderError, PaymentConfirmation> processPayment(
    ValidatedOrder order, DiscountResult discount) {
    return paymentService.processPayment(
        order.orderId(), discount.finalTotal(), order.paymentMethod());
}
```

Service methods return `Either` directly. The `lift()` helper in the comprehension handles the conversion to the Kind type system.

---

## Pattern Spotlight: Sub-Comprehensions

Smaller groups of related steps can use their own `For` comprehension and be called as a single step from the main workflow:

```java
private EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    EitherMonad<OrderError> monad = EitherMonad.instance();
    Kind<EitherKind.Witness<OrderError>, Customer> result =
        For.from(monad, lift(lookupCustomer(customerId)))
            .from(found -> lift(validateCustomerEligibility(found)))
            .yield((found, validated) -> validated);
    return Path.either(EITHER.narrow(result));
}
```

This keeps the main comprehension readable while encapsulating sub-workflows.

### When to Use Which Pattern

| Pattern | Best For |
|---------|----------|
| `For` → `toState()` → `ForState` | Multi-step workflows (3+ steps) where named access improves clarity |
| `For` alone | Short workflows (1-3 steps) where tuple access is clear |
| `ForPath` | Working directly with Path types when you don't need named state |
| `via()` | One-off chaining, conditional branching |

~~~admonish tip title="For and ForState support up to 12 steps"
`For` comprehensions support up to 12 chained bindings. `ForState` has no arity limit — the state record can have any number of fields. The `toState()` bridge works at all arities (1 through 12).
~~~

---

## Recovery Patterns

Not all errors are fatal. Notifications, for instance, should not fail the entire order:

```java
private Either<OrderError, NotificationResult> sendNotifications(
    ValidatedOrder order, Customer customer, DiscountResult discount) {

    return notificationService
        .sendOrderConfirmation(order.orderId(), customer, discount.finalTotal())
        .fold(
            error -> Either.right(NotificationResult.none()),  // non-fatal: recover
            Either::right);
}
```

The `fold()` method handles both cases: on error, it recovers with a "no notification" result; on success, it passes through. The `ForState` step that calls this method via `fromThen()` will store the result via `notificationLens` either way.

### Recovery Options

| Method | Use Case |
|--------|----------|
| `fold(leftFn, rightFn)` | Pattern-match both cases of an Either |
| `recover(f)` | Transform error to success value directly (on EitherPath) |
| `recoverWith(f)` | Provide alternative path (may itself fail) (on EitherPath) |
| `mapError(f)` | Transform error type (stays on failure track) |

---

~~~admonish info title="Key Takeaways"
* **Sealed error hierarchies** enable exhaustive pattern matching and type-safe error handling
* **`For` → `toState()` → `ForState`** composes multi-step workflows with named field access — no tuple positions after the bridge
* **Two-phase gather/enrich** uses the best tool for each part of the workflow
* **Recovery patterns** (`fold`, `recover`, `recoverWith`) handle non-fatal errors gracefully
~~~

---

~~~admonish tip title="See Also"
- [ForState: Named State Comprehensions](../functional/forstate_comprehension.md) - Full ForState API reference
- [For Comprehension](../functional/for_comprehension.md) - The `toState()` bridge documentation
- [Effect Path Overview](../effect/effect_path_overview.md) - The railway model and core operations
- [EitherPath](../effect/path_either.md) - Complete reference for EitherPath
~~~

---

**Previous:** [Order Workflow Overview](order-walkthrough.md)
**Next:** [Production Patterns](order-production.md)
