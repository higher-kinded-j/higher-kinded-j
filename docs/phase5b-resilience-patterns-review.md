# Phase 5b Resilience Patterns — Review & Suggested Changes

## Summary

This review compares the phase5b plan against the current state of VTask, VStream, Scope, Resource,
and the existing resilience package. The codebase has evolved significantly since the plan was
written. Several proposed components already exist, others need rethinking to avoid duplication, and
there are opportunities the plan misses entirely.

The suggestions below are grouped into three categories:
1. **Things the plan gets wrong** (conflicts with what already exists)
2. **Things the plan should add** (opportunities it misses)
3. **Things the plan should refine** (good ideas that need adjustment)

---

## 1. Conflicts with What Already Exists

### 1.1 Retry Is Already Implemented — Plan Section 3 Needs Major Rework

The plan proposes a new `Retry` class, `RetryConfig` record, and `RetryEvent` record. However, the
codebase already has a working resilience package:

- **`RetryPolicy`** (`hkj-core/.../resilience/RetryPolicy.java`) — fully implemented with `fixed()`,
  `exponentialBackoff()`, `exponentialBackoffWithJitter()`, `noRetry()`, builder pattern,
  configurable predicates, max delay capping, and jitter support.
- **`Retry`** (`hkj-core/.../resilience/Retry.java`) — utility with `execute(RetryPolicy, Supplier<A>)`,
  `withExponentialBackoff()`, and `withFixedDelay()`.
- **`RetryExhaustedException`** (`hkj-core/.../resilience/RetryExhaustedException.java`) — custom
  exception preserving cause and attempt count.
- **IOPath integration** — `IOPath.withRetry(RetryPolicy)` and `IOPath.retry(maxAttempts, initialDelay)`.

**Suggested changes:**
- Remove the `RetryConfig` proposal entirely. `RetryPolicy` already covers this ground and is more
  mature than the proposed `RetryConfig`.
- Rename section 3 from "Retry and Bulkhead Patterns" to "VTask Retry Integration and Bulkhead
  Pattern" to reflect that we're extending existing retry functionality, not creating it from scratch.
- Focus the retry work on what's actually missing (see section 2.1 below).
- The plan's `BackoffStrategy` enum (`FIXED`, `EXPONENTIAL`, `EXPONENTIAL_WITH_JITTER`, `LINEAR`) is
  already handled by `RetryPolicy`'s factory methods. Adding a `LINEAR` strategy could be useful but
  should be added to `RetryPolicy`, not to a separate `RetryConfig`.

### 1.2 Bulkhead Overlaps with VStreamPar Bounded Concurrency

`VStreamPar` already provides bounded-concurrency execution:
- `parEvalMap(VStream<A>, int concurrency, Function<A, VTask<B>>)` — bounded parallel processing
  preserving order
- `parEvalMapUnordered(...)` — bounded parallel, completion order
- `parCollect(VStream<A>, int batchSize)` — parallel batch collection

The proposed `Bulkhead` class (semaphore-based concurrency limiter) serves a similar purpose for
individual VTask invocations. The plan should explicitly address the relationship:

**Suggested changes:**
- Add a "Relationship to VStreamPar" subsection in the Bulkhead section explaining that
  `VStreamPar` handles bounded concurrency for stream pipelines, while `Bulkhead` protects shared
  services/resources accessed by individual VTasks (e.g., a database connection pool or external API).
- Consider whether `Bulkhead.protect()` should be usable as a `VStreamPar` concurrency strategy, so
  users can share a single bulkhead across both stream and non-stream use cases.

### 1.3 The File Checklist Lists Files That Already Exist

The appendix lists these as "new files to create":
- `Retry.java` — already exists
- `package-info.java` — already exists

**Suggested change:** Update the file checklist to distinguish between new files and files to modify.
`Retry.java` should be listed under "Files to Update" (to add VTask-aware methods).

---

## 2. Opportunities the Plan Misses

### 2.1 VTask-Native Retry (The Key Gap)

The most significant missing piece: the existing `Retry.execute()` works with `Supplier<A>` and
blocks with `Thread.sleep()`. There is no **VTask-aware retry** that preserves laziness and composes
with VTask's monadic operations.

The plan proposes `retry(VTask<A>, int maxAttempts)` and `retry(VTask<A>, RetryConfig)` but uses a
new `RetryConfig` instead of the existing `RetryPolicy`. The right approach is:

**Suggested addition — add to `Retry.java`:**
```java
// Lazy VTask retry — returns a new VTask that retries on execution
public static <A> VTask<A> retryTask(VTask<A> task, RetryPolicy policy);

// VTask retry with fallback
public static <A> VTask<A> retryTaskWithFallback(
    VTask<A> task, RetryPolicy policy, Function<Throwable, A> fallback);

// VTask retry with recovery task
public static <A> VTask<A> retryTaskWithRecovery(
    VTask<A> task, RetryPolicy policy, Function<Throwable, VTask<A>> recovery);
```

These methods would return a lazy `VTask<A>` that, when executed, retries according to the policy.
This integrates naturally with VTask composition:
```java
VTask<String> resilientFetch = Retry.retryTask(
    VTask.of(() -> httpClient.get(url)),
    RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofMillis(100))
).map(response -> response.body());
```

Also consider adding a convenience method directly on VTask:
```java
// On VTask itself
default VTask<A> retry(RetryPolicy policy) {
    return Retry.retryTask(this, policy);
}
```

This mirrors how `VTask.timeout(Duration)` works — a combinator on the task itself.

### 2.2 RetryEvent / onRetry Callback — Still Valuable

The plan's `RetryEvent` record and `onRetry` callback are genuinely useful additions that the
existing `RetryPolicy` lacks. They enable monitoring and logging of retry attempts.

**Suggested addition — enhance `RetryPolicy`:**
```java
// Add to RetryPolicy
public RetryPolicy onRetry(Consumer<RetryEvent> listener);

// New record
public record RetryEvent(
    int attemptNumber,
    Throwable lastException,
    Duration nextDelay,
    Instant timestamp
) {}
```

This should be added to the existing `RetryPolicy` rather than to a separate `RetryConfig`.

### 2.3 VStream Resilience Integration — Entirely Missing from Plan

The plan focuses exclusively on VTask but ignores VStream, despite VStream being a major part of the
codebase with rich error handling (`recover`, `recoverWith`, `mapError`, `onError`) and parallel
processing (`VStreamPar`).

**Suggested additions — new section "5. VStream Resilience Patterns":**

**a) Stream-level circuit breaker:**
```java
// Protect a stream so that if too many element-pulls fail, the circuit opens
public static <A> VStream<A> protectStream(
    VStream<A> stream, CircuitBreaker<A> circuitBreaker);
```

**b) Per-element retry for streams:**
```java
// Retry each element's effectful processing
public static <A, B> VStream<B> retryMapTask(
    VStream<A> stream,
    Function<A, VTask<B>> f,
    RetryPolicy policy);
```

**c) Rate limiting / throttling for streams:**
```java
// Emit at most N elements per time window
public static <A> VStream<A> throttle(
    VStream<A> stream, int maxElements, Duration window);

// Add minimum delay between elements
public static <A> VStream<A> metered(
    VStream<A> stream, Duration interval);
```

This would be extremely useful for API-calling streams where you need to respect rate limits.
VStreamPar provides bounded concurrency but not time-based rate limiting.

**d) Resilient stream processing combining patterns:**
```java
VStream<Response> resilientStream = VStream.fromList(urls)
    .mapTask(url -> Retry.retryTask(
        circuitBreaker.protect(VTask.of(() -> httpClient.get(url))),
        retryPolicy));
```

### 2.4 Resource + Saga Relationship — Not Addressed

The `Resource` class (`hkj-core/.../vtask/Resource.java`) implements the bracket pattern
(acquire-use-release) with guaranteed cleanup and LIFO release ordering. This is conceptually
related to Saga's compensating transactions.

**Suggested addition to Saga section:**
- Add a subsection explaining the distinction: `Resource` manages individual resources with
  deterministic cleanup (close files, release connections), while `Saga` orchestrates multi-step
  distributed operations where compensation may be complex business logic (refund payment, restore
  inventory).
- Consider a `Saga.fromResource(Resource<A>)` bridge that wraps a Resource's release as a
  compensation action.
- Consider whether `Saga` steps should be able to acquire `Resource` instances, with the resource
  release forming part of the compensation chain.

### 2.5 Scope Integration — Not Addressed

The `Scope` class provides structured concurrency with multiple joiner strategies:
- `allSucceed()` — fail-fast
- `anySucceed()` — first success
- `accumulating()` — collect all errors in `Validated`

The plan asks "How should Saga compensation interact with Scope cancellation?" but doesn't propose
an answer.

**Suggested additions:**
- Saga's `parallel(List<Saga<A>>)` should be implemented using `Scope.allSucceed()` internally, with
  fail-fast cancellation when any saga step fails.
- Saga compensation should also leverage Scope, e.g. running compensations in parallel using
  `Scope.accumulating()` to collect compensation failures without fail-fast.
- Add a design note: when a Saga step that is running inside a Scope is cancelled, its compensation
  should still run. This may require the Saga to register compensations outside the Scope's
  cancellation boundary.

### 2.6 VTaskPath / VTaskContext Integration — Not Addressed

The plan doesn't mention how resilience patterns integrate with the Path/Effect API layer:
- `IOPath` already has `.withRetry(RetryPolicy)` and `.retry(maxAttempts, initialDelay)`.
- `VTaskPath` and `VTaskContext` have `.handleError()` and `.handleErrorWith()` but no retry or
  circuit breaker integration.

**Suggested additions:**
- Add `.withRetry(RetryPolicy)` to `VTaskPath` (mirrors IOPath).
- Add `.withCircuitBreaker(CircuitBreaker)` to `VTaskPath`.
- Add corresponding methods to `VTaskContext` for the Layer 2 API.
- Update Tutorial03 to show resilience patterns used through the Path API, not just raw VTask.

### 2.7 Timeout Integration with Circuit Breaker

VTask already has `.timeout(Duration)`. The plan's `CircuitBreakerConfig` includes a `callTimeout`
field. The plan should clarify the relationship:

**Suggested addition:**
- `CircuitBreaker.protect(VTask<A>)` should apply `callTimeout` using VTask's existing
  `.timeout(Duration)` method internally, rather than implementing a separate timeout mechanism.
- Document that users can also apply their own `.timeout()` before passing to the circuit breaker if
  they want different timeout semantics.

---

## 3. Good Ideas That Need Refinement

### 3.1 Circuit Breaker — Refine the Type Parameter

The plan proposes `CircuitBreaker<A>`, parameterized on the result type. This is unusual — most
circuit breaker implementations are unparameterized because they protect a service endpoint, not a
specific result type. A single circuit breaker is typically shared across calls that may return
different types.

**Suggested change:**
- Make it `CircuitBreaker` (no type parameter). The `protect()` method should be generic:
  ```java
  public final class CircuitBreaker {
      public <A> VTask<A> protect(VTask<A> task);
      public <A> VTask<A> protectWithFallback(VTask<A> task, Function<Throwable, A> fallback);
  }
  ```
- This allows a single circuit breaker instance to protect multiple methods returning different types,
  which is the standard use case (e.g., one circuit breaker for all calls to a particular service).

### 3.2 Saga Sealed Interface — Reconsider

The plan proposes `sealed interface Saga<A>` but doesn't specify the permitted implementations. A
sealed interface is appropriate when you have a closed set of known variants (like `Step.Emit`,
`Step.Done`, `Step.Skip`). For Saga, the user needs to compose sagas freely.

**Suggested change:**
- Make `Saga<A>` a final class (not sealed interface) that wraps a `VTask<A>` and a compensation
  function. Composition (`map`, `flatMap`, `andThen`, `zip`) should return new `Saga` instances.
- Alternatively, if there are genuinely distinct Saga variants (e.g., `SingleStep`, `Sequential`,
  `Parallel`), document them as the permitted types.

### 3.3 SagaBuilder Type Flow

The plan shows `SagaBuilder<A>` where `A` changes type at each step:
```java
SagaBuilder<Unit> → step() → SagaBuilder<B> → step() → SagaBuilder<C>
```

This is a nice API but needs care to ensure the intermediate types are accessible for dependent
steps. The plan's `step(String name, Function<A, VTask<B>> action, Consumer<B> compensate)` is the
right approach — each step receives the previous step's result.

**Suggested refinement:**
- Add a `stepAsync` variant that accepts `Function<A, VTask<B>>` for the compensation too
  (matching the existing `Saga.of(VTask<A>, Function<A, VTask<Unit>>)` factory).
- Ensure the builder tracks all intermediate values for compensation, not just the latest.

### 3.4 ResilienceBuilder — Clarify Ordering Semantics

The plan proposes `ResilienceBuilder` for composing patterns, but doesn't specify the order in which
patterns are applied. The order matters significantly:

- **Retry outside Circuit Breaker:** Each retry attempt is independently checked by the circuit
  breaker. A circuit breaker opening stops retries early. (Usually desired.)
- **Retry inside Circuit Breaker:** The circuit breaker sees the retried operation as a single call.
  Only the final failure/success counts. (Rarely desired.)

**Suggested addition:**
- Document that `ResilienceBuilder` applies patterns in this order (outermost to innermost):
  1. Timeout (outermost — bounds total time)
  2. Bulkhead (concurrency limiting)
  3. Retry (retry the inner operation)
  4. Circuit Breaker (innermost — each attempt checks the circuit)
- This means a single retry attempt that fails due to `CircuitOpenException` is not retried
  (circuit breaker failure is not retryable by default), which is the correct behavior.
- Make this ordering explicit in the Javadoc.

### 3.5 Testing — Acknowledge Existing Tests

The plan proposes new `RetryTest.java` and `RetryPropertyTest.java`, but comprehensive retry tests
already exist:
- `RetryTest.java` (492 lines) — basic execution, retry-and-succeed, exception handling, delay
  behavior, interrupt handling, predicate validation, exhaustion scenarios.
- `RetryPolicyTest.java` (382 lines) — factory methods, configuration, delay calculation, builder,
  predicate filtering.
- `IOPathRetryTest.java` (299 lines) — integration with IOPath.

**Suggested change:**
- The testing section should reference existing tests and focus on testing new functionality:
  - VTask-native retry methods (the new `retryTask()` additions)
  - Circuit Breaker state machine
  - Saga compensation
  - Bulkhead concurrency limits
  - Combined patterns via ResilienceBuilder
  - VStream resilience integration

### 3.6 LINEAR Backoff Strategy

The plan proposes a `LINEAR` backoff strategy. This is a reasonable addition but should be added
to the existing `RetryPolicy` rather than a new `RetryConfig`.

**Suggested implementation:**
```java
// Add to RetryPolicy
public static RetryPolicy linear(int maxAttempts, Duration initialDelay) {
    // delay = initialDelay * attemptNumber (linear growth)
}
```

---

## 4. Structural / Organizational Suggestions

### 4.1 Package Organization

The plan puts everything in `org.higherkindedj.hkt.resilience`. Given the scope, consider
sub-packages:

```
org.higherkindedj.hkt.resilience/
├── RetryPolicy.java          (existing)
├── Retry.java                (existing, extend with VTask methods)
├── RetryExhaustedException.java (existing)
├── RetryEvent.java           (new)
├── CircuitBreaker.java       (new)
├── CircuitBreakerConfig.java (new)
├── CircuitBreakerMetrics.java(new)
├── CircuitOpenException.java (new)
├── Bulkhead.java             (new)
├── BulkheadConfig.java       (new)
├── BulkheadFullException.java(new)
├── Resilience.java           (new - combined patterns)
├── ResilienceBuilder.java    (new)
└── package-info.java         (existing, update)
```

Saga could remain in the same package or move to `org.higherkindedj.hkt.resilience.saga/` given it
has 4 files of its own (Saga, SagaStep, SagaError, SagaBuilder). Either way is reasonable.

### 4.2 Suggested Implementation Order

The plan doesn't specify implementation order. Suggested sequence based on dependencies:

1. **VTask retry methods** — extend existing `Retry.java` with VTask-native methods and add
   `RetryEvent`. Low risk, high value, builds on existing foundation.
2. **Circuit Breaker** — standalone stateful component. No dependencies on other new patterns.
3. **Bulkhead** — standalone concurrency limiter. No dependencies on other new patterns.
4. **ResilienceBuilder** — combines the above. Depends on Circuit Breaker and Bulkhead.
5. **VStream resilience** — stream-level patterns. Depends on Circuit Breaker and retry.
6. **VTaskPath integration** — add `.withRetry()`, `.withCircuitBreaker()`. Depends on above.
7. **Saga** — most complex pattern, independent of the others. Can be developed in parallel.

### 4.3 Pre-Implementation Questions — Updated

The plan's design questions should be updated:

| Original Question | Suggested Answer |
|---|---|
| Circuit Breaker state shared or per-instance? | Per-instance, but users can share a single instance across VTasks. Remove the type parameter to make sharing natural. |
| Saga compensation vs. Scope cancellation? | Use Scope internally. `parallel()` uses `Scope.allSucceed()` for execution, `Scope.accumulating()` for compensations. Register compensations outside the cancellation boundary. |
| Bulkhead: semaphores vs. virtual thread limits? | Semaphores. Virtual threads are cheap to create but the resource being protected (e.g., a database) has finite capacity. `Semaphore` with fairness option is the right primitive. |
| Resilience4j adapter integration? | Defer to a future phase. Focus on native implementations first. Consider an `hkj-resilience4j` adapter module later. |

---

## 5. Summary of Recommended Changes

### Remove from plan:
- `RetryConfig` record (replaced by existing `RetryPolicy`)
- `BackoffStrategy` enum as a separate type (use `RetryPolicy` factory methods)
- Duplicate `Retry.java` in file checklist

### Add to plan:
- VTask-native retry methods on existing `Retry` class
- `VTask.retry(RetryPolicy)` convenience method
- `RetryEvent` and `onRetry` callback on `RetryPolicy`
- `LINEAR` backoff strategy on `RetryPolicy`
- VStream resilience section (circuit breaker, per-element retry, rate limiting/throttling)
- Resource/Saga relationship discussion
- Scope integration design for Saga parallel execution and compensation
- VTaskPath/VTaskContext resilience integration
- Implementation ordering
- Updated design decision answers

### Modify in plan:
- `CircuitBreaker<A>` → `CircuitBreaker` (remove type parameter)
- `Saga<A>` sealed interface → consider final class or document permitted types
- `ResilienceBuilder` → document pattern application ordering
- Testing section → acknowledge existing tests, focus on new functionality
- File checklist → distinguish new vs. modified files
- Required reading → add Resource.java, VStreamPar.java, IOPath retry integration
