# EffectPath API: Complete Type Coverage Analysis

> **Status**: Living Document (v1.0)
> **Last Updated**: 2025-01-15
> **Scope**: Comprehensive analysis of all higher-kinded-j types and their mapping to capability interfaces
> **Java Baseline**: Java 25 (RELEASE_25)

## Executive Summary

This document analyzes how the proposed capability-based interface design maps to **all 29 core types** in higher-kinded-j, with specific attention to:

1. **Applicative** (independent combination)
2. **Selective** (conditional execution)
3. **Foldable** (reduction to summary values)
4. **Traverse** (effectful mapping with structure preservation)

## Java 25 Baseline Clarification

**CRITICAL**: This project targets Java 25, not Java 21.

```kotlin
// From build.gradle.kts
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

**Required Update**: All annotation processor references in design documents must use:
```java
@SupportedSourceVersion(SourceVersion.RELEASE_25)
```

This enables use of:
- Pattern matching enhancements
- Unnamed variables (`_` in lambdas)
- Sequenced collections
- Virtual threads (for IO/Future paths)
- Scoped values (for Reader-like contexts)

---

## Complete Type Catalog

### Category 1: Core Sum Types (Right-Biased)

| Type | Witness | Typeclasses | Path Priority | Complexity |
|------|---------|-------------|---------------|------------|
| `Maybe<A>` | `MaybeKind.Witness` | Functor, Applicative, Monad, Selective, Traverse | **Phase 1** | Low |
| `Either<E, A>` | `EitherKind.Witness<E>` | Functor, Applicative, Monad, Bifunctor, Selective, Traverse | **Phase 1** | Medium |
| `Try<A>` | `TryKind.Witness` | Functor, Applicative, Monad, Traverse | **Phase 1** | Medium |
| `Validated<E, A>` | `ValidatedKind.Witness<E>` | Functor, Applicative, Monad, Bifunctor, Selective, Traverse | **Phase 2** | Medium |

### Category 2: Effect Types

| Type | Witness | Typeclasses | Path Priority | Complexity |
|------|---------|-------------|---------------|------------|
| `IO<A>` | `IOKind.Witness` | Functor, Applicative, Monad, Selective | **Phase 2** | Medium |
| `Reader<R, A>` | `ReaderKind.Witness<R>` | Functor, Applicative, Monad, Selective | Phase 3 | High |
| `State<S, A>` | `StateKind.Witness<S>` | Functor, Applicative, Monad | Phase 3 | High |
| `Writer<W, A>` | `WriterKind.Witness<W>` | Functor, Applicative, Monad, Bifunctor | Phase 3 | High |
| `Lazy<A>` | `LazyKind.Witness` | Functor, Monad | Phase 3 | Medium |
| `Id<A>` | `IdKind.Witness` | Functor, Applicative, Monad, Selective, Traverse | Phase 2 | Low |

### Category 3: Monad Transformers

| Type | Witness | Base Requirement | Path Priority | Complexity |
|------|---------|------------------|---------------|------------|
| `StateT<S, F, A>` | `StateTKind.Witness<S, F>` | `Monad<F>` | Phase 4+ | Very High |
| `ReaderT<F, R, A>` | `ReaderTKind.Witness<F, R>` | `Monad<F>` | Phase 4+ | Very High |
| `EitherT<F, E, A>` | `EitherTKind.Witness<F, E>` | `Monad<F>` | Phase 4+ | Very High |
| `MaybeT<F, A>` | `MaybeTKind.Witness<F>` | `Monad<F>` | Phase 4+ | Very High |
| `OptionalT<F, A>` | `OptionalTKind.Witness<F>` | `Monad<F>` | Phase 4+ | Very High |

### Category 4: Free Structures

| Type | Witness | Typeclasses | Path Priority | Complexity |
|------|---------|-------------|---------------|------------|
| `Free<F, A>` | `FreeKind.Witness<F>` | Functor, Monad | Phase 4+ | Very High |
| `FreeAp<F, A>` | `FreeApKind.Witness<F>` | Functor, Applicative | Phase 4+ | Very High |

### Category 5: Utility Types

| Type | Witness | Typeclasses | Path Priority | Complexity |
|------|---------|-------------|---------------|------------|
| `Trampoline<A>` | `TrampolineKind.Witness` | Functor, Monad | N/A (internal) | Medium |
| `Coyoneda<F, A>` | `CoyonedaKind.Witness<F>` | Functor | N/A (internal) | High |
| `Const<C, A>` | `ConstKind.Witness<C>` | Applicative, Bifunctor | N/A (internal) | Medium |

### Category 6: Java Stdlib Wrappers

| Type | Witness | Typeclasses | Path Priority | Complexity |
|------|---------|-------------|---------------|------------|
| `CompletableFuture<A>` | `CompletableFutureKind.Witness` | Functor, Applicative, Monad | Phase 3 | Medium |
| `Stream<A>` | `StreamKind.Witness` | Functor, Applicative, Monad, Traverse | Phase 3 | Medium |
| `Optional<A>` | `OptionalKind.Witness` | Functor, Applicative, Monad, Selective, Traverse | Phase 2 | Low |

### Category 7: Product Types

| Type | Witness | Typeclasses | Path Priority | Complexity |
|------|---------|-------------|---------------|------------|
| `Tuple2<A, B>` | `Tuple2Kind2.Witness` | Bifunctor | N/A | Low |
| `Function<A, B>` | `FunctionKind.Witness` | Profunctor | N/A | N/A |

---

## Capability Interface Coverage Analysis

### Current Proposed Hierarchy

```
Composable<A>                        (Functor: map, peek)
    │
    ├── Chainable<A>                 (Monad: via, flatMap)
    │       │
    │       ├── Recoverable<E, A>    (MonadError: recover, mapError)
    │       │       │
    │       │       ├── MaybePath<A>
    │       │       ├── EitherPath<E, A>
    │       │       ├── TryPath<A>
    │       │       └── ValidatedPath<E, A>
    │       │
    │       └── Effectful<A>         (IO semantics: run)
    │               │
    │               └── IOPath<A>
    │
    └── GenericPath<F, A>            (Escape hatch)
```

### Gap Analysis: Missing Capability Interfaces

#### 1. Combinable (Applicative)

**Status**: MISSING - Critical Gap

**Problem**: `map2`/`map3` exist on concrete types but not as a capability interface.

**Required Interface**:
```java
public sealed interface Combinable<A> extends Composable<A>
    permits Chainable, ValidatedPath, GenericPath {

    /**
     * Combines two paths using a combining function.
     * This enables applicative-style independent combination.
     */
    <B, C> Combinable<C> zipWith(Combinable<B> other, BiFunction<A, B, C> f);

    /**
     * Combines two paths into a tuple.
     */
    default <B> Combinable<Tuple2<A, B>> zip(Combinable<B> other) {
        return zipWith(other, Tuple2::of);
    }

    /**
     * Combines three paths.
     */
    default <B, C, D> Combinable<D> zipWith3(
            Combinable<B> pb,
            Combinable<C> pc,
            Function3<A, B, C, D> f) {
        return zipWith(pb, Tuple2::of)
            .zipWith(pc, (ab, c) -> f.apply(ab.first(), ab.second(), c));
    }
}
```

**Type Coverage**:
| Type | Combinable Support | Notes |
|------|-------------------|-------|
| MaybePath | Yes | Short-circuit on Nothing |
| EitherPath | Yes | Short-circuit on Left |
| TryPath | Yes | Short-circuit on Failure |
| ValidatedPath | **Yes (CRITICAL)** | Error accumulation via Semigroup |
| IOPath | Yes | Sequenced effects |
| ReaderPath | Yes | Environment sharing |
| StatePath | Yes | State threading |

**ValidatedPath Special Case**: ValidatedPath MUST support `Combinable` for error accumulation:
```java
// Error accumulation - ALL errors collected
ValidatedPath<List<Error>, User> user = Path.validated(formData)
    .zipWith(validateName(formData), User::withName)
    .zipWith(validateEmail(formData), User::withEmail)
    .zipWith(validateAge(formData), User::withAge);
// If ANY validation fails, ALL errors are accumulated
```

#### 2. Selective

**Status**: PARTIALLY MISSING - Advanced Feature

**Assessment**: Selective is already implemented as a typeclass in `hkj-api`. The question is whether to expose it in the Path API.

**Recommendation**: **Defer to Phase 4+**

**Rationale**:
1. Selective is a niche use case for most Java developers
2. The primary benefit (static analysis of branches) is less relevant in Java
3. Can be accessed via `GenericPath` for advanced users

**However**, provide escape hatch:
```java
// On GenericPath<F, A> where F has Selective
public <B> GenericPath<F, B> ifS(
        GenericPath<F, Boolean> condition,
        GenericPath<F, B> thenBranch,
        GenericPath<F, B> elseBranch) {
    return new GenericPath<>(
        selective.ifS(condition.run(), thenBranch.run(), elseBranch.run()),
        monad
    );
}
```

**Types with Selective Support**:
- Maybe, Either, Validated, Id, Optional - via their Selective instances
- IO, Reader - via their Selective instances

#### 3. Foldable

**Status**: IMPLICIT - No Interface Needed

**Assessment**: Terminal operations on paths ARE foldable operations in disguise.

**Current Coverage**:
```java
// These ARE folds:
path.getOrElse(default)           // foldLeft with identity/default
path.getOrElse(() -> compute())   // foldLeft with lazy default
path.fold(onEmpty, onValue)       // explicit fold
path.toOptional()                 // fold to Optional
path.stream()                     // fold to Stream
path.run()                        // extract underlying effect
```

**Recommendation**: **No separate Foldable interface**

**Rationale**:
1. Path types have at most ONE value (Maybe, Either, Try, IO)
2. Foldable is more relevant for collection types (List, Tree)
3. Terminal operations already provide fold semantics
4. Adding explicit `foldLeft`/`foldRight` adds no value for single-value containers

**Exception - Future ListPath/StreamPath**:
```java
// If we add ListPath in Phase 4+
public sealed interface Collectable<A> extends Composable<A>
    permits ListPath, StreamPath {

    <B> B foldLeft(B initial, BiFunction<B, A, B> f);
    <B> B foldRight(B initial, BiFunction<A, B, B> f);
    default List<A> toList() { ... }
    default boolean isEmpty() { ... }
    default long count() { ... }
}
```

#### 4. Traverse

**Status**: MISSING - Requires HKT Exposure

**Problem**: Traverse fundamentally requires higher-kinded types:
```java
// Traverse signature requires Kind<F, _>
<F, B> Kind<F, Path<B>> traverse(
    Applicative<F> applicative,
    Function<A, Kind<F, B>> f
);
```

**This breaks the "hide HKT complexity" principle.**

**Mitigation Strategies**:

**Strategy A: Concrete Overloads** (Recommended for Phase 3)
```java
// On MaybePath<A>
public <B> MaybePath<List<B>> traverseList(Function<A, List<B>> f);
public <E, B> EitherPath<E, List<B>> traverseEither(Function<A, Either<E, B>> f);
public <B> TryPath<List<B>> traverseTry(Function<A, Try<B>> f);

// On ListPath<A> (future)
public <B> MaybePath<List<B>> traverseMaybe(Function<A, Maybe<B>> f);
public <E, B> EitherPath<E, List<B>> traverseEither(Function<A, Either<E, B>> f);
```

**Strategy B: GenericPath with Traverse** (For advanced users)
```java
// Full HKT-aware traverse only on GenericPath
public <G, B> GenericPath<G, GenericPath<F, B>> traverse(
        Traverse<F> traverse,
        Applicative<G> applicative,
        Function<A, Kind<G, B>> f) {
    Kind<G, Kind<F, B>> result = traverse.traverse(applicative, f, value);
    return new GenericPath<>(applicative.map(k -> new GenericPath<>(k, monad), result), ???);
}
```

**Strategy C: Sequence Shortcuts** (For common patterns)
```java
// Static helpers for common traversals
public final class PathTraverse {
    // List<Maybe<A>> -> Maybe<List<A>>
    public static <A> MaybePath<List<A>> sequenceMaybe(List<MaybePath<A>> paths);

    // List<Either<E, A>> -> Either<E, List<A>>
    public static <E, A> EitherPath<E, List<A>> sequenceEither(List<EitherPath<E, A>> paths);

    // List<Try<A>> -> Try<List<A>>
    public static <A> TryPath<List<A>> sequenceTry(List<TryPath<A>> paths);
}
```

---

## Extended Capability Hierarchy (Revised)

```
Composable<A>                           (Functor: map, peek)
    │
    ├── Combinable<A>                   (Applicative: zipWith, zip, map2, map3)
    │       │
    │       ├── Chainable<A>            (Monad: via, flatMap)
    │       │       │
    │       │       ├── Recoverable<E, A>  (MonadError: recover, mapError)
    │       │       │       │
    │       │       │       ├── MaybePath<A>
    │       │       │       ├── EitherPath<E, A>
    │       │       │       ├── TryPath<A>
    │       │       │       └── ValidatedPath<E, A>  (also Accumulating)
    │       │       │
    │       │       └── Effectful<A>       (IO semantics: run)
    │       │               │
    │       │               └── IOPath<A>
    │       │
    │       └── Accumulating<E, A>         (Error accumulation, no short-circuit)
    │               │
    │               └── ValidatedPath<E, A>
    │
    └── GenericPath<F, A>                  (Escape hatch for HKT-aware operations)
```

---

## Type-to-Capability Mapping Matrix

| Type | Composable | Combinable | Chainable | Recoverable | Accumulating | Effectful | Notes |
|------|------------|------------|-----------|-------------|--------------|-----------|-------|
| **MaybePath** | Yes | Yes | Yes | Yes | No | No | Short-circuit on Nothing |
| **EitherPath** | Yes | Yes | Yes | Yes | No | No | Short-circuit on Left |
| **TryPath** | Yes | Yes | Yes | Yes | No | No | Exception handling |
| **ValidatedPath** | Yes | Yes | Yes* | Yes | **Yes** | No | *via toEither conversion |
| **IOPath** | Yes | Yes | Yes | No | No | **Yes** | Lazy evaluation |
| **IdPath** | Yes | Yes | Yes | No | No | No | Trivial wrapper |
| **OptionalPath** | Yes | Yes | Yes | Yes | No | No | Java stdlib bridge |
| **ReaderPath** | Yes | Yes | Yes | No | No | Yes | Environment access |
| **StatePath** | Yes | Yes | Yes | No | No | Yes | State threading |
| **WriterPath** | Yes | Yes | Yes | No | Yes* | No | *Log accumulation |
| **FuturePath** | Yes | Yes | Yes | Yes | No | Yes | Async effects |
| **StreamPath** | Yes | Yes | Yes | No | No | No | Lazy sequences |
| **GenericPath** | Yes | Partial | Yes | Partial | Partial | Partial | HKT escape hatch |

---

## Pain Points and Mitigation Strategies

### Pain Point 1: Traverse Requires HKT

**Problem**: `traverse` signature exposes `Kind<F, _>`.

**Mitigation**:
1. **Phase 3**: Provide concrete overloads (`traverseMaybe`, `traverseEither`, etc.)
2. **Phase 4**: Full HKT traverse only on `GenericPath`
3. **Documentation**: Explain when to "break glass" and use GenericPath

### Pain Point 2: ValidatedPath Has Dual Nature

**Problem**: ValidatedPath needs BOTH `Chainable` (for sequencing) AND `Accumulating` (for validation).

**Mitigation**:
```java
// ValidatedPath implements BOTH interfaces
public final class ValidatedPath<E, A>
    implements Chainable<A>, Accumulating<E, A>, Recoverable<E, A> {

    // Chainable: short-circuit (like Either)
    @Override
    public <B> ValidatedPath<E, B> via(Function<A, Chainable<B>> f) { ... }

    // Accumulating: error collection (unique to Validated)
    @Override
    public <B, C> ValidatedPath<E, C> zipWith(
            Accumulating<E, B> other,
            BiFunction<A, B, C> f) { ... }

    // Bridge between modes
    public EitherPath<E, A> toEitherPath() { ... }
}
```

### Pain Point 3: Monad Transformers Are Complex

**Problem**: StateT, ReaderT, EitherT require outer monad evidence.

**Mitigation**:
1. **Defer to Phase 4+**: Not needed for typical Java use cases
2. **GenericPath**: Advanced users can use `GenericPath<StateTKind.Witness<S, F>, A>`
3. **Specialized Combinations**: Pre-built combinations like `EitherIO<E, A>` = `EitherT<IOKind.Witness, E, A>`

### Pain Point 4: Free Structures Need Interpreters

**Problem**: Free/FreeAp require `foldMap` with `Natural<F, G>` transformations.

**Mitigation**:
1. **Defer to Phase 4+**: Highly advanced feature
2. **DSL-specific paths**: Generate purpose-built paths for specific DSLs
3. **GenericPath escape hatch**: Full HKT access when needed

### Pain Point 5: Selective Is Too Abstract

**Problem**: Selective is powerful but unfamiliar to Java developers.

**Mitigation**:
1. **Hide by default**: Not exposed in basic Path interfaces
2. **Concrete methods**: Provide `ifPresent`, `filter`, `recover` instead
3. **GenericPath access**: Full `select`, `ifS`, `whenS` on GenericPath

---

## Implementation Phase Plan (Revised)

### Phase 1: Core Types (MVP)
- [x] Design `Composable` interface
- [x] Design `Chainable` interface
- [x] Design `Recoverable` interface
- [ ] Add `Combinable` interface (NEW)
- [ ] Implement `MaybePath`
- [ ] Implement `EitherPath`
- [ ] Implement `TryPath`
- [ ] Implement `Path` factory

### Phase 2: Extended Types
- [ ] Implement `IOPath` with `Effectful` interface
- [ ] Implement `ValidatedPath` with `Accumulating` interface
- [ ] Implement `IdPath`
- [ ] Implement `OptionalPath` (Java stdlib bridge)

### Phase 3: Collection & Traverse Support
- [ ] Add concrete traverse overloads to existing paths
- [ ] Implement `PathTraverse` static helpers
- [ ] Implement `FuturePath` (CompletableFuture bridge)
- [ ] Implement `StreamPath` (if needed)

### Phase 4: Advanced Types
- [ ] Implement `GenericPath` with full HKT access
- [ ] Implement `ReaderPath`
- [ ] Implement `StatePath`
- [ ] Implement `WriterPath`
- [ ] Selective support via GenericPath

### Phase 5+: Specialized Structures
- [ ] Monad transformer support via GenericPath
- [ ] Free monad DSL support
- [ ] FreeAp support for parallel composition

---

## Summary

### Typeclass Coverage Assessment

| Typeclass | Coverage | Strategy |
|-----------|----------|----------|
| **Functor** | COMPLETE | `Composable.map()` |
| **Applicative** | ADD `Combinable` | New interface in Phase 1 |
| **Monad** | COMPLETE | `Chainable.via()/flatMap()` |
| **MonadError** | COMPLETE | `Recoverable` |
| **Selective** | DEFER | GenericPath escape hatch |
| **Foldable** | IMPLICIT | Terminal methods suffice |
| **Traverse** | CONCRETE OVERLOADS | Phase 3 concrete methods |

### Key Recommendations

1. **Add `Combinable` interface** to the Phase 1 design for applicative combination
2. **Keep Foldable implicit** - terminal operations cover the use case
3. **Provide concrete traverse overloads** rather than exposing HKT
4. **Defer Selective** to advanced GenericPath access
5. **Update Java version** to RELEASE_25 in all design documents
6. **ValidatedPath dual nature** - implement both Chainable and Accumulating

### Success Criteria

The EffectPath API will be considered complete when:

1. All Phase 1-2 types have fluent Path wrappers
2. Independent combination (Applicative) is supported via `Combinable`
3. Error accumulation (Validated) works correctly
4. Common traverse patterns have concrete overloads
5. Advanced users can escape to full HKT via GenericPath
6. Zero breaking changes to existing higher-kinded-j code
