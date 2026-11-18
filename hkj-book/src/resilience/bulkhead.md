# Bulkhead: Containing the Blast Radius

~~~admonish info title="What You'll Learn"
- How a bulkhead isolates resource usage to prevent cascading failures
- How to configure concurrency limits, wait queues, and fairness
- The distinction between Bulkhead and VStreamPar
- How to protect VTask operations with concurrency limiting
- How to chain `withBulkhead` on Path carriers, with typed rejections on the railway
- Why a call that passes two guards of one bulkhead takes a single permit
~~~

---

A ship's bulkhead divides the hull into compartments. If one compartment floods, the others stay dry. Without bulkheads, a single breach sinks the entire vessel.

Software systems face the same risk. If your application calls three external services and one becomes very slow, every thread that calls that service blocks indefinitely. Eventually those threads are exhausted and the other two services, both perfectly healthy, become unreachable because there are no threads left to call them. One slow service has sunk the ship.

A `Bulkhead` prevents this by limiting how many concurrent callers can access a shared resource. If the limit is reached, additional callers either wait briefly or are turned away immediately.

## How It Works

```
    Incoming requests
    ─────┬──────┬──────┬──────┬──────┬──────┬──────
         │      │      │      │      │      │
         ▼      ▼      ▼      ▼      ▼      ▼
    ┌─────────────────────────────────────────────┐
    │              Bulkhead (max=3)               │
    │                                             │
    │   ┌─────┐  ┌─────┐  ┌─────┐                 │
    │   │ R1  │  │ R2  │  │ R3  │  ← executing    │
    │   └─────┘  └─────┘  └─────┘                 │
    │                                             │
    │   ┌─────┐  ┌─────┐                          │
    │   │ R4  │  │ R5  │  ← waiting for permit    │
    │   └─────┘  └─────┘                          │
    │                                             │
    │   R6 → BulkheadFullException                │
    │         (wait queue full or timeout)        │
    └─────────────────────────────────────────────┘
```

## Creating a Bulkhead

<!-- verify -->
```java
// Simple: just a concurrency limit
Bulkhead dbBulkhead = Bulkhead.withMaxConcurrent(10);

// Full configuration
Bulkhead apiBulkhead = Bulkhead.create(BulkheadConfig.builder()
    .maxConcurrent(5)                         // 5 concurrent callers
    .maxWait(10)                              // Up to 10 callers can wait
    .waitTimeout(Duration.ofSeconds(2))       // Wait up to 2 seconds for a permit
    .fairness(true)                           // FIFO ordering for waiting callers
    .build());
```

| Setting | Default | Description |
|---------|---------|-------------|
| `maxConcurrent` | 10 | Maximum simultaneous executions |
| `maxWait` | 0 | Maximum callers in the wait queue (0 = no limit) |
| `waitTimeout` | 5s | How long to wait for a permit before giving up |
| `fairness` | false | Whether to serve waiting callers in FIFO order |

## Protecting VTask Operations

<!-- verify -->
```java
Bulkhead dbBulkhead = Bulkhead.withMaxConcurrent(10);

VTask<Result> protectedQuery = dbBulkhead.protect(
    VTask.of(() -> database.query(sql)));

// When the permit is acquired, the task runs normally.
// When the bulkhead is full, BulkheadFullException is thrown.
Result result = protectedQuery.run();
```

Like `CircuitBreaker.protect()`, the method is generic: one bulkhead can protect calls returning different types. The bulkhead passes on the task's failure unchanged, so `runSafe()` gives the task's own exception, such as an `IOException`.

## Handling Rejection

When the bulkhead cannot accept a caller, it throws `BulkheadFullException`:

<!-- verify -->
```java
VTask<Result> resilient = dbBulkhead.protect(
        VTask.of(() -> database.query(sql)))
    .recover(ex -> {
        if (ex instanceof BulkheadFullException) {
            log.warn("Database connection pool exhausted");
            return Result.fromCache(sql);
        }
        throw (ex instanceof RuntimeException re) ? re : new RuntimeException(ex);
    });
```

A guarded fork of a guarded call can be turned away too, while it waits for the permit its own call holds. [One Call, One Permit](#one-call-one-permit) shows how to avoid that.

## Path-Native Bulkheads

The lazy Path carriers chain bulkhead protection directly:

<!-- verify -->
```java
IOPath<Result> guarded = Path.io(() -> database.query(sql))
    .withBulkhead(dbBulkhead);

VTaskPath<Result> guardedAsync = Path.vtask(() -> database.query(sql))
    .withBulkhead(dbBulkhead);
```

On the typed-error carriers, the typed overload keeps a rejected execution on the typed channel: `BulkheadFullException` becomes a `Left` instead of a thrown exception or defect.

<!-- verify -->
```java
// VResultPath: instance combinator, rejection lands as a Left
VResultPath<OrderError, Reservation> guarded =
    reserveInventoryAsync(order)
        .withBulkhead(
            inventoryBulkhead,
            full -> OrderError.SystemError.fromException("Inventory service busy", full));

// EitherPath is eager, so the combinator is static and takes the step as a Supplier
EitherPath<OrderError, Reservation> reserved = EitherPath.withBulkhead(
    () -> reserveInventory(order),
    inventoryBulkhead,
    full -> OrderError.SystemError.fromException("Inventory service busy", full));
```

Without the `onFull` argument, `BulkheadFullException` propagates as-is.

## One Call, One Permit {#one-call-one-permit}

A repository and the service that calls it may each guard one dependency with the same bulkhead. The call then passes two guards, but takes one permit. A `ReentrantLock` works the same way: a thread that holds it can lock it again without waiting, and a thread it starts is not the holder. A bulkhead goes one step further, because its permit also follows the call onto the thread a timeout runs it on.

<!-- verify -->
```java
IOPath<Result> query = Path.io(() -> database.query(sql))
    .withBulkhead(dbBulkhead)               // the repository's guard
    .withTimeout(Duration.ofSeconds(2))
    .withBulkhead(dbBulkhead);              // the service's guard takes no second permit
```

Whether the inner guard needs a permit of its own depends on what lies between the two guards:

| Between the guards | Inner guard's permit | Why |
|---|---|---|
| `map`, `withRetry` or `recover` | The call's | One thread runs the call |
| `withTimeout`, or a circuit breaker's call timeout | The call's | The call does nothing else until the timed task stops, so one thread uses the permit at a time |
| A fork, as `Par`, `Scope`, `parEvalMap` and `runAsync` start | Its own | It runs beside the call, so it counts against `maxConcurrent` |
| `detachOnCancel` | Its own | A cancelled call stops waiting, and the detached task runs on beside it |

A [circuit breaker](circuit_breaker.md#the-state-machine) counts a fork as part of the call. A bulkhead gives the fork a permit of its own, because the fork runs at the same time as the rest of the call. The call keeps its permit while it waits for a guarded fork. So once calls hold every permit, each of their forks waits for `waitTimeout` and fails with `BulkheadFullException`, however large `maxConcurrent` is. Guard the forks, not the call that starts them. The same goes for a guarded task under `detachOnCancel`.

## Bulkhead vs VStreamPar

Both limit concurrency, but at different scopes (`VStreamPar` is the stream `parEvalMap` family):

| | Bulkhead | VStreamPar |
|---|----------|------------|
| **Scope** | Per-service (shared across callers) | Per-stream (within a pipeline) |
| **Use case** | "This database allows 10 connections" | "Process this stream with 4 in-flight" |
| **Shared** | One instance across the application | Per-stream instance |
| **Semantics** | Acquire/release permit | Bounded parallel map |

They compose naturally. A stream can use VStreamPar for pipeline parallelism and have each element's processing protected by a shared bulkhead:

<!-- verify -->
```java
Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(10);

Path.vstreamFromList(userIds)
    .parEvalMap(4, id ->
        serviceBulkhead.protect(
            VTask.of(() -> userService.fetch(id))))
    .toList()
    .unsafeRun();
```

Here, `parEvalMap(4, ...)` limits the stream to 4 in-flight elements, whilst `serviceBulkhead` ensures that across all streams in the application, no more than 10 concurrent calls reach the user service.

## Inspecting State

<!-- verify -->
```java
int available = dbBulkhead.availablePermits();  // How many more callers can enter
int active = dbBulkhead.activeCount();           // How many callers are currently executing
```

~~~admonish info title="Key Takeaways"
* **A bulkhead protects everyone else**: capping concurrent access to one slow dependency keeps the shared capacity available to healthy ones; waiting for a permit is itself bounded by `waitTimeout`, and `maxWait` bounds the queue
* **One instance per resource**: like a breaker, a bulkhead is shared application-wide; `protect()` is generic across return types
* **A guard reached again reuses the call's permit**: on the call's thread or under a timeout, but a thread the call starts takes its own
* **Rejection is explicit**: a full bulkhead throws `BulkheadFullException`, or lands as a typed `Left` via the `onFull` overload on the railway carriers
* **Different scope from `parEvalMap`**: VStreamPar bounds one pipeline's parallelism; a bulkhead bounds a shared resource across the whole application, and the two compose
~~~

~~~admonish tip title="See Also"
- [Circuit Breaker](circuit_breaker.md) - detecting and responding to service failures
- [Combined Patterns](combined.md) - using bulkhead with retry and circuit breaker
~~~

---

**Previous:** [Circuit Breaker](circuit_breaker.md)
**Next:** [Saga](saga.md)
