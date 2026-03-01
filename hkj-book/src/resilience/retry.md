# Retry: Patience as Policy

~~~admonish info title="What You'll Learn"
- How to configure retry policies with different backoff strategies
- When to use fixed, exponential, linear, or jittered delays
- How to filter retries by exception type
- How to monitor retry attempts with `RetryEvent`
- How retry integrates with `VTask`, `IOPath`, and `VTaskPath`
~~~

---

Networks are unreliable. Services restart. Databases hiccup during failover. Most of these failures are transient: the same request that failed at 14:32:07.003 would have succeeded at 14:32:07.250. A retry policy encodes the belief that patience will be rewarded, whilst also setting a limit on how much patience to exercise.

## RetryPolicy

`RetryPolicy` is an immutable configuration object that describes how to retry: how many times, how long to wait, and which failures are worth retrying.

### Factory Methods

```java
// Fixed delay: same wait between every attempt
RetryPolicy fixed = RetryPolicy.fixed(3, Duration.ofMillis(100));
// Delays: 100ms, 100ms, 100ms

// Exponential backoff: doubling delays
RetryPolicy exponential = RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1));
// Delays: 1s, 2s, 4s, 8s, 16s (capped at maxDelay)

// Exponential with jitter: randomised to prevent thundering herd
RetryPolicy jittered = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1));
// Delays: ~1s, ~2s, ~4s (each randomised between 0 and the calculated delay)

// Linear backoff: delays increase by a fixed increment
RetryPolicy linear = RetryPolicy.linear(5, Duration.ofMillis(200));
// Delays: 200ms, 400ms, 600ms, 800ms, 1000ms

// No retry: fail immediately
RetryPolicy none = RetryPolicy.noRetry();
```

### Choosing a Backoff Strategy

```
    Fixed           Exponential        Exponential         Linear
    (predictable)   (aggressive)       + Jitter            (gentle)
                                       (distributed)

    ──X──X──X──     ──X─X──X────X──    ──X─X───X──X────   ──X──X───X────X──
      │  │  │         │ │  │    │        │ │   │  │          │  │   │    │
    100 100 100     100 200 400 800    ~100 ~200 ~400 ~800  200 400 600 800
    ms  ms  ms      ms  ms  ms  ms     ms   ms   ms   ms   ms  ms  ms  ms
```

| Strategy | Best for | Risk |
|----------|----------|------|
| Fixed | Known recovery time (e.g., lock contention) | Can overwhelm a recovering service |
| Exponential | Unknown recovery time | Slow convergence for quick recoveries |
| Exponential + Jitter | Multiple clients retrying the same service | Slightly less predictable |
| Linear | Gentle ramp-up, moderate recovery times | Slower backoff than exponential |

### Configuration

Policies are immutable. Configuration methods return new instances:

```java
RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100))
    .withMaxDelay(Duration.ofSeconds(30))   // Cap the maximum wait
    .retryOn(IOException.class);             // Only retry I/O errors
```

#### Custom Retry Predicates

```java
RetryPolicy selective = RetryPolicy.fixed(3, Duration.ofMillis(100))
    .retryIf(ex ->
        ex instanceof IOException
        || ex instanceof TimeoutException
        || (ex instanceof HttpException http && http.statusCode() >= 500));
```

#### The Builder

For complex policies, the builder offers full control:

```java
RetryPolicy policy = RetryPolicy.builder()
    .maxAttempts(5)
    .initialDelay(Duration.ofMillis(100))
    .backoffMultiplier(2.0)
    .maxDelay(Duration.ofSeconds(30))
    .useJitter(true)
    .retryOn(IOException.class)
    .onRetry(event -> log.warn("Retry #{}: {}",
        event.attemptNumber(), event.lastException().getMessage()))
    .build();
```

---

## Monitoring with RetryEvent

The `onRetry` listener receives a `RetryEvent` before each retry attempt:

```java
RetryPolicy monitored = RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1))
    .onRetry(event -> {
        log.warn("Attempt {} failed after {}: {}",
            event.attemptNumber(),
            event.nextDelay(),
            event.lastException().getMessage());
        metrics.incrementRetryCount(event.attemptNumber());
    });
```

`RetryEvent` contains:

| Field | Type | Description |
|-------|------|-------------|
| `attemptNumber()` | `int` | The 1-based attempt that just failed |
| `lastException()` | `Throwable` | The exception that triggered this retry |
| `nextDelay()` | `Duration` | How long the system will wait before the next attempt |
| `timestamp()` | `Instant` | When this event occurred |

---

## Using Retry

### Direct Execution

The `Retry` utility class executes an operation immediately with retry:

```java
String response = Retry.execute(policy, () -> httpClient.get(url));

// Convenience methods
String fast = Retry.withExponentialBackoff(3, Duration.ofMillis(100),
    () -> httpClient.get(url));

String fixed = Retry.withFixedDelay(3, Duration.ofMillis(100),
    () -> httpClient.get(url));
```

### VTask-Native Retry

For lazy, composable retry, use `VTask.retry()` or `Retry.retryTask()`:

```java
// As a VTask combinator (preferred)
VTask<String> resilient = VTask.of(() -> httpClient.get(url))
    .retry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200))
        .retryOn(IOException.class));

// Static method form
VTask<String> resilient2 = Retry.retryTask(
    VTask.of(() -> httpClient.get(url)),
    RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));
```

Both forms return a lazy `VTask`. Nothing executes until you call `run()`, `runSafe()`, or `runAsync()`.

### Retry with Fallback

```java
VTask<String> withFallback = Retry.retryTaskWithFallback(
    VTask.of(() -> httpClient.get(url)),
    RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)),
    lastError -> "default response");
```

### Retry with Recovery Task

```java
VTask<String> withRecovery = Retry.retryTaskWithRecovery(
    VTask.of(() -> primaryService.get(url)),
    RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)),
    lastError -> VTask.of(() -> backupService.get(url)));
```

### IOPath and VTaskPath Integration

```java
// IOPath
IOPath<Response> resilient = IOPath.delay(() -> httpClient.get(url))
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));

// VTaskPath (once Path API integration is complete)
VTaskPath<Response> resilient = Path.vtask(() -> httpClient.get(url))
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));
```

---

## Handling Exhausted Retries

When all attempts fail, `RetryExhaustedException` is thrown with the last failure as its cause:

```java
try {
    resilient.run();
} catch (RetryExhaustedException e) {
    log.error("All {} retries failed: {}", e.getAttempts(), e.getMessage());
    Throwable lastFailure = e.getCause();
    // Handle the last failure specifically
}
```

---

## Composing Retry with Other Patterns

Retry composes naturally with other resilience patterns and effect combinators:

```java
VTask<Data> robust = VTask.of(() -> primarySource.fetch())
    .retry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)))
    .recover(e -> {
        log.warn("Primary exhausted, trying backup", e);
        return Retry.retryTask(
            VTask.of(() -> backupSource.fetch()),
            RetryPolicy.fixed(2, Duration.ofMillis(100))
        ).run();
    })
    .recover(e -> {
        log.error("All sources failed", e);
        return Data.empty();
    });
```

---

## Quick Reference

| Pattern | Code |
|---------|------|
| Fixed delay | `RetryPolicy.fixed(3, Duration.ofMillis(100))` |
| Exponential backoff | `RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1))` |
| With jitter | `RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1))` |
| Linear backoff | `RetryPolicy.linear(5, Duration.ofMillis(200))` |
| Cap max delay | `.withMaxDelay(Duration.ofSeconds(30))` |
| Retry specific errors | `.retryOn(IOException.class)` |
| Custom predicate | `.retryIf(ex -> ...)` |
| Monitor retries | `.onRetry(event -> ...)` |
| Apply to VTask | `task.retry(policy)` |
| Apply to IOPath | `path.withRetry(policy)` |
| Simple retry | `Retry.retryTask(task, 3)` |
| Retry with fallback | `Retry.retryTaskWithFallback(task, policy, fallbackFn)` |
| Retry with recovery | `Retry.retryTaskWithRecovery(task, policy, recoveryFn)` |

~~~admonish tip title="See Also"
- [Circuit Breaker](circuit_breaker.md) -- protecting against persistent failures
- [Combined Patterns](combined.md) -- composing retry with circuit breaker and bulkhead
- [Effect Path API: Patterns and Recipes](../effect/patterns.md) -- retry in the context of IOPath
~~~

---

**Previous:** [Resilience Patterns](ch_intro.md)
**Next:** [Circuit Breaker](circuit_breaker.md)
