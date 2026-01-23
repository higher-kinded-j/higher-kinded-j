# Phase 1: VTask Foundation — Implementation Plan

> **Target Version**: v2.0.0-M1
> **Prerequisites**: Java 25 with preview features enabled
> **Estimated Duration**: To be determined by team velocity

## Overview

Phase 1 establishes the core `VTask<A>` effect type for virtual thread execution, including the HKT witness, type class instances, and foundational combinators.

---

## 1. Gradle Configuration Changes

### 1.1 Enable Java 25 Preview Features

- [ ] Update `build.gradle.kts` to enable preview features:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf(
        "-Xmaxerrs", "10000",
        "--enable-preview"
    ))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}
```

- [ ] Update JMH benchmark configuration for preview features
- [ ] Verify all modules compile with preview features enabled

### 1.2 Design Decision Required

> **Consult**: Should we require Java 25 as minimum version for v2.0.0, or maintain Java 21 compatibility with virtual threads as optional feature?

---

## 2. Core Implementation Checklist

### 2.1 VTaskKind Interface

**Location**: `hkj-api/src/main/java/org/higherkindedj/hkt/vtask/VTaskKind.java`

- [ ] Create `VTaskKind<A>` interface extending `Kind<VTaskKind.Witness, A>`
- [ ] Define `Witness` class implementing `WitnessArity<TypeArity.Unary>`
- [ ] Ensure `Witness` class is `final` with private constructor
- [ ] Add `@NullMarked` package annotation

```java
@NullMarked
public interface VTaskKind<A> extends Kind<VTaskKind.Witness, A> {

    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

### 2.2 VTask Sealed Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/VTask.java`

- [ ] Create sealed `VTask<A>` interface with permitted implementations
- [ ] Implement `Chainable<A>` and `Effectful<A>` capabilities
- [ ] Factory methods:
  - [ ] `of(Callable<A>)` — create from callable
  - [ ] `delay(Supplier<A>)` — create from supplier (lazy)
  - [ ] `succeed(A)` — pure value
  - [ ] `fail(Throwable)` — failed task
  - [ ] `exec(Runnable)` — void task
  - [ ] `blocking(Callable<A>)` — explicit blocking marker
- [ ] Execution methods:
  - [ ] `run()` — execute on virtual thread, blocking
  - [ ] `runSafe()` — execute returning `Try<A>`
  - [ ] `runAsync()` — execute returning `CompletableFuture<A>`
- [ ] Composition methods:
  - [ ] `map(Function<A, B>)` — functor
  - [ ] `flatMap(Function<A, VTask<B>>)` — monad
  - [ ] `via(Function<A, VTask<B>>)` — alias for flatMap
  - [ ] `then(Supplier<VTask<B>>)` — sequence, discard result
  - [ ] `peek(Consumer<A>)` — side-effect observation
- [ ] Timeout and cancellation:
  - [ ] `timeout(Duration)` — fail if not complete within duration
  - [ ] `interruptible()` — mark as interruptible
  - [ ] `uninterruptible()` — mark as uninterruptible
- [ ] Error handling:
  - [ ] `recover(Function<Throwable, A>)` — recover from error
  - [ ] `recoverWith(Function<Throwable, VTask<A>>)` — recover with new task

### 2.3 VTask Implementation Records

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/`

- [ ] `VTaskImpl.java` — internal implementation details
- [ ] Use records where appropriate for immutability
- [ ] Ensure thread-safety for execution state

### 2.4 VTaskKindHelper

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/VTaskKindHelper.java`

- [ ] Create utility class/enum for zero-cost unwrapping
- [ ] `unwrap(Kind<VTaskKind.Witness, A>)` method
- [ ] `wrap(VTask<A>)` method (if needed)
- [ ] Follow existing `EitherKindHelper` pattern (enum with `VTASK` instance)

### 2.5 VTaskMonad

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/VTaskMonad.java`

- [ ] Implement `MonadError<VTaskKind.Witness, Throwable>`
- [ ] Use enum singleton pattern with `INSTANCE`
- [ ] Implement all required methods:
  - [ ] `of(A)` — pure
  - [ ] `map(Function, Kind)` — functor
  - [ ] `ap(Kind, Kind)` — applicative
  - [ ] `flatMap(Function, Kind)` — monad
  - [ ] `raiseError(Throwable)` — error
  - [ ] `handleErrorWith(Function, Kind)` — recovery

### 2.6 Basic Par Combinators

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/vtask/Par.java`

- [ ] Create `Par` utility class for parallel composition
- [ ] Implement basic combinators:
  - [ ] `zip(VTask<A>, VTask<B>)` — parallel zip
  - [ ] `zip3(VTask<A>, VTask<B>, VTask<C>)` — parallel zip3
  - [ ] `map2(VTask<A>, VTask<B>, BiFunction)` — parallel map2
  - [ ] `race(VTask<A>...)` — first to complete
  - [ ] `race(List<VTask<A>>)` — first to complete (list)
  - [ ] `all(List<VTask<A>>)` — all must succeed
  - [ ] `traverse(List<A>, Function<A, VTask<B>>)` — parallel traverse

### 2.7 Design Decisions Required

> **Consult**:
> 1. Should `VTask` be lazy (like IO) or eager (starts on creation)?
> 2. How should we handle interruption — cooperative or preemptive?
> 3. Should `Par` combinators use `StructuredTaskScope` internally in Phase 1, or defer to Phase 3?

---

## 3. Testing Requirements

### 3.1 Unit Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/vtask/`

Following the three-layer testing strategy from TESTING.md:

#### VTaskTest.java — Comprehensive Unit Tests

- [ ] `@Nested` class structure matching Effect Path pattern:
  - [ ] `FactoryMethodsTests`
  - [ ] `RunAndTerminalMethodsTests`
  - [ ] `ComposableOperationsTests` (map, peek)
  - [ ] `ChainableOperationsTests` (via, flatMap, then)
  - [ ] `RecoverableOperationsTests` (recover, recoverWith)
  - [ ] `TimeoutAndCancellationTests`
  - [ ] `ObjectMethodsTests` (equals, hashCode, toString)

#### VTaskPropertyTest.java — Property-Based Tests with jQwik

- [ ] Functor identity law
- [ ] Functor composition law
- [ ] Monad left identity law
- [ ] Monad right identity law
- [ ] Monad associativity law
- [ ] MonadError left zero law
- [ ] MonadError recovery law
- [ ] MonadError success passthrough law
- [ ] Arbitrary providers for VTask values and functions

#### VTaskLawsTest.java — Explicit Law Verification

- [ ] `@TestFactory` for Functor laws
- [ ] `@TestFactory` for Monad laws
- [ ] `@TestFactory` for MonadError laws
- [ ] Include VTask in existing law test factories

### 3.2 Par Combinator Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/vtask/ParTest.java`

- [ ] `zip` parallel execution verification
- [ ] `zip` error propagation (fail-fast)
- [ ] `race` returns first completion
- [ ] `race` cancels losers
- [ ] `all` waits for all tasks
- [ ] `all` error propagation
- [ ] `traverse` parallel execution
- [ ] Timeout behaviour tests
- [ ] Interruption tests

### 3.3 Virtual Thread Specific Tests

- [ ] Verify execution on virtual threads (not platform threads)
- [ ] Test thread name patterns
- [ ] Test carrier thread behaviour
- [ ] Test that blocking operations don't block carrier threads
- [ ] Test high-concurrency scenarios (thousands of concurrent tasks)

### 3.4 Coverage Requirements

- [ ] 100% line coverage for VTask implementation
- [ ] 100% branch coverage for VTask implementation
- [ ] All public API methods tested
- [ ] All error paths tested
- [ ] Edge cases documented and tested

---

## 4. ArchUnit Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/architecture/`

### 4.1 VTaskArchitectureRules.java

- [ ] VTaskKind.Witness must be final with private constructor
- [ ] VTaskKindHelper must be final/enum utility class
- [ ] VTaskMonad must follow singleton pattern (INSTANCE constant)
- [ ] VTaskMonad must be stateless (no instance fields)
- [ ] VTask implementations must be sealed
- [ ] Par must be utility class (final, private constructor)

### 4.2 Update Existing Rules

- [ ] Add VTaskMonad to `TypeClassPatternRules` checks
- [ ] Add VTaskKind.Witness to `WitnessTypeRules` checks
- [ ] Add VTask package to `PackageStructureRules`
- [ ] Add VTask to `ImmutabilityRules` checks

---

## 5. Custom AssertJ Assertions

**Location**: `hkj-core/src/test/java/org/higherkindedj/test/assertions/`

### 5.1 VTaskAssert.java

- [ ] Create `VTaskAssert<A>` for fluent VTask assertions
- [ ] Methods:
  - [ ] `succeeds()` — verify task completes successfully
  - [ ] `succeedsWith(A expected)` — verify result value
  - [ ] `fails()` — verify task fails
  - [ ] `failsWith(Class<? extends Throwable>)` — verify exception type
  - [ ] `failsWithMessage(String)` — verify exception message
  - [ ] `completesWithin(Duration)` — verify timing
  - [ ] `runsOnVirtualThread()` — verify thread type

### 5.2 Assertions Entry Point

- [ ] Create `VTaskAssertions.assertThat(VTask<A>)` factory
- [ ] Add to main `Assertions` class if one exists

---

## 6. Documentation Plan

> **Note**: Review this plan before writing documentation.

### 6.1 hkj-book Documentation

**Location**: `hkj-book/src/`

#### New Pages Required

- [ ] `effects/vtask.md` — VTask: Virtual Thread Effects
  - What You'll Learn admonishment
  - Core concepts (virtual threads, structured execution)
  - Factory methods with examples
  - Composition patterns
  - Error handling
  - Comparison with IOPath
  - Key Takeaways
  - See Also (Par, Structured Concurrency)

- [ ] `effects/par.md` — Par: Parallel Composition
  - Parallel combinators overview
  - zip, race, all, traverse
  - Error handling in parallel contexts
  - Performance considerations

#### Updates Required

- [ ] Update `effects/ch_intro.md` with VTask entry
- [ ] Update SUMMARY.md with new pages
- [ ] Add cross-references from IOPath documentation
- [ ] Update glossary with VTask terms

### 6.2 Javadoc Requirements

- [ ] All public classes fully documented
- [ ] All public methods with @param, @return, @throws
- [ ] Package-level documentation for `org.higherkindedj.hkt.vtask`
- [ ] Examples in Javadoc where appropriate

---

## 7. Tutorial Plan

> **Note**: Review this plan before writing tutorials.

**Location**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vtask/`

### 7.1 Tutorial Track: Virtual Threads

#### Tutorial01_VTaskBasics.java (~10 minutes)

- [ ] Exercise 1: Create VTask from callable
- [ ] Exercise 2: Create VTask from supplier (delay)
- [ ] Exercise 3: Run VTask and get result
- [ ] Exercise 4: Run VTask safely with Try
- [ ] Exercise 5: Map over VTask values
- [ ] Exercise 6: Chain VTasks with flatMap
- [ ] Exercise 7: Error handling with recover

#### Tutorial02_ParallelComposition.java (~12 minutes)

- [ ] Exercise 1: Parallel zip of two tasks
- [ ] Exercise 2: Parallel zip of three tasks
- [ ] Exercise 3: Race multiple tasks
- [ ] Exercise 4: All tasks must succeed
- [ ] Exercise 5: Parallel traverse
- [ ] Exercise 6: Timeout on parallel group
- [ ] Exercise 7: Error handling in parallel context

### 7.2 Solution Files

- [ ] Tutorial01_VTaskBasics_Solution.java
- [ ] Tutorial02_ParallelComposition_Solution.java

### 7.3 Tutorial README

- [ ] Create `tutorial/vtask/README.md` with track overview
- [ ] Update main tutorial README with VTask track

---

## 8. Performance Benchmarks

**Location**: `hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/`

### 8.1 VTaskBenchmark.java

Following existing IOBenchmark pattern:

- [ ] `constructSimple` — VTask creation overhead
- [ ] `runSimple` — Basic execution cost
- [ ] `mapConstruction` — Map chain building (lazy)
- [ ] `mapExecution` — Map chain execution
- [ ] `flatMapConstruction` — FlatMap chain building
- [ ] `flatMapExecution` — FlatMap chain execution
- [ ] `longChainConstruction` — Deep chain building (100 ops)
- [ ] `longChainExecution` — Deep chain execution
- [ ] `deepRecursion` — Stack safety test (1000 ops)

### 8.2 VTaskVsIOBenchmark.java

Comparison benchmarks:

- [ ] VTask vs IO simple execution
- [ ] VTask vs IO map chain
- [ ] VTask vs IO flatMap chain
- [ ] VTask vs IO deep recursion
- [ ] VTask vs CompletableFuture simple
- [ ] VTask vs CompletableFuture chain

### 8.3 VTaskParBenchmark.java

Parallel combinator benchmarks:

- [ ] `zipTwoTasks` — Parallel zip overhead
- [ ] `zipVsSequential` — Parallel vs sequential comparison
- [ ] `raceMultipleTasks` — Race overhead
- [ ] `allTasks` — All combinator overhead
- [ ] `traverseList` — Parallel traverse at various sizes
- [ ] `highConcurrency` — 1000+ concurrent tasks
- [ ] `virtualVsPlatformThreads` — Thread type comparison

### 8.4 Baseline Benchmarks

To demonstrate advantages:

- [ ] Traditional `ExecutorService` equivalent operations
- [ ] Platform thread creation and execution
- [ ] `CompletableFuture` equivalent patterns
- [ ] Memory footprint comparison (if measurable)

---

## 9. Completion Criteria

### 9.1 Code Complete

- [ ] All implementation classes complete
- [ ] All tests passing
- [ ] 100% test coverage achieved
- [ ] ArchUnit tests passing
- [ ] Code review approved

### 9.2 Documentation Complete

- [ ] Documentation plan reviewed and approved
- [ ] All hkj-book pages written
- [ ] All Javadoc complete
- [ ] Tutorials written and tested
- [ ] README updates complete

### 9.3 Performance Validated

- [ ] All benchmarks implemented and running
- [ ] Baseline comparisons documented
- [ ] No performance regressions from IO
- [ ] Virtual thread advantages demonstrated

### 9.4 Integration Verified

- [ ] Builds successfully with preview features
- [ ] All existing tests still pass
- [ ] No breaking changes to existing API
- [ ] Ready for Phase 2 integration

---

## 10. Design Decisions Summary

The following decisions require consultation before implementation:

1. **Java Version**: Minimum Java 25 requirement vs optional virtual threads on Java 21+?
2. **Laziness**: Should VTask be lazy (like IO) or eager (starts immediately)?
3. **Interruption Model**: Cooperative vs preemptive interruption?
4. **Par Implementation**: Use `StructuredTaskScope` in Phase 1 or defer to Phase 3?
5. **Naming**: `VTask` vs `VThread` vs `Virtual` vs other alternatives?
6. **Package Location**: `org.higherkindedj.hkt.vtask` or `org.higherkindedj.hkt.virtual`?

---

## Appendix: File Checklist

### New Files to Create

```
hkj-api/src/main/java/org/higherkindedj/hkt/vtask/
├── VTaskKind.java
└── package-info.java

hkj-core/src/main/java/org/higherkindedj/hkt/vtask/
├── VTask.java
├── VTaskImpl.java (internal)
├── VTaskKindHelper.java
├── VTaskMonad.java
├── Par.java
└── package-info.java

hkj-core/src/test/java/org/higherkindedj/hkt/vtask/
├── VTaskTest.java
├── VTaskPropertyTest.java
├── VTaskLawsTest.java
├── ParTest.java
└── VTaskArchitectureRules.java

hkj-core/src/test/java/org/higherkindedj/test/assertions/
├── VTaskAssert.java
└── VTaskAssertions.java

hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/
├── VTaskBenchmark.java
├── VTaskVsIOBenchmark.java
└── VTaskParBenchmark.java

hkj-examples/src/test/java/org/higherkindedj/tutorial/vtask/
├── README.md
├── Tutorial01_VTaskBasics.java
├── Tutorial02_ParallelComposition.java
└── solutions/
    ├── Tutorial01_VTaskBasics_Solution.java
    └── Tutorial02_ParallelComposition_Solution.java

hkj-book/src/effects/
├── vtask.md
└── par.md
```

### Files to Update

```
build.gradle.kts (preview features)
hkj-book/src/SUMMARY.md
hkj-book/src/effects/ch_intro.md
hkj-examples/src/test/java/org/higherkindedj/tutorial/README.md
```
