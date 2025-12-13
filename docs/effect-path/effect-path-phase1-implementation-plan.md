# EffectPath Phase 1: Complete Implementation Plan

> **Status**: ✅ COMPLETE (v1.0)
> **Last Updated**: 2025-12-13
> **Scope**: MaybePath, EitherPath, TryPath, IOPath + Capability Interfaces
> **Java Baseline**: Java 25 (RELEASE_25)
> **Target Module**: `hkj-core`

---

## Table of Contents

1. [Naming Decision: Effect DSL vs Effect Path API](#naming-decision)
2. [Scope Definition](#scope-definition)
3. [Module Structure](#module-structure)
4. [Implementation Tasks](#implementation-tasks)
5. [Testing Strategy](#testing-strategy)
6. [Documentation Plan](#documentation-plan)
7. [Examples Catalog](#examples-catalog)
8. [Task Breakdown](#task-breakdown)
9. [Acceptance Criteria](#acceptance-criteria)

---

## Naming Decision

### Question: DSL or API?

| Option | Full Name | Rationale |
|--------|-----------|-----------|
| **Effect Path** | Effect Path API | Mirrors "Focus Path" from optics; emphasizes navigation/composition |
| **Effect DSL** | Effect Domain-Specific Language | Emphasizes fluent syntax; suggests specialized vocabulary |
| **Path API** | Path API | Simple, generic; might conflict with java.nio.file.Path |
| **Flow API** | Flow API | Suggests data flow; conflicts with java.util.concurrent.Flow |
| **Pipeline API** | Pipeline API | Suggests transformation chains; generic |

### Recommendation: **Effect Path API** (or just **Path API** within HKJ context)

**Rationale:**

1. **Consistency with Focus DSL**: The optics module uses "Focus Path" (`FocusPath`, `AffinePath`, `TraversalPath`). Using "Effect Path" creates a clear parallel:
   - Focus Path → navigating **data structures**
   - Effect Path → navigating **effect types**

2. **API vs DSL Distinction**:
   - **DSL** implies a mini-language with its own grammar/vocabulary
   - **API** implies a programmatic interface
   - Effect Path is more API than DSL — it provides fluent methods, not a new syntax

3. **Path Semantics**:
   - "Path" suggests a journey through transformations
   - `MaybePath<A>` reads as "a path through Maybe effects carrying A"
   - Method `via` suggests "travel via this function"

4. **Discoverability**: Users familiar with FocusPath will immediately understand EffectPath

### Alternative Names for Consideration

| Name | Example Class | Pros | Cons |
|------|---------------|------|------|
| `MaybePath<A>` | `Path.maybe(value)` | Consistent with Focus | "Path" overloaded in Java |
| `MaybeFlow<A>` | `Flow.maybe(value)` | Clear metaphor | Conflicts with j.u.c.Flow |
| `MaybeChain<A>` | `Chain.maybe(value)` | Suggests composition | Less intuitive |
| `MaybeEffect<A>` | `Effect.maybe(value)` | Direct naming | "Effect" is abstract |
| `MaybeContext<A>` | `Context.maybe(value)` | FP terminology | Too abstract for Java devs |

### Final Recommendation

**Primary**: `MaybePath<A>`, `EitherPath<E, A>`, etc. with factory `Path.maybe()`

**Package**: `org.higherkindedj.hkt.path`

**Documentation Title**: "Effect Path API" or "Path API for Effects"

---

## Scope Definition

### In Scope (Phase 1)

| Component | Description |
|-----------|-------------|
| **Capability Interfaces** | `Composable`, `Combinable`, `Chainable`, `Recoverable`, `Effectful` |
| **Core Path Types** | `MaybePath<A>`, `EitherPath<E, A>`, `TryPath<A>`, `IOPath<A>` |
| **Factory Class** | `Path` static factory with `maybe()`, `either()`, `tryOf()`, `io()` methods |
| **Test Coverage** | Unit tests, property tests, law verification (100% line coverage target) |
| **Documentation** | Book chapter, Javadoc, README section |
| **Examples** | Runnable examples in `hkj-examples` module |

### Out of Scope (Deferred)

| Component | Deferred To |
|-----------|-------------|
| `ValidatedPath<E, A>` | Phase 2 (requires Accumulating interface) |
| `IdPath<A>`, `OptionalPath<A>` | Phase 2 |
| `ReaderPath<R, A>`, `StatePath<S, A>` | Phase 3 |
| Annotation processor `@PathSource` | Phase 3 |
| Service bridge generation | Phase 3 |
| `GenericPath<F, A>` | Phase 4 |
| Monad transformer paths | Phase 4+ |

### Success Criteria

1. All Phase 1 path types pass their typeclass law tests
2. 100% line coverage on new code
3. Zero breaking changes to existing HKJ code
4. Documentation chapter published in hkj-book
5. At least 5 runnable examples in hkj-examples

---

## Module Structure

### Package Layout

```
hkj-core/src/main/java/org/higherkindedj/hkt/path/
├── package-info.java                  # Package documentation
│
├── capability/                        # Capability interfaces
│   ├── package-info.java
│   ├── Composable.java               # Functor operations
│   ├── Combinable.java               # Applicative operations
│   ├── Chainable.java                # Monad operations
│   ├── Recoverable.java              # Error recovery operations
│   └── Effectful.java                # Effect execution operations
│
├── Path.java                         # Static factory class
├── MaybePath.java                    # Maybe wrapper path
├── EitherPath.java                   # Either wrapper path
├── TryPath.java                      # Try wrapper path
├── IOPath.java                       # IO wrapper path
│
└── internal/                         # Internal implementation details
    ├── package-info.java
    └── PathValidation.java           # Input validation utilities
```

### Test Package Layout

```
hkj-core/src/test/java/org/higherkindedj/hkt/path/
├── MaybePathTest.java                # Core functionality tests
├── MaybePathPropertyTest.java        # Property-based tests (jQwik)
├── MaybePathLawsTest.java            # Functor/Monad law verification
├── EitherPathTest.java
├── EitherPathPropertyTest.java
├── EitherPathLawsTest.java
├── TryPathTest.java
├── TryPathPropertyTest.java
├── TryPathLawsTest.java
├── IOPathTest.java
├── IOPathPropertyTest.java
├── PathFactoryTest.java              # Path factory tests
├── PathIntegrationTest.java          # Cross-type conversion tests
│
├── capability/                       # Capability contract tests
│   ├── ComposableContractTest.java
│   ├── CombinableContractTest.java
│   ├── ChainableContractTest.java
│   └── RecoverableContractTest.java
│
├── assertions/                       # Custom AssertJ assertions
│   ├── PathAssertions.java           # Static import facade
│   ├── MaybePathAssert.java
│   ├── EitherPathAssert.java
│   ├── TryPathAssert.java
│   └── IOPathAssert.java
│
└── fixtures/                         # Test fixtures
    ├── TestDomain.java               # Domain objects
    ├── ArbitraryProviders.java       # jQwik providers
    └── PathTestHelpers.java          # Common utilities
```

---

## Implementation Tasks

### Task 1: Capability Interfaces

#### 1.1 Composable Interface (Functor)

```java
/**
 * Capability interface for types that support mapping (Functor operations).
 *
 * <p>Composable provides the ability to transform the value inside an effect
 * context without changing the structure of the effect itself.
 *
 * @param <A> The type of value in this composable context
 */
public sealed interface Composable<A>
    permits Combinable, MaybePath, EitherPath, TryPath, IOPath {

    /**
     * Transforms the value inside this context using the given function.
     *
     * <p>This is the fundamental Functor operation. The function is only applied
     * if a value is present (for Maybe-like types) or if the computation succeeds
     * (for Either/Try-like types).
     *
     * @param f the mapping function
     * @param <B> the result type
     * @return a new Composable with the transformed value
     */
    <B> Composable<B> map(Function<? super A, ? extends B> f);

    /**
     * Performs a side-effect on the value without transforming it.
     *
     * <p>Useful for debugging, logging, or other observational operations.
     *
     * @param action the action to perform
     * @return this composable, unchanged
     */
    default Composable<A> peek(Consumer<? super A> action) {
        return map(a -> { action.accept(a); return a; });
    }
}
```

#### 1.2 Combinable Interface (Applicative)

```java
/**
 * Capability interface for independent combination (Applicative operations).
 *
 * <p>Combinable extends Composable with the ability to combine multiple
 * independent computations. Unlike Chainable, which sequences computations,
 * Combinable combines computations that don't depend on each other's results.
 *
 * @param <A> The type of value in this combinable context
 */
public sealed interface Combinable<A> extends Composable<A>
    permits Chainable {

    /**
     * Combines this value with another using a combining function.
     *
     * <p>Both computations are independent - neither depends on the other's result.
     * The combining function is only called if both computations succeed.
     *
     * @param other the other combinable to combine with
     * @param f the combining function
     * @param <B> the type of the other value
     * @param <C> the result type
     * @return a new Combinable with the combined result
     */
    <B, C> Combinable<C> zipWith(Combinable<B> other, BiFunction<A, B, C> f);

    /**
     * Combines this value with another into a tuple.
     *
     * @param other the other combinable
     * @param <B> the type of the other value
     * @return a new Combinable containing a tuple of both values
     */
    default <B> Combinable<Tuple2<A, B>> zip(Combinable<B> other) {
        return zipWith(other, Tuple2::of);
    }

    /**
     * Combines three values using a combining function.
     */
    default <B, C, D> Combinable<D> zipWith3(
            Combinable<B> pb,
            Combinable<C> pc,
            Function3<A, B, C, D> f) {
        return zipWith(pb, Tuple2::of)
            .zipWith(pc, (ab, c) -> f.apply(ab.first(), ab.second(), c));
    }

    /**
     * Alias for zipWith, using Applicative terminology.
     */
    default <B, C> Combinable<C> map2(Combinable<B> other, BiFunction<A, B, C> f) {
        return zipWith(other, f);
    }
}
```

#### 1.3 Chainable Interface (Monad)

```java
/**
 * Capability interface for sequential composition (Monad operations).
 *
 * <p>Chainable extends Combinable with the ability to sequence computations
 * where each step can depend on the result of the previous step.
 *
 * @param <A> The type of value in this chainable context
 */
public sealed interface Chainable<A> extends Combinable<A>
    permits Recoverable, Effectful, MaybePath, EitherPath, TryPath, IOPath {

    /**
     * Sequences this computation with another that depends on its result.
     *
     * <p>The function {@code f} is only called if this computation produces a value.
     * This is the fundamental monadic bind operation.
     *
     * <p><b>Note:</b> The function returns the underlying effect type (e.g., Maybe),
     * not the Path type. This allows seamless integration with existing code.
     *
     * @param f the function producing the next computation
     * @param <B> the result type
     * @return a new Chainable with the result of the sequenced computation
     */
    <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> f);

    /**
     * Alias for via, using traditional Monad terminology.
     */
    default <B> Chainable<B> flatMap(Function<? super A, ? extends Chainable<B>> f) {
        return via(f);
    }

    /**
     * Sequences this computation with another, discarding this result.
     *
     * @param next the next computation
     * @param <B> the type of the next computation's result
     * @return the next computation
     */
    default <B> Chainable<B> then(Chainable<B> next) {
        return via(_ -> next);
    }
}
```

#### 1.4 Recoverable Interface (MonadError)

```java
/**
 * Capability interface for error recovery operations.
 *
 * <p>Recoverable extends Chainable with operations for handling and recovering
 * from errors or absent values.
 *
 * @param <E> The error type
 * @param <A> The success value type
 */
public sealed interface Recoverable<E, A> extends Chainable<A>
    permits MaybePath, EitherPath, TryPath {

    /**
     * Recovers from an error by providing an alternative value.
     *
     * @param defaultValue the value to use if this computation fails
     * @return a new path with the recovered value
     */
    Recoverable<E, A> orElse(A defaultValue);

    /**
     * Recovers from an error using a supplier.
     *
     * @param supplier the supplier of the alternative value
     * @return a new path with the recovered value
     */
    Recoverable<E, A> orElseGet(Supplier<? extends A> supplier);

    /**
     * Recovers from an error using an alternative computation.
     *
     * @param alternative the alternative path to use if this fails
     * @return this path if successful, otherwise the alternative
     */
    Recoverable<E, A> orElsePath(Recoverable<E, A> alternative);

    /**
     * Recovers from a specific error using a recovery function.
     *
     * @param recovery the function to apply to the error
     * @return a new path with the recovered value
     */
    Recoverable<E, A> recover(Function<? super E, ? extends A> recovery);

    /**
     * Recovers from a specific error using an alternative computation.
     *
     * @param recovery the function producing the alternative computation
     * @return a new path with the recovered value
     */
    Recoverable<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> recovery);

    /**
     * Transforms the error type.
     *
     * @param f the error transformation function
     * @param <E2> the new error type
     * @return a new path with the transformed error
     */
    <E2> Recoverable<E2, A> mapError(Function<? super E, ? extends E2> f);

    /**
     * Filters the value, producing an error if the predicate fails.
     *
     * @param predicate the predicate to test
     * @param errorSupplier supplies the error if the predicate fails
     * @return this path if the predicate passes, an error path otherwise
     */
    Recoverable<E, A> filterOrElse(Predicate<? super A> predicate, Supplier<? extends E> errorSupplier);
}
```

#### 1.5 Effectful Interface (IO semantics)

```java
/**
 * Capability interface for types with deferred execution semantics.
 *
 * <p>Effectful represents computations that are not executed until explicitly
 * requested. This enables lazy evaluation and referential transparency.
 *
 * @param <A> The type of value produced when the effect is executed
 */
public sealed interface Effectful<A> extends Chainable<A>
    permits IOPath {

    /**
     * Executes this effect and returns the result.
     *
     * <p><b>Warning:</b> This method performs side effects. Use with caution.
     *
     * @return the result of executing this effect
     */
    A unsafeRun();

    /**
     * Executes this effect, wrapping any exception in a Try.
     *
     * @return a Try containing the result or the exception
     */
    default Try<A> runSafe() {
        return Try.of(this::unsafeRun);
    }

    /**
     * Converts this effectful computation to an IOPath.
     *
     * @return an IOPath wrapping this computation
     */
    IOPath<A> toIOPath();
}
```

### Task 2: Path Types

#### 2.1 MaybePath

```java
/**
 * A fluent path wrapper for Maybe effects.
 *
 * <p>MaybePath provides a chainable, discoverable API for working with optional
 * values. It wraps the underlying {@link Maybe} type while exposing operations
 * through capability interfaces.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * MaybePath<Integer> path = Path.maybe(42)
 *     .map(x -> x * 2)
 *     .via(x -> x > 50 ? Path.maybe(x) : Path.nothing())
 *     .peek(System.out::println);
 *
 * int result = path.getOrElse(0);
 * }</pre>
 *
 * @param <A> The type of value in this path
 */
public final class MaybePath<A> implements Recoverable<Unit, A> {

    private final Maybe<A> underlying;

    private MaybePath(Maybe<A> underlying) {
        this.underlying = requireNonNull(underlying, "underlying Maybe cannot be null");
    }

    // ===== Factory Methods =====

    public static <A> MaybePath<A> just(A value) {
        return new MaybePath<>(Maybe.just(value));
    }

    public static <A> MaybePath<A> nothing() {
        return new MaybePath<>(Maybe.nothing());
    }

    public static <A> MaybePath<A> of(Maybe<A> maybe) {
        return new MaybePath<>(maybe);
    }

    public static <A> MaybePath<A> fromNullable(A value) {
        return new MaybePath<>(Maybe.fromNullable(value));
    }

    public static <A> MaybePath<A> fromOptional(Optional<A> optional) {
        return new MaybePath<>(Maybe.fromOptional(optional));
    }

    // ===== Composable Implementation =====

    @Override
    public <B> MaybePath<B> map(Function<? super A, ? extends B> f) {
        return new MaybePath<>(underlying.map(f));
    }

    @Override
    public MaybePath<A> peek(Consumer<? super A> action) {
        if (underlying.isJust()) {
            action.accept(underlying.get());
        }
        return this;
    }

    // ===== Combinable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B, C> MaybePath<C> zipWith(Combinable<B> other, BiFunction<A, B, C> f) {
        if (!(other instanceof MaybePath<?> otherMaybe)) {
            throw new IllegalArgumentException("Can only combine with another MaybePath");
        }
        var typedOther = (MaybePath<B>) otherMaybe;
        return new MaybePath<>(
            underlying.flatMap(a ->
                typedOther.underlying.map(b -> f.apply(a, b)))
        );
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> MaybePath<B> via(Function<? super A, ? extends Chainable<B>> f) {
        return new MaybePath<>(
            underlying.flatMap(a -> {
                Chainable<B> result = f.apply(a);
                if (result instanceof MaybePath<?> maybePath) {
                    return ((MaybePath<B>) maybePath).underlying;
                }
                throw new IllegalArgumentException("via function must return MaybePath");
            })
        );
    }

    // Convenience: via that accepts Maybe directly
    public <B> MaybePath<B> via(Function<? super A, ? extends Maybe<B>> f) {
        return new MaybePath<>(underlying.flatMap(f));
    }

    // ===== Recoverable Implementation =====

    @Override
    public MaybePath<A> orElse(A defaultValue) {
        return underlying.isJust() ? this : just(defaultValue);
    }

    @Override
    public MaybePath<A> orElseGet(Supplier<? extends A> supplier) {
        return underlying.isJust() ? this : just(supplier.get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public MaybePath<A> orElsePath(Recoverable<Unit, A> alternative) {
        if (!(alternative instanceof MaybePath<?> maybePath)) {
            throw new IllegalArgumentException("alternative must be MaybePath");
        }
        return underlying.isJust() ? this : (MaybePath<A>) maybePath;
    }

    @Override
    public MaybePath<A> recover(Function<? super Unit, ? extends A> recovery) {
        return underlying.isJust() ? this : just(recovery.apply(Unit.INSTANCE));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MaybePath<A> recoverWith(Function<? super Unit, ? extends Recoverable<Unit, A>> recovery) {
        if (underlying.isJust()) {
            return this;
        }
        Recoverable<Unit, A> recovered = recovery.apply(Unit.INSTANCE);
        if (recovered instanceof MaybePath<?> maybePath) {
            return (MaybePath<A>) maybePath;
        }
        throw new IllegalArgumentException("recovery must return MaybePath");
    }

    @Override
    public <E2> EitherPath<E2, A> mapError(Function<? super Unit, ? extends E2> f) {
        return underlying.isJust()
            ? EitherPath.right(underlying.get())
            : EitherPath.left(f.apply(Unit.INSTANCE));
    }

    @Override
    public MaybePath<A> filterOrElse(Predicate<? super A> predicate, Supplier<? extends Unit> errorSupplier) {
        return underlying.isJust() && predicate.test(underlying.get())
            ? this
            : nothing();
    }

    // Simpler filter without error
    public MaybePath<A> filter(Predicate<? super A> predicate) {
        return filterOrElse(predicate, () -> Unit.INSTANCE);
    }

    // ===== Terminal Operations =====

    public Maybe<A> run() {
        return underlying;
    }

    public A getOrElse(A defaultValue) {
        return underlying.orElse(defaultValue);
    }

    public A getOrElseGet(Supplier<? extends A> supplier) {
        return underlying.orElseGet(supplier);
    }

    public A getOrThrow() {
        return underlying.get();
    }

    public <X extends Throwable> A getOrThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (underlying.isJust()) {
            return underlying.get();
        }
        throw exceptionSupplier.get();
    }

    public Optional<A> toOptional() {
        return underlying.isJust() ? Optional.of(underlying.get()) : Optional.empty();
    }

    public boolean isPresent() {
        return underlying.isJust();
    }

    public boolean isEmpty() {
        return underlying.isNothing();
    }

    // ===== Conversion Methods =====

    public EitherPath<Unit, A> toEitherPath() {
        return underlying.isJust()
            ? EitherPath.right(underlying.get())
            : EitherPath.left(Unit.INSTANCE);
    }

    public <E> EitherPath<E, A> toEitherPath(E error) {
        return underlying.isJust()
            ? EitherPath.right(underlying.get())
            : EitherPath.left(error);
    }

    public <E> EitherPath<E, A> toEitherPath(Supplier<E> errorSupplier) {
        return underlying.isJust()
            ? EitherPath.right(underlying.get())
            : EitherPath.left(errorSupplier.get());
    }

    public TryPath<A> toTryPath() {
        return underlying.isJust()
            ? TryPath.success(underlying.get())
            : TryPath.failure(new NoSuchElementException("Nothing"));
    }

    // ===== Debugging =====

    public MaybePath<A> traced(String label) {
        return peek(a -> System.out.println("[" + label + "] Just: " + a))
            .orElseGet(() -> {
                System.out.println("[" + label + "] Nothing");
                return null; // Won't be used, just for side effect
            });
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MaybePath<?> other)) return false;
        return underlying.equals(other.underlying);
    }

    @Override
    public int hashCode() {
        return underlying.hashCode();
    }

    @Override
    public String toString() {
        return "MaybePath[" + underlying + "]";
    }
}
```

#### 2.2 EitherPath, TryPath, IOPath

Similar implementations following the same patterns. Key differences:

| Type | Error Type | Additional Features |
|------|------------|---------------------|
| `EitherPath<E, A>` | Generic `E` | `mapLeft()`, `swap()`, Bifunctor ops |
| `TryPath<A>` | `Throwable` | `recover(Class<X>, Function)`, exception handling |
| `IOPath<A>` | N/A | Lazy execution, `unsafeRun()`, `runSafe()` |

### Task 3: Path Factory

```java
/**
 * Static factory for creating Path instances.
 *
 * <p>This class provides a unified entry point for the Effect Path API.
 * It follows the same pattern as the Focus DSL entry points.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Creating paths
 * MaybePath<String> maybe = Path.maybe("hello");
 * EitherPath<Error, User> either = Path.right(user);
 * TryPath<Integer> tryPath = Path.tryOf(() -> Integer.parseInt(str));
 * IOPath<String> io = Path.io(() -> readFile(path));
 *
 * // Lifting values
 * MaybePath<Integer> fromNullable = Path.maybeNullable(possiblyNull);
 * EitherPath<String, Integer> fromValidation = Path.fromValidation(result);
 * }</pre>
 */
public final class Path {

    private Path() {} // Utility class

    // ===== Maybe Paths =====

    public static <A> MaybePath<A> maybe(A value) {
        return MaybePath.just(value);
    }

    public static <A> MaybePath<A> nothing() {
        return MaybePath.nothing();
    }

    public static <A> MaybePath<A> maybeNullable(A value) {
        return MaybePath.fromNullable(value);
    }

    public static <A> MaybePath<A> maybeOptional(Optional<A> optional) {
        return MaybePath.fromOptional(optional);
    }

    public static <A> MaybePath<A> maybe(Maybe<A> maybe) {
        return MaybePath.of(maybe);
    }

    // ===== Either Paths =====

    public static <E, A> EitherPath<E, A> right(A value) {
        return EitherPath.right(value);
    }

    public static <E, A> EitherPath<E, A> left(E error) {
        return EitherPath.left(error);
    }

    public static <E, A> EitherPath<E, A> either(Either<E, A> either) {
        return EitherPath.of(either);
    }

    public static <E, A> EitherPath<E, A> fromMaybe(Maybe<A> maybe, E error) {
        return maybe.isJust()
            ? EitherPath.right(maybe.get())
            : EitherPath.left(error);
    }

    // ===== Try Paths =====

    public static <A> TryPath<A> success(A value) {
        return TryPath.success(value);
    }

    public static <A> TryPath<A> failure(Throwable error) {
        return TryPath.failure(error);
    }

    public static <A> TryPath<A> tryOf(ThrowingSupplier<A> supplier) {
        return TryPath.of(supplier);
    }

    public static <A> TryPath<A> tryPath(Try<A> tryValue) {
        return TryPath.of(tryValue);
    }

    // ===== IO Paths =====

    public static <A> IOPath<A> io(Supplier<A> computation) {
        return IOPath.of(computation);
    }

    public static <A> IOPath<A> io(IO<A> ioValue) {
        return IOPath.of(ioValue);
    }

    public static <A> IOPath<A> ioLazy(Supplier<A> computation) {
        return IOPath.defer(computation);
    }

    public static IOPath<Unit> ioRun(Runnable action) {
        return IOPath.fromRunnable(action);
    }

    // ===== Utilities =====

    @FunctionalInterface
    public interface ThrowingSupplier<A> {
        A get() throws Throwable;
    }
}
```

---

## Testing Strategy

### Test Categories

| Category | Purpose | Tool | Coverage Target |
|----------|---------|------|-----------------|
| **Unit Tests** | Core functionality | JUnit 5 | 100% lines |
| **Property Tests** | Invariant verification | jQwik | Key properties |
| **Law Tests** | Typeclass law compliance | JUnit 5 + TestFactory | All laws |
| **Contract Tests** | Capability interface contracts | JUnit 5 | All capabilities |
| **Integration Tests** | Cross-type conversions | JUnit 5 | All conversions |
| **Performance Tests** | Wrapper overhead | JUnit 5 | Baseline established |

### Law Verification Tests

Each path type must verify:

#### Functor Laws
```java
@TestFactory
Stream<DynamicTest> functorLaws() {
    return Stream.of(
        dynamicTest("Identity: path.map(id) == path", () -> {
            var path = MaybePath.just(42);
            assertThat(path.map(Function.identity()).run())
                .isEqualTo(path.run());
        }),
        dynamicTest("Composition: path.map(f).map(g) == path.map(g.compose(f))", () -> {
            var path = MaybePath.just(42);
            Function<Integer, Integer> f = x -> x + 1;
            Function<Integer, Integer> g = x -> x * 2;
            assertThat(path.map(f).map(g).run())
                .isEqualTo(path.map(f.andThen(g)).run());
        })
    );
}
```

#### Monad Laws
```java
@TestFactory
Stream<DynamicTest> monadLaws() {
    return Stream.of(
        dynamicTest("Left Identity: Path.just(a).via(f) == f(a)", () -> {
            int a = 42;
            Function<Integer, MaybePath<Integer>> f = x -> MaybePath.just(x * 2);
            assertThat(MaybePath.just(a).via(f).run())
                .isEqualTo(f.apply(a).run());
        }),
        dynamicTest("Right Identity: path.via(Path::just) == path", () -> {
            var path = MaybePath.just(42);
            assertThat(path.via(MaybePath::just).run())
                .isEqualTo(path.run());
        }),
        dynamicTest("Associativity: (path.via(f)).via(g) == path.via(x -> f(x).via(g))", () -> {
            var path = MaybePath.just(42);
            Function<Integer, MaybePath<Integer>> f = x -> MaybePath.just(x + 1);
            Function<Integer, MaybePath<Integer>> g = x -> MaybePath.just(x * 2);
            assertThat(path.via(f).via(g).run())
                .isEqualTo(path.via(x -> f.apply(x).via(g)).run());
        })
    );
}
```

### Property-Based Tests with jQwik

```java
@Property
@Label("Functor identity holds for all MaybePaths")
void functorIdentity(@ForAll("maybePaths") MaybePath<Integer> path) {
    assertThat(path.map(Function.identity()).run())
        .isEqualTo(path.run());
}

@Provide
Arbitrary<MaybePath<Integer>> maybePaths() {
    return Arbitraries.integers().between(-1000, 1000)
        .injectNull(0.2)
        .map(i -> i == null ? MaybePath.nothing() : MaybePath.just(i));
}
```

### Custom Assertions

```java
public final class PathAssertions {
    public static <A> MaybePathAssert<A> assertThatPath(MaybePath<A> actual) {
        return new MaybePathAssert<>(actual);
    }
    // ... other path types
}

// Usage in tests
import static org.higherkindedj.hkt.path.assertions.PathAssertions.*;

assertThatPath(maybePath).isJust().hasValue("expected");
assertThatPath(eitherPath).isRight().hasRightValue(42);
assertThatPath(tryPath).isSuccess().hasValue("result");
```

### Test Coverage Requirements

| Component | Line Coverage | Branch Coverage |
|-----------|---------------|-----------------|
| Capability interfaces | 100% | 100% |
| MaybePath | 100% | 100% |
| EitherPath | 100% | 100% |
| TryPath | 100% | 100% |
| IOPath | 100% | 100% |
| Path factory | 100% | 100% |

**Note:** 100% branch coverage is required for all components. This ensures all conditional paths are tested.

---

## Documentation Plan

### Book Chapter Structure

New section: **"Effect Path API"** (NEW section between "Monads in Practice" and "Optics I")

```markdown
# Effect Path API: Fluent Effect Composition

## _Type-Safe Navigation Through Effect Types_

~~~admonish info title="What You'll Learn"
- How to compose effect types (Maybe, Either, Try, IO) with fluent chains
- The capability interface hierarchy (Composable, Combinable, Chainable, Recoverable)
- Converting between effect types seamlessly
- Error recovery patterns
- Integration with existing higher-kinded-j types
~~~

~~~admonish title="Example Code"
[BasicEffectPathExample](link) | [ErrorHandlingExample](link) | [ChainedComputationsExample](link)
~~~
```

### Chapter Sections

1. **Introduction**
   - The Problem: Verbose effect composition
   - The Solution: Effect Path API
   - Relationship to Focus DSL

2. **Quick Start**
   - Creating paths
   - Basic transformations
   - Terminal operations

3. **The Capability Hierarchy**
   - Composable (Functor)
   - Combinable (Applicative)
   - Chainable (Monad)
   - Recoverable (Error handling)
   - Effectful (IO)

4. **Path Types**
   - MaybePath
   - EitherPath
   - TryPath
   - IOPath

5. **Composition Patterns**
   - Sequential composition with `via`
   - Independent combination with `zipWith`
   - Error recovery with `recover`/`orElse`

6. **Converting Between Types**
   - Maybe ↔ Either ↔ Try conversions
   - Lifting to IO
   - Extracting values

7. **Real-World Examples**
   - Service layer composition
   - Validation pipelines
   - Database operations with error handling

8. **Service Bridges** (Phase 2)
   - `@GeneratePathBridge` annotation
   - `@PathVia` for method marking
   - Generated code patterns

9. **Custom Effects** (Phase 3)
   - `@PathSource` for library authors
   - Extending the Path hierarchy

### Proposed SUMMARY.md Structure

```markdown
# Monads in Practice
- [Introduction](monads/ch_intro.md)
  - [Maybe](monads/maybe_monad.md)
  - [Either](monads/either_monad.md)
  - [Try](monads/try_monad.md)
  - [IO](monads/io_monad.md)
  - ... (other monads)

# Effect Path API  ← NEW SECTION
- [Introduction](effects/ch_intro.md)
  - [Effect Path Overview](effects/effect_path_overview.md)
  - [Capability Interfaces](effects/capabilities.md)
  - [Path Types](effects/path_types.md)
  - [Composition Patterns](effects/composition.md)
  - [Service Bridges](effects/service_bridges.md)    ← Phase 2
  - [Custom Effects](effects/custom_effects.md)      ← Phase 3
  - [Patterns and Recipes](effects/patterns.md)

# Optics I: Fundamentals
- [Introduction](optics/ch1_intro.md)
  ...
```

**Rationale for placement:**
- After "Monads in Practice" so users understand Maybe, Either, Try, IO
- Before "Optics" because Effect Path is simpler than optics
- Creates natural learning progression: Types → Effects → Data Navigation

10. **Best Practices**
    - When to use Path vs raw types
    - Error handling strategies
   - Performance considerations

---

## Examples Catalog

### Location: `hkj-examples/src/main/java/org/higherkindedj/example/path/`

| Example | Description | Demonstrates |
|---------|-------------|--------------|
| `BasicPathExample.java` | Introduction to Path API | `map`, `via`, `getOrElse` |
| `ChainedComputationsExample.java` | Sequential effect composition | `via`, `flatMap`, `then` |
| `ErrorHandlingExample.java` | Error recovery patterns | `recover`, `orElse`, `recoverWith` |
| `ValidationPipelineExample.java` | Form validation with paths | `zipWith`, `map2`, error combination |
| `ServiceLayerExample.java` | Repository pattern with paths | IO composition, error handling |
| `ConversionExample.java` | Converting between path types | `toEitherPath`, `toTryPath` |
| `DebugTracingExample.java` | Debugging path chains | `traced`, `peek` |

### Example: BasicPathExample.java

```java
package org.higherkindedj.example.path;

import org.higherkindedj.hkt.path.*;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Basic examples of the Effect Path API.
 *
 * <p>This example demonstrates:
 * <ul>
 *   <li>Creating paths from values</li>
 *   <li>Transforming values with map</li>
 *   <li>Chaining computations with via</li>
 *   <li>Extracting values with terminal operations</li>
 * </ul>
 */
public class BasicPathExample {

    public static void main(String[] args) {
        // Creating paths
        MaybePath<String> greeting = Path.maybe("Hello");
        MaybePath<String> empty = Path.nothing();

        // Transforming with map
        MaybePath<Integer> length = greeting.map(String::length);
        System.out.println("Length: " + length.getOrElse(0)); // 5

        // Chaining with via
        MaybePath<String> result = greeting
            .map(String::toUpperCase)
            .via(s -> s.length() > 3 ? Path.maybe(s) : Path.nothing())
            .map(s -> s + "!");

        System.out.println("Result: " + result.getOrElse("(empty)")); // HELLO!

        // Working with empty paths
        String emptyResult = empty
            .map(String::toUpperCase)
            .getOrElse("default");
        System.out.println("Empty result: " + emptyResult); // default

        // Converting to Either
        EitherPath<String, String> either = greeting.toEitherPath("was empty");
        System.out.println("Either: " + either.run()); // Right[Hello]
    }
}
```

### Example: ValidationPipelineExample.java

```java
package org.higherkindedj.example.path;

import org.higherkindedj.hkt.path.*;
import java.util.function.BiFunction;

/**
 * Demonstrates validation pipelines using Effect Paths.
 */
public class ValidationPipelineExample {

    record User(String name, String email, int age) {}

    public static void main(String[] args) {
        // Independent validations combined with zipWith
        MaybePath<String> validName = validateName("Alice");
        MaybePath<String> validEmail = validateEmail("alice@example.com");
        MaybePath<Integer> validAge = validateAge(25);

        // Combine all validations (fails fast on first Nothing)
        MaybePath<User> user = validName.zipWith3(
            validEmail,
            validAge,
            User::new
        );

        System.out.println("User: " + user.run());

        // With error messages using EitherPath
        EitherPath<String, String> name = validateNameE("Alice");
        EitherPath<String, String> email = validateEmailE("invalid-email");
        EitherPath<String, Integer> age = validateAgeE(25);

        EitherPath<String, User> userE = name.zipWith3(
            email,
            age,
            User::new
        );

        userE.run().fold(
            error -> System.out.println("Validation failed: " + error),
            u -> System.out.println("Valid user: " + u)
        );
    }

    static MaybePath<String> validateName(String name) {
        return name != null && name.length() >= 2
            ? Path.maybe(name)
            : Path.nothing();
    }

    static MaybePath<String> validateEmail(String email) {
        return email != null && email.contains("@")
            ? Path.maybe(email)
            : Path.nothing();
    }

    static MaybePath<Integer> validateAge(int age) {
        return age >= 0 && age <= 150
            ? Path.maybe(age)
            : Path.nothing();
    }

    static EitherPath<String, String> validateNameE(String name) {
        return name != null && name.length() >= 2
            ? Path.right(name)
            : Path.left("Name must be at least 2 characters");
    }

    static EitherPath<String, String> validateEmailE(String email) {
        return email != null && email.contains("@")
            ? Path.right(email)
            : Path.left("Invalid email format");
    }

    static EitherPath<String, Integer> validateAgeE(int age) {
        return age >= 0 && age <= 150
            ? Path.right(age)
            : Path.left("Age must be between 0 and 150");
    }
}
```

---

## Task Breakdown

### Phase 1 Implementation Checklist

#### Week 1: Foundation

| Task ID | Task | Est. Hours | Dependencies |
|---------|------|------------|--------------|
| P1-001 | Create `path` package structure | 1 | - |
| P1-002 | Implement `Composable` interface | 2 | P1-001 |
| P1-003 | Implement `Combinable` interface | 2 | P1-002 |
| P1-004 | Implement `Chainable` interface | 2 | P1-003 |
| P1-005 | Implement `Recoverable` interface | 3 | P1-004 |
| P1-006 | Implement `Effectful` interface | 2 | P1-004 |
| P1-007 | Write capability interface tests | 4 | P1-002 to P1-006 |

#### Week 2: Core Path Types

| Task ID | Task | Est. Hours | Dependencies |
|---------|------|------------|--------------|
| P1-008 | Implement `MaybePath` | 4 | P1-005 |
| P1-009 | Write `MaybePath` unit tests | 3 | P1-008 |
| P1-010 | Write `MaybePath` property tests | 2 | P1-008 |
| P1-011 | Write `MaybePath` law tests | 2 | P1-008 |
| P1-012 | Implement `EitherPath` | 4 | P1-005 |
| P1-013 | Write `EitherPath` tests (all types) | 5 | P1-012 |
| P1-014 | Implement `TryPath` | 4 | P1-005 |
| P1-015 | Write `TryPath` tests (all types) | 5 | P1-014 |

#### Week 3: IO and Factory

| Task ID | Task | Est. Hours | Dependencies |
|---------|------|------------|--------------|
| P1-016 | Implement `IOPath` | 4 | P1-006 |
| P1-017 | Write `IOPath` tests (all types) | 5 | P1-016 |
| P1-018 | Implement `Path` factory | 2 | P1-008, P1-012, P1-014, P1-016 |
| P1-019 | Write `Path` factory tests | 2 | P1-018 |
| P1-020 | Implement custom assertions | 3 | P1-008 to P1-016 |
| P1-021 | Write integration tests | 4 | All path types |

#### Week 4: Documentation and Examples

| Task ID | Task | Est. Hours | Dependencies |
|---------|------|------------|--------------|
| P1-022 | Write Javadoc for all public APIs | 4 | All implementations |
| P1-023 | Create book chapter outline | 2 | - |
| P1-024 | Write book chapter content | 6 | P1-023 |
| P1-025 | Create `BasicPathExample` | 2 | P1-018 |
| P1-026 | Create `ChainedComputationsExample` | 2 | P1-018 |
| P1-027 | Create `ErrorHandlingExample` | 2 | P1-018 |
| P1-028 | Create `ValidationPipelineExample` | 2 | P1-018 |
| P1-029 | Create `ServiceLayerExample` | 3 | P1-018 |
| P1-030 | Final review and coverage check | 4 | All tasks |

**Total Estimated Hours**: ~90 hours

---

## Acceptance Criteria

### Code Quality

- [x] All public APIs have complete Javadoc
- [x] No compiler warnings
- [x] Spotless formatting passes
- [x] All null-safety annotations in place

### Test Coverage

- [x] 100% line coverage on capability interfaces
- [x] 100% line coverage on path types
- [x] All Functor laws verified for each path type
- [x] All Monad laws verified for each path type
- [x] Property-based tests for key invariants
- [x] Integration tests for all type conversions

### Documentation

- [x] Book chapter complete and reviewed (7 chapters in hkj-book/src/effect/)
- [x] All examples compile and run (5 examples in hkj-examples)
- [x] SUMMARY.md updated with new chapter
- [x] package-info.java for all packages

### Integration

- [x] No breaking changes to existing APIs
- [x] Builds successfully with CI
- [x] All existing tests pass
- [x] Examples module compiles

### Performance

- [ ] Path wrapper overhead < 2x raw type operations
- [ ] No unexpected memory allocations in hot paths

---

## Appendix: Naming Alternatives Summary

For final decision, here are the naming options:

| Choice | Package | Classes | Factory | Recommendation |
|--------|---------|---------|---------|----------------|
| **Effect Path** | `o.h.hkt.path` | `MaybePath`, `EitherPath` | `Path.maybe()` | **Recommended** |
| Effect Flow | `o.h.hkt.flow` | `MaybeFlow`, `EitherFlow` | `Flow.maybe()` | Conflicts with j.u.c |
| Effect Chain | `o.h.hkt.chain` | `MaybeChain`, `EitherChain` | `Chain.maybe()` | Less intuitive |
| Effect Context | `o.h.hkt.context` | `MaybeContext`, etc. | `Context.maybe()` | Too abstract |

**Recommendation**: Use **"Effect Path API"** with `Path` factory and `*Path` classes to maintain consistency with the Focus DSL pattern already established in the codebase.
