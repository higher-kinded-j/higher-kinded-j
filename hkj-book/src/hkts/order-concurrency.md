# Order Workflow: Concurrency and Scale

This page covers patterns for scaling the order workflow: context propagation with `ScopedValue`, structured concurrency with `Scope`, resource management with `Resource`, and virtual thread execution with `VTaskPath`.

~~~admonish info title="What You'll Learn"
- Propagating cross-cutting concerns (trace IDs, tenant isolation, deadlines) with `Context`
- Running parallel operations with proper cancellation using `Scope`
- Ensuring resource cleanup with the bracket pattern via `Resource`
- Scaling to millions of concurrent orders with virtual threads via `VTaskPath`
- Adapting these patterns to your own domain
~~~

~~~admonish example title="See Example Code"
- [EnhancedOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/EnhancedOrderWorkflow.java) - Full implementation with Context, Scope, Resource, VTaskPath
- [OrderContext.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/context/OrderContext.java) - Scoped value keys
- [EnhancedOrderWorkflowDemo.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/runner/EnhancedOrderWorkflowDemo.java) - Demo runner
~~~

---

## Context Propagation with ScopedValue

Cross-cutting concerns like trace IDs, tenant isolation, and deadlines can be propagated automatically using `Context`:

```java
// Define scoped values for order context
public final class OrderContext {
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<Instant> DEADLINE = ScopedValue.newInstance();
}

// Set context at workflow entry
ScopedValue
    .where(OrderContext.TRACE_ID, traceId)
    .where(OrderContext.TENANT_ID, tenantId)
    .where(OrderContext.DEADLINE, deadline)
    .run(() -> workflow.process(request));

// Access context in any step (including parallel operations)
String traceId = OrderContext.TRACE_ID.get();
```

The key benefit: context values automatically propagate to child virtual threads when using structured concurrency. No more passing trace IDs through every method signature.

### Deadline Enforcement

Operations can check remaining time and fail fast when the deadline is exceeded:

```java
public static Duration remainingTime() {
    if (!DEADLINE.isBound()) {
        return Duration.ofDays(365); // No deadline, effectively infinite
    }
    Duration remaining = Duration.between(Instant.now(), DEADLINE.get());
    return remaining.isNegative() ? Duration.ZERO : remaining;
}

public static boolean isDeadlineExceeded() {
    return DEADLINE.isBound() && Instant.now().isAfter(DEADLINE.get());
}
```

---

## Structured Concurrency with Scope

Parallel operations with proper cancellation and timeout handling:

```java
// Race inventory checks across multiple warehouses
VTask<InventoryReservation> result = Scope.<InventoryReservation>anySucceed()
    .timeout(Duration.ofSeconds(10))
    .fork(warehouse1.reserve(orderId, lines))
    .fork(warehouse2.reserve(orderId, lines))
    .fork(warehouse3.reserve(orderId, lines))
    .join();
```

| Pattern | Use Case |
|---------|----------|
| `allSucceed()` | Wait for all tasks; fail if any fails |
| `anySucceed()` | First success wins; cancel others |
| `accumulating()` | Collect all results, including errors |

Context values propagate to all forked tasks automatically. When using `Scope` within a `ScopedValue.where()` block, each forked task inherits the trace ID, tenant ID, and deadline without explicit passing.

### Example: Parallel Inventory Check

```java
public VTaskPath<Either<OrderError, InventoryReservation>> reserveInventoryParallel(
    OrderId orderId, List<ValidatedOrderLine> lines) {

    VTask<InventoryReservation> warehouse1 = VTask.of(() -> {
        logSync("Checking warehouse 1 [trace=" + OrderContext.shortTraceId() + "]");
        return inventoryService.reserve(orderId, lines)
            .fold(e -> { throw new ReservationException(e); }, r -> r);
    });

    VTask<InventoryReservation> warehouse2 = VTask.of(() -> {
        logSync("Checking warehouse 2 [trace=" + OrderContext.shortTraceId() + "]");
        return inventoryService.reserve(orderId, lines)
            .fold(e -> { throw new ReservationException(e); }, r -> r);
    });

    // Race all warehouses - first to succeed wins
    VTask<InventoryReservation> raceResult = Scope.<InventoryReservation>anySucceed()
        .timeout(getRemainingTimeout())
        .fork(warehouse1)
        .fork(warehouse2)
        .join();

    return Path.vtask(() -> raceResult.runSafe()
        .fold(Either::right, e -> Either.left(toOrderError(e))));
}
```

---

## Resource Management with Resource

The bracket pattern ensures cleanup even when operations fail:

```java
// Create a managed reservation
Resource<InventoryReservation> reservationResource = Resource.make(
    () -> inventoryService.reserve(orderId, lines),  // Acquire
    res -> inventoryService.releaseReservation(res.reservationId())  // Release
);

// Use with guaranteed cleanup
VTask<OrderResult> result = reservationResource.use(reservation ->
    processPayment(order, reservation)
        .map(payment -> buildResult(order, reservation, payment))
);
```

Resources are released in reverse order of acquisition (LIFO), and cleanup runs even if the computation fails or is cancelled.

### Combining Resources

Multiple resources can be combined with guaranteed cleanup ordering:

```java
Resource<Connection> dbResource = Resource.fromAutoCloseable(
    () -> connectionPool.getConnection()
);

Resource<PreparedStatement> stmtResource = dbResource.flatMap(conn ->
    Resource.fromAutoCloseable(() -> conn.prepareStatement(sql))
);

// Both connection and statement are cleaned up
VTask<List<Order>> orders = stmtResource.use(stmt ->
    VTask.of(() -> executeQuery(stmt))
);
```

---

## Virtual Thread Execution with VTaskPath

Scale to millions of concurrent orders using virtual threads:

```java
// VTaskPath operations are lazy - they describe computation
VTaskPath<Either<OrderError, OrderResult>> workflow =
    validateShippingAddress(request.shippingAddress())
        .via(addr -> lookupCustomer(customerId))
        .via(customer -> processOrder(customer, addr));

// Execute on a virtual thread
Try<Either<OrderError, OrderResult>> result = workflow.run().runSafe();
```

Virtual threads are managed by the JVM and can handle blocking operations (database calls, HTTP requests) without consuming platform threads.

### Converting Between Effect Types

`VTaskPath` integrates with the Effect Path API:

```java
// Create a VTaskPath from a computation
VTaskPath<String> fetch = Path.vtask(() -> httpClient.get(url));

// Convert existing Either to VTaskPath
VTaskPath<Either<Error, Data>> wrapped = Path.vtaskPure(existingEither);

// Chain VTaskPath operations
VTaskPath<Either<Error, Result>> pipeline = fetch
    .map(String::toUpperCase)
    .via(s -> Path.vtask(() -> process(s)));
```

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

### Step 5: Add Concurrency for Scale

When you need to scale, add context propagation and structured concurrency:

```java
// Wrap entry point with context
ScopedValue
    .where(OrderContext.TRACE_ID, traceId)
    .where(OrderContext.DEADLINE, deadline)
    .run(() -> workflow.process(request));

// Use Scope for parallel operations
VTask<List<Result>> parallel = Scope.<Result>allSucceed()
    .timeout(remainingTime())
    .fork(operation1)
    .fork(operation2)
    .join();
```

---

~~~admonish info title="Key Takeaways"
* **Context propagation** with `ScopedValue` enables implicit trace IDs, tenant isolation, and deadlines
* **Structured concurrency** with `Scope` provides parallel operations with proper cancellation and timeout
* **Resource management** with `Resource` ensures cleanup via the bracket pattern
* **Virtual threads** with `VTaskPath` enable scaling to millions of concurrent operations
* **Gradual adoption** allows you to start simple and add concurrency patterns as needs grow
~~~

---

~~~admonish tip title="See Also"
- [VTaskPath](../effect/path_vtask.md) - Virtual thread effect type
- [Structured Concurrency](../monads/vtask_scope.md) - Scope patterns
- [Resource Management](../monads/vtask_resource.md) - Resource patterns
- [Effect Path Overview](../effect/effect_path_overview.md) - The railway model
~~~

---

**Previous:** [Production Patterns](order-production.md)
**Next:** [Draughts Game](draughts.md)
