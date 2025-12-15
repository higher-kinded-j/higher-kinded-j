# GenericPath Design: Phase 2 Scope Analysis

> **Status**: Design Document (v1.0)
> **Last Updated**: 2025-12-13
> **Purpose**: Define GenericPath scope for Phase 2 vs later phases
> **Java Baseline**: Java 25 (RELEASE_25)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [The GenericPath Challenge](#the-genericpath-challenge)
3. [Phase 2 Scope: Basic GenericPath](#phase-2-scope)
4. [Phase 3+ Scope: Full HKT Support](#phase-3-scope)
5. [Implementation Comparison](#implementation-comparison)
6. [Recommended Approach](#recommended-approach)
7. [Open Questions](#open-questions)

---

## Executive Summary

`GenericPath<F, A>` serves as an **escape hatch** allowing advanced users to work with monads not directly supported by the Path API. This document analyzes what can be practically achieved in Phase 2 versus what should be deferred.

### Key Decision Points

| Aspect | Phase 2 (Basic) | Phase 3+ (Full) |
|--------|-----------------|-----------------|
| **HKT Exposure** | Minimal | Full `Kind<F, A>` |
| **Monad Parameter** | At construction | Runtime polymorphism |
| **Type Safety** | Coarse-grained | Fine-grained |
| **Use Cases** | Simple custom monads | Library authoring |
| **Complexity** | Medium | Very High |

**Recommendation**: Implement **Phase 2 Basic GenericPath** with limited HKT exposure, deferring full polymorphism to Phase 3+.

---

## The GenericPath Challenge

### Why GenericPath Exists

The EffectPath API uses sealed interfaces with explicit permits:

```java
public sealed interface Chainable<A>
    permits Recoverable, Effectful, MaybePath, EitherPath, TryPath, IOPath,
            ValidatedPath, IdPath, OptionalPath, GenericPath {
```

This creates a problem: **users with custom monads cannot participate in Path composition**.

### The Tension

| Goal | Solution | Trade-off |
|------|----------|-----------|
| **Type Safety** | Sealed interfaces | Limited extensibility |
| **Extensibility** | GenericPath escape hatch | Exposes HKT complexity |
| **Simplicity** | Hide HKT from users | Can't support all monads |

GenericPath must balance these competing concerns.

---

## Phase 2 Scope: Basic GenericPath

### What Phase 2 Can Achieve

A **limited GenericPath** that:
1. Works with any `Monad<F>` instance
2. Supports basic composition (`map`, `via`, `flatMap`)
3. Does NOT expose `Kind<F, A>` in public method signatures
4. Provides factory methods for common conversions

### Phase 2 GenericPath Interface

```java
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.typeclass.Monad;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Composable;

/**
 * Escape hatch for using custom monads in Path composition.
 *
 * <p>GenericPath allows advanced users to wrap any type that has a Monad
 * instance, enabling participation in Path-style composition without
 * requiring explicit support in the capability interface hierarchy.
 *
 * <h2>Phase 2 Limitations</h2>
 * <ul>
 *   <li>Cannot mix with concrete path types in zipWith/via operations</li>
 *   <li>Run method returns Object (requires casting)</li>
 *   <li>No automatic error recovery (no MonadError evidence)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Wrap a custom monad
 * CustomResult<User> result = customService.getUser(id);
 * GenericPath<CustomResultKind.Witness, User> path =
 *     GenericPath.of(result, CustomResultMonad.INSTANCE);
 *
 * // Compose with map and via
 * GenericPath<CustomResultKind.Witness, String> name = path
 *     .map(User::getName)
 *     .via(n -> GenericPath.of(validateName(n), CustomResultMonad.INSTANCE));
 * }</pre>
 *
 * @param <F> the witness type of the underlying monad
 * @param <A> the value type
 */
public final class GenericPath<F, A> implements Chainable<A> {

    private final Kind<F, A> value;
    private final Monad<F> monad;

    private GenericPath(Kind<F, A> value, Monad<F> monad) {
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.monad = Objects.requireNonNull(monad, "monad must not be null");
    }

    // ===== Factory Methods =====

    /**
     * Creates a GenericPath from a Kind and Monad instance.
     */
    public static <F, A> GenericPath<F, A> of(Kind<F, A> value, Monad<F> monad) {
        return new GenericPath<>(value, monad);
    }

    /**
     * Lifts a pure value into a GenericPath.
     */
    public static <F, A> GenericPath<F, A> pure(A value, Monad<F> monad) {
        return new GenericPath<>(monad.of(value), monad);
    }

    // ===== Terminal Operations =====

    /**
     * Returns the underlying Kind.
     *
     * <p><b>Note:</b> This exposes HKT types. Use only when necessary.
     */
    public Kind<F, A> runKind() {
        return value;
    }

    /**
     * Returns the Monad instance for this path.
     */
    public Monad<F> monad() {
        return monad;
    }

    // ===== Composable Implementation =====

    @Override
    public <B> GenericPath<F, B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new GenericPath<>(monad.map(mapper, value), monad);
    }

    @Override
    public GenericPath<F, A> peek(Consumer<? super A> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        return map(a -> {
            consumer.accept(a);
            return a;
        });
    }

    // ===== Combinable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B, C> GenericPath<F, C> zipWith(
            Combinable<B> other,
            BiFunction<? super A, ? super B, ? extends C> combiner) {
        Objects.requireNonNull(other, "other must not be null");
        Objects.requireNonNull(combiner, "combiner must not be null");

        if (!(other instanceof GenericPath<?, ?> otherGeneric)) {
            throw new IllegalArgumentException(
                "GenericPath can only zipWith another GenericPath. Got: " + other.getClass());
        }

        // Runtime check: same witness type
        GenericPath<F, B> typedOther = (GenericPath<F, B>) otherGeneric;

        Kind<F, C> result = monad.flatMap(
            a -> monad.map(b -> combiner.apply(a, b), typedOther.value),
            value
        );
        return new GenericPath<>(result, monad);
    }

    // ===== Chainable Implementation =====

    @Override
    @SuppressWarnings("unchecked")
    public <B> GenericPath<F, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");

        Kind<F, B> result = monad.flatMap(a -> {
            Chainable<B> chainResult = mapper.apply(a);
            Objects.requireNonNull(chainResult, "mapper must not return null");

            if (!(chainResult instanceof GenericPath<?, ?> gp)) {
                throw new IllegalArgumentException(
                    "GenericPath.via must return GenericPath. Got: " + chainResult.getClass());
            }

            return ((GenericPath<F, B>) gp).value;
        }, value);

        return new GenericPath<>(result, monad);
    }

    @Override
    public <B> GenericPath<F, B> then(Supplier<? extends Chainable<B>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return via(_ -> supplier.get());
    }

    // ===== Conversion Methods =====

    /**
     * Converts to MaybePath if the underlying monad is Maybe.
     *
     * @param narrower function to convert Kind to Maybe
     * @return a MaybePath
     */
    public MaybePath<A> toMaybePath(Function<Kind<F, A>, Maybe<A>> narrower) {
        return new MaybePath<>(narrower.apply(value));
    }

    /**
     * Converts to EitherPath if the underlying monad is Either.
     */
    public <E> EitherPath<E, A> toEitherPath(Function<Kind<F, A>, Either<E, A>> narrower) {
        return new EitherPath<>(narrower.apply(value));
    }

    // ===== Object Methods =====

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GenericPath<?, ?> other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "GenericPath(" + value + ")";
    }
}
```

### Phase 2 Limitations

| Limitation | Reason | Workaround |
|------------|--------|------------|
| **No cross-type via** | Type safety requires same witness | Convert to concrete path first |
| **No Recoverable** | MonadError evidence not stored | Use custom recovery methods |
| **Runtime witness check** | Java type erasure | Careful usage patterns |
| **Object return from narrow** | No automatic type inference | Manual casting |

### Phase 2 Use Cases

1. **Custom domain monads**:
   ```java
   // User has a custom Result<E, A> type with Monad instance
   GenericPath<ResultKind.Witness<AppError>, User> path =
       GenericPath.of(resultKind, ResultMonad.instance());
   ```

2. **Interop with third-party libraries**:
   ```java
   // Wrapping a library's monad type
   GenericPath<LibraryMonad.Witness, Data> path =
       GenericPath.of(libraryValue, LibraryMonad.INSTANCE);
   ```

3. **Testing/prototyping**:
   ```java
   // Quick prototypes before adding full Path support
   GenericPath<IdKind.Witness, Value> idPath =
       GenericPath.pure(value, IdMonad.INSTANCE);
   ```

---

## Phase 3+ Scope: Full HKT Support

### What Phase 3+ Adds

1. **MonadError evidence** for recovery operations
2. **Natural transformations** between path types
3. **Type-safe witness propagation**
4. **PathProvider SPI** for automatic resolution

### Phase 3+ GenericPath Enhancements

```java
// Phase 3: Add MonadError support
public final class GenericPath<F, A> implements Chainable<A> {

    private final Kind<F, A> value;
    private final Monad<F> monad;
    private final @Nullable MonadError<F, ?> monadError;  // Phase 3

    // Phase 3: Recoverable-like operations when MonadError available
    public <E> GenericPath<F, A> recover(
            Function<? super E, ? extends A> recovery,
            MonadError<F, E> evidence) {
        // ...
    }

    // Phase 3: Natural transformations
    public <G> GenericPath<G, A> mapK(
            NaturalTransformation<F, G> transform,
            Monad<G> targetMonad) {
        return new GenericPath<>(transform.apply(value), targetMonad);
    }
}
```

### Phase 3+ PathProvider SPI

```java
// Phase 3: Service Provider Interface
public interface PathProvider<F> {
    Class<?> witnessType();
    <A> Chainable<A> createPath(Kind<F, A> value);
    Monad<F> monad();
    Optional<MonadError<F, ?>> monadError();
}

// Auto-discovery via ServiceLoader
public final class PathRegistry {
    private static final Map<Class<?>, PathProvider<?>> providers = loadProviders();

    public static <F, A> Optional<Chainable<A>> createPath(
            Kind<F, A> value,
            Class<?> witness) {
        return Optional.ofNullable(providers.get(witness))
            .map(p -> p.createPath(value));
    }
}
```

### Phase 3+ Use Cases

1. **Library authors** providing Path support for their monads
2. **Automatic path resolution** based on witness type
3. **Type-safe natural transformations** between effect types
4. **Full error recovery** for any MonadError

---

## Implementation Comparison

### Code Size Estimate

| Component | Phase 2 | Phase 3+ |
|-----------|---------|----------|
| `GenericPath.java` | ~200 lines | ~400 lines |
| `PathProvider.java` | N/A | ~50 lines |
| `PathRegistry.java` | N/A | ~100 lines |
| Test coverage | ~300 lines | ~600 lines |
| **Total** | ~500 lines | ~1150 lines |

### Complexity Assessment

| Aspect | Phase 2 | Phase 3+ |
|--------|---------|----------|
| **Type Parameters** | 2 (`F`, `A`) | 2-3 (`F`, `A`, `E`) |
| **Runtime Checks** | Yes (witness) | Reduced (SPI) |
| **User Learning Curve** | Medium | High |
| **Error Handling** | Manual | Automatic |
| **Interop** | Limited | Full |

---

## Recommended Approach

### Phase 2 Implementation Plan

1. **Basic GenericPath** as shown above
2. **Factory methods** for common patterns:
   - `GenericPath.of(Kind<F, A>, Monad<F>)`
   - `GenericPath.pure(A, Monad<F>)`
3. **Conversion methods** to concrete paths:
   - `toMaybePath(Function<Kind<F, A>, Maybe<A>>)`
   - `toEitherPath(Function<Kind<F, A>, Either<E, A>>)`
4. **Documentation** clearly stating limitations
5. **Examples** showing common usage patterns

### What NOT to Include in Phase 2

1. ~~PathProvider SPI~~ (defer to Phase 3)
2. ~~PathRegistry~~ (defer to Phase 3)
3. ~~MonadError support~~ (defer to Phase 3)
4. ~~Natural transformations~~ (defer to Phase 3)
5. ~~Automatic witness resolution~~ (defer to Phase 3)

### Migration Path

Phase 2 users will be able to:
1. Continue using basic GenericPath unchanged
2. Optionally register PathProviders in Phase 3
3. Gain automatic recovery when MonadError is detected

---

## Open Questions

### For Discussion

1. **Witness type validation**: Should we validate at runtime that the witness type matches the Monad instance?
   - Pro: Catches bugs early
   - Con: Additional complexity, may not be reliably checkable

2. **Sealed interface permits**: Should GenericPath be the ONLY escape hatch, or should we permit `PluginPath` for SPI?
   ```java
   // Option A: Single escape hatch
   permits ... GenericPath

   // Option B: Separate SPI escape hatch
   permits ... GenericPath, PluginPath
   ```

3. **Cross-type composition**: Should `via` accept any `Chainable<B>` or require `GenericPath<F, B>`?
   - Current design: Requires same witness type (GenericPath)
   - Alternative: Allow any Chainable but lose type safety

4. **KindHelper integration**: Should we provide pre-built narrower functions for core types?
   ```java
   // Instead of:
   genericPath.toMaybePath(kind -> MaybeKindHelper.narrow(kind))

   // Provide:
   genericPath.toMaybePath(MaybeKindHelper::narrow)
   // or even:
   genericPath.toMaybePath()  // auto-detect
   ```

### Decisions Required

- [ ] Include basic GenericPath in Phase 2? **Recommended: Yes**
- [ ] Require same witness type for via/zipWith? **Recommended: Yes**
- [ ] Add KindHelper convenience methods? **Recommended: Phase 3**
- [ ] Validate witness at construction? **Recommended: No (too complex)**

---

## Summary

### Phase 2 Deliverables

| Item | Included |
|------|----------|
| `GenericPath<F, A>` class | Yes |
| `map`, `via`, `flatMap`, `zipWith` | Yes |
| `pure`, `of` factory methods | Yes |
| Conversion to concrete paths | Yes |
| Full documentation | Yes |
| PathProvider SPI | **No** |
| PathRegistry | **No** |
| MonadError support | **No** |
| Natural transformations | **No** |

### Success Criteria

Phase 2 GenericPath is successful if:
1. Users with custom monads can participate in Path composition
2. API is clear about limitations
3. Migration to Phase 3 enhancements is straightforward
4. No breaking changes required for Phase 3
