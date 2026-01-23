# Phase 3: Structured Concurrency — Implementation Plan

> **Target Version**: v2.0.0-M3
> **Prerequisites**: Phase 2 complete (Effect Path Integration)
> **Estimated Duration**: To be determined by team velocity

## Overview

Phase 3 integrates Java 25's `StructuredTaskScope` with higher-kinded-j, providing `Scope<A>` for hierarchical task management, custom joiners, and the `Resource<A>` bracket pattern for safe resource handling.

---

## 1. Prerequisites Check

### 1.1 Java 25 Structured Concurrency Status

- [ ] Verify JEP 505/525 preview status in Java 25
- [ ] Confirm `StructuredTaskScope` API stability
- [ ] Document any API changes from JDK 24 to JDK 25

### 1.2 Design Decision Required

> **Consult**:
> 1. `StructuredTaskScope` is still preview in Java 25 (JEP 505). Should we wait for finalization or proceed with preview API?
> 2. How should we handle API changes if structured concurrency finalizes in Java 26?

---

## 2. Scope Implementation

### 2.1 ScopeKind Interface

**Location**: `hkj-api/src/main/java/org/higherkindedj/hkt/scope/ScopeKind.java`

- [ ] Create `ScopeKind<A>` interface extending `Kind<ScopeKind.Witness, A>`
- [ ] Define `Witness` class implementing `WitnessArity<TypeArity.Unary>`

```java
@NullMarked
public interface ScopeKind<A> extends Kind<ScopeKind.Witness, A> {

    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

### 2.2 Scope Sealed Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/scope/Scope.java`

- [ ] Create sealed `Scope<A>` interface
- [ ] Extend `ScopeKind<A>`, `Chainable<A>`

```java
public sealed interface Scope<A> extends ScopeKind<A>, Chainable<A>
    permits ScopeImpl {

    // Factory methods (Java 25 static factory pattern)
    static <A> ScopeBuilder<A> open();
    static <A> ScopeBuilder<A> open(ScopeJoiner<A> joiner);

    // Pre-configured scope builders
    static <A> ScopeBuilder<List<A>> allSucceed();
    static <A> ScopeBuilder<A> anySucceed();
    static <A> ScopeBuilder<A> race();

    // Configuration (builder pattern)
    Scope<A> named(String name);
    Scope<A> withTimeout(Duration timeout);
    Scope<A> withThreadFactory(ThreadFactory factory);

    // Fork subtasks
    <B> Subtask<B> fork(VTask<B> task);
    <B> Subtask<B> fork(Callable<B> computation);
    <B> Subtask<B> fork(VTaskPath<B> path);

    // Join and get result
    A join() throws InterruptedException;
    Try<A> joinSafe();
    VTask<A> toVTask();

    // Composition
    <B> Scope<B> map(Function<A, B> f);
    <B> Scope<B> flatMap(Function<A, Scope<B>> f);
    <B> Scope<B> via(Function<A, Scope<B>> f);
}
```

### 2.3 ScopeBuilder

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/scope/ScopeBuilder.java`

- [ ] Fluent builder for scope configuration
- [ ] Immutable builder pattern
- [ ] Chain fork operations

```java
public final class ScopeBuilder<A> {

    public ScopeBuilder<A> named(String name);
    public ScopeBuilder<A> withTimeout(Duration timeout);
    public ScopeBuilder<A> withThreadFactory(ThreadFactory factory);

    public <B> ScopeBuilder<A> fork(VTask<B> task);
    public <B> ScopeBuilder<A> fork(Callable<B> computation);

    public Scope<A> build();
    public A joinNow() throws InterruptedException;
    public Try<A> joinSafeNow();
}
```

### 2.4 ScopeJoiner Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/scope/ScopeJoiner.java`

- [ ] Adapter interface for Java 25's `Joiner<T>`
- [ ] Built-in joiners
- [ ] Custom joiner support

```java
public interface ScopeJoiner<T> {

    // Called when subtask completes
    boolean onComplete(Subtask<? extends T> subtask);

    // Called after join() completes
    T result() throws Throwable;

    // Built-in joiners
    static <T> ScopeJoiner<List<T>> allSucceed();
    static <T> ScopeJoiner<T> anySucceed();
    static <T> ScopeJoiner<T> firstComplete();
    static <T> ScopeJoiner<T> firstSuccess();

    // Error accumulation joiner (like Validated)
    static <E, T> ScopeJoiner<Validated<List<E>, List<T>>>
        accumulating(Function<Throwable, E> errorMapper);

    // Custom aggregation
    static <T, R> ScopeJoiner<R> collecting(Collector<T, ?, R> collector);

    // Timeout handler (Java 26 feature, prepare for future)
    default T onTimeout() throws Throwable {
        throw new TimeoutException("Scope timed out");
    }
}
```

### 2.5 Subtask Wrapper

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/scope/Subtask.java`

- [ ] Wrapper for `StructuredTaskScope.Subtask`
- [ ] State inspection methods
- [ ] Result extraction

```java
public interface Subtask<T> {

    enum State { UNAVAILABLE, SUCCESS, FAILED }

    State state();
    T get();
    Throwable exception();

    boolean isSuccess();
    boolean isFailed();
    boolean isUnavailable();

    Maybe<T> toMaybe();
    Either<Throwable, T> toEither();
    Try<T> toTry();
}
```

---

## 3. Enhanced Par Combinators

### 3.1 Update Par with StructuredTaskScope

**Location**: Update `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/Par.java`

- [ ] Reimplement using `StructuredTaskScope` internally
- [ ] Add timeout support to all combinators
- [ ] Add cancellation propagation

```java
public final class Par {

    // Updated implementations using StructuredTaskScope
    public static <A, B> VTask<Pair<A, B>> zip(VTask<A> a, VTask<B> b) {
        return VTask.of(() -> {
            try (var scope = StructuredTaskScope.open()) {
                var subtaskA = scope.fork(a::run);
                var subtaskB = scope.fork(b::run);
                scope.join();
                return Pair.of(subtaskA.get(), subtaskB.get());
            }
        });
    }

    // With timeout
    public static <A, B> VTask<Pair<A, B>> zip(
        VTask<A> a, VTask<B> b, Duration timeout);

    // N of M pattern
    public static <A> VTask<List<A>> nOf(int n, List<VTask<A>> tasks);

    // First success (ignores failures until all fail)
    public static <A> VTask<A> firstSuccess(List<VTask<A>> tasks);

    // With custom joiner
    public static <A, R> VTask<R> withJoiner(
        List<VTask<A>> tasks, ScopeJoiner<R> joiner);
}
```

---

## 4. Resource Management

### 4.1 ResourceKind Interface

**Location**: `hkj-api/src/main/java/org/higherkindedj/hkt/resource/ResourceKind.java`

- [ ] Create `ResourceKind<A>` interface
- [ ] Define `Witness` class

### 4.2 Resource Sealed Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resource/Resource.java`

- [ ] Bracket pattern implementation
- [ ] Composable resource acquisition

```java
public sealed interface Resource<A> extends ResourceKind<A>, Chainable<A>
    permits ResourceImpl {

    // Factory methods
    static <A> Resource<A> make(
        VTask<A> acquire,
        Consumer<A> release);

    static <A> Resource<A> make(
        VTask<A> acquire,
        Function<A, VTask<Void>> release);

    static <A extends AutoCloseable> Resource<A> fromAutoCloseable(
        VTask<A> acquire);

    static <A extends AutoCloseable> Resource<A> fromAutoCloseable(
        Callable<A> acquire);

    // Use the resource
    <B> VTask<B> use(Function<A, VTask<B>> f);
    <B> VTask<B> useSync(Function<A, B> f);

    // Composition
    <B> Resource<B> map(Function<A, B> f);
    <B> Resource<B> flatMap(Function<A, Resource<B>> f);
    <B> Resource<B> via(Function<A, Resource<B>> f);

    // Combine resources
    static <A, B> Resource<Pair<A, B>> both(Resource<A> a, Resource<B> b);
    <B> Resource<Pair<A, B>> and(Resource<B> other);

    // Parallel resource acquisition
    static <A, B> Resource<Pair<A, B>> parBoth(Resource<A> a, Resource<B> b);

    // Resource pooling support
    static <A> Resource<A> pooled(Supplier<Resource<A>> factory, int size);
}
```

### 4.3 Resource Implementation

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/resource/ResourceImpl.java`

- [ ] Guarantee release on exception
- [ ] Guarantee release on scope exit
- [ ] Thread-safe state management

### 4.4 Design Decision Required

> **Consult**:
> 1. Should `Resource.use` automatically use structured concurrency?
> 2. How should nested resources interact with structured scope?
> 3. Should we support finalizer-based cleanup as fallback?

---

## 5. Testing Requirements

### 5.1 Scope Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/scope/ScopeTest.java`

- [ ] `@Nested FactoryMethodsTests`
  - [ ] `Scope.open()` creates builder
  - [ ] `Scope.allSucceed()` waits for all
  - [ ] `Scope.anySucceed()` returns first success
  - [ ] `Scope.race()` returns first completion
- [ ] `@Nested ForkAndJoinTests`
  - [ ] Fork single task
  - [ ] Fork multiple tasks
  - [ ] Join returns aggregated result
  - [ ] Join propagates errors
- [ ] `@Nested TimeoutTests`
  - [ ] Timeout triggers cancellation
  - [ ] Timeout exception includes context
- [ ] `@Nested CancellationTests`
  - [ ] Child tasks cancelled when scope closes
  - [ ] Cancellation propagates to subtasks
- [ ] `@Nested ErrorHandlingTests`
  - [ ] First error fails scope (allSucceed)
  - [ ] Errors accumulated (accumulating joiner)
  - [ ] Errors ignored until all fail (firstSuccess)

### 5.2 Scope Property Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/scope/ScopePropertyTest.java`

- [ ] Functor laws for Scope
- [ ] Monad laws for Scope
- [ ] Arbitrary providers

### 5.3 ScopeJoiner Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/scope/ScopeJoinerTest.java`

- [ ] `allSucceed` behaviour
- [ ] `anySucceed` behaviour
- [ ] `firstComplete` behaviour
- [ ] `accumulating` error collection
- [ ] `collecting` with various collectors
- [ ] Custom joiner implementation

### 5.4 Resource Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/resource/ResourceTest.java`

- [ ] `@Nested FactoryMethodsTests`
  - [ ] `Resource.make` with acquire/release
  - [ ] `Resource.fromAutoCloseable`
- [ ] `@Nested UseTests`
  - [ ] Resource acquired before use
  - [ ] Resource released after use
  - [ ] Resource released on exception
- [ ] `@Nested CompositionTests`
  - [ ] `map` transforms resource value
  - [ ] `flatMap` sequences resource acquisition
  - [ ] `both` acquires in parallel
- [ ] `@Nested NestedResourceTests`
  - [ ] Nested resources released in reverse order
  - [ ] Failure in inner resource releases outer
- [ ] `@Nested StructuredConcurrencyTests`
  - [ ] Resource released when scope closes
  - [ ] Parallel resources all released

### 5.5 Resource Property Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/resource/ResourcePropertyTest.java`

- [ ] Functor laws
- [ ] Monad laws
- [ ] Release guarantee property

### 5.6 Enhanced Par Tests

**Location**: Update `hkj-core/src/test/java/org/higherkindedj/hkt/vtask/ParTest.java`

- [ ] Verify StructuredTaskScope integration
- [ ] `nOf` returns first N successes
- [ ] `firstSuccess` ignores failures
- [ ] `withJoiner` custom behaviour
- [ ] Timeout propagation

### 5.7 Coverage Requirements

- [ ] 100% coverage for Scope
- [ ] 100% coverage for ScopeJoiner
- [ ] 100% coverage for Resource
- [ ] 100% coverage for Par enhancements

---

## 6. ArchUnit Tests

### 6.1 ScopeArchitectureRules.java

**Location**: `hkj-core/src/test/java/org/higherkindedj/architecture/`

- [ ] ScopeKind.Witness must be final with private constructor
- [ ] Scope must be sealed interface
- [ ] ScopeBuilder must be final
- [ ] ScopeJoiner built-in implementations follow patterns

### 6.2 ResourceArchitectureRules.java

- [ ] ResourceKind.Witness must be final with private constructor
- [ ] Resource must be sealed interface
- [ ] ResourceImpl must guarantee release (method call check)

---

## 7. Custom AssertJ Assertions

### 7.1 ScopeAssert.java

**Location**: `hkj-core/src/test/java/org/higherkindedj/test/assertions/`

- [ ] `ScopeAssert<A>` for Scope assertions
- [ ] `joinsSuccessfully()` — verify join succeeds
- [ ] `joinsWithResult(A)` — verify result
- [ ] `joinsWithError(Class)` — verify error type
- [ ] `hasForkedTasks(int)` — verify fork count
- [ ] `completesWithin(Duration)` — timing check

### 7.2 ResourceAssert.java

- [ ] `ResourceAssert<A>` for Resource assertions
- [ ] `acquiresSuccessfully()` — verify acquisition
- [ ] `releasesAfterUse()` — verify release
- [ ] `releasesOnException()` — verify exception handling

### 7.3 SubtaskAssert.java

- [ ] `SubtaskAssert<A>` for Subtask assertions
- [ ] `isSuccess()` / `isFailed()` / `isUnavailable()`
- [ ] `hasResult(A)` — verify success result
- [ ] `hasException(Class)` — verify failure exception

---

## 8. Documentation Plan

> **Note**: Review this plan before writing documentation.

### 8.1 hkj-book Documentation

#### New Pages

- [ ] `concurrency/scope.md` — Scope: Structured Concurrency
  - What You'll Learn
  - StructuredTaskScope integration
  - Fork and join patterns
  - Joiner strategies
  - Timeout and cancellation
  - Comparison with raw StructuredTaskScope
  - Key Takeaways

- [ ] `concurrency/resource.md` — Resource: Safe Resource Management
  - What You'll Learn
  - Bracket pattern explained
  - AutoCloseable integration
  - Nested resources
  - Parallel resource acquisition
  - Integration with Scope
  - Key Takeaways

- [ ] `concurrency/joiners.md` — Custom Joiners
  - Built-in joiners
  - Creating custom joiners
  - Error accumulation patterns
  - Collector integration

- [ ] `concurrency/ch_intro.md` — Chapter: Structured Concurrency
  - Chapter introduction
  - In This Chapter
  - Chapter Contents

#### Updates

- [ ] Update `effects/vtask.md` with Scope references
- [ ] Update `effects/par.md` with StructuredTaskScope details
- [ ] Add Concurrency chapter to SUMMARY.md

### 8.2 Javadoc Requirements

- [ ] Scope fully documented
- [ ] ScopeBuilder fully documented
- [ ] ScopeJoiner fully documented
- [ ] Subtask fully documented
- [ ] Resource fully documented

---

## 9. Tutorial Plan

> **Note**: Review this plan before writing tutorials.

**Location**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/`

### 9.1 New Tutorial Track: Concurrency

#### Tutorial01_ScopeBasics.java (~12 minutes)

- [ ] Exercise 1: Create and join a simple scope
- [ ] Exercise 2: Fork multiple tasks
- [ ] Exercise 3: Use allSucceed joiner
- [ ] Exercise 4: Use anySucceed joiner
- [ ] Exercise 5: Race pattern
- [ ] Exercise 6: Timeout handling
- [ ] Exercise 7: Error handling with accumulating joiner

#### Tutorial02_ResourceManagement.java (~10 minutes)

- [ ] Exercise 1: Create Resource from AutoCloseable
- [ ] Exercise 2: Use Resource with VTask
- [ ] Exercise 3: Compose resources with flatMap
- [ ] Exercise 4: Combine resources with both
- [ ] Exercise 5: Nested resource handling
- [ ] Exercise 6: Resource with Scope

#### Tutorial03_AdvancedConcurrency.java (~12 minutes)

- [ ] Exercise 1: Custom ScopeJoiner
- [ ] Exercise 2: N of M pattern
- [ ] Exercise 3: First success pattern
- [ ] Exercise 4: Parallel resource acquisition
- [ ] Exercise 5: Complex workflow combining all concepts

### 9.2 Solution Files

- [ ] Tutorial01_ScopeBasics_Solution.java
- [ ] Tutorial02_ResourceManagement_Solution.java
- [ ] Tutorial03_AdvancedConcurrency_Solution.java

---

## 10. Performance Benchmarks

**Location**: `hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/`

### 10.1 ScopeBenchmark.java

- [ ] Scope creation overhead
- [ ] Fork single task overhead
- [ ] Fork multiple tasks overhead
- [ ] Join latency
- [ ] Scope vs raw StructuredTaskScope

### 10.2 ResourceBenchmark.java

- [ ] Resource.make overhead
- [ ] Resource.use overhead
- [ ] Resource vs try-with-resources
- [ ] Nested resource overhead
- [ ] Parallel resource acquisition

### 10.3 ParStructuredBenchmark.java

- [ ] Par.zip with StructuredTaskScope vs manual
- [ ] Par.all scaling (10, 100, 1000 tasks)
- [ ] Par.race overhead
- [ ] Par.nOf overhead
- [ ] Cancellation overhead

### 10.4 Comparison Benchmarks

- [ ] HKJ Scope vs raw StructuredTaskScope
- [ ] HKJ Resource vs try-with-resources
- [ ] HKJ Par vs CompletableFuture.allOf

---

## 11. Completion Criteria

### 11.1 Code Complete

- [ ] Scope implementation complete
- [ ] ScopeJoiner implementation complete
- [ ] Resource implementation complete
- [ ] Par enhancements complete
- [ ] All tests passing at 100% coverage
- [ ] ArchUnit tests passing

### 11.2 Documentation Complete

- [ ] Documentation plan reviewed and approved
- [ ] All documentation written
- [ ] Tutorials complete

### 11.3 Performance Validated

- [ ] Benchmarks implemented
- [ ] Minimal overhead vs raw APIs
- [ ] Structured concurrency advantages demonstrated

### 11.4 Integration Verified

- [ ] Works with VTask from Phase 1
- [ ] Works with VTaskPath from Phase 2
- [ ] Ready for Phase 4 (Context integration)

---

## 12. Design Decisions Summary

1. **Preview API Usage**: Proceed with preview StructuredTaskScope or wait for finalization?
2. **Resource Scope Integration**: Automatic structured concurrency for Resource.use?
3. **Nested Resource Ordering**: Strict reverse-order release guarantee?
4. **Finalizer Fallback**: Support finalizer-based cleanup for missed releases?
5. **Joiner API**: Align exactly with Java 25 Joiner or create HKJ-specific abstraction?
6. **Error Accumulation**: Use Validated internally for accumulating joiner?

---

## Appendix: File Checklist

### New Files to Create

```
hkj-api/src/main/java/org/higherkindedj/hkt/scope/
├── ScopeKind.java
└── package-info.java

hkj-api/src/main/java/org/higherkindedj/hkt/resource/
├── ResourceKind.java
└── package-info.java

hkj-core/src/main/java/org/higherkindedj/hkt/scope/
├── Scope.java
├── ScopeImpl.java
├── ScopeBuilder.java
├── ScopeJoiner.java
├── Subtask.java
└── package-info.java

hkj-core/src/main/java/org/higherkindedj/hkt/resource/
├── Resource.java
├── ResourceImpl.java
└── package-info.java

hkj-core/src/test/java/org/higherkindedj/hkt/scope/
├── ScopeTest.java
├── ScopePropertyTest.java
├── ScopeJoinerTest.java
└── SubtaskTest.java

hkj-core/src/test/java/org/higherkindedj/hkt/resource/
├── ResourceTest.java
└── ResourcePropertyTest.java

hkj-core/src/test/java/org/higherkindedj/architecture/
├── ScopeArchitectureRules.java
└── ResourceArchitectureRules.java

hkj-core/src/test/java/org/higherkindedj/test/assertions/
├── ScopeAssert.java
├── ResourceAssert.java
└── SubtaskAssert.java

hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/
├── ScopeBenchmark.java
├── ResourceBenchmark.java
└── ParStructuredBenchmark.java

hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/
├── README.md
├── Tutorial01_ScopeBasics.java
├── Tutorial02_ResourceManagement.java
├── Tutorial03_AdvancedConcurrency.java
└── solutions/
    ├── Tutorial01_ScopeBasics_Solution.java
    ├── Tutorial02_ResourceManagement_Solution.java
    └── Tutorial03_AdvancedConcurrency_Solution.java

hkj-book/src/concurrency/
├── ch_intro.md
├── scope.md
├── resource.md
└── joiners.md
```

### Files to Update

```
hkj-core/src/main/java/org/higherkindedj/hkt/vtask/Par.java
hkj-core/src/test/java/org/higherkindedj/hkt/vtask/ParTest.java
hkj-book/src/effects/vtask.md
hkj-book/src/effects/par.md
hkj-book/src/SUMMARY.md
```
