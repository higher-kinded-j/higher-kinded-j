# Combined Patterns: Layered Defences

~~~admonish info title="What You'll Learn"
- How to compose retry, circuit breaker, and bulkhead into a single protected operation
- Why the ordering of resilience patterns matters
- How to use `ResilienceBuilder` for correct, readable composition
- How resilience patterns integrate with VTask, VStream, and the Path API
- Which `with*` combinators are available on each Path carrier
- How to apply resilience per step, protecting the idempotent and leaving the non-idempotent alone
~~~

---

Each resilience pattern addresses a different failure mode. Retry handles transient failures. Circuit breaker handles persistent failures. Bulkhead handles resource exhaustion. In production, services face all three simultaneously. The question is how to layer them.

## The Ordering Problem

The order in which patterns wrap the underlying call determines their behaviour. Consider retry and circuit breaker:

```
    CORRECT: Circuit breaker inside retry
    ─────────────────────────────────────
    Retry sees each attempt individually.
    Circuit breaker records each attempt's outcome.
    If the circuit opens, retry stops (CircuitOpenException is not retryable).

    ┌─────────────────────────────────────────────┐
    │ Retry                                       │
    │   attempt 1 ──▶ ┌──────────────────┐ ──▶ ✗  │
    │   attempt 2 ──▶ │ Circuit Breaker  │ ──▶ ✗  │
    │   attempt 3 ──▶ │                  │ ──▶ ✓  │
    │                 └──────────────────┘        │
    └─────────────────────────────────────────────┘


    WRONG: Retry inside circuit breaker
    ────────────────────────────────────
    Circuit breaker sees one "call" that internally retries.
    A single logical failure counts as one failure, not three.
    The circuit breaker has an inaccurate picture of service health.

    ┌──────────────────┐
    │ Circuit Breaker  │
    │  ┌──────────────────────────────────┐
    │  │ Retry                            │
    │  │  attempt 1 ──▶ task ──▶ ✗        │
    │  │  attempt 2 ──▶ task ──▶ ✗        │  ── counts as
    │  │  attempt 3 ──▶ task ──▶ ✗        │     ONE failure
    │  └──────────────────────────────────┘
    └──────────────────┘
```

## The Correct Order

`ResilienceBuilder` applies patterns in a fixed order, from outermost to innermost:

```
    ┌───────────────────────────────────────────────────────────────┐
    │ 1. Timeout (outermost)                                        │
    │    Bounds total elapsed time across all retry attempts        │
    │                                                               │
    │   ┌───────────────────────────────────────────────────────┐   │
    │   │ 2. Bulkhead                                           │   │
    │   │    Limits concurrent access to the protected resource │   │
    │   │                                                       │   │
    │   │   ┌─────────────────────────────────────────────┐     │   │
    │   │   │ 3. Retry                                    │     │   │
    │   │   │    Re-attempts on transient failure         │     │   │
    │   │   │                                             │     │   │
    │   │   │   ┌───────────────────────────────────────┐ │     │   │
    │   │   │   │ 4. Circuit Breaker (innermost)        │ │     │   │
    │   │   │   │    Each attempt checks circuit state  │ │     │   │
    │   │   │   │                                       │ │     │   │
    │   │   │   │         ┌──────────┐                  │ │     │   │
    │   │   │   │         │   Task   │                  │ │     │   │
    │   │   │   │         └──────────┘                  │ │     │   │
    │   │   │   └───────────────────────────────────────┘ │     │   │
    │   │   └─────────────────────────────────────────────┘     │   │
    │   └───────────────────────────────────────────────────────┘   │
    └───────────────────────────────────────────────────────────────┘
```

This ordering ensures:
- The **timeout** bounds the entire operation, including all retries and wait times
- The **bulkhead** prevents too many concurrent operations from even starting
- **Retry** re-attempts the inner operation, each attempt independently
- The **circuit breaker** evaluates each attempt, and `CircuitOpenException` naturally stops retry (since it is not a retryable exception by default)

## Using ResilienceBuilder

```java
CircuitBreaker serviceBreaker = CircuitBreaker.create(
    CircuitBreakerConfig.builder()
        .failureThreshold(5)
        .openDuration(Duration.ofSeconds(30))
        .build());

Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(10);

RetryPolicy retryPolicy = RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200))
    .retryOn(IOException.class)
    .onRetry(e -> log.warn("Retry #{}: {}", e.attemptNumber(),
        e.lastException().getMessage()));

VTask<Response> resilientCall = Resilience.<Response>builder(
        VTask.of(() -> httpClient.get(url)))
    .withTimeout(Duration.ofSeconds(30))
    .withBulkhead(serviceBulkhead)
    .withRetry(retryPolicy)
    .withCircuitBreaker(serviceBreaker)
    .withFallback(ex -> Response.fallback())
    .build();

Response response = resilientCall.run();
```

The builder methods can be called in any order; patterns are always applied in the correct sequence.

## Convenience Methods

For simpler combinations, the `Resilience` utility class provides direct methods:

```java
// Circuit breaker + retry
VTask<String> protected1 = Resilience.withCircuitBreakerAndRetry(
    VTask.of(() -> service.call()),
    serviceBreaker,
    retryPolicy);

// All three core patterns
VTask<String> protected2 = Resilience.protect(
    VTask.of(() -> service.call()),
    serviceBreaker,
    retryPolicy,
    serviceBulkhead);
```

## Path-Native Resilience: Per-Step Protection

The `with*` vocabulary is available across the Path family, so resilience composes in the same fluent chains as `map` and `via`. The principle throughout: **resilience wraps a computation**. On the lazy carriers (`IOPath`, `VTaskPath`, `VResultPath`) the computation has not yet run, so the combinators chain as instance methods. `EitherPath` is eager — by the time an instance exists, the computation already ran — so there the same combinators are static, taking the step as a `Supplier`.

### Per-Carrier Availability

| Combinator | `IOPath` | `VTaskPath` | `VResultPath` | `EitherPath` (static) |
|------------|:--------:|:-----------:|:-------------:|:---------------------:|
| `withRetry(policy)` | ✓ | ✓ | ✓ | ✓ |
| `withRetry(retryOn, policy)` — typed errors opt in | · | · | ✓ | ✓ |
| `retry(maxAttempts, initialDelay)` convenience | ✓ | ✓ | · | · |
| `withTimeout(duration)` | ✓ | ✓ | · | · |
| `withTimeout(duration, onTimeout)` — timeout as a typed `Left` | · | · | ✓ | ✓ |
| `withCircuitBreaker(breaker)` | ✓ | ✓ | ✓ | ✓ |
| `withCircuitBreaker(breaker, onOpen)` — rejection as a typed `Left` | · | · | ✓ | ✓ |
| `withBulkhead(bulkhead)` | ✓ | ✓ | ✓ | ✓ |
| `withBulkhead(bulkhead, onFull)` — rejection as a typed `Left` | · | · | ✓ | ✓ |

The typed carriers (`EitherPath`, `VResultPath`) are **railway-aware** throughout, sharing one retry lowering internally:

- A business `Left` is a value, not a fault: it is **never retried** by default, and it **never trips a circuit breaker** (only thrown exceptions count as failures).
- The typed `withRetry(retryOn, policy)` overload opts selected transient errors in; on exhaustion the **last `Left`** is returned, staying on the typed channel.
- The `onTimeout` / `onOpen` / `onFull` overloads land timeouts, open-circuit rejections, and bulkhead rejections as typed `Left`s instead of thrown exceptions.
- Timeouts do **not** interrupt the losing computation — it keeps running unobserved after the typed timeout is returned, so bound its side effects accordingly.

On `IOPath` and `VTaskPath` there is no typed channel, so `withTimeout(duration)` surfaces the timeout on the failure channel (`IOPath` fails with a `CompletionException` wrapping the `TimeoutException`; `VTaskPath` fails with the `TimeoutException` itself), and breaker/bulkhead rejections propagate as exceptions.

### Choosing Per Step, Not Per Workflow

Resilience granularity matters more than resilience coverage. The worked example: an order pipeline where reserving inventory is idempotent (a reservation can safely be re-issued) but charging the card is not.

```java
// Shared infrastructure — one breaker per dependency, not per call
CircuitBreaker inventoryBreaker = CircuitBreaker.withDefaults();

RetryPolicy transientPolicy =
    RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200));

// Typed errors worth retrying: transient infrastructure failures — but never a
// rejection the breaker has already made. An open circuit means "stop asking".
Predicate<OrderError> transientError =
    error -> error instanceof OrderError.SystemError s
        && !"CIRCUIT_BREAKER_OPEN".equals(s.code());

EitherPath<OrderError, Shipment> workflow =
    validateOrder(request)
        // IDEMPOTENT step: reserving stock is safe to re-run, so it earns the
        // full treatment — breaker innermost (typed, so an open circuit lands
        // as a Left), railway-aware retry around it. A business Left such as
        // "out of stock" passes straight through: it is an answer, not a fault.
        .via(order ->
            EitherPath.withRetry(
                () ->
                    EitherPath.withCircuitBreaker(
                        () -> reserveInventory(order),
                        inventoryBreaker,
                        open -> OrderError.SystemError.circuitBreakerOpen("inventory")),
                transientError,
                transientPolicy))
        // NOT idempotent: a payment retried after the charge actually succeeded
        // double-bills the customer. No retry, no breaker — only a typed time
        // budget, arriving as a Left rather than a thrown TimeoutException.
        .via(reservation ->
            EitherPath.withTimeout(
                () -> chargePayment(reservation),
                Duration.ofSeconds(10),
                () -> OrderError.SystemError.timeout("payment", Duration.ofSeconds(10))))
        .via(payment -> createShipment(payment));
```

The layering inside the inventory step follows [the correct order](#the-ordering-problem): the breaker is inside the retry, so each attempt is individually recorded, and the `transientError` predicate refuses to retry an open-circuit rejection — the typed analogue of excluding `CircuitOpenException` from a retry predicate.

~~~admonish warning title="A Timeout Is Not a Rollback"
Since a timed-out computation is not interrupted, `Left(timeout)` on the payment step means the charge's outcome is *unknown*, not that it did not happen. Never respond to a payment timeout by simply retrying; reconcile with the payment provider, or use a [Saga](saga.md) to compensate.
~~~

The same shape works on the async railway: `VResultPath` carries these combinators as instance methods, so a lazy pipeline chains `.withRetry(retryOn, policy).withCircuitBreaker(breaker, onOpen)` directly. The order example's [`ConfigurableOrderWorkflow`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/ConfigurableOrderWorkflow.java) applies this per-step discipline in full: retry confined to an idempotent pre-flight, the committing phase run exactly once under a typed timeout — see [Order Workflow: Production Patterns](../hkts/order-production.md).

## Stream Integration

Resilience patterns compose with `VStream` through per-element VTask composition:

```java
// Per-element retry and circuit breaker protection
List<UserProfile> profiles = Path.vstreamFromList(userIds)
    .parEvalMap(4, id ->
        serviceBreaker.protect(
            Retry.retryTask(
                VTask.of(() -> profileService.fetch(id)),
                retryPolicy)))
    .recover(ex -> UserProfile.unknown())
    .toList()
    .run();
```

The `Resilience` utility provides convenience functions for this pattern:

```java
// Equivalent, using helper functions
Function<String, VTask<UserProfile>> resilientFetch =
    Resilience.withCircuitBreakerPerElement(
        Resilience.withRetryPerElement(
            id -> VTask.of(() -> profileService.fetch(id)),
            retryPolicy),
        serviceBreaker);

List<UserProfile> profiles = Path.vstreamFromList(userIds)
    .parEvalMap(4, resilientFetch)
    .recover(ex -> UserProfile.unknown())
    .toList()
    .run();
```

## Pattern Selection Guide

Not every service needs every pattern. Choose based on the failure characteristics:

```
    Is the service likely to fail?
    │
    ├── Occasionally (transient)
    │   └── Retry only
    │
    ├── Sometimes for extended periods
    │   └── Retry + Circuit Breaker
    │
    ├── Has limited capacity
    │   └── Retry + Bulkhead
    │
    └── Critical service, all failure modes possible
        └── Retry + Circuit Breaker + Bulkhead + Timeout
```

| Failure mode | Pattern | Why |
|-------------|---------|-----|
| Transient errors (network blips) | Retry | Trying again usually works |
| Service outage (deployment, crash) | Circuit Breaker | Stop wasting effort on a dead service |
| Resource exhaustion (connection pool) | Bulkhead | Prevent one slow service from consuming all threads |
| Unbounded latency | Timeout | Ensure callers do not wait forever |
| Multi-step distributed operations | Saga | Automatic compensation for partial failures |

## Complete Example

```java
// Shared infrastructure
CircuitBreaker paymentBreaker = CircuitBreaker.create(
    CircuitBreakerConfig.builder()
        .failureThreshold(3)
        .openDuration(Duration.ofSeconds(60))
        .recordFailure(ex -> ex instanceof IOException
            || ex instanceof TimeoutException)
        .build());

Bulkhead paymentBulkhead = Bulkhead.withMaxConcurrent(5);

RetryPolicy paymentRetry = RetryPolicy.exponentialBackoffWithJitter(
        3, Duration.ofMillis(500))
    .retryOn(IOException.class)
    .withMaxDelay(Duration.ofSeconds(5))
    .onRetry(e -> metrics.recordPaymentRetry(e));

// Build resilient payment call
VTask<PaymentResult> chargePayment = Resilience.<PaymentResult>builder(
        VTask.of(() -> paymentGateway.charge(order)))
    .withTimeout(Duration.ofSeconds(15))
    .withBulkhead(paymentBulkhead)
    .withRetry(paymentRetry)
    .withCircuitBreaker(paymentBreaker)
    .withFallback(ex -> {
        if (ex instanceof CircuitOpenException) {
            return PaymentResult.deferred("Payment service temporarily unavailable");
        }
        return PaymentResult.failed(ex.getMessage());
    })
    .build();

// Execute
PaymentResult result = chargePayment.run();
```

~~~admonish tip title="See Also"
- [Retry](retry.md) -- backoff strategies and retry configuration
- [Circuit Breaker](circuit_breaker.md) -- state machine and service protection
- [Bulkhead](bulkhead.md) -- concurrency limiting
- [Saga](saga.md) -- compensating transactions
~~~

---

**Previous:** [Saga](saga.md)
**Next:** [Reference](../effect/capabilities.md)
