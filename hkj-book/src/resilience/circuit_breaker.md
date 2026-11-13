# Circuit Breaker: Learning When to Stop

~~~admonish info title="What You'll Learn"
- How the circuit breaker state machine works (CLOSED, OPEN, HALF_OPEN)
- How to configure failure thresholds, recovery timeouts, and call timeouts
- How to protect VTask operations with a shared circuit breaker
- How to chain `withCircuitBreaker` on Path carriers, and why a `Left` never trips the circuit
- How to use fallbacks when the circuit rejects a call
- How to monitor circuit breaker health via metrics
~~~

---

A retry policy assumes that trying again will eventually work. Sometimes it will not. If the database is down, retrying every 200 milliseconds for the next 30 seconds just generates 150 doomed requests. The service needs time to recover, and hammering it with traffic makes recovery harder.

A circuit breaker solves this by tracking recent failures and, when enough accumulate, *stopping* calls entirely for a cooling-off period. After the period expires, it cautiously lets probe requests through, as many at once as the successes it still needs (one by default), and turns the rest away. If the probes succeed, normal traffic resumes. If one fails, the circuit re-opens.

## The State Machine

```mermaid
stateDiagram-v2
    accTitle: The circuit breaker's three states
    accDescr: The breaker starts CLOSED, where calls go through and a success resets the failure count. When consecutive failures reach failureThreshold, it moves to OPEN, where calls are rejected at once with CircuitOpenException. When openDuration expires, it moves to HALF_OPEN. From there it returns to CLOSED when successThreshold probes succeed, and to OPEN when any probe fails.
    state "<b>CLOSED</b><br/>calls go through, and<br/>a success resets<br/>the failure count" as CLOSED
    state "<b>OPEN</b><br/>calls are rejected at once<br/>with CircuitOpenException" as OPEN
    state "<b>HALF_OPEN</b>" as HALF_OPEN
    [*] --> CLOSED
    CLOSED --> OPEN : consecutive failures<br/>reach failureThreshold
    OPEN --> HALF_OPEN : openDuration<br/>expires
    HALF_OPEN --> CLOSED : successThreshold<br/>probes succeed
    HALF_OPEN --> OPEN : any probe<br/>fails

    classDef closed fill:#a6d189,stroke:#40a02b,color:#232634
    classDef open fill:#e78284,stroke:#d20f39,color:#232634
    classDef probing fill:#e5c890,stroke:#df8e1d,color:#232634
    class CLOSED closed
    class OPEN open
    class HALF_OPEN probing
```

| State | Behaviour | Transitions to |
|-------|-----------|----------------|
| **CLOSED** | All calls allowed. Consecutive failures counted. | OPEN (when failures reach threshold) |
| **OPEN** | All calls rejected immediately with `CircuitOpenException`. | HALF_OPEN (after open duration expires) |
| **HALF_OPEN** | A call runs as a probe while running and successful probes are fewer than `successThreshold`. Any other call is rejected with a `CircuitOpenException` whose `status()` is `HALF_OPEN`. | CLOSED (`successThreshold` probes succeed) or OPEN (a probe fails) |

A call counts towards the state it was admitted in. One still running when the circuit opens, or when `reset()` closes it, finishes without moving it, though the metrics still count it. So a slow success from before an outage cannot close the circuit while it is half-open. A half-open rejection's `retryAfter()` is the call timeout, and an open one's is the time left before the circuit half-opens.

Protect a call with one breaker once. A breaker that protects a call it already protects counts each outcome twice. While half-open, the inner call takes a second probe place if one is free, so one request can supply two of the successes that close the circuit. When none is free, the inner call is rejected.

## Configuration

<!-- verify -->
```java
CircuitBreakerConfig config = CircuitBreakerConfig.builder()
    .failureThreshold(5)                      // 5 failures before opening
    .successThreshold(3)                      // 3 probes must succeed to close
    .openDuration(Duration.ofSeconds(30))     // Stay open for 30 seconds
    .callTimeout(Duration.ofSeconds(5))       // Each call times out after 5s
    .recordFailure(ex ->                      // Only count certain exceptions
        !(ex instanceof BusinessValidationException))
    .build();
```

| Setting | Default | Description |
|---------|---------|-------------|
| `failureThreshold` | 5 | Consecutive failures before the circuit opens |
| `successThreshold` | 1 | Successful probes in HALF_OPEN before closing; running and successful probes together never exceed it |
| `openDuration` | 60s | How long the circuit stays open |
| `callTimeout` | 10s | Timeout applied to each protected call: a slower call is interrupted, and fails once it has stopped |
| `recordFailure` | all exceptions | Predicate determining which exceptions count |

The `recordFailure` predicate is important: not every exception means the service is unhealthy. A `400 Bad Request` or a business validation error reflects a problem with the *request*, not the *service*. Only count failures that indicate the service itself is struggling. A cancelled call counts as neither a success nor a failure, whatever `recordFailure` says, and its `InterruptedException` passes through.

A call slower than `callTimeout` counts once it has stopped. So a call that hangs where an interrupt cannot reach it would keep the circuit closed, or, running as a half-open probe, hold its place until it stops. With the default threshold of one, every other call is turned away meanwhile. Detach the call before protecting it, and the breaker counts it when the time runs out:

<!-- verify -->
```java
VTask<String> counted = paymentBreaker.protect(
    VTask.of(() -> paymentService.getStatus(orderId)).detachOnCancel());
```

[VTaskPath](../effect/path_vtask.md#timeouts) says when detaching is safe.

## Creating a Circuit Breaker

<!-- verify -->
```java
// With custom configuration
CircuitBreaker configured = CircuitBreaker.create(config);

// With sensible defaults
CircuitBreaker defaults = CircuitBreaker.withDefaults();
```

## Protecting VTask Operations

The `protect()` method is generic. A single circuit breaker instance can protect calls that return different types:

<!-- verify -->
```java
CircuitBreaker paymentBreaker = CircuitBreaker.create(
    CircuitBreakerConfig.builder()
        .failureThreshold(3)
        .openDuration(Duration.ofSeconds(30))
        .build());

// Protects a call returning String
VTask<String> getStatus = paymentBreaker.protect(
    VTask.of(() -> paymentService.getStatus(orderId)));

// Same breaker protects a call returning BigDecimal
VTask<BigDecimal> getBalance = paymentBreaker.protect(
    VTask.of(() -> paymentService.getBalance(accountId)));

// Both share state: failures from either call count towards the threshold
```

This is the correct design. A circuit breaker protects a *service endpoint*, not a specific return type.

## Fallbacks

When the breaker rejects a call, because it is open or half-open with every probe place taken, `protect()` throws `CircuitOpenException`. Use `protectWithFallback()` to provide a default value instead:

<!-- verify -->
```java
VTask<String> withFallback = paymentBreaker.protectWithFallback(
    VTask.of(() -> paymentService.getStatus(orderId)),
    ex -> "status-unavailable");
```

The fallback runs only for `CircuitOpenException`. Any other failure passes through as thrown.

Or compose with `recover()` for more control:

<!-- verify -->
```java
VTask<String> resilient = paymentBreaker.protect(
        VTask.of(() -> paymentService.getStatus(orderId)))
    .recover(ex -> {
        if (ex instanceof CircuitOpenException coe) {
            log.warn("Payment service down, retry after {}", coe.retryAfter());
            return cachedStatus(orderId);
        }
        return "unknown";
    });
```

## Path-Native Circuit Breakers

The lazy Path carriers chain breaker protection directly, with the same shareable breaker:

<!-- verify -->
```java
IOPath<String> guarded = Path.io(() -> paymentService.getStatus(orderId))
    .withCircuitBreaker(paymentBreaker);

VTaskPath<String> guardedAsync = Path.vtask(() -> paymentService.getStatus(orderId))
    .withCircuitBreaker(paymentBreaker);
```

On the typed-error carriers the breaker is railway-aware: a `Left` is a *successfully computed value* (the service answered, just not with a `Right`), so it does **not** count as a breaker failure. Only thrown exceptions (defects) trip the circuit. A stream of "customer not found" responses will never open the circuit; a stream of connection resets will.

~~~admonish tip title="Why this matters"
A circuit breaker measures *service health*, not business outcomes. A breaker that counts every non-success trips on a healthy service the moment clients send a burst of bad requests, taking the service away from everyone else exactly when it was answering correctly. On the typed carriers the largest class of non-successes, business `Left`s, is excluded by construction; `recordFailure` then only has to classify what still arrives as a thrown exception.
~~~

The typed overloads keep the breaker's rejection on the typed channel instead of surfacing `CircuitOpenException`:

<!-- verify -->
```java
// VResultPath: instance combinator, rejection lands as a Left
VResultPath<OrderError, Reservation> guarded =
    reserveInventoryAsync(order)
        .withCircuitBreaker(
            inventoryBreaker,
            open -> OrderError.SystemError.circuitBreakerOpen("inventory"));

// EitherPath is eager, so the combinator is static and takes the step as a Supplier
EitherPath<OrderError, Reservation> reserved = EitherPath.withCircuitBreaker(
    () -> reserveInventory(order),
    inventoryBreaker,
    open -> OrderError.SystemError.circuitBreakerOpen("inventory"));
```

Without the `onOpen` argument, the rejection propagates as-is: a thrown `CircuitOpenException` on `EitherPath`, a defect on the `VTask` failure channel for `VResultPath`.

## Metrics

<!-- verify -->
```java
CircuitBreakerMetrics m = breaker.metrics();

log.info("Circuit breaker: total={}, success={}, failed={}, rejected={}, transitions={}",
    m.totalCalls(), m.successfulCalls(), m.failedCalls(),
    m.rejectedCalls(), m.stateTransitions());
```

| Metric | Description |
|--------|-------------|
| `totalCalls` | Total calls attempted (including rejected and cancelled) |
| `successfulCalls` | Calls recorded as successes, including those whose exception `recordFailure` does not select |
| `failedCalls` | Calls that failed (counted by the failure predicate) |
| `rejectedCalls` | Calls rejected because the circuit was open, or half-open with every probe place taken |
| `stateTransitions` | Number of state transitions |
| `lastStateChange` | When the last transition occurred |

## Manual Control

<!-- verify -->
```java
// Reset to CLOSED, with its failure and probe counts at zero
breaker.reset();

// Manually trip to OPEN (e.g., during maintenance)
breaker.tripOpen();

// Inspect current state
CircuitBreaker.Status status = breaker.currentStatus();
```

## Combining with Retry

A common pattern is to combine circuit breaker with retry. The order matters:

<!-- verify -->
```java
// Circuit breaker inside retry: each retry attempt checks the circuit
VTask<String> resilient = Retry.retryTask(
    paymentBreaker.protect(VTask.of(() -> paymentService.get(url))),
    RetryPolicy.exponentialBackoff(3, Duration.ofMillis(200))
        .retryIf(ex -> !(ex instanceof CircuitOpenException)));
```

Note the retry predicate: `CircuitOpenException` should *not* be retried, because the circuit breaker has already determined the service is unhealthy. [Combined Patterns](combined.md) introduces `ResilienceBuilder`, which applies this ordering for you without manual wiring.

~~~admonish info title="Key Takeaways"
* **Three states**: CLOSED counts consecutive failures and opens at `failureThreshold`; OPEN rejects instantly for `openDuration`
* **Recovery is probed**: HALF_OPEN runs a call as a probe while running and successful probes are fewer than `successThreshold`, and turns the rest away; that many successes close it, and any probe failure re-opens it
* **One breaker per protected endpoint**: `protect()` is generic, so a single instance guards every call to that endpoint and they share failure state; unrelated endpoints get their own breakers
* **Only faults trip it**: on the typed carriers a business `Left` never counts; the `onOpen` overload lands rejections as typed `Left`s
* **Tune `recordFailure`**: a 400 or a validation error reflects the request, not the service; count only what signals an unhealthy dependency
~~~

~~~admonish tip title="See Also"
- [Retry](retry.md) - backoff strategies and retry configuration
- [Bulkhead](bulkhead.md) - concurrency limiting
- [Combined Patterns](combined.md) - composing all patterns with ResilienceBuilder
~~~

---

**Previous:** [Retry](retry.md)
**Next:** [Bulkhead](bulkhead.md)
