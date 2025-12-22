# Order Workflow: A Practical Guide to Effect Composition

> *"The major difference between a thing that might go wrong and a thing that cannot possibly go wrong is that when a thing that cannot possibly go wrong goes wrong, it usually turns out to be impossible to get at or repair."*
>
> — Douglas Adams, *Mostly Harmless*

Enterprise software can be like this.  Consider order processing. Every step can fail. Every failure has a type. Every type demands a different response. And when you've nested enough `try-catch` blocks inside enough null checks inside enough `if` statements, the thing that cannot possibly go wrong becomes the thing you cannot possibly debug.

This walkthrough demonstrates how to build a robust, multi-step order workflow using the Effect Path API and Focus DSL. You'll see how typed errors, composable operations, and functional patterns transform the pyramid of doom into a railway of clarity.

~~~admonish info title="What You'll Learn"
- Composing multi-step workflows with `EitherPath` and `via()` chains
- Modelling domain errors with sealed interfaces for exhaustive handling
- Using `ForPath` comprehensions for readable sequential composition
- Implementing resilience patterns: retry policies, timeouts, and recovery
- Integrating Focus DSL for immutable state updates
- Configuring workflow behaviour with feature flags
- Adapting these patterns to your own domain
~~~

~~~admonish example title="See Example Code"
- [OrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflow.java) - Main workflow implementation
- [ConfigurableOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/ConfigurableOrderWorkflow.java) - Feature flags and resilience
- [FocusDSLExamples.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/FocusDSLExamples.java) - Optics integration
- [OrderError.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/error/OrderError.java) - Sealed error hierarchy
~~~

---

## The Territory: Why Order Workflows Are Hard

Consider a typical e-commerce order flow:

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

Each step can fail for specific, typed reasons. Traditional Java handles this with a patchwork of approaches:

```java
// The pyramid of doom
public OrderResult processOrder(OrderRequest request) {
    if (request == null) {
        return OrderResult.error("Request is null");
    }
    try {
        var address = validateAddress(request.address());
        if (address == null) {
            return OrderResult.error("Invalid address");
        }
        var customer = customerService.find(request.customerId());
        if (customer == null) {
            return OrderResult.error("Customer not found");
        }
        try {
            var inventory = inventoryService.reserve(request.items());
            if (!inventory.isSuccess()) {
                return OrderResult.error(inventory.getReason());
            }
            // ... and so on, ever deeper
        } catch (InventoryException e) {
            return OrderResult.error("Inventory error: " + e.getMessage());
        }
    } catch (ValidationException e) {
        return OrderResult.error("Validation error: " + e.getMessage());
    }
}
```

The problems multiply:

| Issue | Consequence |
|-------|-------------|
| Mixed idioms | Nulls, exceptions, and booleans don't compose |
| Nested structure | Business logic buried under error handling |
| String errors | No type safety, no exhaustive matching |
| Repeated patterns | Each step reinvents error propagation |

---

## The Map: Effect Path helps tame complexity

The Effect Path API provides a unified approach. Here's the same workflow:

```java
public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    return validateShippingAddress(request.shippingAddress())
        .via(validAddress ->
            lookupAndValidateCustomer(request.customerId())
                .via(customer ->
                    buildValidatedOrder(request, customer, validAddress)
                        .via(order -> processOrderCore(order, customer))));
}
```

The transformation is dramatic:

- **Flat structure**: Each step chains to the next with `via()`
- **Typed errors**: `OrderError` is a sealed interface; the compiler ensures exhaustive handling
- **Automatic propagation**: Failures short-circuit; no explicit checks required
- **Composable**: Each step returns `EitherPath<OrderError, T>`, so they combine naturally

---

## Workflow Architecture

![Order Workflow Architecture](../images/order-workflow-architecture.svg)

Notice how errors branch off at each decision point, while success flows forward. This is the railway model in action: success stays on the main track; errors switch to the failure track and propagate to the end.

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

Not all errors are fatal. Notifications, for instance, shouldn't fail the entire order:

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

## Resilience: Retry and Timeout

The `ConfigurableOrderWorkflow` demonstrates production-grade resilience:

```java
public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var retryPolicy = createRetryPolicy();
    var totalTimeout = calculateTotalTimeout();

    return Resilience.resilient(
        Path.io(() -> executeWorkflow(request)),
        retryPolicy,
        totalTimeout,
        "ConfigurableOrderWorkflow.process");
}
```

### Retry Policy

```java
public record RetryPolicy(
    int maxAttempts,
    Duration initialDelay,
    double backoffMultiplier,
    Duration maxDelay,
    Predicate<Throwable> retryOn
) {
    public static RetryPolicy defaults() {
        return new RetryPolicy(
            3,                              // attempts
            Duration.ofMillis(100),         // initial delay
            2.0,                            // exponential backoff
            Duration.ofSeconds(5),          // max delay cap
            t -> t instanceof IOException  // retry on IO errors
                || t instanceof TimeoutException
        );
    }

    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1) return Duration.ZERO;
        var retryNumber = attempt - 1;
        var delayMillis = initialDelay.toMillis()
            * Math.pow(backoffMultiplier, retryNumber - 1);
        return Duration.ofMillis(
            Math.min((long) delayMillis, maxDelay.toMillis()));
    }
}
```

> *"I love deadlines. I love the whooshing noise they make as they go by."*
>
> — Douglas Adams

Timeouts ensure deadlines don't just whoosh by indefinitely:

```java
var timeout = Resilience.withTimeout(
    operation,
    Duration.ofSeconds(30),
    "paymentService.charge"
);
```

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

## Adapting These Patterns to Your Domain

### Step 1: Define Your Error Hierarchy

Start with a sealed interface for your domain errors:

```java
public sealed interface MyDomainError
    permits ValidationError, NotFoundError, ConflictError, SystemError {

    String code();
    String message();
}
```

### Step 2: Wrap Your Services

Convert existing services to return `Either`:

```java
// Before
public User findUser(String id) throws UserNotFoundException { ... }

// After
public Either<MyDomainError, User> findUser(String id) {
    try {
        return Either.right(legacyService.findUser(id));
    } catch (UserNotFoundException e) {
        return Either.left(NotFoundError.user(id));
    }
}
```

### Step 3: Compose with EitherPath

Build your workflows using `via()`:

```java
public EitherPath<MyDomainError, Result> process(Request request) {
    return Path.either(validateRequest(request))
        .via(valid -> Path.either(findUser(valid.userId())))
        .via(user -> Path.either(performAction(user, valid)))
        .map(this::buildResult);
}
```

### Step 4: Add Resilience Gradually

Start simple, add resilience as needed:

```java
// Start with basic composition
var result = workflow.process(request);

// Add timeout when integrating external services
var withTimeout = Resilience.withTimeout(result, Duration.ofSeconds(30), "process");

// Add retry for transient failures
var resilient = Resilience.withRetry(withTimeout, RetryPolicy.defaults());
```

---

## Reflection: Complexity Tamed by Simple Building Blocks

Step back and consider what we have built. An order workflow with eight distinct steps, seven potential error types, recovery logic, retry policies, feature flags, and immutable state updates. In traditional Java, this would likely span hundreds of lines of nested conditionals, try-catch blocks, and defensive null checks.

Instead, the core workflow fits in a single method:

```java
return validateShippingAddress(request.shippingAddress())
    .via(validAddress -> lookupAndValidateCustomer(request.customerId())
        .via(customer -> buildValidatedOrder(request, customer, validAddress)
            .via(order -> processOrderCore(order, customer))));
```

This is not magic. It is the result of combining a small number of simple, composable building blocks:

| Building Block | What It Does |
|----------------|--------------|
| `Either<E, A>` | Represents success or typed failure |
| `EitherPath<E, A>` | Wraps `Either` with chainable operations |
| `via(f)` | Sequences operations, propagating errors |
| `map(f)` | Transforms success values |
| `recoverWith(f)` | Handles failures with fallbacks |
| Sealed interfaces | Enables exhaustive error handling |
| Records | Provides immutable data with minimal syntax |
| Annotations | Generates lenses, prisms, and bridges |

None of these concepts is particularly complex. `Either` is just a container with two cases. `via` is just `flatMap` with a friendlier name. Sealed interfaces are just sum types. Records are just product types. Lenses are just pairs of getter and setter functions.

The power comes from *composition*. Each building block does one thing well, and they combine without friction. Error propagation is automatic. State updates are immutable. Pattern matching is exhaustive. Code generation eliminates boilerplate.

> *"Make each program do one thing well. To do a new job, build afresh rather than complicate old programs by adding new features."*
>
> — Doug McIlroy, Unix Philosophy

This is the Unix philosophy applied to data and control flow. Small, focused tools, combined freely. The result is code that is:

- **Readable**: The workflow reads like a specification
- **Testable**: Each step is a pure function
- **Maintainable**: Changes are localised; the compiler catches missing cases
- **Resilient**: Error handling is consistent and explicit

The pyramid of doom we started with was not a failure of Java. It was a failure to find the right abstractions. Effect Path, sealed types, and code generation provide those abstractions. The complexity has not disappeared, but it is now *managed* rather than *sprawling*.

---

~~~admonish info title="Key Takeaways"
* **Sealed error hierarchies** enable exhaustive pattern matching and type-safe error handling
* **`via()` chains** compose sequential operations with automatic error propagation
* **`ForPath` comprehensions** provide readable syntax for simple sequences (up to 3 steps)
* **Recovery patterns** (`recover`, `recoverWith`) handle non-fatal errors gracefully
* **Resilience utilities** add retry and timeout behaviour without cluttering business logic
* **Focus DSL** complements Effect Path for immutable state updates
* **Feature flags** enable configuration-driven workflow behaviour
* **Annotation processors** generate lenses, prisms, and service bridges, eliminating boilerplate
* **Composition of simple building blocks** tames complexity without hiding it
~~~

---

~~~admonish tip title="See Also"
- [Effect Path Overview](../effect/effect_path_overview.md) - The railway model and core operations
- [Path Types](../effect/path_types.md) - Complete reference for all Path types
- [Patterns and Recipes](../effect/patterns.md) - More real-world patterns
- [Focus DSL](../optics/focus_dsl.md) - Composable data navigation
- [Monad](../functional/monad.md) - The type class powering `via` and `flatMap`
~~~

---

**Previous:** [Usage Guide](usage-guide.md)
**Next:** [Draughts Game](draughts.md)
