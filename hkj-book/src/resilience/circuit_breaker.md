# Circuit Breaker: Learning When to Stop

~~~admonish info title="What You'll Learn"
- How the circuit breaker state machine works (CLOSED, OPEN, HALF_OPEN)
- How to configure failure thresholds, recovery timeouts, and call timeouts
- How to protect VTask operations with a shared circuit breaker
- How to use fallbacks when the circuit is open
- How to monitor circuit breaker health via metrics
~~~

---

A retry policy assumes that trying again will eventually work. Sometimes it will not. If the database is down, retrying every 200 milliseconds for the next 30 seconds just generates 150 doomed requests. The service needs time to recover, and hammering it with traffic makes recovery harder.

A circuit breaker solves this by tracking recent failures and, when enough accumulate, *stopping* calls entirely for a cooling-off period. After the period expires, it cautiously allows a single probe request through. If the probe succeeds, normal traffic resumes. If it fails, the circuit re-opens.

## The State Machine

```
    Normal operation              Service failing              Probing recovery
    ┌────────────────┐           ┌────────────────┐           ┌────────────────┐
    │                │  failures │                │  timeout   │                │
    │     CLOSED     │  reach    │      OPEN      │  expires   │   HALF_OPEN    │
    │                │  threshold│                │           │                │
    │  All calls     │─────────▶│  All calls     │──────────▶│  One probe     │
    │  flow through  │           │  rejected with │           │  call allowed  │
    │                │           │  CircuitOpen-  │           │                │
    │  Failures      │           │  Exception     │           │  Success:      │
    │  counted       │           │                │           │  close circuit │
    │                │◀──────────│                │◀──────────│                │
    │  Successes     │  probes   │  No calls      │  probe    │  Failure:      │
    │  reset count   │  succeed  │  reach service │  fails    │  re-open       │
    └────────────────┘           └────────────────┘           └────────────────┘
```

| State | Behaviour | Transitions to |
|-------|-----------|----------------|
| **CLOSED** | All calls allowed. Consecutive failures counted. | OPEN (when failures reach threshold) |
| **OPEN** | All calls rejected immediately with `CircuitOpenException`. | HALF_OPEN (after open duration expires) |
| **HALF_OPEN** | One probe call allowed. | CLOSED (probe succeeds) or OPEN (probe fails) |

## Configuration

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
| `successThreshold` | 1 | Successful probes in HALF_OPEN before closing |
| `openDuration` | 60s | How long the circuit stays open |
| `callTimeout` | 10s | Timeout applied to each protected call |
| `recordFailure` | all exceptions | Predicate determining which exceptions count |

The `recordFailure` predicate is important: not every exception means the service is unhealthy. A `400 Bad Request` or a business validation error reflects a problem with the *request*, not the *service*. Only count failures that indicate the service itself is struggling.

## Creating a Circuit Breaker

```java
// With custom configuration
CircuitBreaker breaker = CircuitBreaker.create(config);

// With sensible defaults
CircuitBreaker breaker = CircuitBreaker.withDefaults();
```

## Protecting VTask Operations

The `protect()` method is generic. A single circuit breaker instance can protect calls that return different types:

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

When the circuit is open, `protect()` throws `CircuitOpenException`. Use `protectWithFallback()` to provide a default value instead:

```java
VTask<String> withFallback = paymentBreaker.protectWithFallback(
    VTask.of(() -> paymentService.getStatus(orderId)),
    ex -> "status-unavailable");
```

Or compose with `recover()` for more control:

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

## Metrics

```java
CircuitBreakerMetrics m = breaker.metrics();

log.info("Circuit breaker: total={}, success={}, failed={}, rejected={}, transitions={}",
    m.totalCalls(), m.successfulCalls(), m.failedCalls(),
    m.rejectedCalls(), m.stateTransitions());
```

| Metric | Description |
|--------|-------------|
| `totalCalls` | Total calls attempted (including rejected) |
| `successfulCalls` | Calls that completed successfully |
| `failedCalls` | Calls that failed (counted by the failure predicate) |
| `rejectedCalls` | Calls rejected because the circuit was open |
| `stateTransitions` | Number of state transitions |
| `lastStateChange` | When the last transition occurred |

## Manual Control

```java
// Reset to CLOSED with zeroed counters
breaker.reset();

// Manually trip to OPEN (e.g., during maintenance)
breaker.tripOpen();

// Inspect current state
CircuitBreaker.State state = breaker.currentState();
```

## Combining with Retry

A common pattern is to combine circuit breaker with retry. The order matters:

```java
// Circuit breaker inside retry: each retry attempt checks the circuit
VTask<String> resilient = Retry.retryTask(
    paymentBreaker.protect(VTask.of(() -> paymentService.get(url))),
    RetryPolicy.exponentialBackoff(3, Duration.ofMillis(200))
        .retryIf(ex -> !(ex instanceof CircuitOpenException)));
```

Note the retry predicate: `CircuitOpenException` should *not* be retried, because the circuit breaker has already determined the service is unhealthy. Use `ResilienceBuilder` for correct ordering without manual wiring:

```java
VTask<String> resilient = Resilience.<String>builder(
        VTask.of(() -> paymentService.get(url)))
    .withCircuitBreaker(paymentBreaker)
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(200)))
    .build();
```

~~~admonish tip title="See Also"
- [Retry](retry.md) -- backoff strategies and retry configuration
- [Bulkhead](bulkhead.md) -- concurrency limiting
- [Combined Patterns](combined.md) -- composing all patterns with ResilienceBuilder
~~~

---

**Previous:** [Retry](retry.md)
**Next:** [Bulkhead](bulkhead.md)
