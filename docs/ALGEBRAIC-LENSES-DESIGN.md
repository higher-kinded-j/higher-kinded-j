# Algebraic Lenses Design Document

> **Status**: Proposal
> **Source**: [Algebraic Lenses](https://chrispenner.ca/posts/algebraic) by Chris Penner
> **Created**: 2026-01-23

## Overview

This document captures ideas from Chris Penner's "Algebraic Lenses" blog post that could enhance higher-kinded-j's optics system. The blog describes a different characterization of lenses that operates on *containers of values* rather than single states, enabling powerful aggregation operations.

## Current State

Higher-kinded-j already has a solid foundation:

| Feature | Status | Location |
|---------|--------|----------|
| Profunctor | ✅ Implemented | `hkj-api/.../hkt/Profunctor.java` |
| Lens | ✅ Implemented | `hkj-api/.../optics/Lens.java` |
| Prism | ✅ Implemented | `hkj-api/.../optics/Prism.java` |
| Iso | ✅ Implemented | `hkj-api/.../optics/Iso.java` |
| Affine | ✅ Implemented | `hkj-api/.../optics/Affine.java` |
| Traversal | ✅ Implemented | `hkj-api/.../optics/Traversal.java` |
| Fold | ✅ Implemented | `hkj-api/.../optics/Fold.java` |
| Setter | ✅ Implemented | `hkj-api/.../optics/Setter.java` |
| Getter | ✅ Implemented | `hkj-api/.../optics/Getter.java` |
| Indexed Optics | ✅ Implemented | `hkj-api/.../optics/indexed/` |
| Monoid/Semigroup | ✅ Implemented | `hkj-api/.../hkt/Monoid.java` |
| Lens.paired | ✅ Implemented | Atomic updates for coupled fields |

## Proposed Additions

### 1. Grate Optic

**Priority**: Medium
**Complexity**: High

#### Concept

A **Grate** is the categorical dual of a Lens. While a Lens focuses *into* a structure to extract a part, a Grate focuses *out of* a context, representing values that exist "everywhere" in a structure.

```
Lens:  S → A           (extract one part from whole)
Grate: ((S → A) → A)   (given any way to extract, produce a value)
```

#### Haskell Definition (for reference)

```haskell
type Grate s t a b = forall p. Closed p => p a b -> p s t

class Profunctor p => Closed p where
  closed :: p a b -> p (x -> a) (x -> b)
```

#### Proposed Java Interface

```java
/**
 * A Grate is an optic for values that exist "everywhere" in a structure.
 * It is the dual of Lens - while Lens extracts one part, Grate aggregates
 * from all possible extraction points.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Pair/Tuple where both elements have same type: {@code Pair<A, A>}</li>
 *   <li>Functions: {@code R -> A} (the result exists for all inputs)</li>
 *   <li>Environment/Reader patterns</li>
 *   <li>Grids/matrices (diagonal, all cells)</li>
 * </ul>
 *
 * @param <S> The source/whole type
 * @param <A> The focus type
 */
public interface Grate<S, A> {

    /**
     * The core grate operation. Given a function that can extract A from any
     * "extraction context" (S → A), produces an A.
     *
     * <p>For {@code Pair<A, A>}, this would be:
     * {@code grate(extract -> combine(extract.apply(pair.first()), extract.apply(pair.second())))}
     *
     * @param extractor A function that, given any way to view S as A, produces an A
     * @return The aggregated/combined value
     */
    A grate(Function<Function<S, A>, A> extractor);

    /**
     * Modify all focused values using a function.
     */
    default S modify(Function<A, A> f, S source) {
        return grate(extract -> f.apply(extract.apply(source)));
    }

    /**
     * Set all focused values to a constant.
     */
    default S set(A value, S source) {
        return modify(__ -> value, source);
    }

    /**
     * Zip two structures together using a combining function.
     */
    default S zipWith(BiFunction<A, A, A> combine, S s1, S s2) {
        return grate(extract -> combine.apply(extract.apply(s1), extract.apply(s2)));
    }

    /**
     * Create a Grate from a "degrate" function.
     */
    static <S, A> Grate<S, A> of(Function<Function<Function<S, A>, A>, S> degrate) {
        return extractor -> degrate.apply(extractor);
    }
}
```

#### Example Instances

```java
// Grate for Pair<A, A> - focuses on "both elements"
public static <A> Grate<Pair<A, A>, A> both() {
    return Grate.of(f -> Pair.of(
        f.apply(Pair::first),
        f.apply(Pair::second)
    ));
}

// Grate for Function<R, A> - focuses on "the result for all inputs"
public static <R, A> Grate<Function<R, A>, A> represented(R rep) {
    return Grate.of(f -> r -> f.apply(fn -> fn.apply(r)));
}
```

#### Use Cases

```java
// Average both elements of a pair
Pair<Double, Double> pair = Pair.of(10.0, 20.0);
Grate<Pair<Double, Double>, Double> both = Grates.both();

// Zip with addition
Pair<Double, Double> sum = both.zipWith(Double::sum, pair, Pair.of(1.0, 2.0));
// Result: Pair(11.0, 22.0)

// Set both to same value
Pair<Double, Double> uniform = both.set(5.0, pair);
// Result: Pair(5.0, 5.0)
```

---

### 2. Accessor Profunctors

**Priority**: High
**Complexity**: Medium

These profunctor types enable different operations through the same optic definition, completing the profunctor optics encoding.

#### 2.1 Forget Profunctor

**Purpose**: Extract a constant result, ignoring the "output" type. Used for `get`/`view` operations.

```java
/**
 * A profunctor that "forgets" its second type parameter, extracting only
 * a constant result R. Used to implement 'view' operations on optics.
 *
 * <p>Forget R A B ≅ A -> R (the B is phantom)
 *
 * @param <R> The result type being accumulated
 * @param <A> The input type
 * @param <B> The phantom output type (ignored)
 */
public record Forget<R, A, B>(Function<A, R> run)
    implements Kind2<Forget.Witness<R>, A, B> {

    public sealed interface Witness<R> extends WitnessArity<TypeArity.Binary>
        permits ForgetWitness {}

    public R apply(A a) {
        return run.apply(a);
    }
}

/**
 * Profunctor instance for Forget.
 */
public class ForgetProfunctor<R> implements Profunctor<Forget.Witness<R>> {
    @Override
    public <A, B, C, D> Kind2<Forget.Witness<R>, C, D> dimap(
            Function<C, A> f,
            Function<B, D> g,  // ignored!
            Kind2<Forget.Witness<R>, A, B> pab) {
        Forget<R, A, B> forget = narrow(pab);
        return new Forget<>(c -> forget.apply(f.apply(c)));
    }
}

/**
 * Strong instance for Forget - enables use with Lens.
 */
public class ForgetStrong<R> extends ForgetProfunctor<R>
    implements Strong<Forget.Witness<R>> {

    @Override
    public <A, B, C> Kind2<Forget.Witness<R>, Pair<A, C>, Pair<B, C>> first(
            Kind2<Forget.Witness<R>, A, B> pab) {
        Forget<R, A, B> forget = narrow(pab);
        return new Forget<>(pair -> forget.apply(pair.first()));
    }
}

/**
 * Choice instance for Forget with Monoid - enables use with Prism.
 */
public class ForgetChoice<R> extends ForgetStrong<R>
    implements Choice<Forget.Witness<R>> {

    private final Monoid<R> monoid;

    public ForgetChoice(Monoid<R> monoid) {
        this.monoid = monoid;
    }

    @Override
    public <A, B, C> Kind2<Forget.Witness<R>, Either<A, C>, Either<B, C>> left(
            Kind2<Forget.Witness<R>, A, B> pab) {
        Forget<R, A, B> forget = narrow(pab);
        return new Forget<>(either -> either.fold(
            forget::apply,
            c -> monoid.empty()
        ));
    }
}
```

#### 2.2 Tagged Profunctor

**Purpose**: Ignore the input type, just carry a value. Used for `review`/`build` operations.

```java
/**
 * A profunctor that ignores its first type parameter, carrying only a value.
 * Used to implement 'review' operations on optics (building from a focus).
 *
 * <p>Tagged A B ≅ B (the A is phantom)
 *
 * @param <A> The phantom input type (ignored)
 * @param <B> The value type being carried
 */
public record Tagged<A, B>(B value)
    implements Kind2<Tagged.Witness, A, B> {

    public sealed interface Witness extends WitnessArity<TypeArity.Binary>
        permits TaggedWitness {}
}

/**
 * Profunctor instance for Tagged.
 */
public class TaggedProfunctor implements Profunctor<Tagged.Witness> {
    @Override
    public <A, B, C, D> Kind2<Tagged.Witness, C, D> dimap(
            Function<C, A> f,  // ignored!
            Function<B, D> g,
            Kind2<Tagged.Witness, A, B> pab) {
        Tagged<A, B> tagged = narrow(pab);
        return new Tagged<>(g.apply(tagged.value()));
    }
}

/**
 * Choice instance for Tagged - enables use with Prism.
 */
public class TaggedChoice extends TaggedProfunctor
    implements Choice<Tagged.Witness> {

    @Override
    public <A, B, C> Kind2<Tagged.Witness, Either<A, C>, Either<B, C>> left(
            Kind2<Tagged.Witness, A, B> pab) {
        Tagged<A, B> tagged = narrow(pab);
        return new Tagged<>(Either.left(tagged.value()));
    }
}
```

#### 2.3 Costar Profunctor

**Purpose**: Container-based aggregation operations. Enables "algebraic" lens behavior.

```java
/**
 * A profunctor representing functions from containers.
 * Costar F A B ≅ F A -> B
 *
 * <p>This enables container-aware optics that can aggregate across collections.
 *
 * @param <F> The container/functor witness type
 * @param <A> The element type in the container
 * @param <B> The result type
 */
public record Costar<F extends WitnessArity<TypeArity.Unary>, A, B>(
    Function<Kind<F, A>, B> run
) implements Kind2<Costar.Witness<F>, A, B> {

    public sealed interface Witness<F extends WitnessArity<TypeArity.Unary>>
        extends WitnessArity<TypeArity.Binary>
        permits CostarWitness {}

    public B apply(Kind<F, A> fa) {
        return run.apply(fa);
    }
}

/**
 * Profunctor instance for Costar.
 */
public class CostarProfunctor<F extends WitnessArity<TypeArity.Unary>>
    implements Profunctor<Costar.Witness<F>> {

    private final Functor<F> functor;

    public CostarProfunctor(Functor<F> functor) {
        this.functor = functor;
    }

    @Override
    public <A, B, C, D> Kind2<Costar.Witness<F>, C, D> dimap(
            Function<C, A> f,
            Function<B, D> g,
            Kind2<Costar.Witness<F>, A, B> pab) {
        Costar<F, A, B> costar = narrow(pab);
        return new Costar<>(fc -> g.apply(costar.apply(functor.map(f, fc))));
    }
}
```

---

### 3. MStrong Profunctor (Monoid-Strength)

**Priority**: Medium
**Complexity**: Medium

#### Concept

**MStrong** is a profunctor that can incorporate monoidal structure, orthogonal to the standard `Strong` profunctor. This enables aggregation-aware optics.

```java
/**
 * A profunctor with "monoid strength" - can incorporate monoidal projections.
 *
 * <p>While Strong allows pairing with extra context, MStrong allows
 * accumulating multiple projections using a Monoid.
 *
 * @param <P> The profunctor witness type
 */
public interface MStrong<P extends WitnessArity<TypeArity.Binary>>
    extends Profunctor<P> {

    /**
     * Incorporate a monoidal projection into the profunctor.
     *
     * @param monoid The monoid for combining projections
     * @param pab The profunctor to strengthen
     * @param <A> Input type
     * @param <B> Output type
     * @param <M> The monoid carrier type
     * @return A profunctor that can work with monoidal projections
     */
    <A, B, M> Kind2<P, A, B> mstrong(
        Monoid<M> monoid,
        Function<A, M> project,
        Kind2<P, M, B> pmb
    );
}
```

#### Use Case: Aggregation Through Optics

```java
// With MStrong, we can aggregate through a traversal
List<Measurement> measurements = ...;

// Sum all values
double total = traversal.aggregate(
    Monoids.doubleSum(),
    Measurement::value,
    measurements
);

// Find element closest to mean
Optional<Measurement> closest = traversal.selectBy(
    comparing(m -> Math.abs(m.value() - mean)),
    measurements
);
```

---

### 4. Corepresentable Profunctor

**Priority**: Low
**Complexity**: High

#### Concept

A **Corepresentable** profunctor can "round-trip" through a representation type. This is the foundation for Grate-like optics.

```java
/**
 * A profunctor that is corepresentable by a functor F.
 * This means the profunctor "behaves like" functions from F.
 *
 * @param <P> The profunctor witness type
 * @param <F> The representing functor witness type
 */
public interface Corepresentable<
    P extends WitnessArity<TypeArity.Binary>,
    F extends WitnessArity<TypeArity.Unary>
> extends Profunctor<P> {

    /**
     * Extract a result by applying the profunctor to a container.
     */
    <A, B> B cosieve(Kind2<P, A, B> pab, Kind<F, A> fa);

    /**
     * Create a profunctor from a function on containers.
     */
    <A, B> Kind2<P, A, B> cotabulate(Function<Kind<F, A>, B> f);
}
```

---

### 5. Closed Profunctor

**Priority**: Medium
**Complexity**: Medium

#### Concept

A **Closed** profunctor can lift through function types. This is the key constraint for Grate optics.

```java
/**
 * A profunctor that is "closed" - can lift through function types.
 * This is the dual of Strong (which lifts through product types).
 *
 * <p>Laws:
 * <ul>
 *   <li>closed . closed ≡ dimap uncurry curry . closed</li>
 *   <li>dimap const ($()) . closed ≡ id</li>
 * </ul>
 *
 * @param <P> The profunctor witness type
 */
public interface Closed<P extends WitnessArity<TypeArity.Binary>>
    extends Profunctor<P> {

    /**
     * Lift a profunctor through a function type.
     *
     * <p>If p transforms A to B, then closed(p) transforms (X -> A) to (X -> B).
     *
     * @param pab The profunctor to lift
     * @param <X> The function input type (unchanged)
     * @param <A> The original input type
     * @param <B> The original output type
     * @return A profunctor on function types
     */
    <X, A, B> Kind2<P, Function<X, A>, Function<X, B>> closed(Kind2<P, A, B> pab);
}
```

---

### 6. Enhanced Aggregation Combinators

**Priority**: High
**Complexity**: Low

Add practical aggregation methods to existing optics.

#### Additions to Fold

```java
public interface Fold<S, A> {
    // Existing
    <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source);

    // NEW: Position-aware folding
    default <M> M foldMapIndexed(
            Monoid<M> monoid,
            BiFunction<Integer, ? super A, ? extends M> f,
            S source) {
        // Implementation using internal counter
    }

    // NEW: Select by comparator (minimum/maximum)
    default Optional<A> selectBy(Comparator<? super A> comparator, S source) {
        // Implementation using foldMap with "first minimum" monoid
    }

    // NEW: Find first matching predicate
    default Optional<A> find(Predicate<? super A> predicate, S source) {
        // Short-circuiting implementation
    }

    // NEW: Check if any/all match predicate
    default boolean any(Predicate<? super A> predicate, S source) {
        return find(predicate, source).isPresent();
    }

    default boolean all(Predicate<? super A> predicate, S source) {
        return !any(predicate.negate(), source);
    }

    // NEW: Count elements
    default int count(S source) {
        return foldMap(Monoids.intSum(), a -> 1, source);
    }

    // NEW: Check if empty
    default boolean isEmpty(S source) {
        return count(source) == 0;
    }

    // NEW: Collect to list
    default List<A> toList(S source) {
        return foldMap(Monoids.list(), List::of, source);
    }
}
```

#### Additions to Traversal

```java
public interface Traversal<S, A> extends Fold<S, A> {
    // Existing
    <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app);

    // NEW: Filter during traversal
    default Traversal<S, A> filtered(Predicate<? super A> predicate) {
        Traversal<S, A> self = this;
        return new Traversal<>() {
            @Override
            public <F> Kind<F, S> modifyF(
                    Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
                return self.modifyF(
                    a -> predicate.test(a) ? f.apply(a) : app.of(a),
                    s, app
                );
            }
        };
    }

    // NEW: Take first N elements
    default Traversal<S, A> take(int n) {
        // Implementation with counter
    }

    // NEW: Drop first N elements
    default Traversal<S, A> drop(int n) {
        // Implementation with counter
    }

    // NEW: Element at specific index
    default Affine<S, A> index(int i) {
        // Returns Affine since element may not exist
    }
}
```

---

### 7. Lens.fanout / Lens.zip

**Priority**: Medium
**Complexity**: Low

Extend `Lens.paired` to more arities.

```java
public interface Lens<S, A> {
    // Existing: paired for 2 lenses

    // NEW: Fanout for 3 lenses
    static <S, A, B, C> Lens<S, Tuple3<A, B, C>> fanout3(
            Lens<S, A> l1,
            Lens<S, B> l2,
            Lens<S, C> l3,
            Function4<S, A, B, C, S> reconstructor) {
        return Lens.of(
            s -> Tuple3.of(l1.get(s), l2.get(s), l3.get(s)),
            (s, t) -> reconstructor.apply(s, t._1(), t._2(), t._3())
        );
    }

    // NEW: Fanout for 4 lenses
    static <S, A, B, C, D> Lens<S, Tuple4<A, B, C, D>> fanout4(
            Lens<S, A> l1,
            Lens<S, B> l2,
            Lens<S, C> l3,
            Lens<S, D> l4,
            Function5<S, A, B, C, D, S> reconstructor) {
        // Implementation
    }

    // NEW: Zip two lenses with a combining function
    default <B, C> Lens<S, C> zipWith(
            Lens<S, B> other,
            BiFunction<A, B, C> combine,
            BiFunction<S, C, S> setter) {
        return Lens.of(
            s -> combine.apply(this.get(s), other.get(s)),
            setter
        );
    }
}
```

---

## Unified Profunctor Optic Encoding

The blog shows how all optics can be encoded uniformly as:

```
type Optic p s t a b = p a b -> p s t
```

Where profunctor constraints determine the optic type:

| Optic | Profunctor Constraint |
|-------|----------------------|
| Iso | Profunctor |
| Lens | Strong |
| Prism | Choice |
| Affine | Strong + Choice |
| Grate | Closed |
| Traversal | Wander |
| Fold | Folding (Forget + Monoid) |
| Setter | Mapping |

### Potential Unified Interface

```java
/**
 * A unified profunctor optic encoding.
 *
 * @param <P> The profunctor constraint (determines optic type)
 * @param <S> The source type
 * @param <T> The modified source type
 * @param <A> The focus type
 * @param <B> The modified focus type
 */
@FunctionalInterface
public interface POptic<P extends WitnessArity<TypeArity.Binary>, S, T, A, B> {
    Kind2<P, S, T> apply(Kind2<P, A, B> pab);
}

// Type aliases (conceptual - Java doesn't have these)
// type PLens<S, T, A, B> = POptic<Strong, S, T, A, B>
// type PPrism<S, T, A, B> = POptic<Choice, S, T, A, B>
// type PGrate<S, T, A, B> = POptic<Closed, S, T, A, B>
```

---

## Implementation Roadmap

### Phase 1: Accessor Profunctors (High Value, Medium Effort)

1. Implement `Forget<R, A, B>` with Profunctor, Strong, Choice instances
2. Implement `Tagged<A, B>` with Profunctor, Choice instances
3. Add `view` operation using Forget to all optics
4. Add `review` operation using Tagged to Prism and Iso

### Phase 2: Enhanced Aggregation (High Value, Low Effort)

1. Add `selectBy`, `find`, `any`, `all`, `count` to Fold
2. Add `filtered`, `take`, `drop`, `index` to Traversal
3. Add `foldMapIndexed` for position-aware folding
4. Add `Lens.fanout3`, `Lens.fanout4`

### Phase 3: Container-Aware Optics (Medium Value, Medium Effort)

1. Implement `Costar<F, A, B>` profunctor
2. Add `CostarProfunctor` instance
3. Create aggregation utilities using Costar

### Phase 4: Grate and Closed (Medium Value, High Effort)

1. Implement `Closed` profunctor interface
2. Implement `Grate<S, A>` optic
3. Add common Grate instances (`both`, `represented`)
4. Add composition rules: Grate >>> Grate = Grate, Iso >>> Grate = Grate

### Phase 5: MStrong and Advanced Patterns (Low Priority)

1. Implement `MStrong` profunctor interface
2. Implement `Corepresentable` interface
3. Create unified `POptic` encoding (optional, research-oriented)

---

## Example: Complete Algebraic Lens Usage

After implementation, users could write:

```java
// Domain model
record Flower(double sepalLength, double sepalWidth,
              double petalLength, double petalWidth, String species) {}

// Traversal over all measurements
Traversal<Flower, Double> measurements = Traversals.of(
    Lens.of(Flower::sepalLength, (f, v) -> new Flower(v, f.sepalWidth(), f.petalLength(), f.petalWidth(), f.species())),
    Lens.of(Flower::sepalWidth, (f, v) -> new Flower(f.sepalLength(), v, f.petalLength(), f.petalWidth(), f.species())),
    Lens.of(Flower::petalLength, (f, v) -> new Flower(f.sepalLength(), f.sepalWidth(), v, f.petalWidth(), f.species())),
    Lens.of(Flower::petalWidth, (f, v) -> new Flower(f.sepalLength(), f.sepalWidth(), f.petalLength(), v, f.species()))
);

// With algebraic lenses: find flower closest to sample
List<Flower> dataset = loadIrisDataset();
Flower sample = new Flower(5.1, 3.5, 1.4, 0.2, "unknown");

// Aggregate operation: minimum by euclidean distance
Optional<Flower> nearest = ListTraversals.<Flower>each()
    .selectBy(comparing(flower -> euclideanDistance(
        measurements.toList(flower),
        measurements.toList(sample)
    )), dataset);

String predictedSpecies = nearest
    .map(Flower::species)
    .orElse("unknown");
```

---

## References

- [Algebraic Lenses](https://chrispenner.ca/posts/algebraic) - Chris Penner
- [Profunctor Optics: Modular Data Accessors](https://arxiv.org/abs/1703.10857) - Pickering, Gibbons, Wu
- [Optics By Example](https://leanpub.com/optics-by-example) - Chris Penner
- [lens library documentation](https://hackage.haskell.org/package/lens)

---

## Open Questions

1. **Should Grate compose with Lens?** In Haskell, Grate and Lens don't compose because they have incompatible profunctor constraints (Closed vs Strong). Should we enforce this or allow lossy composition?

2. **Unified encoding vs specialized interfaces?** The current specialized interfaces (Lens, Prism, etc.) provide good ergonomics. Should we add the unified POptic encoding alongside, or is it too abstract for Java users?

3. **Performance implications?** Profunctor-encoded optics may have performance overhead from boxing/unboxing. Need benchmarks comparing concrete vs profunctor-encoded implementations.

4. **Should Forget require Monoid for all uses?** Some implementations make Monoid optional (only needed for Choice), others require it always. What's the best approach for Java?
