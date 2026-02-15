# Order Workflow: A Practical Guide to Effect Composition

> *"The major difference between a thing that might go wrong and a thing that cannot possibly go wrong is that when a thing that cannot possibly go wrong goes wrong, it usually turns out to be impossible to get at or repair."*
>
> -- Douglas Adams, *Mostly Harmless*

Enterprise software can be like this. Consider order processing. Every step can fail. Every failure has a type. Every type demands a different response. And when you have nested enough `try-catch` blocks inside enough null checks inside enough `if` statements, the thing that cannot possibly go wrong becomes the thing you cannot possibly debug.

This walkthrough demonstrates how to build a robust, multi-step order workflow using the Effect Path API and Focus DSL. You will see how typed errors, composable operations, and functional patterns transform the pyramid of doom into a railway of clarity.

~~~admonish info title="What You'll Learn"
- Composing multi-step workflows with `ForPath` comprehensions (up to 12 steps)
- Modelling domain errors with sealed interfaces for exhaustive handling
- Encapsulating sub-workflows as composable building blocks
- Implementing resilience patterns: retry policies, timeouts, and recovery
- Scaling with structured concurrency, resource management, and virtual threads
- Adapting these patterns to your own domain
~~~

~~~admonish info title="In This Chapter"
- **Effect Composition** – The core patterns for building workflows: sealed error hierarchies for type-safe error handling, `ForPath` comprehensions for flat multi-step composition (up to 12 steps), sub-comprehensions for encapsulating related steps, and recovery patterns for graceful degradation.
- **Production Patterns** – Making workflows production-ready: retry policies with exponential backoff, timeouts for external services, Focus DSL for immutable state updates, feature flags for configuration, and code generation to eliminate boilerplate.
- **Concurrency and Scale** – Patterns for high-throughput systems: context propagation with `ScopedValue` for cross-cutting concerns, structured concurrency with `Scope` for parallel operations, resource management with the bracket pattern, and virtual thread execution for massive scale.
~~~

~~~admonish example title="See Example Code"
- [OrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflow.java) - Main workflow implementation
- [ConfigurableOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/ConfigurableOrderWorkflow.java) - Feature flags and resilience
- [EnhancedOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/EnhancedOrderWorkflow.java) - Concurrency patterns
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
| Mixed idioms | Nulls, exceptions, and booleans do not compose |
| Nested structure | Business logic buried under error handling |
| String errors | No type safety, no exhaustive matching |
| Repeated patterns | Each step reinvents error propagation |

---

## The Map: Effect Path Tames Complexity

The Effect Path API provides a unified approach. Here is the same workflow using a `ForPath` comprehension — all eight steps composed into a single flat pipeline:

```java
public EitherPath<OrderError, OrderResult> process(OrderRequest request) {
    var orderId = OrderId.generate();
    var customerId = new CustomerId(request.customerId());

    return ForPath.from(validateShippingAddress(request.shippingAddress()))
        .from(validAddress -> lookupAndValidateCustomer(customerId))
        .from(t -> buildValidatedOrder(orderId, request, t._2(), t._1()))
        .from(t -> reserveInventory(t._3().orderId(), t._3().lines()))
        .from(t -> applyDiscounts(t._3(), t._2()))
        .from(t -> processPayment(t._3(), t._5()))
        .from(t -> createShipment(t._3(), t._1()))
        .from(t -> sendNotifications(t._3(), t._2(), t._5()))
        .yield((validAddress, customer, order, reservation, discount,
                payment, shipment, notification) ->
            buildOrderResult(order, discount, payment, shipment, notification));
}
```

The transformation is dramatic:

- **Flat structure**: All eight steps read top-to-bottom in a single `ForPath` comprehension
- **Typed errors**: `OrderError` is a sealed interface; the compiler ensures exhaustive handling
- **Automatic propagation**: Failures short-circuit; no explicit checks required
- **Named results**: The final `yield` destructures all values by name for readability
- **Composable**: Each step returns `EitherPath<OrderError, T>`, so they combine naturally

---

## Workflow Architecture

![Order Workflow Architecture](../images/order-workflow-architecture.svg)

Notice how errors branch off at each decision point, while success flows forward. This is the railway model in action: success stays on the main track; errors switch to the failure track and propagate to the end.

---

## Complexity Tamed by Simple Building Blocks

Step back and consider what this example builds. An order workflow with eight distinct steps, seven potential error types, recovery logic, retry policies, feature flags, immutable state updates, and concurrent execution. In traditional Java, this would likely span hundreds of lines of nested conditionals, try-catch blocks, and defensive null checks.

Instead, the core workflow fits in a single `ForPath` comprehension — eight steps, flat and readable:

```java
return ForPath.from(validateShippingAddress(request.shippingAddress()))
    .from(validAddress -> lookupAndValidateCustomer(customerId))
    .from(t -> buildValidatedOrder(orderId, request, t._2(), t._1()))
    .from(t -> reserveInventory(t._3().orderId(), t._3().lines()))
    .from(t -> applyDiscounts(t._3(), t._2()))
    .from(t -> processPayment(t._3(), t._5()))
    .from(t -> createShipment(t._3(), t._1()))
    .from(t -> sendNotifications(t._3(), t._2(), t._5()))
    .yield((validAddress, customer, order, reservation, discount,
            payment, shipment, notification) ->
        buildOrderResult(order, discount, payment, shipment, notification));
```

This is not magic. It is the result of combining a small number of simple, composable building blocks:

| Building Block | What It Does |
|----------------|--------------|
| `Either<E, A>` | Represents success or typed failure |
| `EitherPath<E, A>` | Wraps `Either` with chainable operations |
| `ForPath` | Composes up to 12 sequential steps into a flat comprehension |
| `via(f)` | One-off chaining for simple cases |
| `map(f)` | Transforms success values |
| `recoverWith(f)` | Handles failures with fallbacks |
| Sealed interfaces | Enables exhaustive error handling |
| Records | Provides immutable data with minimal syntax |
| Annotations | Generates lenses, prisms, and bridges |

None of these concepts is particularly complex. `Either` is just a container with two cases. `ForPath` is just a for-comprehension that accumulates results in a tuple. Sealed interfaces are just sum types. Records are just product types. Lenses are just pairs of getter and setter functions.

The power comes from *composition*. Each building block does one thing well, and they combine without friction. Error propagation is automatic. State updates are immutable. Pattern matching is exhaustive. Code generation eliminates boilerplate.

> *"Make each program do one thing well. To do a new job, build afresh rather than complicate old programs by adding new features."*
>
> -- Doug McIlroy, Unix Philosophy

This is the Unix philosophy applied to data and control flow. Small, focused tools, combined freely. The result is code that is:

- **Readable**: The workflow reads like a specification
- **Testable**: Each step is a pure function
- **Maintainable**: Changes are localised; the compiler catches missing cases
- **Resilient**: Error handling is consistent and explicit

The pyramid of doom we started with was not a failure of Java. It was a failure to find the right abstractions. Effect Path, sealed types, and code generation provide those abstractions. The complexity has not disappeared, but it is now *managed* rather than *sprawling*.

---

## Chapter Contents

1. [Effect Composition](order-composition.md) - Sealed errors, ForPath comprehensions, sub-workflows, recovery patterns
2. [Production Patterns](order-production.md) - Retry, timeout, Focus DSL, feature flags, code generation
3. [Concurrency and Scale](order-concurrency.md) - Context propagation, Scope, Resource, VTaskPath

---

~~~admonish info title="Key Takeaways"
* **Sealed error hierarchies** enable exhaustive pattern matching and type-safe error handling
* **`ForPath` comprehensions** compose multi-step workflows into flat, readable pipelines (up to 12 steps)
* **Recovery patterns** handle non-fatal errors gracefully
* **Resilience utilities** add retry and timeout behaviour without cluttering business logic
* **Focus DSL** complements Effect Path for immutable state updates
* **Context propagation** enables implicit trace IDs, tenant isolation, and deadlines
* **Structured concurrency** provides parallel operations with proper cancellation
* **Resource management** ensures cleanup via the bracket pattern
* **Virtual threads** enable scaling to millions of concurrent operations
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
**Next:** [Effect Composition](order-composition.md)
