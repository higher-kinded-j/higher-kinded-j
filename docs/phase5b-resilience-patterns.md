# Resilience Patterns — Implementation Plan

## Overview

Introduces resilience patterns for robust virtual thread-based applications: Circuit Breaker, Saga,
Bulkhead, and combined resilience orchestration. These patterns integrate with `VTask`, `VStream`,
`Scope`, and the Path API layer to provide fault tolerance and distributed transaction management
with full type safety.

This plan builds on the **existing** retry foundation (`RetryPolicy`, `Retry`,
`RetryExhaustedException`) by extending it with VTask-native retry and monitoring, and adds new
patterns that complement the existing bounded-concurrency support in `VStreamPar` and the
structured-concurrency primitives in `Scope`.

### Existing Resilience Foundation

The following are already implemented and should **not** be recreated:

| Component | Location | Capability |
|---|---|---|
| `RetryPolicy` | `hkj-core/.../resilience/RetryPolicy.java` | Immutable retry configuration with `fixed()`, `exponentialBackoff()`, `exponentialBackoffWithJitter()`, `noRetry()`, builder, predicates, max delay, jitter |
| `Retry` | `hkj-core/.../resilience/Retry.java` | `execute(RetryPolicy, Supplier<A>)`, `withExponentialBackoff()`, `withFixedDelay()` |
| `RetryExhaustedException` | `hkj-core/.../resilience/RetryExhaustedException.java` | Exception preserving cause and attempt count |
| `IOPath.withRetry()` | `hkj-core/.../effect/IOPath.java` | Retry integration for IOPath |
| `IOPath.retry()` | `hkj-core/.../effect/IOPath.java` | Convenience retry for IOPath |
| `CompletableFuturePath.withRetry()` | `hkj-core/.../effect/CompletableFuturePath.java` | Retry integration for CompletableFuturePath |
| `VStreamPar` | `hkj-core/.../vstream/VStreamPar.java` | Bounded-concurrency parallel stream processing |
| `Resource` | `hkj-core/.../vtask/Resource.java` | Bracket pattern (acquire-use-release) with LIFO cleanup |
| `Scope` | `hkj-core/.../vtask/Scope.java` | Structured concurrency with `allSucceed`, `anySucceed`, `accumulating` joiners |

---

## Pre-Implementation Requirements

### Required Reading

Before beginning implementation, ensure familiarity with:

- [ ] `docs/STYLE-GUIDE.md` — Code style and naming conventions
- [ ] `docs/TESTING-GUIDE.md` — Three-layer testing strategy
- [ ] `docs/TUTORIAL-STYLE-GUIDE.md` — Tutorial writing guidelines
- [ ] `docs/PERFORMANCE-TESTING-GUIDE.md` — Benchmark requirements
- [ ] Existing `VTask` implementation — `map`, `flatMap`, `recover`, `recoverWith`, `mapError`, `timeout`, `asCallable`
- [ ] Existing `Scope` implementation — `allSucceed`, `anySucceed`, `accumulating`, `firstComplete` joiners
- [ ] Existing `Resource` implementation — bracket pattern, `make`, `fromAutoCloseable`, `and`, `flatMap`
- [ ] Existing `RetryPolicy` and `Retry` — current retry foundation
- [ ] Existing `IOPath.withRetry()` and `CompletableFuturePath.withRetry()` — Path-layer retry integration pattern
- [ ] Existing `VStream` error handling — `recover`, `recoverWith`, `mapError`, `onError`, `bracket`, `onFinalize`
- [ ] Existing `VStreamPar` — `parEvalMap`, `parEvalMapUnordered`, `parEvalFlatMap`, `merge`, `parCollect`
- [ ] Existing `Par` — `zip`, `map2`, `race`, `all`, `traverse`

### Design Decisions (Resolved)

| Question | Decision | Rationale |
|---|---|---|
| Circuit Breaker state: shared or per-instance? | Per-instance, unparameterized. Users share a single `CircuitBreaker` across VTasks of different types. | A circuit breaker protects a service endpoint, not a specific return type. The `protect()` method is generic. |
| Saga compensation vs. Scope cancellation? | Use `Scope` internally. `parallel()` uses `Scope.allSucceed()` for execution. Compensations run via `Scope.accumulating()` to collect failures. Compensations are registered outside the cancellation boundary. | Leverages existing structured concurrency. Accumulating joiner prevents fail-fast during compensation. |
| Bulkhead: semaphores vs. virtual thread limits? | Semaphores with configurable fairness. | Virtual threads are cheap but the protected resource (database, API) has finite capacity. `Semaphore` is the right primitive. |
| Resilience4j adapter integration? | Defer to a future phase. Focus on native implementations first. | Keeps the core library dependency-free. An `hkj-resilience4j` adapter module can be added later. |
| Relationship between Bulkhead and VStreamPar? | Complementary. `VStreamPar` handles bounded concurrency for stream pipelines. `Bulkhead` protects shared services/resources accessed by individual VTasks. | Different scopes: per-stream vs. per-service. A Bulkhead can optionally be used inside VStreamPar processing. |

---

## 1. VTask-Native Retry (Extend Existing)

### 1.1 Extend RetryPolicy with Monitoring

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryPolicy.java` (modify existing)

- [ ] Add `onRetry(Consumer<RetryEvent>)` method for retry monitoring
- [ ] Add `linear(int, Duration)` factory method for linear backoff

```java
// New factory method
public static RetryPolicy linear(int maxAttempts, Duration initialDelay);

// New configuration method
public RetryPolicy onRetry(Consumer<RetryEvent> listener);
```

### 1.2 RetryEvent

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryEvent.java`

- [ ] Create event record for retry monitoring

```java
public record RetryEvent(
    int attemptNumber,
    Throwable lastException,
    Duration nextDelay,
    Instant timestamp
) {}
```

### 1.3 Extend Retry with VTask-Native Methods

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Retry.java` (modify existing)

- [ ] Add lazy VTask retry methods that preserve composability
- [ ] Integrate with `RetryPolicy.onRetry` listener

```java
// Add to existing Retry class:

// Lazy VTask retry — returns a new VTask that retries on execution
public static <A> VTask<A> retryTask(VTask<A> task, RetryPolicy policy);

// Simple VTask retry — convenience with default exponential backoff
public static <A> VTask<A> retryTask(VTask<A> task, int maxAttempts);

// VTask retry with fallback value on exhaustion
public static <A> VTask<A> retryTaskWithFallback(
    VTask<A> task,
    RetryPolicy policy,
    Function<Throwable, A> fallback);

// VTask retry with recovery task on exhaustion
public static <A> VTask<A> retryTaskWithRecovery(
    VTask<A> task,
    RetryPolicy policy,
    Function<Throwable, VTask<A>> recovery);
```

These methods return a lazy `VTask<A>` that composes naturally:
```java
VTask<String> resilientFetch = Retry.retryTask(
    VTask.of(() -> httpClient.get(url)),
    RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
        .retryOn(IOException.class)
        .onRetry(e -> log.warn("Retry attempt {}", e.attemptNumber()))
).map(Response::body);
```

### 1.4 VTask Retry Convenience Method

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/VTask.java` (modify existing)

- [ ] Add `retry(RetryPolicy)` default method directly on VTask

```java
// Add to VTask interface:
default VTask<A> retry(RetryPolicy policy) {
    return Retry.retryTask(this, policy);
}
```

This mirrors how `VTask.timeout(Duration)` works — a combinator on the task itself.

---

## 2. Circuit Breaker Pattern

### 2.1 CircuitBreaker

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/CircuitBreaker.java`

- [ ] Create unparameterized `CircuitBreaker` class for protecting VTask operations
- [ ] Implement state machine: CLOSED -> OPEN -> HALF_OPEN -> CLOSED
- [ ] Thread-safe state management using `AtomicReference`
- [ ] `protect()` method is generic — a single instance protects calls returning different types

```java
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    // Factory methods
    public static CircuitBreaker create(CircuitBreakerConfig config);
    public static CircuitBreaker withDefaults();

    // Protection — generic method, not tied to circuit breaker's type
    public <A> VTask<A> protect(VTask<A> task);
    public <A> VTask<A> protectWithFallback(VTask<A> task, Function<Throwable, A> fallback);

    // State inspection
    public State currentState();
    public CircuitBreakerMetrics metrics();

    // Manual control
    public void reset();
    public void tripOpen();
}
```

**Timeout integration**: `protect()` applies `callTimeout` from `CircuitBreakerConfig` using
VTask's existing `.timeout(Duration)` method internally, rather than implementing a separate
timeout mechanism. Users can also apply their own `.timeout()` before passing to the circuit
breaker if they want different timeout semantics.

### 2.2 CircuitBreakerConfig

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/CircuitBreakerConfig.java`

- [ ] Create immutable configuration record
- [ ] Builder pattern for configuration

```java
public record CircuitBreakerConfig(
    int failureThreshold,           // Failures before opening
    int successThreshold,           // Successes in half-open before closing
    Duration openDuration,          // Time to stay open before half-open
    Duration callTimeout,           // Timeout for protected calls (uses VTask.timeout internally)
    Predicate<Throwable> recordFailure  // Which exceptions count as failures
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        public Builder failureThreshold(int threshold);
        public Builder successThreshold(int threshold);
        public Builder openDuration(Duration duration);
        public Builder callTimeout(Duration timeout);
        public Builder recordFailure(Predicate<Throwable> predicate);
        public CircuitBreakerConfig build();
    }
}
```

### 2.3 CircuitBreakerMetrics

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/CircuitBreakerMetrics.java`

- [ ] Create metrics record for monitoring

```java
public record CircuitBreakerMetrics(
    long totalCalls,
    long successfulCalls,
    long failedCalls,
    long rejectedCalls,
    long stateTransitions,
    Instant lastStateChange
) {}
```

### 2.4 CircuitOpenException

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/CircuitOpenException.java`

- [ ] Create exception thrown when circuit is open

```java
public class CircuitOpenException extends RuntimeException {
    private final CircuitBreaker.State state;
    private final Duration retryAfter;

    public CircuitOpenException(CircuitBreaker.State state, Duration retryAfter);
    public CircuitBreaker.State state();
    public Duration retryAfter();
}
```

---

## 3. Saga Pattern

### 3.1 Saga

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Saga.java`

- [ ] Create `Saga<A>` final class for compensating transactions
- [ ] Support for sequential and parallel saga execution
- [ ] Automatic compensation on failure in reverse order

**Distinction from Resource**: `Resource` manages individual resources with deterministic cleanup
(close files, release connections). `Saga` orchestrates multi-step distributed operations where
compensation may be complex business logic (refund payment, restore inventory). `Resource`
cleanup is always the same action; `Saga` compensation depends on what was successfully
completed.

```java
public final class Saga<A> {

    // Factory methods
    static <A> Saga<A> of(VTask<A> action, Consumer<A> compensate);
    static <A> Saga<A> of(VTask<A> action, Function<A, VTask<Unit>> compensate);
    static <A> Saga<A> noCompensation(VTask<A> action);

    // Composition
    <B> Saga<B> map(Function<A, B> f);
    <B> Saga<B> flatMap(Function<A, Saga<B>> f);

    // Sequential composition — each step receives the previous step's result
    <B> Saga<B> andThen(Function<A, Saga<B>> next);

    // Combining sagas
    static <A, B> Saga<Pair<A, B>> zip(Saga<A> a, Saga<B> b);
    static <A, B, C> Saga<C> map2(Saga<A> a, Saga<B> b, BiFunction<A, B, C> f);

    // Parallel composition — uses Scope.allSucceed() for execution,
    // Scope.accumulating() for compensations
    static <A> Saga<List<A>> parallel(List<Saga<A>> sagas);

    // Execution
    VTask<A> run();
    VTask<Either<SagaError, A>> runSafe();
}
```

**Scope integration**: `Saga.parallel()` uses `Scope.allSucceed()` internally for the forward
execution of saga steps (fail-fast if any step fails). Compensations are run via
`Scope.accumulating()` to collect compensation failures without fail-fast — all compensations
are attempted even if some fail. Compensations are registered outside the Scope's cancellation
boundary so that a cancelled saga step still triggers compensation for previously completed steps.

### 3.2 SagaStep

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/SagaStep.java`

- [ ] Internal record for tracking saga steps

```java
record SagaStep<A>(
    VTask<A> action,
    Function<A, VTask<Unit>> compensate,
    String name
) {
    public static <A> SagaStep<A> of(
        VTask<A> action,
        Consumer<A> compensate,
        String name) {
        return new SagaStep<>(
            action,
            a -> VTask.exec(() -> compensate.accept(a)),
            name
        );
    }
}
```

### 3.3 SagaError

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/SagaError.java`

- [ ] Create error type for saga failures

```java
public record SagaError(
    Throwable originalError,
    String failedStep,
    List<CompensationResult> compensationResults
) {
    public record CompensationResult(
        String stepName,
        Either<Throwable, Unit> result
    ) {}

    public boolean allCompensationsSucceeded();
    public List<Throwable> compensationFailures();
}
```

### 3.4 SagaBuilder

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/SagaBuilder.java`

- [ ] Fluent builder for complex sagas
- [ ] `stepAsync` variant accepts `Function<A, VTask<Unit>>` for async compensation

```java
public final class SagaBuilder<A> {

    public static SagaBuilder<Unit> start();

    public <B> SagaBuilder<B> step(
        String name,
        VTask<B> action,
        Consumer<B> compensate);

    public <B> SagaBuilder<B> step(
        String name,
        Function<A, VTask<B>> action,
        Consumer<B> compensate);

    // Async compensation variant
    public <B> SagaBuilder<B> stepAsync(
        String name,
        Function<A, VTask<B>> action,
        Function<B, VTask<Unit>> compensate);

    public <B> SagaBuilder<B> stepNoCompensation(
        String name,
        Function<A, VTask<B>> action);

    public Saga<A> build();
}
```

---

## 4. Bulkhead Pattern

### 4.1 Bulkhead

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Bulkhead.java`

- [ ] Create `Bulkhead` for limiting concurrent executions of individual VTasks
- [ ] Semaphore-based with configurable fairness

**Relationship to VStreamPar**: `VStreamPar` handles bounded concurrency for stream pipelines
(e.g., "process this stream with at most 4 in-flight elements"). `Bulkhead` protects a shared
service or resource at the VTask level (e.g., "this database allows at most 10 concurrent
connections"). A Bulkhead can be used inside VStreamPar processing to share a concurrency limit
across multiple streams accessing the same service.

```java
public final class Bulkhead {

    // Factory methods
    public static Bulkhead create(BulkheadConfig config);
    public static Bulkhead withMaxConcurrent(int maxConcurrent);

    // Protection — generic method
    public <A> VTask<A> protect(VTask<A> task);
    public <A> VTask<A> protectWithTimeout(VTask<A> task, Duration waitTimeout);

    // Metrics
    public BulkheadMetrics metrics();

    // State
    public int availablePermits();
    public int activeCount();
}
```

### 4.2 BulkheadConfig

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/BulkheadConfig.java`

```java
public record BulkheadConfig(
    int maxConcurrent,
    int maxWait,
    Duration waitTimeout,
    boolean fairness
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        public Builder maxConcurrent(int max);
        public Builder maxWait(int max);
        public Builder waitTimeout(Duration timeout);
        public Builder fairness(boolean fair);
        public BulkheadConfig build();
    }
}
```

### 4.3 BulkheadFullException

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/BulkheadFullException.java`

```java
public class BulkheadFullException extends RuntimeException {
    private final int maxConcurrent;
    private final int currentWaiting;

    public BulkheadFullException(int maxConcurrent, int currentWaiting);
}
```

---

## 5. VStream Resilience Integration

### 5.1 VStream Rate Limiting / Throttling

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamThrottle.java`

- [ ] Time-based rate limiting for streams (complementary to VStreamPar's concurrency-based limiting)

```java
public final class VStreamThrottle {

    // Emit at most maxElements per time window
    public static <A> VStream<A> throttle(
        VStream<A> stream, int maxElements, Duration window);

    // Add minimum delay between element emissions
    public static <A> VStream<A> metered(
        VStream<A> stream, Duration interval);
}
```

**Distinction from VStreamPar**: `VStreamPar` limits how many elements are _in-flight_
concurrently. `VStreamThrottle` limits how many elements are _emitted per time window_. Both
can be combined: a stream can have bounded concurrency (VStreamPar) and be rate-limited
(VStreamThrottle) simultaneously.

### 5.2 Stream-Level Resilience Combinators

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Resilience.java`

Per-element resilience for streams is achieved naturally through VTask composition (since
`VTask` will gain `.retry(policy)` in section 1). Dedicated stream-level combinators are
provided as convenience methods:

- [ ] Per-element retry via mapTask composition
- [ ] Circuit breaker-protected stream processing

```java
// In Resilience utility class:

// Retry each element's effectful processing
public static <A, B> VStream<B> retryMapTask(
    VStream<A> stream,
    Function<A, VTask<B>> f,
    RetryPolicy policy);

// Wraps each element's effectful processing with circuit breaker protection
public static <A, B> VStream<B> protectStream(
    VStream<A> stream,
    Function<A, VTask<B>> f,
    CircuitBreaker circuitBreaker);
```

These are convenience wrappers. Users can achieve the same with direct VTask composition:
```java
// Equivalent to Resilience.retryMapTask(stream, f, policy):
stream.mapTask(a -> f.apply(a).retry(policy));

// Equivalent to Resilience.protectStream(stream, f, cb):
stream.mapTask(a -> cb.protect(f.apply(a)));

// Combining both with bounded concurrency and rate limiting:
Path.vstreamFromList(urls)
    .parEvalMap(4, url ->
        circuitBreaker.protect(
            VTask.of(() -> httpClient.get(url))
                .retry(policy)
                .timeout(Duration.ofSeconds(5))))
    .throttle(Duration.ofMillis(100))
    .recover(ex -> Response.empty())
    .toList()
    .unsafeRun();
```

---

## 6. Combined Resilience Patterns

### 6.1 Resilience Utility Class

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Resilience.java`

- [ ] Combine multiple resilience patterns with documented ordering

```java
public final class Resilience {

    // Combine circuit breaker with retry
    public static <A> VTask<A> withCircuitBreakerAndRetry(
        VTask<A> task,
        CircuitBreaker circuitBreaker,
        RetryPolicy retryPolicy);

    // Combine all patterns
    public static <A> VTask<A> protect(
        VTask<A> task,
        CircuitBreaker circuitBreaker,
        RetryPolicy retryPolicy,
        Bulkhead bulkhead);

    // Builder for complex resilience chains
    public static <A> ResilienceBuilder<A> builder(VTask<A> task);
}
```

### 6.2 ResilienceBuilder

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/ResilienceBuilder.java`

- [ ] Builder for composing resilience patterns with explicit ordering

**Pattern application order** (outermost to innermost):
1. **Timeout** (outermost) — bounds total elapsed time
2. **Bulkhead** — limits concurrent access to the protected resource
3. **Retry** — retries the inner operation on failure
4. **Circuit Breaker** (innermost) — each attempt independently checks circuit state

This ordering means: a retry attempt that fails due to `CircuitOpenException` is **not** retried
(circuit breaker failure is not retryable by default), which is the correct behaviour. The
circuit breaker sees each retry attempt individually, so repeated failures across retries will
trip the circuit.

```java
public final class ResilienceBuilder<A> {

    public ResilienceBuilder<A> withCircuitBreaker(CircuitBreaker cb);
    public ResilienceBuilder<A> withCircuitBreaker(CircuitBreakerConfig config);
    public ResilienceBuilder<A> withRetry(RetryPolicy policy);
    public ResilienceBuilder<A> withBulkhead(Bulkhead bulkhead);
    public ResilienceBuilder<A> withBulkhead(BulkheadConfig config);
    public ResilienceBuilder<A> withTimeout(Duration timeout);
    public ResilienceBuilder<A> withFallback(Function<Throwable, A> fallback);

    public VTask<A> build();
}
```

**Example usage:**
```java
VTask<String> resilient = Resilience.<String>builder(
        VTask.of(() -> httpClient.get(url)))
    .withTimeout(Duration.ofSeconds(30))
    .withBulkhead(Bulkhead.withMaxConcurrent(10))
    .withRetry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200))
        .retryOn(IOException.class))
    .withCircuitBreaker(serviceCircuitBreaker)
    .withFallback(ex -> "default response")
    .build();
```

---

## 7. Path API Integration

This section addresses the ergonomic gaps across all three API layers:
- **Layer 0**: Raw VTask / VStream / IO
- **Layer 1**: Path API (VTaskPath, VStreamPath, IOPath)
- **Layer 2**: Context API (VTaskContext)

### 7.1 Current Gaps Summary

| Capability | IOPath | VTaskPath | VStreamPath | VTaskContext |
|---|---|---|---|---|
| `withRetry(policy)` | Yes | **Missing** | N/A | **Missing** |
| `retry(max, delay)` | Yes | **Missing** | N/A | **Missing** |
| `handleError` / `recover` | Yes | Yes | **Missing** | Yes |
| `handleErrorWith` / `recoverWith` | Yes | Yes | **Missing** | Yes |
| `mapError` | N/A | **Missing** | **Missing** | **Missing** |
| `onError` (side-effect) | N/A | N/A | **Missing** | N/A |
| `timeout` | N/A | Yes | N/A | Yes |
| `catching` (typed errors) | Yes | **Missing** | N/A | **Missing** |
| `asMaybe` | Yes | **Missing** | N/A | **Missing** |
| `asTry` | Yes | **Missing** | N/A | **Missing** |
| `race` | Yes | **Missing** | N/A | **Missing** |
| `parZipWith` | Yes | **Missing** (zipWith uses Par) | N/A | **Missing** |
| `guarantee` (finalizer) | Yes | **Missing** | `onFinalize` | **Missing** |
| `mapTask` (effectful map) | N/A | N/A | **Missing** | N/A |
| Rate limiting | N/A | N/A | **Missing** | N/A |

Notes:
- VTaskPath `zipWith` already uses `Par.map2` internally (parallel by default)
- VStreamPath has `parEvalMap` but no `mapTask` for sequential effectful mapping
- IOPath `race`/`parZipWith` use CompletableFuture; VTaskPath equivalents would use Par

### 7.2 VTaskPath Resilience Methods

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/VTaskPath.java` (modify existing)

- [ ] Add retry methods matching IOPath's API

```java
// Add to VTaskPath interface:

// ===== Retry Operations =====

/**
 * Returns a VTaskPath that retries this computation according to the given policy.
 */
VTaskPath<A> withRetry(RetryPolicy policy);

/**
 * Returns a VTaskPath that retries with exponential backoff and jitter.
 */
VTaskPath<A> retry(int maxAttempts, Duration initialDelay);
```

- [ ] Add circuit breaker and bulkhead protection

```java
// ===== Resilience Operations =====

/**
 * Returns a VTaskPath protected by the given circuit breaker.
 */
VTaskPath<A> withCircuitBreaker(CircuitBreaker circuitBreaker);

/**
 * Returns a VTaskPath protected by the given bulkhead.
 */
VTaskPath<A> withBulkhead(Bulkhead bulkhead);
```

**DefaultVTaskPath implementations:**
```java
@Override
public VTaskPath<A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new DefaultVTaskPath<>(Retry.retryTask(value, policy));
}

@Override
public VTaskPath<A> retry(int maxAttempts, Duration initialDelay) {
    return withRetry(RetryPolicy.exponentialBackoffWithJitter(maxAttempts, initialDelay));
}

@Override
public VTaskPath<A> withCircuitBreaker(CircuitBreaker circuitBreaker) {
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    return new DefaultVTaskPath<>(circuitBreaker.protect(value));
}

@Override
public VTaskPath<A> withBulkhead(Bulkhead bulkhead) {
    Objects.requireNonNull(bulkhead, "bulkhead must not be null");
    return new DefaultVTaskPath<>(bulkhead.protect(value));
}
```

### 7.3 VTaskPath Parity with IOPath

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/VTaskPath.java` (modify existing)

These methods bring VTaskPath to feature parity with IOPath. They don't depend on the
resilience package and could be added independently.

- [ ] Add typed error wrapping methods

```java
// ===== Effect Wrapping Methods =====

/**
 * Wraps the result in an Either, catching exceptions.
 */
<E> VTaskPath<Either<E, A>> catching(
    Function<? super Throwable, ? extends E> exceptionMapper);

/**
 * Converts exceptions to Nothing, success to Just.
 */
VTaskPath<Maybe<A>> asMaybe();

/**
 * Wraps the result in a Try.
 */
VTaskPath<Try<A>> asTry();
```

- [ ] Add error mapping

```java
/**
 * Transforms the exception without affecting success values.
 */
VTaskPath<A> mapError(Function<? super Throwable, ? extends Throwable> f);
```

- [ ] Add resource safety

```java
/**
 * Ensures a finalizer runs whether this task succeeds or fails.
 */
VTaskPath<A> guarantee(Runnable finalizer);
```

- [ ] Add parallel combinators

```java
/**
 * Combines this path with another in parallel on virtual threads.
 * Note: zipWith already uses Par.map2; this is an explicit parallel alias.
 */
<B, C> VTaskPath<C> parZipWith(
    VTaskPath<B> other, BiFunction<? super A, ? super B, ? extends C> combiner);

/**
 * Races this path against another, returning the first to complete.
 */
VTaskPath<A> race(VTaskPath<A> other);
```

**DefaultVTaskPath implementations:**
```java
@Override
public <E> VTaskPath<Either<E, A>> catching(
        Function<? super Throwable, ? extends E> exceptionMapper) {
    Objects.requireNonNull(exceptionMapper, "exceptionMapper must not be null");
    return new DefaultVTaskPath<>(VTask.delay(() -> {
        try {
            return Either.right(this.unsafeRun());
        } catch (Throwable t) {
            return Either.left(exceptionMapper.apply(t));
        }
    }));
}

@Override
public VTaskPath<Maybe<A>> asMaybe() {
    return new DefaultVTaskPath<>(VTask.delay(() -> {
        try {
            return Maybe.just(this.unsafeRun());
        } catch (Throwable t) {
            return Maybe.nothing();
        }
    }));
}

@Override
public VTaskPath<Try<A>> asTry() {
    return new DefaultVTaskPath<>(VTask.delay(() -> this.runSafe()));
}

@Override
public VTaskPath<A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVTaskPath<>(value.mapError(f));
}

@Override
public VTaskPath<A> guarantee(Runnable finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");
    return new DefaultVTaskPath<>(VTask.delay(() -> {
        try {
            return this.unsafeRun();
        } finally {
            finalizer.run();
        }
    }));
}

@Override
public <B, C> VTaskPath<C> parZipWith(
        VTaskPath<B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner) {
    // VTaskPath.zipWith already uses Par.map2 for parallel execution.
    // parZipWith is an explicit alias for discoverability / IOPath symmetry.
    return zipWith(other, combiner);
}

@Override
public VTaskPath<A> race(VTaskPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return new DefaultVTaskPath<>(
        Par.race(List.of(this.run(), other.run())));
}
```

### 7.4 VStreamPath Error Handling and Resilience

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/VStreamPath.java` (modify existing)

VStream already has `recover`, `recoverWith`, `mapError`, `onError`, and `mapTask`.
VStreamPath currently exposes **none** of these. Users who want stream-level error handling
must drop down to the raw VStream API, breaking the fluent Path chain.

- [ ] Expose VStream error handling methods on VStreamPath

```java
// Add to VStreamPath interface:

// ===== Error handling =====

/**
 * Recovers from stream errors by providing a fallback value.
 * If pulling an element fails, the recovery function produces a replacement.
 */
VStreamPath<A> recover(Function<? super Throwable, ? extends A> recovery);

/**
 * Recovers from stream errors by switching to a fallback stream.
 */
VStreamPath<A> recoverWith(
    Function<? super Throwable, ? extends VStreamPath<A>> recovery);

/**
 * Transforms errors without affecting successfully produced elements.
 */
VStreamPath<A> mapError(Function<? super Throwable, ? extends Throwable> f);

/**
 * Performs a side effect when an error occurs, then re-raises the error.
 */
VStreamPath<A> onError(Consumer<? super Throwable> action);
```

- [ ] Expose VStream effectful mapping on VStreamPath

```java
// ===== Effectful mapping =====

/**
 * Applies an effectful function to each element sequentially.
 * Unlike parEvalMap, this processes elements one at a time.
 */
<B> VStreamPath<B> mapTask(Function<? super A, ? extends VTask<B>> f);
```

- [ ] Add rate limiting methods on VStreamPath (delegates to VStreamThrottle)

```java
// ===== Rate limiting =====

/**
 * Limits throughput to at most maxElements per time window.
 */
VStreamPath<A> throttle(int maxElements, Duration window);

/**
 * Adds a fixed delay between element emissions.
 */
VStreamPath<A> metered(Duration interval);
```

**DefaultVStreamPath implementations:**
```java
@Override
public VStreamPath<A> recover(Function<? super Throwable, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new DefaultVStreamPath<>(stream.recover(recovery));
}

@Override
public VStreamPath<A> recoverWith(
        Function<? super Throwable, ? extends VStreamPath<A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new DefaultVStreamPath<>(
        stream.recoverWith(t -> recovery.apply(t).run()));
}

@Override
public VStreamPath<A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVStreamPath<>(stream.mapError(f));
}

@Override
public VStreamPath<A> onError(Consumer<? super Throwable> action) {
    Objects.requireNonNull(action, "action must not be null");
    return new DefaultVStreamPath<>(stream.onError(action));
}

@Override
public <B> VStreamPath<B> mapTask(Function<? super A, ? extends VTask<B>> f) {
    Objects.requireNonNull(f, "f must not be null");
    @SuppressWarnings("unchecked")
    Function<A, VTask<B>> typedF = (Function<A, VTask<B>>) (Function<?, ?>) f;
    return new DefaultVStreamPath<>(stream.mapTask(typedF));
}

@Override
public VStreamPath<A> throttle(int maxElements, Duration window) {
    return new DefaultVStreamPath<>(VStreamThrottle.throttle(stream, maxElements, window));
}

@Override
public VStreamPath<A> metered(Duration interval) {
    return new DefaultVStreamPath<>(VStreamThrottle.metered(stream, interval));
}
```

### 7.5 VTaskContext Resilience Methods

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/context/VTaskContext.java` (modify existing)

VTaskContext is the Layer 2 user-friendly API. It should expose resilience methods that
mirror VTaskPath but use VTaskContext's own chaining pattern (no Chainable/Combinable).

- [ ] Add retry, circuit breaker, and bulkhead to VTaskContext

```java
// Add to VTaskContext:

/**
 * Returns a VTaskContext that retries this computation according to the given policy.
 */
public VTaskContext<A> withRetry(RetryPolicy policy) {
    return fromPath(path.withRetry(policy));
}

/**
 * Returns a VTaskContext that retries with exponential backoff and jitter.
 */
public VTaskContext<A> retry(int maxAttempts, Duration initialDelay) {
    return fromPath(path.retry(maxAttempts, initialDelay));
}

/**
 * Returns a VTaskContext protected by the given circuit breaker.
 */
public VTaskContext<A> withCircuitBreaker(CircuitBreaker circuitBreaker) {
    return fromPath(path.withCircuitBreaker(circuitBreaker));
}

/**
 * Returns a VTaskContext protected by the given bulkhead.
 */
public VTaskContext<A> withBulkhead(Bulkhead bulkhead) {
    return fromPath(path.withBulkhead(bulkhead));
}
```

### 7.6 End-to-End Ergonomic Examples

These examples demonstrate the composability of the complete resilience API across all layers.

**VTaskPath — resilient HTTP client:**
```java
CircuitBreaker serviceBreaker = CircuitBreaker.create(
    CircuitBreakerConfig.builder()
        .failureThreshold(5)
        .openDuration(Duration.ofSeconds(30))
        .build());

RetryPolicy retryPolicy = RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200))
    .retryOn(IOException.class)
    .onRetry(e -> log.warn("Retry #{}: {}", e.attemptNumber(), e.lastException().getMessage()));

VTaskPath<Response> resilientCall = Path.vtask(() -> httpClient.get(url))
    .withCircuitBreaker(serviceBreaker)
    .withRetry(retryPolicy)
    .timeout(Duration.ofSeconds(10))
    .handleError(ex -> Response.fallback());

Response response = resilientCall.unsafeRun();
```

**VTaskContext — user-friendly Layer 2:**
```java
Try<Response> result = VTaskContext.of(() -> httpClient.get(url))
    .withCircuitBreaker(serviceBreaker)
    .retry(3, Duration.ofMillis(200))
    .timeout(Duration.ofSeconds(10))
    .recover(ex -> Response.fallback())
    .run();
```

**VStreamPath — resilient stream processing with rate limiting:**
```java
List<UserProfile> profiles = Path.vstreamFromList(userIds)
    .parEvalMap(4, id ->
        serviceBreaker.protect(
            VTask.of(() -> profileService.fetch(id))
                .retry(retryPolicy)))
    .metered(Duration.ofMillis(50))
    .recover(ex -> UserProfile.unknown())
    .toList()
    .unsafeRun();
```

**VStreamPath — API scraping with throttle:**
```java
Path.vstreamRange(1, 1000)
    .map(page -> apiUrl + "?page=" + page)
    .mapTask(url -> VTask.of(() -> httpClient.get(url)))
    .throttle(10, Duration.ofSeconds(1))    // max 10 requests/second
    .map(Response::body)
    .onError(ex -> log.error("Failed page fetch", ex))
    .recover(ex -> "")
    .filter(body -> !body.isEmpty())
    .toList()
    .unsafeRun();
```

**ForPath — resilient for-comprehension:**
```java
VTaskPath<OrderConfirmation> order = ForPath.from(
        Path.vtask(() -> userService.getUser(userId))
            .withCircuitBreaker(userServiceBreaker)
            .retry(2, Duration.ofMillis(100)))
    .from(user ->
        Path.vtask(() -> inventoryService.reserve(user, itemId))
            .withCircuitBreaker(inventoryBreaker))
    .from((user, reservation) ->
        Path.vtask(() -> paymentService.charge(user, reservation.amount()))
            .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(500))))
    .yield((user, reservation, payment) ->
        new OrderConfirmation(user, reservation, payment));
```

---

## 8. Testing Requirements

### 8.1 Existing Tests (Reference — Not Recreated)

The following tests already exist and cover the current retry foundation:

- `RetryTest.java` (~492 lines) — basic execution, retry-and-succeed, exception handling, delay, interruption, predicate, exhaustion
- `RetryPolicyTest.java` (~382 lines) — factory methods, configuration, delay calculation, builder, predicate filtering
- `IOPathRetryTest.java` (~299 lines) — integration with IOPath's `withRetry()` and `retry()`

### 8.2 New Unit Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/resilience/`

Following the three-layer testing strategy from TESTING-GUIDE.md:

#### RetryTaskTest.java (new — tests VTask-native retry additions)

- [ ] `@Nested` class structure:
  - [ ] `VTaskRetryTests` — `retryTask()` basic behaviour
  - [ ] `VTaskRetryWithFallbackTests` — fallback on exhaustion
  - [ ] `VTaskRetryWithRecoveryTests` — recovery task on exhaustion
  - [ ] `RetryEventTests` — `onRetry` callback invocation and timing
  - [ ] `LinearBackoffTests` — linear backoff strategy
  - [ ] `VTaskRetryConvenienceTests` — `VTask.retry(policy)` method
  - [ ] `LazyEvaluationTests` — verify VTask retry is lazy (no execution until `.run()`)

#### CircuitBreakerTest.java

- [ ] `@Nested` class structure:
  - [ ] `StateTransitionTests` — CLOSED -> OPEN -> HALF_OPEN -> CLOSED
  - [ ] `FailureThresholdTests` — Opening after threshold failures
  - [ ] `SuccessThresholdTests` — Closing after threshold successes in half-open
  - [ ] `TimeoutTests` — Open duration behaviour, callTimeout via VTask.timeout
  - [ ] `MetricsTests` — Metrics accuracy
  - [ ] `ConcurrencyTests` — Thread-safe state management under concurrent access
  - [ ] `FallbackTests` — Fallback execution when circuit is open
  - [ ] `GenericProtectTests` — Single circuit breaker protecting different return types

#### SagaTest.java

- [ ] `@Nested` class structure:
  - [ ] `SuccessfulExecutionTests` — All steps succeed
  - [ ] `CompensationTests` — Compensation on failure, reverse order
  - [ ] `ParallelSagaTests` — Parallel saga execution via Scope
  - [ ] `CompensationOrderTests` — Reverse compensation order
  - [ ] `PartialCompensationFailureTests` — Some compensations fail, all attempted
  - [ ] `BuilderTests` — SagaBuilder functionality
  - [ ] `AsyncCompensationTests` — `stepAsync` with VTask compensation

#### BulkheadTest.java

- [ ] `@Nested` class structure:
  - [ ] `ConcurrencyLimitTests` — Respects max concurrent
  - [ ] `WaitQueueTests` — Wait queue behaviour
  - [ ] `TimeoutTests` — Wait timeout
  - [ ] `MetricsTests` — Metrics accuracy
  - [ ] `FairnessTests` — FIFO ordering when fair
  - [ ] `GenericProtectTests` — Single bulkhead protecting different return types

#### ResilienceBuilderTest.java

- [ ] `@Nested` class structure:
  - [ ] `PatternOrderingTests` — Verify correct Timeout > Bulkhead > Retry > CircuitBreaker ordering
  - [ ] `CombinedPatternsTests` — Multiple patterns composed together
  - [ ] `CircuitBreakerStopsRetryTests` — `CircuitOpenException` not retried by default
  - [ ] `FallbackTests` — Fallback after all patterns exhausted

#### VStreamResilienceTest.java

- [ ] `@Nested` class structure:
  - [ ] `StreamCircuitBreakerTests` — Circuit breaker protecting stream element processing
  - [ ] `StreamRetryTests` — Per-element retry
  - [ ] `ThrottleTests` — Time-based rate limiting
  - [ ] `MeteredTests` — Fixed delay between elements

#### VTaskPathResilienceTest.java

- [ ] `@Nested` class structure:
  - [ ] `WithRetryTests` — `VTaskPath.withRetry()` behaviour
  - [ ] `RetryConvenienceTests` — `VTaskPath.retry(maxAttempts, initialDelay)`
  - [ ] `WithCircuitBreakerTests` — `VTaskPath.withCircuitBreaker()` behaviour
  - [ ] `WithBulkheadTests` — `VTaskPath.withBulkhead()` behaviour
  - [ ] `CatchingTests` — `catching()`, `asMaybe()`, `asTry()` typed error wrapping
  - [ ] `MapErrorTests` — `mapError()` exception transformation
  - [ ] `GuaranteeTests` — `guarantee()` finalizer
  - [ ] `RaceTests` — `race()` first-to-complete
  - [ ] `ChainedResilienceTests` — retry + circuitBreaker + timeout + handleError composition

#### VStreamPathErrorHandlingTest.java

- [ ] `@Nested` class structure:
  - [ ] `RecoverTests` — `recover()` element-level fallback
  - [ ] `RecoverWithTests` — `recoverWith()` stream switching
  - [ ] `MapErrorTests` — `mapError()` exception transformation
  - [ ] `OnErrorTests` — `onError()` side-effect on error
  - [ ] `MapTaskTests` — `mapTask()` sequential effectful mapping
  - [ ] `ThrottleTests` — `throttle()` time-based rate limiting
  - [ ] `MeteredTests` — `metered()` fixed delay between elements
  - [ ] `CombinedResilienceTests` — parEvalMap + retry + recover + throttle composition

#### VTaskContextResilienceTest.java

- [ ] `@Nested` class structure:
  - [ ] `WithRetryTests` — `VTaskContext.withRetry()` and `retry()`
  - [ ] `WithCircuitBreakerTests` — `VTaskContext.withCircuitBreaker()`
  - [ ] `WithBulkheadTests` — `VTaskContext.withBulkhead()`
  - [ ] `CombinedResilienceTests` — Layer 2 resilience composition

### 8.3 Property-Based Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/resilience/`

#### CircuitBreakerPropertyTest.java

- [ ] State machine invariants
- [ ] Metrics consistency
- [ ] Thread-safety properties

#### SagaPropertyTest.java

- [ ] Compensation always runs on failure
- [ ] Compensation order is reverse of execution
- [ ] All steps compensated up to failure point

#### RetryTaskPropertyTest.java

- [ ] VTask retry never exceeds max attempts
- [ ] Delays follow backoff strategy
- [ ] Only retries matching exceptions
- [ ] `onRetry` called exactly `attempts - 1` times

### 8.4 Coverage Requirements

- [ ] **100% line coverage** for all new resilience implementation
- [ ] **100% branch coverage** for all new resilience implementation
- [ ] All state transitions tested
- [ ] All error paths tested
- [ ] Concurrency edge cases tested

---

## 9. ArchUnit Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/architecture/`

### 9.1 ResilienceArchitectureRules.java

- [ ] Configuration records must be immutable
- [ ] Builders must be nested in config classes
- [ ] Exception classes must extend appropriate base
- [ ] Utility classes must be final with private constructor
- [ ] State enums must be package-private or inner classes

---

## 10. Documentation Plan

> **IMPORTANT**: Review this plan with project owner before writing documentation. hkj-book documentation must be checked before commit.

### 10.1 hkj-book Documentation

**Location**: `hkj-book/src/`

#### New Pages Required

- [ ] `resilience/ch_intro.md` — Resilience Patterns Overview
  - Introduction to resilience
  - Pattern selection guide
  - When to use each pattern
  - Relationship to existing VTask/VStream error handling

- [ ] `resilience/circuit-breaker.md` — Circuit Breaker Pattern
  - What You'll Learn
  - State machine explanation
  - Configuration options
  - Usage examples (single breaker, multiple types)
  - Monitoring and metrics
  - Key Takeaways

- [ ] `resilience/saga.md` — Saga Pattern
  - What You'll Learn
  - Compensating transactions concept
  - Distinction from Resource (bracket pattern)
  - Sequential vs parallel sagas (Scope integration)
  - Error handling and compensation
  - Usage examples
  - Key Takeaways

- [ ] `resilience/retry-bulkhead.md` — Retry, Bulkhead, and Rate Limiting
  - What You'll Learn
  - VTask-native retry (building on existing RetryPolicy)
  - Retry monitoring with RetryEvent
  - Bulkhead for concurrency limiting (vs. VStreamPar)
  - VStream rate limiting / throttling
  - Combining patterns with ResilienceBuilder
  - Pattern application ordering
  - Usage examples
  - Key Takeaways

#### Updates Required

- [ ] Update main SUMMARY.md with new chapter
- [ ] Add cross-references from VTask documentation
- [ ] Add cross-references from VStream documentation
- [ ] Update glossary with resilience terms

### 10.2 Javadoc Requirements

- [ ] All public classes fully documented
- [ ] All public methods with @param, @return, @throws
- [ ] Package-level documentation for `org.higherkindedj.hkt.resilience` (update existing)
- [ ] Examples in Javadoc
- [ ] Cross-references to related types

---

## 11. Tutorial Plan

> **IMPORTANT**: Review this plan with project owner before writing tutorials.

**Location**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/resilience/`

### 11.1 Tutorial Track: Resilience Patterns

#### Tutorial01_CircuitBreaker.java (~10 minutes)

- [ ] Exercise 1: Create basic circuit breaker
- [ ] Exercise 2: Configure failure threshold
- [ ] Exercise 3: Protect a VTask (show generic method — different return types)
- [ ] Exercise 4: Handle circuit open state
- [ ] Exercise 5: Use fallback
- [ ] Exercise 6: Monitor metrics

#### Tutorial02_Saga.java (~12 minutes)

- [ ] Exercise 1: Create simple saga with compensation
- [ ] Exercise 2: Chain saga steps with dependent data
- [ ] Exercise 3: Handle saga failure and verify compensation
- [ ] Exercise 4: Parallel saga execution
- [ ] Exercise 5: Use SagaBuilder
- [ ] Exercise 6: Handle compensation failures

#### Tutorial03_RetryBulkheadResilience.java (~12 minutes)

- [ ] Exercise 1: VTask-native retry with `retryTask()` and `VTask.retry(policy)`
- [ ] Exercise 2: Retry monitoring with `RetryEvent`
- [ ] Exercise 3: Retry conditions and backoff strategies
- [ ] Exercise 4: Create bulkhead and protect VTasks
- [ ] Exercise 5: Build complete resilience chain with `ResilienceBuilder`

#### Tutorial04_PathResilience.java (~12 minutes)

- [ ] Exercise 1: VTaskPath `withRetry()` and `retry()` — parity with IOPath
- [ ] Exercise 2: VTaskPath `catching()`, `asMaybe()`, `asTry()` — typed error wrapping
- [ ] Exercise 3: VTaskPath `withCircuitBreaker()` and `withBulkhead()` in a chain
- [ ] Exercise 4: VStreamPath `recover()` and `onError()` — stream error handling
- [ ] Exercise 5: VStreamPath `mapTask()` with per-element retry
- [ ] Exercise 6: VStreamPath `throttle()` and `metered()` — rate limiting
- [ ] Exercise 7: VTaskContext Layer 2 — `retry()`, `withCircuitBreaker()`, `recover()`
- [ ] Exercise 8: ForPath — resilient for-comprehension with circuit breaker per step

### 11.2 Solution Files

- [ ] `solutions/Tutorial01_CircuitBreaker_Solution.java`
- [ ] `solutions/Tutorial02_Saga_Solution.java`
- [ ] `solutions/Tutorial03_RetryBulkheadResilience_Solution.java`
- [ ] `solutions/Tutorial04_PathResilience_Solution.java`

### 11.3 Tutorial README

- [ ] Create `tutorial/resilience/README.md`
- [ ] Update main tutorial README

---

## 12. Example Applications

**Location**: `hkj-examples/src/main/java/org/higherkindedj/example/resilience/`

### 12.1 CircuitBreakerExample.java

- [ ] HTTP client with circuit breaker
- [ ] Single circuit breaker shared across multiple service endpoints
- [ ] Fallback handling

### 12.2 SagaOrderExample.java

- [ ] E-commerce order saga
- [ ] Payment, inventory, shipping steps
- [ ] Compensation on failure

### 12.3 ResilientServiceExample.java

- [ ] Combined patterns example using `ResilienceBuilder`
- [ ] Real-world service call protection
- [ ] Rate-limited stream processing with VStreamThrottle
- [ ] Monitoring and logging via `RetryEvent` and `CircuitBreakerMetrics`

---

## 13. Implementation Order

Suggested sequence based on dependencies:

| Phase | Component | Dependencies | Risk |
|---|---|---|---|
| 1 | VTask retry methods + RetryEvent + linear backoff | Existing `RetryPolicy`, `Retry` | Low — extends existing code |
| 2 | Circuit Breaker | VTask.timeout | Medium — new state machine |
| 3 | Bulkhead | Semaphore | Low — straightforward |
| 4 | VStreamPath error handling + mapTask | VStream (already has these methods) | Low — delegation only |
| 5 | VTaskPath parity (catching, asMaybe, asTry, mapError, guarantee, race) | VTask, Par, Either, Maybe, Try | Low — follows IOPath pattern |
| 6 | VTaskPath + VStreamPath + VTaskContext resilience integration | Circuit Breaker, Bulkhead, RetryPolicy | Low — delegation to underlying types |
| 7 | VStreamThrottle (rate limiting) | VStream, VTask | Medium — new time-based operators |
| 8 | ResilienceBuilder | Circuit Breaker, Bulkhead, RetryPolicy | Low — composition only |
| 9 | Saga | VTask, Scope | High — most complex pattern |

Phases 2-3 can run in parallel. Phases 4-5 can run in parallel.
Phase 9 (Saga) can run in parallel with phases 6-8.

---

## 14. Completion Criteria

### 14.1 Code Complete

- [ ] All implementation classes complete
- [ ] All tests passing
- [ ] **100% test coverage achieved** for new code
- [ ] ArchUnit tests passing
- [ ] Code review approved
- [ ] Consistent with existing higher-kinded-j patterns

### 14.2 Documentation Complete

- [ ] Documentation plan reviewed and approved by project owner
- [ ] All hkj-book pages written (after approval)
- [ ] All Javadoc complete
- [ ] Tutorials written and tested
- [ ] README updates complete

### 14.3 Integration Verified

- [ ] Builds successfully with Java 25 preview features
- [ ] All existing tests still pass
- [ ] No breaking changes to existing API
- [ ] Integration with VTask verified
- [ ] Integration with VStream verified
- [ ] Integration with Scope verified
- [ ] VTaskPath resilience parity with IOPath (withRetry, catching, asMaybe, asTry, race, guarantee)
- [ ] VStreamPath error handling parity with VStream (recover, recoverWith, mapError, onError)
- [ ] VStreamPath exposes mapTask for sequential effectful mapping
- [ ] VStreamPath rate limiting via throttle/metered
- [ ] VTaskContext Layer 2 resilience methods (withRetry, withCircuitBreaker, withBulkhead)
- [ ] ForPath works with resilient VTaskPath (retry + circuit breaker in for-comprehensions)

---

## Appendix: File Checklist

### New Files to Create

```
hkj-core/src/main/java/org/higherkindedj/hkt/resilience/
├── CircuitBreaker.java
├── CircuitBreakerConfig.java
├── CircuitBreakerMetrics.java
├── CircuitOpenException.java
├── Saga.java
├── SagaStep.java
├── SagaError.java
├── SagaBuilder.java
├── RetryEvent.java
├── Bulkhead.java
├── BulkheadConfig.java
├── BulkheadFullException.java
├── Resilience.java
└── ResilienceBuilder.java

hkj-core/src/main/java/org/higherkindedj/hkt/vstream/
└── VStreamThrottle.java

hkj-core/src/test/java/org/higherkindedj/hkt/resilience/
├── CircuitBreakerTest.java
├── CircuitBreakerPropertyTest.java
├── SagaTest.java
├── SagaPropertyTest.java
├── RetryTaskTest.java
├── RetryTaskPropertyTest.java
├── BulkheadTest.java
├── BulkheadPropertyTest.java
├── ResilienceBuilderTest.java
├── VStreamResilienceTest.java
└── ResilienceArchitectureRules.java

hkj-core/src/test/java/org/higherkindedj/hkt/effect/
├── VTaskPathResilienceTest.java
├── VStreamPathErrorHandlingTest.java
└── VTaskContextResilienceTest.java

hkj-examples/src/main/java/org/higherkindedj/example/resilience/
├── CircuitBreakerExample.java
├── SagaOrderExample.java
├── ResilientServiceExample.java
└── package-info.java

hkj-examples/src/test/java/org/higherkindedj/tutorial/resilience/
├── README.md
├── Tutorial01_CircuitBreaker.java
├── Tutorial02_Saga.java
├── Tutorial03_RetryBulkheadResilience.java
├── Tutorial04_PathResilience.java
└── solutions/
    ├── Tutorial01_CircuitBreaker_Solution.java
    ├── Tutorial02_Saga_Solution.java
    ├── Tutorial03_RetryBulkheadResilience_Solution.java
    └── Tutorial04_PathResilience_Solution.java

hkj-book/src/resilience/
├── ch_intro.md
├── circuit-breaker.md
├── saga.md
└── retry-bulkhead.md
```

### Existing Files to Modify

```
hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryPolicy.java        (add linear(), onRetry())
hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Retry.java              (add retryTask() methods)
hkj-core/src/main/java/org/higherkindedj/hkt/vtask/VTask.java                   (add retry() default method)
hkj-core/src/main/java/org/higherkindedj/hkt/effect/VTaskPath.java              (add withRetry, withCircuitBreaker, withBulkhead, catching, asMaybe, asTry, mapError, guarantee, race)
hkj-core/src/main/java/org/higherkindedj/hkt/effect/DefaultVTaskPath.java       (implement all new VTaskPath methods)
hkj-core/src/main/java/org/higherkindedj/hkt/effect/VStreamPath.java            (add recover, recoverWith, mapError, onError, mapTask, throttle, metered)
hkj-core/src/main/java/org/higherkindedj/hkt/effect/DefaultVStreamPath.java     (implement all new VStreamPath methods)
hkj-core/src/main/java/org/higherkindedj/hkt/effect/context/VTaskContext.java   (add withRetry, retry, withCircuitBreaker, withBulkhead)
hkj-core/src/main/java/org/higherkindedj/hkt/resilience/package-info.java       (update package docs)
hkj-book/src/SUMMARY.md                                                          (add resilience chapter)
hkj-examples/src/test/java/org/higherkindedj/tutorial/README.md                  (add resilience tutorials)
```
