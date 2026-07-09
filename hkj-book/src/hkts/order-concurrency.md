# Order Workflow: Concurrency and Scale

This page covers patterns for scaling the order workflow: context propagation with `ScopedValue`, racing warehouses with `VResultPath.firstSuccess`, outcome-aware compensation with `VResultPath.bracketOutcome`, and virtual thread execution on the typed async railway `VResultPath`.

~~~admonish info title="What You'll Learn"
- Propagating cross-cutting concerns (trace IDs, tenant isolation, deadlines) with `Context`
- Racing parallel operations with typed errors using `VResultPath.firstSuccess`
- Deciding confirm-versus-release compensation from the outcome with `bracketOutcome`
- Scaling to millions of concurrent orders with the typed async railway `VResultPath`
- Adapting these patterns to your own domain
~~~

~~~admonish example title="See Example Code"
- [EnhancedOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/EnhancedOrderWorkflow.java) - Full implementation with Context, VResultPath, firstSuccess, and bracketOutcome
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

On the typed railway, "fail fast" means a typed `Left`, not a thrown exception. The workflow checks the deadline as an ordinary step, and a `Left` here short-circuits everything after it:

```java
private VResultPath<OrderError, Unit> checkDeadline(String operation) {
    return Path.vresultDefer(() -> {
        if (OrderContext.isDeadlineExceeded()) {
            return Either.left(SystemError.timeout(operation));
        }
        return Either.right(Unit.INSTANCE);
    });
}
```

---

## Structured Concurrency with firstSuccess

Parallel operations with proper cancellation, timeout handling, and typed failures kept in the value channel:

```java
// Race inventory reservations across multiple warehouses
VResultPath<OrderError, InventoryReservation> result =
    VResultPath.firstSuccess(List.of(warehouse1, warehouse2, warehouse3))
        .mapError(NonEmptyList::head)
        .withTimeout(Duration.ofSeconds(10), () -> SystemError.timeout("inventory race"));
```

| Combinator | Use Case |
|------------|----------|
| `firstSuccess(candidates)` | First `Right` wins and cancels the rest; typed failures are collected, and only when every candidate fails does the race fail with all of them |
| `allSucceed(tasks)` | Wait for all tasks; the first typed failure cancels the rest (fail-fast) |
| `allSucceedAccumulating(tasks)` | Run everything to completion; collect every typed failure at once |
| `withTimeout(duration, onTimeout)` | Overrunning the deadline becomes the designated typed error, on the railway |

A warehouse failing with a typed `Left` does not abort the race; errors stay in the value channel rather than being thrown and caught. Under the hood these combinators run on the same `Scope`/`ScopeJoiner` substrate documented in [Structured Concurrency](../monads/vtask_scope.md); reach for raw `Scope` directly when your tasks have no typed error channel.

Context values propagate to all forked tasks automatically. Each racing candidate inherits the trace ID, tenant ID, and deadline without explicit passing.

### Example: Parallel Inventory Check

```java
public VResultPath<OrderError, InventoryReservation> reserveInventoryParallel(
    OrderId orderId, List<ValidatedOrderLine> lines) {

    // Each candidate automatically inherits the scoped values (traceId, tenantId, etc.).
    var candidates =
        List.of(
            warehouseReservation(1, Duration.ofMillis(50), orderId, lines),
            warehouseReservation(2, Duration.ofMillis(75), orderId, lines),
            warehouseReservation(3, Duration.ofMillis(100), orderId, lines));

    // Race all warehouses - first Right wins; all-Left surfaces the first warehouse's error.
    return VResultPath.firstSuccess(candidates)
        .peekLeft(errors -> logSync("All warehouses failed: " + errors.toJavaList()))
        .mapError(NonEmptyList::head)
        .withTimeout(
            getRemainingTimeout(), () -> SystemError.timeout("parallel inventory reservation"));
}

private VResultPath<OrderError, InventoryReservation> warehouseReservation(
    int warehouse, Duration latency, OrderId orderId, List<ValidatedOrderLine> lines) {
    return Path.vresult(
        VTask.of(() -> {
            logSync("Checking warehouse " + warehouse
                + " [trace=" + OrderContext.shortTraceId() + "]");
            Thread.sleep(latency.toMillis()); // Simulate network latency
            return inventoryService.reserve(orderId, lines);
        }));
}
```

Three moves on the error channel do all the work:

- `peekLeft` observes the collected failures (a `NonEmptyList<OrderError>`, in candidate order) without changing tracks (here, logging that every warehouse failed)
- `mapError(NonEmptyList::head)` collapses the collected errors back to the workflow's single `OrderError`, surfacing the first
- `withTimeout` bounds the whole race by the remaining deadline; overrunning becomes a typed `SystemError.timeout`, not a `TimeoutException`

---

## Outcome-Aware Compensation with bracketOutcome

The bracket pattern ensures cleanup even when operations fail. `VResultPath.bracketOutcome` goes further: the release action *sees* the `Either` outcome of the use phase, so confirm-versus-release is decided from the result rather than a mutable flag:

```java
private VResultPath<OrderError, OrderResult> processWithReservation(
    ValidatedOrder order, Customer customer) {

    return VResultPath.bracketOutcome(
        // Acquire: reserve inventory; a Left skips use and release entirely.
        Path.vresultDefer(() -> inventoryService.reserve(order.orderId(), order.lines())),
        // Use: discount -> payment -> shipment -> notification.
        reservation -> processAfterReservation(order, customer, reservation),
        // Release: decide confirm-versus-release from the outcome.
        (reservation, outcome) ->
            outcome.fold(
                _ -> VTask.exec(() -> {
                    logSync("Releasing reservation " + reservation.reservationId());
                    inventoryService.releaseReservation(reservation.reservationId());
                }),
                _ -> VTask.exec(
                    () -> inventoryService.confirmReservation(reservation.reservationId()))),
        defect -> SystemError.fromException("Order processing failed", defect));
}
```

Release *always* runs and receives the `Either` outcome: a `Right` confirms the reservation, a `Left` releases it. There is no mutable "confirmed" flag. A defect (a thrown exception) inside the use phase is first typed through the final `onDefect` argument (here as a `SystemError`), so the release always observes a real outcome, and the reservation is released.

### General-Purpose Resource

For AutoCloseable-style acquisition where the release does not depend on the outcome, the generic `Resource` bracket remains available, and multiple resources combine with guaranteed cleanup ordering:

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

Resources are released in reverse order of acquisition (LIFO), and cleanup runs even if the computation fails or is cancelled.

---

## The Typed Async Railway with VResultPath

Scale to millions of concurrent orders using virtual threads. The workflow's shape is `VTask<Either<OrderError, A>>` (async work that can fail with a typed domain error), and `VResultPath` is that stack as a first-class railway, with no `Kind` widening, transformer, or hand-carried `Either`:

```java
// VResultPath operations are lazy - they describe computation
VResultPath<OrderError, OrderResult> workflow =
    validateShippingAddress(request.shippingAddress())
        .via(address ->
            lookupAndValidateCustomer(customerId)
                .via(customer ->
                    buildValidatedOrder(orderId, request, customer, address)
                        .via(order -> processWithReservation(order, customer))));

// Execute on a virtual thread at the boundary
Try<Either<OrderError, OrderResult>> result = workflow.run().runSafe();
```

`via` chains dependent steps on the success channel; a `Left` from any step short-circuits the rest. Steps nest only where a later step still needs an earlier binding (the address and customer both feed the order build). Virtual threads are managed by the JVM and can handle blocking operations (database calls, HTTP requests) without consuming platform threads.

### Converting Between Effect Types

`VResultPath` integrates with the rest of the Effect Path API:

```java
// Defer a computation that decides the Either itself
VResultPath<OrderError, ValidatedShippingAddress> step =
    Path.vresultDefer(() -> shippingService.validateAddress(address));

// Lift an existing carrier or a decided Either
VResultPath<OrderError, Data> lifted = Path.vresult(vtaskOfEither);
VResultPath<OrderError, Data> decided = Path.vresultEither(either);

// Pure values on either channel
VResultPath<OrderError, Unit> ok = Path.vresultRight(Unit.INSTANCE);
VResultPath<OrderError, Unit> failed = Path.vresultLeft(SystemError.timeout("order.start"));
```

VTask-native resilience composes on the `VTaskPath` layer and lifts into the typed railway:

```java
private VResultPath<OrderError, Customer> lookupAndValidateCustomer(CustomerId customerId) {
    return Path.vresult(
        Path.vtask(() ->
                customerService.findById(customerId)
                    .flatMap(customerService::validateEligibility))
            .withCircuitBreaker(customerLookupBreaker)
            .withRetry(customerLookupRetry)
            .run());
}
```

Retry engages only when the lookup *throws* (a transient infrastructure failure); a business `Left` such as customer-not-found is returned as-is and never retried.

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

When the steps are asynchronous, the same railway is [`VResultPath`](../effect/path_vresult.md): swap `Path.either(...)` for `Path.vresultDefer(...)` and the shape of the code does not change.

### Step 4: Add Resilience Gradually

Start simple, add resilience as needed. `EitherPath` is eager, so its resilience combinators are static and take the step as a `Supplier`: resilience wraps the *computation*, not the finished result.

```java
// Start with basic composition
var result = workflow.process(request);

// Add a typed timeout when integrating external services: the timeout
// arrives as a Left, not a thrown TimeoutException
var withTimeout = EitherPath.withTimeout(
    () -> workflow.process(request),
    Duration.ofSeconds(30),
    () -> SystemError.timeout("process"));

// Add railway-aware retry for transient failures. A business Left is never
// retried; the predicate opts selected transient errors in. Only wrap steps
// that are safe to re-run.
var resilient = EitherPath.withRetry(
    () -> EitherPath.withTimeout(
        () -> workflow.process(request),
        Duration.ofSeconds(30),
        () -> SystemError.timeout("process")),
    error -> error instanceof SystemError,
    RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200)));
```

On the async railway the same vocabulary chains as instance methods: `VResultPath` carries `withRetry(retryOn, policy)`, `withTimeout(duration, onTimeout)`, `withCircuitBreaker`, and `withBulkhead` directly.

### Step 5: Add Concurrency for Scale

When you need to scale, add context propagation and typed structured concurrency:

```java
// Wrap entry point with context
ScopedValue
    .where(OrderContext.TRACE_ID, traceId)
    .where(OrderContext.DEADLINE, deadline)
    .run(() -> workflow.process(request));

// Race alternatives - typed failures stay in the value channel
VResultPath<NonEmptyList<MyDomainError>, Result> fastest =
    VResultPath.firstSuccess(List.of(primary, replica, cache));

// Or require every operation to succeed (fail-fast)
VResultPath<MyDomainError, List<Result>> all =
    VResultPath.allSucceed(List.of(operation1, operation2));
```

---

~~~admonish info title="Key Takeaways"
* **Context propagation** with `ScopedValue` enables implicit trace IDs, tenant isolation, and deadlines
* **Typed racing** with `VResultPath.firstSuccess` keeps failures in the value channel: the first `Right` wins, and only an all-fail surfaces every error
* **Outcome-aware compensation** with `bracketOutcome` decides confirm-versus-release from the `Either` result, not a mutable flag
* **Virtual threads** with `VResultPath` enable scaling to millions of concurrent operations without giving up typed errors
* **Gradual adoption** allows you to start simple and add concurrency patterns as needs grow
~~~

---

~~~admonish tip title="See Also"
- [VResultPath](../effect/path_vresult.md) - Async work with a typed domain error
- [VTaskPath](../effect/path_vtask.md) - Virtual thread effect type
- [Structured Concurrency](../monads/vtask_scope.md) - The Scope substrate beneath `firstSuccess`
- [Resource Management](../monads/vtask_resource.md) - Resource patterns
- [Effect Path Overview](../effect/effect_path_overview.md) - The railway model
~~~

---

**Previous:** [Production Patterns](order-production.md)
**Next:** [Draughts Game](draughts.md)
