# Phase 2: Effect Path Integration — Implementation Plan

> **Target Version**: v2.0.0-M2
> **Prerequisites**: Phase 1 complete (VTask Foundation)
> **Estimated Duration**: To be determined by team velocity

## Overview

Phase 2 integrates VTask with the Effect Path API, ForPath builder, and Effect Contexts layer, providing a seamless developer experience consistent with existing effect types.

---

## 1. VTaskPath Integration

### 1.1 VTaskPath Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/VTaskPath.java`

- [ ] Create `VTaskPath<A>` sealed interface
- [ ] Extend `VTaskKind<A>`, `Chainable<A>`, `Effectful<A>`
- [ ] Follow existing `IOPath` pattern closely

```java
public sealed interface VTaskPath<A> extends VTaskKind<A>,
    Chainable<A>, Effectful<A> permits VTaskPathImpl {

    // Factory (via Path class)
    // Path.vtask(() -> computation)
    // Path.vtaskPure(value)

    // Composition
    <B> VTaskPath<B> map(Function<A, B> f);
    <B> VTaskPath<B> via(Function<A, VTaskPath<B>> f);
    <B> VTaskPath<B> then(Supplier<VTaskPath<B>> next);
    VTaskPath<A> peek(Consumer<A> action);

    // Error handling
    VTaskPath<A> recover(Function<Throwable, A> handler);
    VTaskPath<A> recoverWith(Function<Throwable, VTaskPath<A>> handler);

    // Execution
    A run() throws InterruptedException;
    Try<A> runSafe();
    CompletableFuture<A> runAsync();

    // Timeout
    VTaskPath<A> timeout(Duration duration);
}
```

### 1.2 VTaskPath Implementation

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/VTaskPathImpl.java`

- [ ] Package-private implementation record
- [ ] Delegate to underlying VTask
- [ ] Maintain immutability

### 1.3 Path Factory Methods

**Location**: Update `hkj-core/src/main/java/org/higherkindedj/hkt/effect/Path.java`

- [ ] Add `vtask(Callable<A>)` factory method
- [ ] Add `vtaskPure(A)` factory method
- [ ] Add `vtaskFail(Throwable)` factory method
- [ ] Add `vtaskExec(Runnable)` factory method
- [ ] Follow existing naming conventions

```java
public static <A> VTaskPath<A> vtask(Callable<A> computation) {
    return new VTaskPathImpl<>(VTask.of(computation));
}

public static <A> VTaskPath<A> vtaskPure(A value) {
    return new VTaskPathImpl<>(VTask.succeed(value));
}
```

---

## 2. ForPath Integration

### 2.1 ForPath Entry Points

**Location**: Update `hkj-core/src/main/java/org/higherkindedj/hkt/expression/ForPath.java`

- [ ] Add `from(VTaskPath<A>)` entry point
- [ ] Create `VTaskSteps1<A>` through `VTaskSteps5<A, B, C, D, E>` builders
- [ ] Support `let`, `when`, `focus` operations
- [ ] Support `yield` terminal operation

```java
// Entry point
public static <A> VTaskSteps1<A> from(VTaskPath<A> path) {
    return new VTaskSteps1<>(path);
}

// Builder chain
public static final class VTaskSteps1<A> {
    public <B> VTaskSteps2<A, B> from(Function<A, VTaskPath<B>> f) { ... }
    public <B> VTaskSteps1<B> let(Function<A, B> f) { ... }
    public VTaskSteps1<A> when(Predicate<A> p) { ... }
    public <B> VTaskPath<B> yield(Function<A, B> f) { ... }
}
```

### 2.2 Focus Integration

- [ ] Support `focus(FocusPath)` in VTask for-comprehension
- [ ] Support `focus(AffinePath)` in VTask for-comprehension
- [ ] Integrate with optics composition

### 2.3 Design Decision Required

> **Consult**: Should ForPath VTask builders be separate classes or integrated into existing ForPath hierarchy?

---

## 3. VTaskContext Layer 2 Wrapper

### 3.1 VTaskContext Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/context/VTaskContext.java`

- [ ] Create `VTaskContext<A>` sealed interface
- [ ] Extend `EffectContext<VTaskKind.Witness, A>`
- [ ] Follow `ErrorContext`, `ConfigContext` patterns

```java
public sealed interface VTaskContext<A>
    extends EffectContext<VTaskKind.Witness, A> permits VTaskContextImpl {

    // Factory methods
    static <A> VTaskContext<A> of(Callable<A> computation);
    static <A> VTaskContext<A> pure(A value);
    static <A> VTaskContext<A> fail(Throwable error);
    static <A> VTaskContext<A> fromPath(VTaskPath<A> path);
    static <A> VTaskContext<A> fromVTask(VTask<A> task);

    // Composition
    <B> VTaskContext<B> map(Function<A, B> f);
    <B> VTaskContext<B> via(Function<A, VTaskContext<B>> f);
    <B> VTaskContext<B> then(Supplier<VTaskContext<B>> next);

    // Error handling
    VTaskContext<A> recover(Function<Throwable, A> handler);
    VTaskContext<A> recoverWith(Function<Throwable, VTaskContext<A>> handler);

    // Execution
    A run() throws InterruptedException;
    Try<A> runSafe();

    // Escape hatch to Layer 3
    VTask<A> underlying();
    VTaskPath<A> toPath();
}
```

### 3.2 VTaskContext Implementation

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/context/VTaskContextImpl.java`

- [ ] Package-private implementation
- [ ] Delegate to underlying VTask
- [ ] Thread-safe execution

---

## 4. PathOps Integration

### 4.1 VTaskPath Operations

**Location**: Update `hkj-core/src/main/java/org/higherkindedj/hkt/effect/PathOps.java`

- [ ] Add `sequenceVTask(List<VTaskPath<A>>)` method
- [ ] Add `traverseVTask(List<A>, Function<A, VTaskPath<B>>)` method
- [ ] Add parallel variants using Par combinators
- [ ] Error handling follows fail-fast semantics

```java
public static <A> VTaskPath<List<A>> sequenceVTask(List<VTaskPath<A>> paths) {
    return paths.stream()
        .reduce(
            Path.vtaskPure(new ArrayList<A>()),
            (acc, path) -> acc.via(list -> path.map(a -> {
                list.add(a);
                return list;
            })),
            (a, b) -> a.via(listA -> b.map(listB -> {
                listA.addAll(listB);
                return listA;
            }))
        );
}

public static <A> VTaskPath<List<A>> sequenceVTaskPar(List<VTaskPath<A>> paths) {
    // Use Par.all for parallel execution
    return new VTaskPathImpl<>(Par.all(
        paths.stream().map(VTaskPath::underlying).toList()
    ));
}
```

---

## 5. Testing Requirements

### 5.1 VTaskPath Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/VTaskPathTest.java`

Following Effect Path testing patterns from TESTING.md:

- [ ] `@Nested FactoryMethodsViaPathTests`
  - [ ] `Path.vtask()` creates VTaskPath
  - [ ] `Path.vtaskPure()` creates pure VTaskPath
  - [ ] `Path.vtaskFail()` creates failed VTaskPath
- [ ] `@Nested RunAndTerminalMethodsTests`
  - [ ] `run()` executes on virtual thread
  - [ ] `runSafe()` returns Try
  - [ ] `runAsync()` returns CompletableFuture
- [ ] `@Nested ComposableOperationsTests`
  - [ ] `map()` transforms value
  - [ ] `peek()` observes without modifying
- [ ] `@Nested ChainableOperationsTests`
  - [ ] `via()` chains computations
  - [ ] `then()` sequences, discards result
- [ ] `@Nested RecoverableOperationsTests`
  - [ ] `recover()` handles errors
  - [ ] `recoverWith()` chains recovery
- [ ] `@Nested TimeoutTests`
  - [ ] `timeout()` fails on timeout
  - [ ] `timeout()` succeeds if fast enough
- [ ] `@Nested ObjectMethodsTests`
  - [ ] equals, hashCode, toString

### 5.2 VTaskPath Property Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/VTaskPathPropertyTest.java`

- [ ] Functor identity law
- [ ] Functor composition law
- [ ] Monad left identity law
- [ ] Monad right identity law
- [ ] Monad associativity law
- [ ] Arbitrary providers for VTaskPath

### 5.3 VTaskPath Laws Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/VTaskPathLawsTest.java`

- [ ] `@TestFactory` for all laws
- [ ] DynamicTest organisation

### 5.4 ForPath VTask Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/expression/ForPathVTaskTest.java`

- [ ] Basic for-comprehension with VTask
- [ ] Multi-step for-comprehension
- [ ] `let` transformation
- [ ] `when` filtering
- [ ] `focus` integration
- [ ] `yield` terminal
- [ ] Error propagation through comprehension

### 5.5 VTaskContext Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/context/VTaskContextTest.java`

Following Effect Contexts testing patterns:

- [ ] Factory method tests
- [ ] Composition tests
- [ ] Error handling tests
- [ ] Execution tests
- [ ] Escape hatch tests (`underlying()`, `toPath()`)

### 5.6 PathOps VTask Tests

**Location**: Update existing `PathOpsTest.java`

- [ ] `sequenceVTask` tests
- [ ] `traverseVTask` tests
- [ ] Parallel variant tests
- [ ] Error propagation tests

### 5.7 Coverage Requirements

- [ ] 100% coverage for VTaskPath
- [ ] 100% coverage for VTaskContext
- [ ] 100% coverage for ForPath VTask builders
- [ ] 100% coverage for PathOps VTask methods

---

## 6. ArchUnit Tests

### 6.1 VTaskPathArchitectureRules.java

**Location**: `hkj-core/src/test/java/org/higherkindedj/architecture/`

- [ ] VTaskPath must be sealed interface
- [ ] VTaskPathImpl must be package-private
- [ ] VTaskContext must be sealed interface
- [ ] VTaskContext must follow EffectContext pattern

### 6.2 Update EffectPathTestingRules.java

- [ ] Add VTaskPath to effect path checks
- [ ] Verify three-layer testing exists for VTaskPath

---

## 7. Custom AssertJ Assertions

### 7.1 VTaskPathAssert.java

**Location**: `hkj-core/src/test/java/org/higherkindedj/test/assertions/`

- [ ] Extend VTaskAssert with Path-specific assertions
- [ ] `hasUnderlyingTask()` — verify underlying VTask
- [ ] `isEquivalentTo(VTaskPath)` — semantic equality

### 7.2 VTaskContextAssert.java

- [ ] `VTaskContextAssert<A>` for VTaskContext assertions
- [ ] Similar methods to VTaskAssert
- [ ] `hasUnderlying(VTask)` — verify underlying

---

## 8. Documentation Plan

> **Note**: Review this plan before writing documentation.

### 8.1 hkj-book Updates

#### Update Existing Pages

- [ ] `effects/io.md` — Add "See Also" link to VTaskPath
- [ ] `effects/path.md` — Add VTaskPath to Path factory methods table
- [ ] `effects/effect_contexts.md` — Add VTaskContext section
- [ ] `expression/for_path.md` — Add VTaskPath examples

#### New Pages

- [ ] `effects/vtask_path.md` — VTaskPath: Effect Path for Virtual Threads
  - What You'll Learn
  - Factory methods via Path
  - Composition patterns
  - Comparison with IOPath
  - Integration with ForPath
  - Key Takeaways
  - See Also

### 8.2 Javadoc Requirements

- [ ] VTaskPath fully documented
- [ ] VTaskContext fully documented
- [ ] ForPath VTask methods documented
- [ ] PathOps VTask methods documented

---

## 9. Tutorial Plan

> **Note**: Review this plan before writing tutorials.

### 9.1 Update Existing Tutorials

- [ ] Add VTaskPath option in Effect Path tutorials where appropriate
- [ ] Add VTaskContext examples to Effect Context tutorials

### 9.2 New Tutorials

**Location**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vtask/`

#### Tutorial03_VTaskPath.java (~10 minutes)

- [ ] Exercise 1: Create VTaskPath via Path factory
- [ ] Exercise 2: Map and chain VTaskPath
- [ ] Exercise 3: Error recovery with VTaskPath
- [ ] Exercise 4: Convert between VTask and VTaskPath
- [ ] Exercise 5: Sequence multiple VTaskPaths
- [ ] Exercise 6: Parallel sequence with sequenceVTaskPar

#### Tutorial04_VTaskForPath.java (~12 minutes)

- [ ] Exercise 1: Basic for-comprehension with VTask
- [ ] Exercise 2: Multi-step for-comprehension
- [ ] Exercise 3: Using `let` for intermediate values
- [ ] Exercise 4: Using `when` for filtering
- [ ] Exercise 5: Combining with Focus DSL
- [ ] Exercise 6: Complex workflow example

### 9.3 Solution Files

- [ ] Tutorial03_VTaskPath_Solution.java
- [ ] Tutorial04_VTaskForPath_Solution.java

---

## 10. Performance Benchmarks

**Location**: `hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/`

### 10.1 VTaskPathBenchmark.java

- [ ] Path factory overhead
- [ ] VTaskPath vs VTask composition
- [ ] VTaskPath vs IOPath comparison
- [ ] sequenceVTask performance
- [ ] sequenceVTaskPar vs sequenceVTask

### 10.2 ForPathVTaskBenchmark.java

- [ ] ForPath VTask construction
- [ ] ForPath VTask execution
- [ ] ForPath vs manual flatMap chain

---

## 11. Completion Criteria

### 11.1 Code Complete

- [ ] VTaskPath implementation complete
- [ ] VTaskContext implementation complete
- [ ] ForPath VTask integration complete
- [ ] PathOps VTask methods complete
- [ ] All tests passing at 100% coverage
- [ ] ArchUnit tests passing

### 11.2 Documentation Complete

- [ ] Documentation plan reviewed and approved
- [ ] All documentation written
- [ ] Tutorials complete

### 11.3 Performance Validated

- [ ] Benchmarks implemented
- [ ] No overhead vs raw VTask usage
- [ ] Parallel operations show expected speedup

### 11.4 Integration Verified

- [ ] Seamless integration with existing Effect Path types
- [ ] ForPath works consistently with VTask
- [ ] Ready for Phase 3

---

## 12. Design Decisions Summary

1. **ForPath Structure**: Separate VTask builders or integrated hierarchy?
2. **PathOps Parallel Default**: Should `sequenceVTask` be parallel by default?
3. **VTaskContext Execution**: Should `run()` be lazy or eager?
4. **Error Context Integration**: Should VTaskContext extend ErrorContext?

---

## Appendix: File Checklist

### New Files to Create

```
hkj-core/src/main/java/org/higherkindedj/hkt/effect/
├── VTaskPath.java
└── VTaskPathImpl.java

hkj-core/src/main/java/org/higherkindedj/hkt/effect/context/
├── VTaskContext.java
└── VTaskContextImpl.java

hkj-core/src/test/java/org/higherkindedj/hkt/effect/
├── VTaskPathTest.java
├── VTaskPathPropertyTest.java
└── VTaskPathLawsTest.java

hkj-core/src/test/java/org/higherkindedj/hkt/effect/context/
└── VTaskContextTest.java

hkj-core/src/test/java/org/higherkindedj/hkt/expression/
└── ForPathVTaskTest.java

hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/
├── VTaskPathBenchmark.java
└── ForPathVTaskBenchmark.java

hkj-examples/src/test/java/org/higherkindedj/tutorial/vtask/
├── Tutorial03_VTaskPath.java
├── Tutorial04_VTaskForPath.java
└── solutions/
    ├── Tutorial03_VTaskPath_Solution.java
    └── Tutorial04_VTaskForPath_Solution.java

hkj-book/src/effects/
└── vtask_path.md
```

### Files to Update

```
hkj-core/src/main/java/org/higherkindedj/hkt/effect/Path.java
hkj-core/src/main/java/org/higherkindedj/hkt/effect/PathOps.java
hkj-core/src/main/java/org/higherkindedj/hkt/expression/ForPath.java
hkj-core/src/test/java/org/higherkindedj/hkt/effect/PathOpsTest.java
hkj-book/src/effects/io.md
hkj-book/src/effects/path.md
hkj-book/src/effects/effect_contexts.md
hkj-book/src/expression/for_path.md
hkj-book/src/SUMMARY.md
```
