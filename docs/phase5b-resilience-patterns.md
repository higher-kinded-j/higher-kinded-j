# Resilience Patterns — Implementation Plan

## Overview

Introduces resilience patterns for robust virtual thread-based applications: Circuit Breaker, Saga, and Retry/Bulkhead patterns. These patterns integrate with `VTask` and `Scope` to provide fault tolerance and distributed transaction management with full type safety.

---

## Pre-Implementation Requirements

### Required Reading

Before beginning implementation, ensure familiarity with:

- [ ] `docs/STYLE-GUIDE.md` — Code style and naming conventions
- [ ] `docs/TESTING-GUIDE.md` — Three-layer testing strategy
- [ ] `docs/TUTORIAL-STYLE-GUIDE.md` — Tutorial writing guidelines
- [ ] `docs/PERFORMANCE-TESTING-GUIDE.md` — Benchmark requirements
- [ ] Existing `VTask` implementation in `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/`
- [ ] Existing `Scope` implementation for structured concurrency
- [ ] Existing `RetryPolicy` pattern (if any) in the codebase
- [ ] Review all the latest changes for `VStream` resource and parallel processing and advanced patterns

### Design Decisions Required

> **Consult before implementation**:
> 1. Should Circuit Breaker state be shared across VTasks or scoped per instance?
> 2. How should Saga compensation interact with Scope cancellation?
> 3. Should Bulkhead use semaphores or virtual thread limits?
> 4. Integration with external circuit breaker libraries (Resilience4j) — provide adapters?

---

## 1. Circuit Breaker Pattern

### 1.1 CircuitBreaker Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/CircuitBreaker.java`

- [ ] Create `CircuitBreaker<A>` class for protecting VTask operations
- [ ] Implement state machine: CLOSED → OPEN → HALF_OPEN → CLOSED
- [ ] Thread-safe state management using `AtomicReference`

```java
public final class CircuitBreaker<A> {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    // Factory methods
    public static <A> CircuitBreaker<A> create(CircuitBreakerConfig config);
    public static <A> CircuitBreaker<A> withDefaults();

    // Protection
    public VTask<A> protect(VTask<A> task);
    public VTask<A> protectWithFallback(VTask<A> task, Function<Throwable, A> fallback);

    // State inspection
    public State currentState();
    public CircuitBreakerMetrics metrics();

    // Manual control
    public void reset();
    public void tripOpen();
}
```

### 1.2 CircuitBreakerConfig

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/CircuitBreakerConfig.java`

- [ ] Create immutable configuration record
- [ ] Builder pattern for configuration

```java
public record CircuitBreakerConfig(
    int failureThreshold,           // Failures before opening
    int successThreshold,           // Successes in half-open before closing
    Duration openDuration,          // Time to stay open before half-open
    Duration callTimeout,           // Timeout for protected calls
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

### 1.3 CircuitBreakerMetrics

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

### 1.4 CircuitOpenException

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

## 2. Saga Pattern

### 2.1 Saga Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Saga.java`

- [ ] Create `Saga<A>` class for compensating transactions
- [ ] Support for sequential and parallel saga execution
- [ ] Automatic compensation on failure

```java
public sealed interface Saga<A> {

    // Factory methods
    static <A> Saga<A> of(VTask<A> action, Consumer<A> compensate);
    static <A> Saga<A> of(VTask<A> action, Function<A, VTask<Unit>> compensate);
    static <A> Saga<A> noCompensation(VTask<A> action);

    // Composition
    <B> Saga<B> map(Function<A, B> f);
    <B> Saga<B> flatMap(Function<A, Saga<B>> f);

    // Combining sagas
    static <A, B> Saga<Pair<A, B>> zip(Saga<A> a, Saga<B> b);
    static <A, B, C> Saga<C> map2(Saga<A> a, Saga<B> b, BiFunction<A, B, C> f);

    // Sequential composition
    <B> Saga<B> andThen(Function<A, Saga<B>> next);

    // Parallel composition (compensations run in reverse parallel)
    static <A> Saga<List<A>> parallel(List<Saga<A>> sagas);

    // Execution
    VTask<A> run();
    VTask<Either<SagaError, A>> runSafe();
}
```

### 2.2 SagaStep Record

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

### 2.3 SagaError

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

### 2.4 SagaBuilder

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/SagaBuilder.java`

- [ ] Fluent builder for complex sagas

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

    public <B> SagaBuilder<B> stepNoCompensation(
        String name,
        Function<A, VTask<B>> action);

    public Saga<A> build();
}
```

---

## 3. Retry and Bulkhead Patterns

### 3.1 Retry Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Retry.java`

- [ ] Create `Retry` utility for retrying VTask operations
- [ ] Support various backoff strategies

```java
public final class Retry {

    // Simple retry
    public static <A> VTask<A> retry(VTask<A> task, int maxAttempts);

    // Retry with config
    public static <A> VTask<A> retry(VTask<A> task, RetryConfig config);

    // Retry with fallback
    public static <A> VTask<A> retryWithFallback(
        VTask<A> task,
        RetryConfig config,
        Function<Throwable, A> fallback);

    // Retry with recovery
    public static <A> VTask<A> retryWithRecovery(
        VTask<A> task,
        RetryConfig config,
        Function<Throwable, VTask<A>> recovery);
}
```

### 3.2 RetryConfig

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryConfig.java`

- [ ] Create immutable retry configuration

```java
public record RetryConfig(
    int maxAttempts,
    Duration initialDelay,
    BackoffStrategy backoffStrategy,
    double backoffMultiplier,
    Duration maxDelay,
    Predicate<Throwable> retryOn,
    Consumer<RetryEvent> onRetry
) {
    public static Builder builder() { return new Builder(); }

    public enum BackoffStrategy {
        FIXED,
        EXPONENTIAL,
        EXPONENTIAL_WITH_JITTER,
        LINEAR
    }

    public static class Builder {
        public Builder maxAttempts(int attempts);
        public Builder initialDelay(Duration delay);
        public Builder backoff(BackoffStrategy strategy);
        public Builder backoffMultiplier(double multiplier);
        public Builder maxDelay(Duration maxDelay);
        public Builder retryOn(Predicate<Throwable> predicate);
        public Builder retryOn(Class<? extends Throwable>... exceptions);
        public Builder onRetry(Consumer<RetryEvent> listener);
        public RetryConfig build();
    }
}
```

### 3.3 RetryEvent

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryEvent.java`

- [ ] Create event for retry monitoring

```java
public record RetryEvent(
    int attemptNumber,
    Throwable lastException,
    Duration nextDelay,
    Instant timestamp
) {}
```

### 3.4 Bulkhead Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Bulkhead.java`

- [ ] Create `Bulkhead` for limiting concurrent executions

```java
public final class Bulkhead {

    // Factory methods
    public static Bulkhead create(BulkheadConfig config);
    public static Bulkhead withMaxConcurrent(int maxConcurrent);

    // Protection
    public <A> VTask<A> protect(VTask<A> task);
    public <A> VTask<A> protectWithTimeout(VTask<A> task, Duration waitTimeout);

    // Metrics
    public BulkheadMetrics metrics();

    // State
    public int availablePermits();
    public int activeCount();
}
```

### 3.5 BulkheadConfig

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

### 3.6 BulkheadFullException

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/BulkheadFullException.java`

```java
public class BulkheadFullException extends RuntimeException {
    private final int maxConcurrent;
    private final int currentWaiting;

    public BulkheadFullException(int maxConcurrent, int currentWaiting);
}
```

---

## 4. Combined Resilience Patterns

### 4.1 Resilience Utility Class

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Resilience.java`

- [ ] Combine multiple resilience patterns

```java
public final class Resilience {

    // Combine circuit breaker with retry
    public static <A> VTask<A> withCircuitBreakerAndRetry(
        VTask<A> task,
        CircuitBreaker<A> circuitBreaker,
        RetryConfig retryConfig);

    // Combine all patterns
    public static <A> VTask<A> protect(
        VTask<A> task,
        CircuitBreaker<A> circuitBreaker,
        RetryConfig retryConfig,
        Bulkhead bulkhead);

    // Builder for complex resilience chains
    public static <A> ResilienceBuilder<A> builder(VTask<A> task);
}
```

### 4.2 ResilienceBuilder

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/ResilienceBuilder.java`

```java
public final class ResilienceBuilder<A> {

    public ResilienceBuilder<A> withCircuitBreaker(CircuitBreaker<A> cb);
    public ResilienceBuilder<A> withCircuitBreaker(CircuitBreakerConfig config);
    public ResilienceBuilder<A> withRetry(RetryConfig config);
    public ResilienceBuilder<A> withBulkhead(Bulkhead bulkhead);
    public ResilienceBuilder<A> withBulkhead(BulkheadConfig config);
    public ResilienceBuilder<A> withTimeout(Duration timeout);
    public ResilienceBuilder<A> withFallback(Function<Throwable, A> fallback);

    public VTask<A> build();
}
```

---

## 5. Testing Requirements

### 5.1 Unit Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/resilience/`

Following the three-layer testing strategy from TESTING-GUIDE.md:

#### CircuitBreakerTest.java

- [ ] `@Nested` class structure:
  - [ ] `StateTransitionTests` — CLOSED → OPEN → HALF_OPEN → CLOSED
  - [ ] `FailureThresholdTests` — Opening after threshold failures
  - [ ] `SuccessThresholdTests` — Closing after threshold successes
  - [ ] `TimeoutTests` — Open duration behavior
  - [ ] `MetricsTests` — Metrics accuracy
  - [ ] `ConcurrencyTests` — Thread-safe state management
  - [ ] `FallbackTests` — Fallback execution

#### SagaTest.java

- [ ] `@Nested` class structure:
  - [ ] `SuccessfulExecutionTests` — All steps succeed
  - [ ] `CompensationTests` — Compensation on failure
  - [ ] `ParallelSagaTests` — Parallel saga execution
  - [ ] `CompensationOrderTests` — Reverse compensation order
  - [ ] `PartialCompensationFailureTests` — Some compensations fail
  - [ ] `BuilderTests` — SagaBuilder functionality

#### RetryTest.java

- [ ] `@Nested` class structure:
  - [ ] `SimpleRetryTests` — Basic retry behavior
  - [ ] `BackoffStrategyTests` — All backoff strategies
  - [ ] `MaxAttemptsTests` — Respects max attempts
  - [ ] `RetryConditionTests` — Retry predicate
  - [ ] `EventNotificationTests` — onRetry callbacks

#### BulkheadTest.java

- [ ] `@Nested` class structure:
  - [ ] `ConcurrencyLimitTests` — Respects max concurrent
  - [ ] `WaitQueueTests` — Wait queue behavior
  - [ ] `TimeoutTests` — Wait timeout
  - [ ] `MetricsTests` — Metrics accuracy
  - [ ] `FairnessTests` — FIFO ordering when fair

### 5.2 Property-Based Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/resilience/`

#### CircuitBreakerPropertyTest.java

- [ ] State machine invariants
- [ ] Metrics consistency
- [ ] Thread-safety properties

#### SagaPropertyTest.java

- [ ] Compensation always runs on failure
- [ ] Compensation order is reverse of execution
- [ ] All steps compensated up to failure point

#### RetryPropertyTest.java

- [ ] Never exceeds max attempts
- [ ] Delays follow backoff strategy
- [ ] Only retries matching exceptions

### 5.3 Coverage Requirements

- [ ] **100% line coverage** for all resilience implementation
- [ ] **100% branch coverage** for all resilience implementation
- [ ] All state transitions tested
- [ ] All error paths tested
- [ ] Concurrency edge cases tested

---

## 6. ArchUnit Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/architecture/`

### 6.1 ResilienceArchitectureRules.java

- [ ] Configuration records must be immutable
- [ ] Builders must be nested in config classes
- [ ] Exception classes must extend appropriate base
- [ ] Utility classes must be final with private constructor
- [ ] State enums must be package-private or inner classes

---

## 7. Documentation Plan

> **IMPORTANT**: Review this plan with project owner before writing documentation. hkj-book documentation must be checked before commit.

### 7.1 hkj-book Documentation

**Location**: `hkj-book/src/`

#### New Pages Required

- [ ] `resilience/circuit-breaker.md` — Circuit Breaker Pattern
  - What You'll Learn
  - State machine explanation
  - Configuration options
  - Usage examples
  - Monitoring and metrics
  - Key Takeaways

- [ ] `resilience/saga.md` — Saga Pattern
  - What You'll Learn
  - Compensating transactions concept
  - Sequential vs parallel sagas
  - Error handling and compensation
  - Usage examples
  - Key Takeaways

- [ ] `resilience/retry-bulkhead.md` — Retry and Bulkhead Patterns
  - What You'll Learn
  - Retry strategies and backoff
  - Bulkhead for concurrency limiting
  - Combining patterns
  - Usage examples
  - Key Takeaways

- [ ] `resilience/ch_intro.md` — Resilience Patterns Overview
  - Introduction to resilience
  - Pattern selection guide
  - When to use each pattern

#### Updates Required

- [ ] Update main SUMMARY.md with new chapter
- [ ] Add cross-references from VTask documentation
- [ ] Update glossary with resilience terms

### 7.2 Javadoc Requirements

- [ ] All public classes fully documented
- [ ] All public methods with @param, @return, @throws
- [ ] Package-level documentation for `org.higherkindedj.hkt.resilience`
- [ ] Examples in Javadoc
- [ ] Cross-references to related types

---

## 8. Tutorial Plan

> **IMPORTANT**: Review this plan with project owner before writing tutorials.

**Location**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/resilience/`

### 8.1 Tutorial Track: Resilience Patterns

#### Tutorial01_CircuitBreaker.java (~10 minutes)

- [ ] Exercise 1: Create basic circuit breaker
- [ ] Exercise 2: Configure failure threshold
- [ ] Exercise 3: Protect a VTask
- [ ] Exercise 4: Handle circuit open state
- [ ] Exercise 5: Use fallback
- [ ] Exercise 6: Monitor metrics

#### Tutorial02_Saga.java (~12 minutes)

- [ ] Exercise 1: Create simple saga with compensation
- [ ] Exercise 2: Chain saga steps
- [ ] Exercise 3: Handle saga failure
- [ ] Exercise 4: Parallel saga execution
- [ ] Exercise 5: Use SagaBuilder
- [ ] Exercise 6: Handle compensation failures

#### Tutorial03_RetryBulkhead.java (~10 minutes)

- [ ] Exercise 1: Simple retry
- [ ] Exercise 2: Exponential backoff
- [ ] Exercise 3: Retry conditions
- [ ] Exercise 4: Create bulkhead
- [ ] Exercise 5: Combine retry and bulkhead
- [ ] Exercise 6: Build complete resilience chain

### 8.2 Solution Files

- [ ] `solutions/Tutorial01_CircuitBreaker_Solution.java`
- [ ] `solutions/Tutorial02_Saga_Solution.java`
- [ ] `solutions/Tutorial03_RetryBulkhead_Solution.java`

### 8.3 Tutorial README

- [ ] Create `tutorial/resilience/README.md`
- [ ] Update main tutorial README

---

## 9. Example Applications

**Location**: `hkj-examples/src/main/java/org/higherkindedj/example/resilience/`

### 9.1 CircuitBreakerExample.java

- [ ] HTTP client with circuit breaker
- [ ] Multiple service endpoints
- [ ] Fallback handling

### 9.2 SagaOrderExample.java

- [ ] E-commerce order saga
- [ ] Payment, inventory, shipping steps
- [ ] Compensation on failure

### 9.3 ResilientServiceExample.java

- [ ] Combined patterns example
- [ ] Real-world service call protection
- [ ] Monitoring and logging

---

## 10. Completion Criteria

### 10.1 Code Complete

- [ ] All implementation classes complete
- [ ] All tests passing
- [ ] **100% test coverage achieved**
- [ ] ArchUnit tests passing
- [ ] Code review approved
- [ ] Consistent with existing higher-kinded-j patterns

### 10.2 Documentation Complete

- [ ] Documentation plan reviewed and approved by project owner
- [ ] All hkj-book pages written (after approval)
- [ ] All Javadoc complete
- [ ] Tutorials written and tested
- [ ] README updates complete

### 10.3 Integration Verified

- [ ] Builds successfully with Java 25 preview features
- [ ] All existing tests still pass
- [ ] No breaking changes to existing API
- [ ] Integration with VTask verified
- [ ] Integration with Scope verified

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
├── Retry.java
├── RetryConfig.java
├── RetryEvent.java
├── Bulkhead.java
├── BulkheadConfig.java
├── BulkheadFullException.java
├── BulkheadMetrics.java
├── Resilience.java
├── ResilienceBuilder.java
└── package-info.java

hkj-core/src/test/java/org/higherkindedj/hkt/resilience/
├── CircuitBreakerTest.java
├── CircuitBreakerPropertyTest.java
├── SagaTest.java
├── SagaPropertyTest.java
├── RetryTest.java
├── RetryPropertyTest.java
├── BulkheadTest.java
├── BulkheadPropertyTest.java
├── ResilienceTest.java
└── ResilienceArchitectureRules.java

hkj-examples/src/main/java/org/higherkindedj/example/resilience/
├── CircuitBreakerExample.java
├── SagaOrderExample.java
├── ResilientServiceExample.java
└── package-info.java

hkj-examples/src/test/java/org/higherkindedj/tutorial/resilience/
├── README.md
├── Tutorial01_CircuitBreaker.java
├── Tutorial02_Saga.java
├── Tutorial03_RetryBulkhead.java
└── solutions/
    ├── Tutorial01_CircuitBreaker_Solution.java
    ├── Tutorial02_Saga_Solution.java
    └── Tutorial03_RetryBulkhead_Solution.java

hkj-book/src/resilience/
├── ch_intro.md
├── circuit-breaker.md
├── saga.md
└── retry-bulkhead.md
```

### Files to Update

```
hkj-book/src/SUMMARY.md
hkj-examples/src/test/java/org/higherkindedj/tutorial/README.md
```
