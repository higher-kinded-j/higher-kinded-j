# Order Workflow: Production Patterns

This page covers production-grade patterns for the order workflow: resilience with retry and timeout, Focus DSL integration, feature flags, and compile-time code generation.

~~~admonish info title="What You'll Learn"
- Implementing resilience patterns: retry policies with exponential backoff
- Adding timeouts to prevent indefinite waits
- Integrating Focus DSL for immutable state updates
- Configuring workflow behaviour with feature flags
- Using annotations to generate boilerplate code
~~~

~~~admonish example title="See Example Code"
- [ConfigurableOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/ConfigurableOrderWorkflow.java) - Feature flags and resilience
- [FocusDSLExamples.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/FocusDSLExamples.java) - Optics integration
~~~

---

## Resilience: Retry and Timeout

The `ConfigurableOrderWorkflow` composes the core path-native combinators (`IOPath.withRetry` for the retry loop, the static `EitherPath.withTimeout` for a typed time budget) and applies them **per step**, not around the whole flow:

```java
public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var retryPolicy = createRetryPolicy();

    // Phase 1: RETRY only the idempotent pre-flight (customer eligibility +
    // address validation); re-running these reads is always safe. IOPath.withRetry
    // drives the loop, then EitherPath.withTimeout puts ONE typed time budget
    // around the whole loop, so a timeout arrives as a Left on the railway.
    EitherPath<OrderError, Unit> preflight =
        EitherPath.withTimeout(
            () -> toEitherPath(
                Path.io(() -> runPreflight(request)).withRetry(retryPolicy),
                "ConfigurableOrderWorkflow.preflight"),
            preflightTimeout,
            () -> OrderError.SystemError.timeout(
                "ConfigurableOrderWorkflow.preflight", preflightTimeout));

    // Phase 2: run the committing workflow (reserve -> payment -> ship -> notify)
    // EXACTLY ONCE under a timeout, never retried.
    return preflight.via(_ ->
        EitherPath.withTimeout(
            () -> toEitherPath(
                Path.io(() -> executeWorkflow(request)),
                "ConfigurableOrderWorkflow.commit"),
            commitTimeout,
            () -> OrderError.SystemError.timeout(
                "ConfigurableOrderWorkflow.commit", commitTimeout)));
}
```

Granularity matters: payment is **not** idempotent, so wrapping the *entire* workflow in retry
could re-run a charge that already succeeded and double-bill the customer. Retry is confined to the
safe pre-flight reads; the commit runs once. (Per-step refund-on-failure compensation across
reserve → pay → ship would use a `Saga`.)

Note the eager/lazy split: `EitherPath.withTimeout` is *static* and takes the step as a `Supplier`, because `EitherPath` is eager: resilience must wrap the computation before it runs. The `toEitherPath` helper runs the `IOPath` and lands any thrown failure on the typed channel as an `OrderError.SystemError`, so by the time the timeout wrapper sees the outcome, everything except the timeout itself is already a `Left`.

### Retry Policy

The retry policy is the core `RetryPolicy`, translated from workflow configuration. Only transient infrastructure failures are selected; the business `WorkflowException` never matches, so a business failure is never retried:

```java
private RetryPolicy createRetryPolicy() {
    var retryConfig = config.retryConfig();
    return RetryPolicy.builder()
        .maxAttempts(retryConfig.maxRetries() + 1) // maxAttempts includes the initial try
        .initialDelay(retryConfig.initialDelay())
        .backoffMultiplier(retryConfig.backoffMultiplier())
        .maxDelay(retryConfig.maxDelay())
        .retryIf(t -> t instanceof IOException || t instanceof TimeoutException)
        .build();
}
```

> *"I love deadlines. I love the whooshing noise they make as they go by."*
>
> — Douglas Adams

Timeouts ensure deadlines do not just whoosh by indefinitely, and with the typed overload they arrive as a `Left` that flows down the same railway as every other domain error, not as a thrown `TimeoutException`:

```java
EitherPath<OrderError, Receipt> charged =
    EitherPath.withTimeout(
        () -> chargePayment(order),
        Duration.ofSeconds(30),
        () -> OrderError.SystemError.timeout("paymentService.charge"));
```

One caveat: the timed-out computation is not interrupted, so on a non-idempotent step like payment, `Left(timeout)` means the outcome is *unknown*: reconcile, do not blindly retry. See [Resilience Patterns](../resilience/ch_intro.md) for the full path-native combinator surface (`withRetry`, `withTimeout`, `withCircuitBreaker`, `withBulkhead`) and the per-carrier availability table.

---

## Focus DSL Integration

The Focus DSL complements Effect Path for immutable state updates. Where Effect Path navigates *computational effects*, Focus navigates *data structures*.

### Immutable State Updates

```java
public static OrderWorkflowState applyDiscount(
    OrderWorkflowState state, DiscountResult discount) {

    var withDiscount = state.withDiscountResult(discount);

    return state.validatedOrder()
        .map(order -> {
            var updatedOrder = updateOrderSubtotal(order, discount.finalTotal());
            return withDiscount.withValidatedOrder(updatedOrder);
        })
        .orElse(withDiscount);
}
```

### Pattern Matching with Sealed Types

```java
public static EitherPath<OrderError, PaymentMethod> validatePaymentMethod(
    PaymentMethod method) {

    return switch (method) {
        case PaymentMethod.CreditCard card -> {
            if (card.cardNumber().length() < 13) {
                yield Path.left(
                    OrderError.ValidationError.forField(
                        "cardNumber", "Card number too short"));
            }
            yield Path.right(method);
        }
        case PaymentMethod.BankTransfer transfer -> {
            if (transfer.accountNumber().isBlank()) {
                yield Path.left(
                    OrderError.ValidationError.forField(
                        "accountNumber", "Account number required"));
            }
            yield Path.right(method);
        }
        // ... other cases
    };
}
```

The sealed `PaymentMethod` type enables exhaustive validation with Effect Path integration.

---

## Feature Flags: Configuration-Driven Behaviour

The `ConfigurableOrderWorkflow` uses feature flags to control optional behaviours:

```java
public record FeatureFlags(
    boolean enablePartialFulfilment,
    boolean enableSplitShipments,
    boolean enableLoyaltyDiscounts
) {
    public static FeatureFlags defaults() {
        return new FeatureFlags(false, false, true);
    }

    public static FeatureFlags allEnabled() {
        return new FeatureFlags(true, true, true);
    }
}
```

These flags control workflow branching:

```java
private EitherPath<OrderError, DiscountResult> applyDiscounts(
    ValidatedOrder order, Customer customer) {

    return order.promoCode()
        .<EitherPath<OrderError, DiscountResult>>map(
            code -> Path.either(discountService.applyPromoCode(code, order.subtotal())))
        .orElseGet(() -> {
            if (config.featureFlags().enableLoyaltyDiscounts()) {
                return Path.either(
                    discountService.calculateLoyaltyDiscount(customer, order.subtotal()));
            }
            return Path.right(DiscountResult.noDiscount(order.subtotal()));
        });
}
```

---

## Compile-Time Code Generation

Much of the boilerplate in this example is generated at compile time through annotations. This keeps your code focused on domain logic while the annotation processors handle the mechanical parts.

### Annotation Overview

| Annotation | Purpose | Generated Code |
|------------|---------|----------------|
| `@GenerateLenses` | Immutable record updates | Type-safe lenses for each field |
| `@GenerateFocus` | Focus DSL integration | `FocusPath` and `AffinePath` accessors |
| `@GeneratePrisms` | Sealed type navigation | Prisms for each variant of sealed interfaces |
| `@GeneratePathBridge` | Service-to-Path bridging | `*Paths` class wrapping service methods |
| `@PathVia` | Method-level documentation | Includes doc strings in generated bridges |

### Lenses and Focus for Records

```java
@GenerateLenses
@GenerateFocus
public record OrderWorkflowState(
    OrderRequest request,
    Optional<ValidatedOrder> validatedOrder,
    Optional<InventoryReservation> inventoryReservation,
    // ... more fields
) { }
```

The annotation processor generates `OrderWorkflowStateLenses` with a lens for each field, plus `OrderWorkflowStateFocus` with `FocusPath` accessors. These enable immutable updates without manual `with*` methods:

```java
// Generated lens usage
var updated = OrderWorkflowStateLenses.validatedOrder()
    .set(state, Optional.of(newOrder));

// Generated focus usage
var subtotal = OrderWorkflowStateFocus.validatedOrder()
    .andThen(ValidatedOrderFocus.subtotal())
    .get(state);
```

### Prisms for Sealed Hierarchies

```java
@GeneratePrisms
public sealed interface OrderError
    permits ValidationError, CustomerError, InventoryError, ... { }
```

This generates `OrderErrorPrisms` with a prism for each permitted variant:

```java
// Extract specific error type if present
Optional<PaymentError> paymentError =
    OrderErrorPrisms.paymentError().getOptional(error);

// Pattern-match in functional style
var recovery = OrderErrorPrisms.shippingError()
    .modifyOptional(error, e -> e.recoverable()
        ? recoverShipping(e)
        : e);
```

### Path Bridges for Services

```java
@GeneratePathBridge
public interface CustomerService {

    @PathVia(doc = "Looks up customer details by ID")
    Either<OrderError, Customer> findById(CustomerId id);

    @PathVia(doc = "Validates customer eligibility")
    Either<OrderError, Customer> validateEligibility(Customer customer);
}
```

This generates `CustomerServicePaths`:

```java
// Generated bridge class
public class CustomerServicePaths {
    private final CustomerService delegate;

    public EitherPath<OrderError, Customer> findById(CustomerId id) {
        return Path.either(delegate.findById(id));
    }

    public EitherPath<OrderError, Customer> validateEligibility(Customer customer) {
        return Path.either(delegate.validateEligibility(customer));
    }
}
```

Now your workflow can use the generated bridges directly:

```java
private final CustomerServicePaths customers;

private EitherPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId id) {
    return customers.findById(id)
        .via(customers::validateEligibility);
}
```

### Why Code Generation Matters

The annotations eliminate three categories of boilerplate:

1. **Structural navigation**: Lenses and prisms provide type-safe access without manual getter/setter chains
2. **Effect wrapping**: Path bridges convert `Either`-returning services to `EitherPath` automatically
3. **Pattern matching**: Prisms enable functional matching on sealed types without explicit `instanceof` checks

The result is domain code that reads like a specification of *what* should happen, while the generated code handles *how* to navigate, wrap, and match.

---

~~~admonish info title="Key Takeaways"
* **Path-native resilience combinators** (`IOPath.withRetry`, `EitherPath.withTimeout`) add retry and timeout behaviour without cluttering business logic: retry only what is idempotent, run commits exactly once
* **Focus DSL** complements Effect Path for immutable state updates
* **Feature flags** enable configuration-driven workflow behaviour
* **Annotation processors** generate lenses, prisms, and service bridges, eliminating boilerplate
~~~

---

~~~admonish tip title="See Also"
- [Focus DSL](../optics/focus_dsl.md) - Composable data navigation
- [Patterns and Recipes](../effect/patterns.md) - More real-world patterns
- [Lenses](../optics/lenses.md) - Lens fundamentals
~~~

---

**Previous:** [Effect Composition](order-composition.md)
**Next:** [Concurrency and Scale](order-concurrency.md)
