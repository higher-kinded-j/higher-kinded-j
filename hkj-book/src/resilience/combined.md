# Combined Patterns: Layered Defences

~~~admonish info title="What You'll Learn"
- How to compose retry, circuit breaker, and bulkhead into a single protected operation
- Why the ordering of resilience patterns matters
- How to use `ResilienceBuilder` for correct, readable composition
- How resilience patterns integrate with VTask, VStream, and the Path API
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
    │ Circuit Breaker   │
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
    ┌─────────────────────────────────────────────────────────────┐
    │ 1. Timeout (outermost)                                      │
    │    Bounds total elapsed time across all retry attempts       │
    │                                                             │
    │   ┌─────────────────────────────────────────────────────┐   │
    │   │ 2. Bulkhead                                         │   │
    │   │    Limits concurrent access to the protected resource│   │
    │   │                                                     │   │
    │   │   ┌─────────────────────────────────────────────┐   │   │
    │   │   │ 3. Retry                                    │   │   │
    │   │   │    Re-attempts on transient failure          │   │   │
    │   │   │                                             │   │   │
    │   │   │   ┌─────────────────────────────────────┐   │   │   │
    │   │   │   │ 4. Circuit Breaker (innermost)      │   │   │   │
    │   │   │   │    Each attempt checks circuit state │   │   │   │
    │   │   │   │                                     │   │   │   │
    │   │   │   │         ┌──────────┐                │   │   │   │
    │   │   │   │         │   Task   │                │   │   │   │
    │   │   │   │         └──────────┘                │   │   │   │
    │   │   │   └─────────────────────────────────────┘   │   │   │
    │   │   └─────────────────────────────────────────────┘   │   │
    │   └─────────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────────┘
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

## Stream Integration

Resilience patterns compose with `VStream` through per-element VTask composition:

```java
// Per-element retry and circuit breaker protection
List<UserProfile> profiles = Path.vstreamFromList(userIds)
    .parEvalMap(4, id ->
        serviceBreaker.protect(
            VTask.of(() -> profileService.fetch(id))
                .retry(retryPolicy)))
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
**Next:** [Advanced Topics](../transformers/ch_intro.md)
