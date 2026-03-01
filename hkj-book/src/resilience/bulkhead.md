# Bulkhead: Containing the Blast Radius

~~~admonish info title="What You'll Learn"
- How a bulkhead isolates resource usage to prevent cascading failures
- How to configure concurrency limits, wait queues, and fairness
- The distinction between Bulkhead and VStreamPar
- How to protect VTask operations with concurrency limiting
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
    │              Bulkhead (max=3)                │
    │                                             │
    │   ┌─────┐  ┌─────┐  ┌─────┐                │
    │   │ R1  │  │ R2  │  │ R3  │  ← executing   │
    │   └─────┘  └─────┘  └─────┘                │
    │                                             │
    │   ┌─────┐  ┌─────┐                          │
    │   │ R4  │  │ R5  │  ← waiting for permit    │
    │   └─────┘  └─────┘                          │
    │                                             │
    │   R6 → BulkheadFullException                │
    │         (wait queue full or timeout)         │
    └─────────────────────────────────────────────┘
```

## Creating a Bulkhead

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

```java
Bulkhead dbBulkhead = Bulkhead.withMaxConcurrent(10);

VTask<Result> protectedQuery = dbBulkhead.protect(
    VTask.of(() -> database.query(sql)));

// When the permit is acquired, the task runs normally.
// When the bulkhead is full, BulkheadFullException is thrown.
Result result = protectedQuery.run();
```

Like `CircuitBreaker.protect()`, the method is generic: one bulkhead can protect calls returning different types.

## Handling Rejection

When the bulkhead cannot accept a caller, it throws `BulkheadFullException`:

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

## Bulkhead vs VStreamPar

Both limit concurrency, but at different scopes:

| | Bulkhead | VStreamPar |
|---|----------|------------|
| **Scope** | Per-service (shared across callers) | Per-stream (within a pipeline) |
| **Use case** | "This database allows 10 connections" | "Process this stream with 4 in-flight" |
| **Shared** | One instance across the application | Per-stream instance |
| **Semantics** | Acquire/release permit | Bounded parallel map |

They compose naturally. A stream can use VStreamPar for pipeline parallelism and have each element's processing protected by a shared bulkhead:

```java
Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(10);

Path.vstreamFromList(userIds)
    .parEvalMap(4, id ->
        serviceBulkhead.protect(
            VTask.of(() -> userService.fetch(id))))
    .toList()
    .run();
```

Here, `parEvalMap(4, ...)` limits the stream to 4 in-flight elements, whilst `serviceBulkhead` ensures that across all streams in the application, no more than 10 concurrent calls reach the user service.

## Inspecting State

```java
int available = dbBulkhead.availablePermits();  // How many more callers can enter
int active = dbBulkhead.activeCount();           // How many callers are currently executing
```

~~~admonish tip title="See Also"
- [Circuit Breaker](circuit_breaker.md) -- detecting and responding to service failures
- [Combined Patterns](combined.md) -- using bulkhead with retry and circuit breaker
~~~

---

**Previous:** [Circuit Breaker](circuit_breaker.md)
**Next:** [Saga](saga.md)
