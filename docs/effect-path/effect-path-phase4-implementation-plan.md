# Effect Path API - Phase 4 Implementation Plan

> **Status**: Planning (v1.0)
> **Last Updated**: 2025-12-17
> **Phase 3 Completion**: Complete
> **Phase 4 Target**: Stack-safety, Resources, Parallelism, Resilience
> **Java Baseline**: Java 25 (RELEASE_25)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Phase 4 Scope Overview](#phase-4-scope-overview)
3. [Sub-Phase Organisation](#sub-phase-organisation)
4. [Phase 4a: Tier 3 Path Types](#phase-4a-tier-3-path-types)
5. [Phase 4b: Resource Management](#phase-4b-resource-management)
6. [Phase 4c: Parallel Execution](#phase-4c-parallel-execution)
7. [Phase 4d: Resilience Patterns](#phase-4d-resilience-patterns)
8. [Phase 4e: Testing Completion](#phase-4e-testing-completion)
9. [Testing Strategy](#testing-strategy)
10. [Documentation Requirements](#documentation-requirements)
11. [Examples](#examples)
12. [Success Criteria](#success-criteria)
13. [Phase 5+ Forward Look](#phase-5-forward-look)

---

## Executive Summary

Phase 4 builds upon the Phase 1-3 foundations to provide production-ready capabilities:

| Category | Components |
|----------|------------|
| **Tier 3 Path Types** | TrampolinePath, FreePath, FreeApPath |
| **Resource Management** | bracket, withResource, guarantee on IOPath |
| **Parallel Execution** | parZipWith, parSequence, race |
| **Resilience Patterns** | RetryPolicy with path integration |
| **Testing Completion** | PathSourceProcessorTest (carry-forward) |

### Phase Dependencies

```
Phase 1-3 (Complete)              Phase 4                    Phase 5 (Future)
────────────────────              ───────                    ────────────────
MaybePath, EitherPath             TrampolinePath             MaybeTPath
TryPath, IOPath                   FreePath                   EitherTPath
ValidationPath, IdPath            FreeApPath                 OptionalTPath
OptionalPath, GenericPath         IOPath.bracket             ReaderTPath
ReaderPath, WithStatePath         IOPath.withResource        StateTPath
WriterPath, LazyPath              parZipWith, parSequence
CompletableFuturePath             RetryPolicy                Phase 6 (Future)
ListPath, StreamPath, NonDetPath  PathSourceProcessorTest    ────────────────
NaturalTransformation                                        FocusPath integration
PathProvider, PathRegistry                                   Path + Lens composition
@PathSource, @PathConfig                                     Prism + Path composition
```

---

## Phase 4 Scope Overview

### New Path Types

| Path Type | Underlying Type | Capabilities | Priority |
|-----------|-----------------|--------------|----------|
| **TrampolinePath<A>** | Trampoline<A> | Chainable, stack-safe | High |
| **FreePath<F, A>** | Free<F, A> | Chainable, DSL building | Medium |
| **FreeApPath<F, A>** | FreeAp<F, A> | Combinable, applicative DSL | Medium |

### IOPath Enhancements

| Feature | Purpose | Priority |
|---------|---------|----------|
| **bracket** | Acquire/use/release pattern | High |
| **withResource** | AutoCloseable management | High |
| **guarantee** | Ensure finalizer runs | High |
| **parZipWith** | Parallel binary combination | High |
| **race** | First-to-complete semantics | Medium |

### PathOps Additions

| Feature | Purpose | Priority |
|---------|---------|----------|
| **parSequenceIO** | Parallel list execution for IOPath | High |
| **parSequenceFuture** | Parallel list execution for FuturePath | High |
| **parZip3, parZip4** | N-ary parallel combination | Medium |

### Resilience Infrastructure

| Component | Purpose | Priority |
|-----------|---------|----------|
| **RetryPolicy** | Configurable retry strategies | High |
| **Retry utilities** | Execute with retry | High |
| **IOPath.withRetry** | Fluent retry integration | High |
| **FuturePath.withRetry** | Fluent retry integration | High |

---

## Sub-Phase Organisation

Phase 4 is organised into five sub-phases:

```
Phase 4a: Tier 3 Paths    Phase 4b: Resources    Phase 4c: Parallel    Phase 4d: Resilience
─────────────────────     ──────────────────     ─────────────────     ───────────────────
TrampolinePath            IOPath.bracket         parZipWith            RetryPolicy
FreePath                  IOPath.withResource    parSequenceIO         Retry utilities
FreeApPath                IOPath.guarantee       parSequenceFuture     withRetry methods
                                                 race

                                    │
                                    ▼
                          Phase 4e: Testing
                          ─────────────────
                          PathSourceProcessorTest
                          All new component tests
```

### Sub-Phase Dependencies

| Sub-Phase | Dependencies | Can Start After |
|-----------|--------------|-----------------|
| **4a** | Phase 3 complete | Phase 3 |
| **4b** | Phase 3 complete | Phase 3 (parallel with 4a) |
| **4c** | Phase 3 complete | Phase 3 (parallel with 4a, 4b) |
| **4d** | Phase 3 complete | Phase 3 (parallel with 4a, 4b, 4c) |
| **4e** | All above complete | 4a, 4b, 4c, 4d |

---

## Phase 4a: Tier 3 Path Types

### A1: TrampolinePath<A>

**Purpose**: Stack-safe recursive computations

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/TrampolinePath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.trampoline.Trampoline;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A fluent path wrapper for {@link Trampoline} computations.
 *
 * <p>{@code TrampolinePath} represents stack-safe recursive computations that are
 * trampolined to avoid stack overflow. This is essential for deeply recursive
 * algorithms or processing deeply nested data structures.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Deeply recursive algorithms (factorial, fibonacci)</li>
 *   <li>Processing deeply nested trees</li>
 *   <li>Mutual recursion without stack overflow</li>
 *   <li>Interpreter/evaluator implementations</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Stack-safe factorial
 * TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
 *     if (n.compareTo(BigInteger.ONE) <= 0) {
 *         return TrampolinePath.done(acc);
 *     }
 *     return TrampolinePath.defer(() ->
 *         factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
 * }
 *
 * BigInteger result = factorial(BigInteger.valueOf(100000), BigInteger.ONE).run();
 * }</pre>
 *
 * @param <A> the result type
 */
public final class TrampolinePath<A> implements Chainable<A> {

    private final Trampoline<A> trampoline;

    private TrampolinePath(Trampoline<A> trampoline) {
        this.trampoline = Objects.requireNonNull(trampoline, "trampoline must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a TrampolinePath with an immediate value.
     */
    public static <A> TrampolinePath<A> done(A value) {
        return new TrampolinePath<>(Trampoline.done(value));
    }

    /**
     * Creates a TrampolinePath with a deferred computation.
     */
    public static <A> TrampolinePath<A> defer(Supplier<TrampolinePath<A>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return new TrampolinePath<>(Trampoline.defer(() -> supplier.get().trampoline));
    }

    /**
     * Creates a TrampolinePath from an existing Trampoline.
     */
    public static <A> TrampolinePath<A> of(Trampoline<A> trampoline) {
        return new TrampolinePath<>(trampoline);
    }

    // ===== Terminal Operations =====

    /**
     * Runs the trampolined computation to completion.
     * This is stack-safe regardless of recursion depth.
     */
    public A run() {
        return trampoline.run();
    }

    /**
     * Returns the underlying Trampoline.
     */
    public Trampoline<A> toTrampoline() {
        return trampoline;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> TrampolinePath<B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new TrampolinePath<>(trampoline.map(mapper));
    }

    @Override
    public TrampolinePath<A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> TrampolinePath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Trampoline<B> flatMapped = trampoline.flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof TrampolinePath<?> tp) {
                return ((TrampolinePath<B>) tp).trampoline;
            }
            throw new IllegalArgumentException(
                "TrampolinePath.via must return TrampolinePath. Got: " + result.getClass());
        });
        return new TrampolinePath<>(flatMapped);
    }

    @Override
    public <B> TrampolinePath<B> then(Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Trampoline-Specific Operations =====

    /**
     * Combines with another TrampolinePath.
     */
    public <B, C> TrampolinePath<C> zipWith(
            TrampolinePath<B> other,
            java.util.function.BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");
        return via(a -> other.map(b -> combiner.apply(a, b)));
    }

    // ===== Conversions =====

    /**
     * Converts to IOPath by running the trampoline.
     */
    public IOPath<A> toIOPath() {
        return IOPath.delay(this::run);
    }

    /**
     * Converts to LazyPath.
     */
    public LazyPath<A> toLazyPath() {
        return LazyPath.defer(this::run);
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TrampolinePath<?> other)) return false;
        return trampoline.equals(other.trampoline);
    }

    @Override
    public int hashCode() {
        return trampoline.hashCode();
    }

    @Override
    public String toString() {
        return "TrampolinePath(...)";
    }
}
```

**Tests**: `TrampolinePathTest.java`, `TrampolinePathPropertyTest.java`, `TrampolinePathLawsTest.java`

---

### A2: FreePath<F, A>

**Purpose**: DSL building and interpretation

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/FreePath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.Functor;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A fluent path wrapper for {@link Free} monad computations.
 *
 * <p>{@code FreePath} represents computations built from a functor {@code F} that can
 * be interpreted into any monad. This is the foundation for building domain-specific
 * languages (DSLs) with deferred interpretation.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Building embedded DSLs</li>
 *   <li>Separating program description from execution</li>
 *   <li>Testing with mock interpreters</li>
 *   <li>Multiple interpretation strategies</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Define a simple Console DSL
 * sealed interface ConsoleF<A> {
 *     record PrintLine<A>(String line, A next) implements ConsoleF<A> {}
 *     record ReadLine<A>(Function<String, A> cont) implements ConsoleF<A> {}
 * }
 *
 * // Build program
 * FreePath<ConsoleF.Witness, String> program =
 *     FreePath.liftF(new PrintLine<>("Enter name:", ()))
 *         .then(() -> FreePath.liftF(new ReadLine<>(name -> name)))
 *         .via(name -> FreePath.liftF(new PrintLine<>("Hello " + name, name)));
 *
 * // Interpret to IO
 * IOPath<String> result = program.foldMap(consoleInterpreter, IOMonad.INSTANCE);
 * }</pre>
 *
 * @param <F> the functor witness type for the DSL
 * @param <A> the result type
 */
public final class FreePath<F, A> implements Chainable<A> {

    private final Free<F, A> free;
    private final Functor<F> functor;

    private FreePath(Free<F, A> free, Functor<F> functor) {
        this.free = Objects.requireNonNull(free, "free must not be null");
        this.functor = Objects.requireNonNull(functor, "functor must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a FreePath containing a pure value.
     */
    public static <F, A> FreePath<F, A> pure(A value, Functor<F> functor) {
        Objects.requireNonNull(functor, "functor must not be null");
        return new FreePath<>(Free.pure(value), functor);
    }

    /**
     * Lifts a functor value into FreePath.
     */
    public static <F, A> FreePath<F, A> liftF(Kind<F, A> fa, Functor<F> functor) {
        Objects.requireNonNull(fa, "fa must not be null");
        Objects.requireNonNull(functor, "functor must not be null");
        return new FreePath<>(Free.liftF(fa, functor), functor);
    }

    /**
     * Creates a FreePath from an existing Free.
     */
    public static <F, A> FreePath<F, A> of(Free<F, A> free, Functor<F> functor) {
        return new FreePath<>(free, functor);
    }

    // ===== Interpretation =====

    /**
     * Interprets this FreePath into a target monad using a natural transformation.
     *
     * @param interpreter natural transformation from F to G
     * @param targetMonad the target monad instance
     * @param <G> the target monad witness type
     * @return the interpreted result wrapped in a GenericPath
     */
    public <G> GenericPath<G, A> foldMap(
            NaturalTransformation<F, G> interpreter,
            Monad<G> targetMonad) {
        Objects.requireNonNull(interpreter, "interpreter must not be null");
        Objects.requireNonNull(targetMonad, "targetMonad must not be null");
        Kind<G, A> result = free.foldMap(interpreter, targetMonad);
        return GenericPath.of(result, targetMonad);
    }

    /**
     * Returns the underlying Free.
     */
    public Free<F, A> toFree() {
        return free;
    }

    /**
     * Returns the Functor for this FreePath.
     */
    public Functor<F> functor() {
        return functor;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> FreePath<F, B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new FreePath<>(free.map(mapper), functor);
    }

    @Override
    public FreePath<F, A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> FreePath<F, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Free<F, B> flatMapped = free.flatMap(a -> {
            Chainable<B> result = mapper.apply(a);
            if (result instanceof FreePath<?, ?> fp) {
                return ((FreePath<F, B>) fp).free;
            }
            throw new IllegalArgumentException(
                "FreePath.via must return FreePath. Got: " + result.getClass());
        });
        return new FreePath<>(flatMapped, functor);
    }

    @Override
    public <B> FreePath<F, B> then(Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FreePath<?, ?> other)) return false;
        return free.equals(other.free);
    }

    @Override
    public int hashCode() {
        return free.hashCode();
    }

    @Override
    public String toString() {
        return "FreePath(" + free + ")";
    }
}
```

**Tests**: `FreePathTest.java`, `FreePathPropertyTest.java`, `FreePathLawsTest.java`

---

### A3: FreeApPath<F, A>

**Purpose**: Applicative DSL building (static analysis capable)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/FreeApPath.java`

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Composable;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.Functor;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A fluent path wrapper for {@link FreeAp} applicative computations.
 *
 * <p>{@code FreeApPath} represents applicative computations that can be statically
 * analysed before interpretation. Unlike FreePath, FreeApPath does not support
 * monadic bind (via), only applicative operations (map, zipWith).
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Form validation with all errors collected</li>
 *   <li>Query builders with static analysis</li>
 *   <li>Dependency graphs</li>
 *   <li>Parallel-by-default execution</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Validation DSL - all validations run, all errors collected
 * FreeApPath<ValidationF.Witness, User> validateUser =
 *     FreeApPath.liftF(validateName(input.name()), validationFunctor)
 *         .zipWith(
 *             FreeApPath.liftF(validateEmail(input.email()), validationFunctor),
 *             FreeApPath.liftF(validateAge(input.age()), validationFunctor),
 *             User::new
 *         );
 *
 * // Interpret - collects ALL validation errors
 * ValidationPath<List<Error>, User> result =
 *     validateUser.foldMap(interpreter, validationApplicative);
 * }</pre>
 *
 * @param <F> the functor witness type
 * @param <A> the result type
 */
public final class FreeApPath<F, A> implements Composable<A>, Combinable<A> {

    private final FreeAp<F, A> freeAp;
    private final Functor<F> functor;

    private FreeApPath(FreeAp<F, A> freeAp, Functor<F> functor) {
        this.freeAp = Objects.requireNonNull(freeAp, "freeAp must not be null");
        this.functor = Objects.requireNonNull(functor, "functor must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a FreeApPath containing a pure value.
     */
    public static <F, A> FreeApPath<F, A> pure(A value, Functor<F> functor) {
        Objects.requireNonNull(functor, "functor must not be null");
        return new FreeApPath<>(FreeAp.pure(value), functor);
    }

    /**
     * Lifts a functor value into FreeApPath.
     */
    public static <F, A> FreeApPath<F, A> liftF(Kind<F, A> fa, Functor<F> functor) {
        Objects.requireNonNull(fa, "fa must not be null");
        Objects.requireNonNull(functor, "functor must not be null");
        return new FreeApPath<>(FreeAp.lift(fa), functor);
    }

    /**
     * Creates a FreeApPath from an existing FreeAp.
     */
    public static <F, A> FreeApPath<F, A> of(FreeAp<F, A> freeAp, Functor<F> functor) {
        return new FreeApPath<>(freeAp, functor);
    }

    // ===== Interpretation =====

    /**
     * Interprets this FreeApPath into a target applicative.
     */
    public <G> GenericPath<G, A> foldMap(
            NaturalTransformation<F, G> interpreter,
            Applicative<G> targetApplicative) {
        Objects.requireNonNull(interpreter, "interpreter must not be null");
        Objects.requireNonNull(targetApplicative, "targetApplicative must not be null");
        Kind<G, A> result = freeAp.foldMap(interpreter, targetApplicative);
        // Note: GenericPath requires Monad, but Applicative is sufficient for the value
        // Consider using a more general wrapper or requiring Monad
        throw new UnsupportedOperationException(
            "foldMap requires Monad for GenericPath. Use foldMapKind instead.");
    }

    /**
     * Interprets this FreeApPath into a target applicative, returning raw Kind.
     */
    public <G> Kind<G, A> foldMapKind(
            NaturalTransformation<F, G> interpreter,
            Applicative<G> targetApplicative) {
        Objects.requireNonNull(interpreter, "interpreter must not be null");
        Objects.requireNonNull(targetApplicative, "targetApplicative must not be null");
        return freeAp.foldMap(interpreter, targetApplicative);
    }

    /**
     * Returns the underlying FreeAp.
     */
    public FreeAp<F, A> toFreeAp() {
        return freeAp;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> FreeApPath<F, B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new FreeApPath<>(freeAp.map(mapper), functor);
    }

    @Override
    public FreeApPath<F, A> peek(java.util.function.Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Combinable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B, C> FreeApPath<F, C> zipWith(
            Combinable<B> other,
            BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");

        if (!(other instanceof FreeApPath<?, ?> otherFreeAp)) {
            throw new IllegalArgumentException(
                "FreeApPath.zipWith requires FreeApPath. Got: " + other.getClass());
        }

        FreeApPath<F, B> typedOther = (FreeApPath<F, B>) otherFreeAp;
        FreeAp<F, C> combined = freeAp.ap(typedOther.freeAp.map(
            b -> (Function<A, C>) a -> combiner.apply(a, b)));
        return new FreeApPath<>(combined, functor);
    }

    /**
     * Combines three FreeApPaths using a ternary function.
     */
    public <B, C, D> FreeApPath<F, D> zipWith3(
            FreeApPath<F, B> second,
            FreeApPath<F, C> third,
            org.higherkindedj.hkt.function.Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
        Objects.requireNonNull(second, "second must not be null");
        Objects.requireNonNull(third, "third must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");

        return this.zipWith(second, (a, b) -> (Function<C, D>) c -> combiner.apply(a, b, c))
            .zipWith(third, Function::apply);
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FreeApPath<?, ?> other)) return false;
        return freeAp.equals(other.freeAp);
    }

    @Override
    public int hashCode() {
        return freeAp.hashCode();
    }

    @Override
    public String toString() {
        return "FreeApPath(" + freeAp + ")";
    }
}
```

**Tests**: `FreeApPathTest.java`, `FreeApPathPropertyTest.java`

---

## Phase 4b: Resource Management

### B1: IOPath.bracket

**Purpose**: Safe resource acquisition and release

**Location**: Add to `hkj-core/src/main/java/org/higherkindedj/hkt/effect/IOPath.java`

```java
// ===== Resource Management =====

/**
 * Safely acquires a resource, uses it, and releases it.
 *
 * <p>The release action is guaranteed to run whether the use succeeds or fails.
 * This is the fundamental pattern for safe resource management.
 *
 * <pre>{@code
 * IOPath<String> readFile = IOPath.bracket(
 *     () -> new FileInputStream("data.txt"),           // acquire
 *     stream -> new String(stream.readAllBytes()),      // use
 *     stream -> stream.close()                          // release
 * );
 * }</pre>
 *
 * @param acquire the resource acquisition action
 * @param use the action that uses the resource
 * @param release the action that releases the resource (always runs)
 * @param <R> the resource type
 * @param <A> the result type
 * @return an IOPath that safely manages the resource
 */
public static <R, A> IOPath<A> bracket(
        Supplier<R> acquire,
        Function<R, A> use,
        java.util.function.Consumer<R> release) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");
    Objects.requireNonNull(release, "release must not be null");

    return new IOPath<>(IO.delay(() -> {
        R resource = acquire.get();
        try {
            return use.apply(resource);
        } finally {
            release.accept(resource);
        }
    }));
}

/**
 * Variant of bracket where use returns an IOPath.
 */
public static <R, A> IOPath<A> bracketIO(
        Supplier<R> acquire,
        Function<R, IOPath<A>> use,
        java.util.function.Consumer<R> release) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");
    Objects.requireNonNull(release, "release must not be null");

    return new IOPath<>(IO.delay(() -> {
        R resource = acquire.get();
        try {
            return use.apply(resource).unsafeRun();
        } finally {
            release.accept(resource);
        }
    }));
}
```

### B2: IOPath.withResource

**Purpose**: AutoCloseable resource management

```java
/**
 * Manages an AutoCloseable resource, ensuring it is closed after use.
 *
 * <p>This is a convenience method for resources that implement AutoCloseable.
 *
 * <pre>{@code
 * IOPath<List<String>> lines = IOPath.withResource(
 *     () -> Files.newBufferedReader(path),
 *     reader -> reader.lines().toList()
 * );
 * }</pre>
 *
 * @param acquire the resource acquisition action
 * @param use the action that uses the resource
 * @param <R> the resource type (must be AutoCloseable)
 * @param <A> the result type
 * @return an IOPath that safely manages the resource
 */
public static <R extends AutoCloseable, A> IOPath<A> withResource(
        Supplier<R> acquire,
        Function<R, A> use) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");

    return bracket(acquire, use, resource -> {
        try {
            resource.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close resource", e);
        }
    });
}

/**
 * Variant where use returns an IOPath.
 */
public static <R extends AutoCloseable, A> IOPath<A> withResourceIO(
        Supplier<R> acquire,
        Function<R, IOPath<A>> use) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");

    return bracketIO(acquire, use, resource -> {
        try {
            resource.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close resource", e);
        }
    });
}
```

### B3: IOPath.guarantee

**Purpose**: Ensure finalizer runs regardless of outcome

```java
/**
 * Ensures a finalizer action runs after this IOPath completes.
 *
 * <p>The finalizer runs whether the main computation succeeds or fails.
 *
 * <pre>{@code
 * IOPath<Result> computation = fetchData()
 *     .guarantee(() -> log.info("Fetch completed"));
 * }</pre>
 *
 * @param finalizer the action to run after completion
 * @return an IOPath with guaranteed finalizer
 */
public IOPath<A> guarantee(Runnable finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");
    return new IOPath<>(IO.delay(() -> {
        try {
            return this.unsafeRun();
        } finally {
            finalizer.run();
        }
    }));
}

/**
 * Ensures a finalizer IOPath runs after this IOPath completes.
 */
public IOPath<A> guaranteeIO(Supplier<IOPath<?>> finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");
    return new IOPath<>(IO.delay(() -> {
        try {
            return this.unsafeRun();
        } finally {
            finalizer.get().unsafeRun();
        }
    }));
}
```

**Tests**: `IOPathResourceTest.java`

---

## Phase 4c: Parallel Execution

### C1: IOPath.parZipWith (Instance Method)

**Location**: Add to `IOPath.java`

```java
// ===== Parallel Execution =====

/**
 * Combines this IOPath with another in parallel.
 *
 * <p>Both computations start immediately and run concurrently.
 * The results are combined when both complete.
 *
 * <pre>{@code
 * IOPath<UserProfile> profile = fetchUser(id)
 *     .parZipWith(fetchPreferences(id), UserProfile::new);
 * }</pre>
 *
 * @param other the other IOPath to run in parallel
 * @param combiner the function to combine results
 * @param <B> the type of the other result
 * @param <C> the combined result type
 * @return an IOPath that runs both in parallel and combines results
 */
public <B, C> IOPath<C> parZipWith(
        IOPath<B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IOPath<>(IO.delay(() -> {
        CompletableFuture<A> futureA = CompletableFuture.supplyAsync(this::unsafeRun);
        CompletableFuture<B> futureB = CompletableFuture.supplyAsync(other::unsafeRun);
        return futureA.thenCombine(futureB, combiner).join();
    }));
}

/**
 * Races this IOPath against another, returning the first to complete.
 *
 * <p>The losing computation is not cancelled (Java limitation).
 *
 * @param other the other IOPath to race against
 * @return an IOPath containing the first result
 */
public IOPath<A> race(IOPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");

    return new IOPath<>(IO.delay(() -> {
        CompletableFuture<A> futureA = CompletableFuture.supplyAsync(this::unsafeRun);
        CompletableFuture<A> futureB = CompletableFuture.supplyAsync(other::unsafeRun);
        return CompletableFuture.anyOf(futureA, futureB)
            .thenApply(result -> (A) result)
            .join();
    }));
}
```

### C2: CompletableFuturePath.parZipWith

**Location**: Add to `CompletableFuturePath.java`

```java
/**
 * Combines this path with another in parallel.
 *
 * <p>Since CompletableFuture is already async, this is equivalent to zipWith
 * but makes the parallel intent explicit.
 */
public <B, C> CompletableFuturePath<C> parZipWith(
        CompletableFuturePath<B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner) {
    return zipWith(other, combiner); // Already parallel
}

/**
 * Races against another path, returning the first to complete.
 */
public CompletableFuturePath<A> race(CompletableFuturePath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    CompletableFuture<A> raced = future.applyToEither(other.future, Function.identity());
    return new CompletableFuturePath<>(raced);
}
```

### C3: PathOps Parallel Utilities

**Location**: Add to `PathOps.java`

```java
// ===== Parallel Execution =====

/**
 * Executes a list of IOPaths in parallel and collects results.
 *
 * <pre>{@code
 * List<IOPath<User>> fetches = ids.stream()
 *     .map(id -> Path.io(() -> userService.fetch(id)))
 *     .toList();
 * IOPath<List<User>> all = PathOps.parSequenceIO(fetches);
 * }</pre>
 *
 * @param paths the IOPaths to execute in parallel
 * @param <A> the result type
 * @return an IOPath containing all results
 */
public static <A> IOPath<List<A>> parSequenceIO(List<IOPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");
    if (paths.isEmpty()) {
        return IOPath.pure(List.of());
    }

    return IOPath.delay(() -> {
        List<CompletableFuture<A>> futures = paths.stream()
            .map(p -> CompletableFuture.supplyAsync(p::unsafeRun))
            .toList();

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));

        return allOf.thenApply(_ -> futures.stream()
            .map(CompletableFuture::join)
            .toList()
        ).join();
    });
}

/**
 * Executes a list of CompletableFuturePaths in parallel.
 */
public static <A> CompletableFuturePath<List<A>> parSequenceFuture(
        List<CompletableFuturePath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");
    if (paths.isEmpty()) {
        return CompletableFuturePath.completed(List.of());
    }

    List<CompletableFuture<A>> futures = paths.stream()
        .map(CompletableFuturePath::run)
        .toList();

    CompletableFuture<List<A>> combined = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]))
        .thenApply(_ -> futures.stream()
            .map(CompletableFuture::join)
            .toList());

    return CompletableFuturePath.fromFuture(combined);
}

/**
 * Combines three IOPaths in parallel.
 */
public static <A, B, C, D> IOPath<D> parZip3(
        IOPath<A> first,
        IOPath<B> second,
        IOPath<C> third,
        org.higherkindedj.hkt.function.Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return IOPath.delay(() -> {
        CompletableFuture<A> fa = CompletableFuture.supplyAsync(first::unsafeRun);
        CompletableFuture<B> fb = CompletableFuture.supplyAsync(second::unsafeRun);
        CompletableFuture<C> fc = CompletableFuture.supplyAsync(third::unsafeRun);

        CompletableFuture.allOf(fa, fb, fc).join();
        return combiner.apply(fa.join(), fb.join(), fc.join());
    });
}

/**
 * Combines four IOPaths in parallel.
 */
public static <A, B, C, D, E> IOPath<E> parZip4(
        IOPath<A> first,
        IOPath<B> second,
        IOPath<C> third,
        IOPath<D> fourth,
        org.higherkindedj.hkt.function.Function4<? super A, ? super B, ? super C, ? super D, ? extends E> combiner) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(fourth, "fourth must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return IOPath.delay(() -> {
        CompletableFuture<A> fa = CompletableFuture.supplyAsync(first::unsafeRun);
        CompletableFuture<B> fb = CompletableFuture.supplyAsync(second::unsafeRun);
        CompletableFuture<C> fc = CompletableFuture.supplyAsync(third::unsafeRun);
        CompletableFuture<D> fd = CompletableFuture.supplyAsync(fourth::unsafeRun);

        CompletableFuture.allOf(fa, fb, fc, fd).join();
        return combiner.apply(fa.join(), fb.join(), fc.join(), fd.join());
    });
}

/**
 * Races multiple IOPaths, returning the first to complete.
 */
public static <A> IOPath<A> raceIO(List<IOPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");
    if (paths.isEmpty()) {
        throw new IllegalArgumentException("Cannot race empty list");
    }
    if (paths.size() == 1) {
        return paths.get(0);
    }

    return IOPath.delay(() -> {
        List<CompletableFuture<A>> futures = paths.stream()
            .map(p -> CompletableFuture.supplyAsync(p::unsafeRun))
            .toList();

        @SuppressWarnings("unchecked")
        CompletableFuture<A> anyOf = (CompletableFuture<A>) CompletableFuture.anyOf(
            futures.toArray(new CompletableFuture[0]));

        return anyOf.join();
    });
}
```

**Tests**: `PathOpsParallelTest.java`, `IOPathParallelTest.java`, `CompletableFuturePathParallelTest.java`

---

## Phase 4d: Resilience Patterns

### D1: RetryPolicy

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryPolicy.java`

```java
package org.higherkindedj.hkt.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Configurable retry policy for resilient operations.
 *
 * <p>RetryPolicy defines how many times to retry, how long to wait between
 * retries, and which exceptions should trigger retries.
 *
 * <h2>Built-in Strategies</h2>
 * <ul>
 *   <li>{@link #fixed(int, Duration)} - Fixed delay between retries</li>
 *   <li>{@link #exponentialBackoff(int, Duration)} - Exponentially increasing delay</li>
 *   <li>{@link #exponentialBackoffWithJitter(int, Duration)} - With randomization</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1))
 *     .retryOn(IOException.class)
 *     .withMaxDelay(Duration.ofSeconds(30));
 *
 * IOPath<Data> resilient = Path.io(() -> fetchData())
 *     .withRetry(policy);
 * }</pre>
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double backoffMultiplier;
    private final Duration maxDelay;
    private final boolean useJitter;
    private final Predicate<Throwable> retryPredicate;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.maxDelay = builder.maxDelay;
        this.useJitter = builder.useJitter;
        this.retryPredicate = builder.retryPredicate;
    }

    // ===== Factory Methods =====

    /**
     * Creates a retry policy with fixed delay between attempts.
     *
     * @param maxAttempts maximum number of attempts (including initial)
     * @param delay the fixed delay between retries
     * @return a fixed-delay retry policy
     */
    public static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return builder()
            .maxAttempts(maxAttempts)
            .initialDelay(delay)
            .backoffMultiplier(1.0)
            .build();
    }

    /**
     * Creates a retry policy with exponentially increasing delay.
     *
     * @param maxAttempts maximum number of attempts (including initial)
     * @param initialDelay the initial delay (doubles each retry)
     * @return an exponential backoff retry policy
     */
    public static RetryPolicy exponentialBackoff(int maxAttempts, Duration initialDelay) {
        return builder()
            .maxAttempts(maxAttempts)
            .initialDelay(initialDelay)
            .backoffMultiplier(2.0)
            .build();
    }

    /**
     * Creates a retry policy with exponential backoff and jitter.
     *
     * <p>Jitter adds randomization to prevent thundering herd problems.
     */
    public static RetryPolicy exponentialBackoffWithJitter(int maxAttempts, Duration initialDelay) {
        return builder()
            .maxAttempts(maxAttempts)
            .initialDelay(initialDelay)
            .backoffMultiplier(2.0)
            .useJitter(true)
            .build();
    }

    /**
     * Creates a policy that never retries.
     */
    public static RetryPolicy noRetry() {
        return fixed(1, Duration.ZERO);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ===== Accessors =====

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public double backoffMultiplier() {
        return backoffMultiplier;
    }

    public Duration maxDelay() {
        return maxDelay;
    }

    public boolean useJitter() {
        return useJitter;
    }

    /**
     * Calculates the delay for a given attempt number.
     *
     * @param attempt the attempt number (1-based)
     * @return the delay before the next retry
     */
    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }

        double multiplier = Math.pow(backoffMultiplier, attempt - 2);
        long delayMillis = (long) (initialDelay.toMillis() * multiplier);

        if (maxDelay != null && delayMillis > maxDelay.toMillis()) {
            delayMillis = maxDelay.toMillis();
        }

        if (useJitter) {
            Random random = new Random();
            delayMillis = (long) (delayMillis * (0.5 + random.nextDouble()));
        }

        return Duration.ofMillis(delayMillis);
    }

    /**
     * Returns whether the given exception should trigger a retry.
     */
    public boolean shouldRetry(Throwable throwable) {
        return retryPredicate.test(throwable);
    }

    // ===== Modifiers =====

    /**
     * Returns a new policy that only retries on the specified exception type.
     */
    public RetryPolicy retryOn(Class<? extends Throwable> exceptionType) {
        Objects.requireNonNull(exceptionType, "exceptionType must not be null");
        return new Builder(this)
            .retryPredicate(t -> exceptionType.isInstance(t))
            .build();
    }

    /**
     * Returns a new policy with a custom retry predicate.
     */
    public RetryPolicy retryIf(Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new Builder(this)
            .retryPredicate(predicate)
            .build();
    }

    /**
     * Returns a new policy with a maximum delay cap.
     */
    public RetryPolicy withMaxDelay(Duration maxDelay) {
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        return new Builder(this)
            .maxDelay(maxDelay)
            .build();
    }

    // ===== Builder =====

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private double backoffMultiplier = 1.0;
        private Duration maxDelay = Duration.ofMinutes(1);
        private boolean useJitter = false;
        private Predicate<Throwable> retryPredicate = _ -> true;

        private Builder() {}

        private Builder(RetryPolicy source) {
            this.maxAttempts = source.maxAttempts;
            this.initialDelay = source.initialDelay;
            this.backoffMultiplier = source.backoffMultiplier;
            this.maxDelay = source.maxDelay;
            this.useJitter = source.useJitter;
            this.retryPredicate = source.retryPredicate;
        }

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = Objects.requireNonNull(initialDelay);
            return this;
        }

        public Builder backoffMultiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            }
            this.backoffMultiplier = multiplier;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = Objects.requireNonNull(maxDelay);
            return this;
        }

        public Builder useJitter(boolean useJitter) {
            this.useJitter = useJitter;
            return this;
        }

        public Builder retryPredicate(Predicate<Throwable> predicate) {
            this.retryPredicate = Objects.requireNonNull(predicate);
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "RetryPolicy{maxAttempts=%d, initialDelay=%s, backoff=%.1f, jitter=%s}",
            maxAttempts, initialDelay, backoffMultiplier, useJitter);
    }
}
```

### D2: Retry Utility

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/Retry.java`

```java
package org.higherkindedj.hkt.resilience;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility for executing operations with retry policies.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1));
 *
 * String result = Retry.execute(policy, () -> httpClient.get(url));
 * }</pre>
 */
public final class Retry {

    private Retry() {}

    /**
     * Executes a supplier with the given retry policy.
     *
     * @param policy the retry policy
     * @param supplier the operation to execute
     * @param <A> the result type
     * @return the successful result
     * @throws RetryExhaustedException if all retries are exhausted
     */
    public static <A> A execute(RetryPolicy policy, Supplier<A> supplier) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        Throwable lastException = null;

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                if (attempt > 1) {
                    Thread.sleep(policy.delayForAttempt(attempt).toMillis());
                }
                return supplier.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RetryExhaustedException("Retry interrupted", e);
            } catch (Throwable t) {
                lastException = t;
                if (!policy.shouldRetry(t) || attempt >= policy.maxAttempts()) {
                    break;
                }
            }
        }

        throw new RetryExhaustedException(
            String.format("Retry exhausted after %d attempts", policy.maxAttempts()),
            lastException);
    }

    /**
     * Executes a runnable with the given retry policy.
     */
    public static void execute(RetryPolicy policy, Runnable runnable) {
        execute(policy, () -> {
            runnable.run();
            return null;
        });
    }
}
```

### D3: RetryExhaustedException

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/resilience/RetryExhaustedException.java`

```java
package org.higherkindedj.hkt.resilience;

/**
 * Exception thrown when all retry attempts have been exhausted.
 */
public class RetryExhaustedException extends RuntimeException {

    public RetryExhaustedException(String message) {
        super(message);
    }

    public RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### D4: IOPath.withRetry

**Location**: Add to `IOPath.java`

```java
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.resilience.Retry;

// ===== Resilience =====

/**
 * Returns an IOPath that retries on failure according to the policy.
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1));
 * IOPath<Data> resilient = fetchData().withRetry(policy);
 * }</pre>
 *
 * @param policy the retry policy
 * @return an IOPath with retry behavior
 */
public IOPath<A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return IOPath.delay(() -> Retry.execute(policy, this::unsafeRun));
}

/**
 * Convenience method for simple retry with default exponential backoff.
 *
 * @param maxAttempts maximum number of attempts
 * @return an IOPath with retry behavior
 */
public IOPath<A> retry(int maxAttempts) {
    return withRetry(RetryPolicy.exponentialBackoff(maxAttempts, java.time.Duration.ofMillis(100)));
}
```

### D5: CompletableFuturePath.withRetry

**Location**: Add to `CompletableFuturePath.java`

```java
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.resilience.Retry;

/**
 * Returns a path that retries on failure according to the policy.
 */
public CompletableFuturePath<A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return CompletableFuturePath.supplyAsync(() -> Retry.execute(policy, this::join));
}

/**
 * Convenience method for simple retry.
 */
public CompletableFuturePath<A> retry(int maxAttempts) {
    return withRetry(RetryPolicy.exponentialBackoff(maxAttempts, java.time.Duration.ofMillis(100)));
}
```

**Tests**: `RetryPolicyTest.java`, `RetryTest.java`, `IOPathResilienceTest.java`

---

## Phase 4e: Testing Completion

### E1: PathSourceProcessorTest (Carry-forward)

**File**: `hkj-processor/src/test/java/org/higherkindedj/hkt/processing/effect/PathSourceProcessorTest.java`

Complete the deferred annotation processor tests:

```java
package org.higherkindedj.hkt.processing.effect;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for PathSourceProcessor annotation processing.
 */
@DisplayName("PathSourceProcessor Tests")
class PathSourceProcessorTest {

    @Nested
    @DisplayName("Basic Code Generation")
    class BasicCodeGenerationTests {

        @Test
        @DisplayName("generates Path class for simple type")
        void generatesPathForSimpleType() {
            JavaFileObject source = JavaFileObjects.forSourceString(
                "test.SimpleResult",
                """
                package test;

                import org.higherkindedj.hkt.effect.annotation.PathSource;

                @PathSource(witness = SimpleResultKind.Witness.class)
                public sealed interface SimpleResult<A> permits Success, Failure {
                }
                """
            );

            Compilation compilation = javac()
                .withProcessors(new PathSourceProcessor())
                .compile(source);

            assertThat(compilation).succeeded();
            assertThat(compilation)
                .generatedSourceFile("test.SimpleResultPath")
                .isNotNull();
        }

        @Test
        @DisplayName("generates Recoverable for type with errorType")
        void generatesRecoverableForErrorType() {
            // Test that errorType triggers Recoverable implementation
        }

        @Test
        @DisplayName("respects custom suffix")
        void respectsCustomSuffix() {
            // Test custom suffix configuration
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("reports error for missing witness")
        void reportsErrorForMissingWitness() {
            // Test error reporting
        }
    }
}
```

---

## Testing Strategy

### Test Categories

| Category | Pattern | Purpose |
|----------|---------|---------|
| **Unit Tests** | `*Test.java` | Core functionality |
| **Property Tests** | `*PropertyTest.java` | Law verification via jqwik |
| **Laws Tests** | `*LawsTest.java` | Monad/Functor law compliance |
| **Integration Tests** | `*IntegrationTest.java` | Cross-component behavior |

### New Test Files

```
hkj-core/src/test/java/org/higherkindedj/hkt/effect/
├── TrampolinePathTest.java
├── TrampolinePathPropertyTest.java
├── TrampolinePathLawsTest.java
├── FreePathTest.java
├── FreePathPropertyTest.java
├── FreePathLawsTest.java
├── FreeApPathTest.java
├── FreeApPathPropertyTest.java
├── IOPathResourceTest.java
├── IOPathParallelTest.java
├── IOPathResilienceTest.java
├── CompletableFuturePathParallelTest.java
├── PathOpsParallelTest.java

hkj-core/src/test/java/org/higherkindedj/hkt/resilience/
├── RetryPolicyTest.java
├── RetryTest.java

hkj-processor/src/test/java/org/higherkindedj/hkt/processing/effect/
├── PathSourceProcessorTest.java
```

### Test Coverage Requirements

| Component | Line Coverage | Branch Coverage |
|-----------|---------------|-----------------|
| TrampolinePath | ≥ 90% | ≥ 85% |
| FreePath | ≥ 90% | ≥ 85% |
| FreeApPath | ≥ 90% | ≥ 85% |
| Resource methods | ≥ 95% | ≥ 90% |
| Parallel methods | ≥ 90% | ≥ 85% |
| RetryPolicy | ≥ 95% | ≥ 90% |
| Retry | ≥ 95% | ≥ 90% |

---

## Documentation Requirements

### Javadoc

All new public API must have comprehensive Javadoc including:
- Purpose and use cases
- Example code snippets
- Parameter/return documentation
- Exception documentation
- Cross-references to related types

### hkj-book Updates

**New Chapter**: `hkj-book/src/effect/advanced_topics.md`

```markdown
# Advanced Effect Path Topics

## Stack-Safe Recursion with TrampolinePath

TrampolinePath provides stack-safe recursive computations...

### Example: Stack-Safe Factorial

```java
TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
    if (n.compareTo(BigInteger.ONE) <= 0) {
        return TrampolinePath.done(acc);
    }
    return TrampolinePath.defer(() ->
        factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
}

// Safe for any input size
BigInteger result = factorial(BigInteger.valueOf(100000), BigInteger.ONE).run();
```

## Building DSLs with FreePath

FreePath enables building domain-specific languages with deferred interpretation...

## Resource Management

### The bracket Pattern

### Using withResource

### Guarantee for Cleanup

## Parallel Execution

### parZipWith for Binary Parallelism

### parSequence for List Parallelism

### Racing Computations

## Resilience Patterns

### Retry Policies

### Configuring Backoff

### Handling Specific Exceptions
```

**Update existing chapters**:
- `path_types.md` - Add TrampolinePath, FreePath, FreeApPath
- `composition.md` - Add parallel composition section
- `patterns.md` - Add resilience patterns section

---

## Examples

### New Example Files

**File**: `hkj-examples/src/main/java/org/higherkindedj/example/effect/TrampolinePathExample.java`

```java
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.TrampolinePath;
import java.math.BigInteger;

/**
 * Examples demonstrating TrampolinePath for stack-safe recursion.
 */
public class TrampolinePathExample {

    public static void main(String[] args) {
        factorialExample();
        fibonacciExample();
        mutualRecursionExample();
    }

    static void factorialExample() {
        System.out.println("=== Factorial Example ===");

        // This would overflow the stack with normal recursion
        BigInteger n = BigInteger.valueOf(10000);
        BigInteger result = factorial(n, BigInteger.ONE).run();

        System.out.println("10000! has " + result.toString().length() + " digits");
    }

    static TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
        if (n.compareTo(BigInteger.ONE) <= 0) {
            return TrampolinePath.done(acc);
        }
        return TrampolinePath.defer(() ->
            factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
    }

    static void fibonacciExample() {
        System.out.println("\n=== Fibonacci Example ===");

        BigInteger result = fibonacci(1000).run();
        System.out.println("fib(1000) = " + result);
    }

    static TrampolinePath<BigInteger> fibonacci(int n) {
        return fibHelper(n, BigInteger.ZERO, BigInteger.ONE);
    }

    static TrampolinePath<BigInteger> fibHelper(int n, BigInteger a, BigInteger b) {
        if (n <= 0) {
            return TrampolinePath.done(a);
        }
        return TrampolinePath.defer(() -> fibHelper(n - 1, b, a.add(b)));
    }

    static void mutualRecursionExample() {
        System.out.println("\n=== Mutual Recursion Example ===");

        boolean result = isEven(1000000).run();
        System.out.println("isEven(1000000) = " + result);
    }

    static TrampolinePath<Boolean> isEven(int n) {
        if (n == 0) return TrampolinePath.done(true);
        return TrampolinePath.defer(() -> isOdd(n - 1));
    }

    static TrampolinePath<Boolean> isOdd(int n) {
        if (n == 0) return TrampolinePath.done(false);
        return TrampolinePath.defer(() -> isEven(n - 1));
    }
}
```

**File**: `hkj-examples/src/main/java/org/higherkindedj/example/effect/ResourceManagementExample.java`

```java
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import java.io.*;
import java.nio.file.*;

/**
 * Examples demonstrating resource management patterns.
 */
public class ResourceManagementExample {

    public static void main(String[] args) {
        bracketExample();
        withResourceExample();
        guaranteeExample();
    }

    static void bracketExample() {
        System.out.println("=== Bracket Example ===");

        IOPath<String> readFile = IOPath.bracket(
            () -> {
                System.out.println("Acquiring resource...");
                return new BufferedReader(new StringReader("Hello, World!"));
            },
            reader -> {
                System.out.println("Using resource...");
                return reader.readLine();
            },
            reader -> {
                System.out.println("Releasing resource...");
                try { reader.close(); } catch (IOException e) { }
            }
        );

        String content = readFile.unsafeRun();
        System.out.println("Read: " + content);
    }

    static void withResourceExample() {
        System.out.println("\n=== WithResource Example ===");

        // Using try-with-resources style API
        IOPath<String> readConfig = IOPath.withResource(
            () -> new ByteArrayInputStream("config=value".getBytes()),
            stream -> new String(stream.readAllBytes())
        );

        String config = readConfig.unsafeRun();
        System.out.println("Config: " + config);
    }

    static void guaranteeExample() {
        System.out.println("\n=== Guarantee Example ===");

        IOPath<String> computation = Path.io(() -> {
                System.out.println("Running computation...");
                return "result";
            })
            .guarantee(() -> System.out.println("Cleanup complete!"));

        String result = computation.unsafeRun();
        System.out.println("Result: " + result);
    }
}
```

**File**: `hkj-examples/src/main/java/org/higherkindedj/example/effect/ParallelExecutionExample.java`

```java
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.Path;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Examples demonstrating parallel execution patterns.
 */
public class ParallelExecutionExample {

    public static void main(String[] args) {
        parZipExample();
        parSequenceExample();
        raceExample();
    }

    static void parZipExample() {
        System.out.println("=== Parallel Zip Example ===");

        IOPath<String> fetchUser = Path.io(() -> {
            Thread.sleep(100);
            return "User123";
        });

        IOPath<String> fetchProfile = Path.io(() -> {
            Thread.sleep(100);
            return "Profile456";
        });

        long start = System.currentTimeMillis();

        // These run in parallel
        IOPath<String> combined = fetchUser.parZipWith(
            fetchProfile,
            (user, profile) -> user + " + " + profile
        );

        String result = combined.unsafeRun();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Result: " + result);
        System.out.println("Time: " + elapsed + "ms (should be ~100ms, not 200ms)");
    }

    static void parSequenceExample() {
        System.out.println("\n=== Parallel Sequence Example ===");

        List<IOPath<Integer>> tasks = IntStream.range(1, 6)
            .mapToObj(i -> Path.io(() -> {
                Thread.sleep(50);
                return i * 10;
            }))
            .toList();

        long start = System.currentTimeMillis();

        IOPath<List<Integer>> allResults = PathOps.parSequenceIO(tasks);
        List<Integer> results = allResults.unsafeRun();

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Results: " + results);
        System.out.println("Time: " + elapsed + "ms (should be ~50ms, not 250ms)");
    }

    static void raceExample() {
        System.out.println("\n=== Race Example ===");

        IOPath<String> slow = Path.io(() -> {
            Thread.sleep(200);
            return "Slow";
        });

        IOPath<String> fast = Path.io(() -> {
            Thread.sleep(50);
            return "Fast";
        });

        IOPath<String> winner = slow.race(fast);
        String result = winner.unsafeRun();

        System.out.println("Winner: " + result);
    }
}
```

**File**: `hkj-examples/src/main/java/org/higherkindedj/example/effect/ResilienceExample.java`

```java
package org.higherkindedj.example.effect;

import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Examples demonstrating resilience patterns.
 */
public class ResilienceExample {

    public static void main(String[] args) {
        basicRetryExample();
        exponentialBackoffExample();
        selectiveRetryExample();
    }

    static void basicRetryExample() {
        System.out.println("=== Basic Retry Example ===");

        AtomicInteger attempts = new AtomicInteger(0);

        IOPath<String> flaky = Path.io(() -> {
            int attempt = attempts.incrementAndGet();
            System.out.println("Attempt " + attempt);
            if (attempt < 3) {
                throw new RuntimeException("Simulated failure");
            }
            return "Success on attempt " + attempt;
        }).retry(5);

        String result = flaky.unsafeRun();
        System.out.println("Result: " + result);
    }

    static void exponentialBackoffExample() {
        System.out.println("\n=== Exponential Backoff Example ===");

        RetryPolicy policy = RetryPolicy.exponentialBackoff(4, Duration.ofMillis(100))
            .withMaxDelay(Duration.ofSeconds(2));

        System.out.println("Policy: " + policy);
        System.out.println("Delay for attempt 1: " + policy.delayForAttempt(1));
        System.out.println("Delay for attempt 2: " + policy.delayForAttempt(2));
        System.out.println("Delay for attempt 3: " + policy.delayForAttempt(3));
        System.out.println("Delay for attempt 4: " + policy.delayForAttempt(4));
    }

    static void selectiveRetryExample() {
        System.out.println("\n=== Selective Retry Example ===");

        RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(50))
            .retryOn(IOException.class);

        AtomicInteger attempts = new AtomicInteger(0);

        IOPath<String> selectiveRetry = Path.io(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new IOException("Retryable");
            }
            return "Success";
        }).withRetry(policy);

        String result = selectiveRetry.unsafeRun();
        System.out.println("Result: " + result + " (after " + attempts.get() + " attempts)");
    }
}
```

---

## Success Criteria

### Functional

- [ ] TrampolinePath compiles and passes all tests
- [ ] FreePath compiles and passes all tests
- [ ] FreeApPath compiles and passes all tests
- [ ] IOPath.bracket/withResource/guarantee work correctly
- [ ] parZipWith runs computations in parallel
- [ ] parSequence parallelizes list of paths
- [ ] race returns first completed result
- [ ] RetryPolicy calculates delays correctly
- [ ] Retry executes with proper backoff
- [ ] withRetry integrates with IOPath and FuturePath

### Quality

- [ ] ≥90% line coverage for new code
- [ ] All property-based law tests pass
- [ ] All monad/functor law tests pass
- [ ] No breaking changes to Phase 1-3 API
- [ ] Comprehensive Javadoc

### Process

- [ ] PathSourceProcessorTest complete
- [ ] All design decisions documented
- [ ] Implementation matches design
- [ ] PR review completed
- [ ] hkj-book updated
- [ ] Examples in hkj-examples

---

## Phase 5+ Forward Look

### Phase 5: Monad Transformers

| Path Type | Underlying | Notes |
|-----------|------------|-------|
| MaybeTPath | MaybeT | Requires outer monad |
| EitherTPath | EitherT | Requires outer monad + error type |
| OptionalTPath | OptionalT | Requires outer monad |
| ReaderTPath | ReaderT | Requires outer monad + env type |
| StateTPath | StateT | Requires outer monad + state type |

### Phase 6: FocusPath Integration

| Feature | Purpose |
|---------|---------|
| focus(Lens) | Bridge method on paths |
| modifyF | Effectful lens modification |
| focusMaybe(Affine) | Partial extraction |
| Path + Lens composition | Full integration |

---

## Appendix: File Structure

```
hkj-core/src/main/java/org/higherkindedj/hkt/
├── effect/
│   ├── TrampolinePath.java           # NEW
│   ├── FreePath.java                 # NEW
│   ├── FreeApPath.java               # NEW
│   ├── IOPath.java                   # UPDATED (resource, parallel, retry)
│   ├── CompletableFuturePath.java    # UPDATED (parallel, retry)
│   ├── PathOps.java                  # UPDATED (parallel utilities)
│   ├── Path.java                     # UPDATED (new factories)
│   └── (existing files)
├── resilience/
│   ├── RetryPolicy.java              # NEW
│   ├── Retry.java                    # NEW
│   └── RetryExhaustedException.java  # NEW

hkj-core/src/test/java/org/higherkindedj/hkt/
├── effect/
│   ├── TrampolinePathTest.java
│   ├── TrampolinePathPropertyTest.java
│   ├── TrampolinePathLawsTest.java
│   ├── FreePathTest.java
│   ├── FreePathPropertyTest.java
│   ├── FreePathLawsTest.java
│   ├── FreeApPathTest.java
│   ├── FreeApPathPropertyTest.java
│   ├── IOPathResourceTest.java
│   ├── IOPathParallelTest.java
│   ├── IOPathResilienceTest.java
│   ├── CompletableFuturePathParallelTest.java
│   └── PathOpsParallelTest.java
├── resilience/
│   ├── RetryPolicyTest.java
│   └── RetryTest.java

hkj-processor/src/test/java/org/higherkindedj/hkt/processing/effect/
└── PathSourceProcessorTest.java

hkj-book/src/effect/
├── advanced_topics.md                # NEW
├── path_types.md                     # UPDATED
├── composition.md                    # UPDATED
└── patterns.md                       # UPDATED

hkj-examples/src/main/java/org/higherkindedj/example/effect/
├── TrampolinePathExample.java        # NEW
├── FreePathExample.java              # NEW
├── ResourceManagementExample.java    # NEW
├── ParallelExecutionExample.java     # NEW
└── ResilienceExample.java            # NEW
```

---

## Checklist Summary

### Phase 4a: Tier 3 Path Types
- [ ] TrampolinePath
- [ ] FreePath
- [ ] FreeApPath
- [ ] Path factory methods

### Phase 4b: Resource Management
- [ ] IOPath.bracket
- [ ] IOPath.bracketIO
- [ ] IOPath.withResource
- [ ] IOPath.withResourceIO
- [ ] IOPath.guarantee
- [ ] IOPath.guaranteeIO

### Phase 4c: Parallel Execution
- [ ] IOPath.parZipWith
- [ ] IOPath.race
- [ ] CompletableFuturePath.parZipWith
- [ ] CompletableFuturePath.race
- [ ] PathOps.parSequenceIO
- [ ] PathOps.parSequenceFuture
- [ ] PathOps.parZip3
- [ ] PathOps.parZip4
- [ ] PathOps.raceIO

### Phase 4d: Resilience
- [ ] RetryPolicy
- [ ] RetryPolicy.Builder
- [ ] Retry utility
- [ ] RetryExhaustedException
- [ ] IOPath.withRetry
- [ ] IOPath.retry
- [ ] CompletableFuturePath.withRetry
- [ ] CompletableFuturePath.retry

### Phase 4e: Testing
- [ ] PathSourceProcessorTest (carry-forward)
- [ ] All new component unit tests
- [ ] All new component property tests
- [ ] Law tests for path types

### Documentation
- [ ] Javadoc for all new public API
- [ ] hkj-book advanced_topics.md
- [ ] hkj-book updates to existing chapters
- [ ] Examples in hkj-examples

---

*End of Phase 4 Implementation Plan*
