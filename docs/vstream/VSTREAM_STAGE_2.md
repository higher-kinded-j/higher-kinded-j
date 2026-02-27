# Stage 2: HKT Encoding and Type Classes

## Overview

This stage integrates `VStream<A>` into higher-kinded-j's type-safe HKT simulation by
defining the `VStreamKind` witness type, widen/narrow operations, and type class instances:
Functor, Applicative, Monad, Foldable, Traverse (finite streams), and Alternative. All
instances are verified against their algebraic laws using the established `LawTestPattern`
and `@TestFactory` dynamic test patterns.

**Module**: `hkj-core` (implementations), `hkj-api` (if VStreamKind witness is API-level)
**Package**: `org.higherkindedj.hkt.vstream`

**Prerequisites**: Stage 1 (Core VStream Type)

---

## Detailed Tasks

### 2.1 VStreamKind Witness Type

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamKind.java`

Define the HKT marker interface following the established pattern (VTaskKind, StreamKind):

```java
public interface VStreamKind<A> extends Kind<VStreamKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

**Requirements**:
- `VStream<A>` must extend `VStreamKind<A>` (update Stage 1 interface)
- `Witness` is a private-constructor final class inside VStreamKind
- Implements `WitnessArity<TypeArity.Unary>` for unary type constructor arity
- Comprehensive javadoc explaining the HKT encoding purpose

### 2.2 VStreamKindHelper (Widen/Narrow Operations)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamKindHelper.java`

Follow the enum singleton pattern (VTaskKindHelper, StreamKindHelper):

```java
public enum VStreamKindHelper implements VStreamConverterOps {
    VSTREAM;

    @Override
    public <A> Kind<VStreamKind.Witness, A> widen(VStream<A> vstream) { ... }

    @Override
    public <A> VStream<A> narrow(Kind<VStreamKind.Witness, A> kind) { ... }
}
```

**Requirements**:
- `widen`: Since VStream extends VStreamKind, this is a simple upcast
- `narrow`: Type check and cast using `Validation.kind().narrowWithTypeCheck()`
- Null-safe: both methods handle null input appropriately
- Corresponding `VStreamConverterOps` interface for the contract

### 2.3 VStreamConverterOps Interface

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamConverterOps.java`

```java
public interface VStreamConverterOps {
    <A> Kind<VStreamKind.Witness, A> widen(VStream<A> vstream);
    <A> VStream<A> narrow(Kind<VStreamKind.Witness, A> kind);
}
```

### 2.4 VStreamFunctor

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamFunctor.java`

```java
public class VStreamFunctor implements Functor<VStreamKind.Witness> {
    @Override
    public <A, B> Kind<VStreamKind.Witness, B> map(
            Function<? super A, ? extends B> f,
            Kind<VStreamKind.Witness, A> fa) {
        VStream<A> stream = VSTREAM.narrow(fa);
        return VSTREAM.widen(stream.map(f));
    }
}
```

**Requirements**:
- Delegates to VStream's `map` method
- Null parameter validation
- Javadoc noting functor laws compliance

### 2.5 VStreamApplicative

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamApplicative.java`

```java
public class VStreamApplicative extends VStreamFunctor
        implements Applicative<VStreamKind.Witness> {

    @Override
    public <A> Kind<VStreamKind.Witness, A> of(A value) {
        return VSTREAM.widen(VStream.of(value));
    }

    @Override
    public <A, B> Kind<VStreamKind.Witness, B> ap(
            Kind<VStreamKind.Witness, ? extends Function<A, B>> ff,
            Kind<VStreamKind.Witness, A> fa) {
        // Cartesian product: apply each function to each element
        VStream<? extends Function<A, B>> fStream = VSTREAM.narrow(ff);
        VStream<A> aStream = VSTREAM.narrow(fa);
        return VSTREAM.widen(fStream.flatMap(f -> aStream.map(f)));
    }
}
```

**Design decision**: The `ap` implementation uses Cartesian product semantics (consistent
with list monad / NonDetPath). This is the standard choice for list-like monads. Document
this choice clearly in javadoc.

### 2.6 VStreamMonad

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamMonad.java`

```java
public class VStreamMonad extends VStreamApplicative
        implements Monad<VStreamKind.Witness> {

    public static final VStreamMonad INSTANCE = new VStreamMonad();

    @Override
    public <A, B> Kind<VStreamKind.Witness, B> flatMap(
            Function<? super A, ? extends Kind<VStreamKind.Witness, B>> f,
            Kind<VStreamKind.Witness, A> ma) {
        VStream<A> stream = VSTREAM.narrow(ma);
        VStream<B> result = stream.flatMap(a -> VSTREAM.narrow(f.apply(a)));
        return VSTREAM.widen(result);
    }
}
```

**Requirements**:
- Singleton INSTANCE for reuse
- Delegates to VStream's `flatMap`
- Satisfies left identity, right identity, and associativity laws

### 2.7 VStreamFoldable

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamFoldable.java`

```java
public class VStreamFoldable implements Foldable<VStreamKind.Witness> {

    @Override
    public <A, M> M foldMap(
            Monoid<M> monoid,
            Function<? super A, ? extends M> f,
            Kind<VStreamKind.Witness, A> fa) {
        VStream<A> stream = VSTREAM.narrow(fa);
        // Materialise: pull all elements, folding via monoid
        // This is a terminal operation that executes VTasks
        return stream.foldLeft(monoid.empty(), (acc, a) -> monoid.combine(acc, f.apply(a)))
                     .run();
    }
}
```

**Requirements**:
- `foldMap` is a terminal operation that executes the stream
- Document clearly that this materialises the entire stream
- For infinite streams, this will not terminate (document this)
- Uses iterative consumption (not recursive) for stack safety

### 2.8 VStreamTraverse

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamTraverse.java`

```java
public class VStreamTraverse extends VStreamFoldable
        implements Traverse<VStreamKind.Witness> {

    @Override
    public <G extends WitnessArity<TypeArity.Unary>, A, B>
    Kind<G, Kind<VStreamKind.Witness, B>> traverse(
            Applicative<G> applicative,
            Function<? super A, ? extends Kind<G, B>> f,
            Kind<VStreamKind.Witness, A> fa) {
        // Materialise stream, traverse as list, reconstruct as VStream
        VStream<A> stream = VSTREAM.narrow(fa);
        List<A> elements = stream.toList().run();
        // Use TrampolineUtils.traverseListStackSafe for large lists
        // Wrap result back into VStream.fromList
    }
}
```

**Requirements**:
- Materialises the stream to a list first (finite streams only)
- Uses `TrampolineUtils.traverseListStackSafe` for stack safety
- Reconstructs result as `VStream.fromList()`
- Document clearly: only safe for finite streams
- Javadoc warns about infinite stream usage

### 2.9 VStreamAlternative

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamAlternative.java`

```java
public class VStreamAlternative extends VStreamMonad
        implements Alternative<VStreamKind.Witness> {

    public static final VStreamAlternative INSTANCE = new VStreamAlternative();

    @Override
    public <A> Kind<VStreamKind.Witness, A> empty() {
        return VSTREAM.widen(VStream.empty());
    }

    @Override
    public <A> Kind<VStreamKind.Witness, A> orElse(
            Kind<VStreamKind.Witness, A> fa,
            Supplier<Kind<VStreamKind.Witness, A>> fb) {
        VStream<A> streamA = VSTREAM.narrow(fa);
        VStream<A> streamB = VSTREAM.narrow(fb.get());
        return VSTREAM.widen(VStream.concat(streamA, streamB));
    }
}
```

**Requirements**:
- `empty()` returns `VStream.empty()`
- `orElse` concatenates streams (consistent with list Alternative)
- Satisfies Alternative laws: left identity, right identity, associativity

---

## Testing

### 2.10 VStreamKindHelper Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamKindHelperTest.java`

- `widen()` preserves identity
- `narrow()` round-trips correctly
- `narrow()` with wrong type throws appropriate exception
- Null handling for both operations

### 2.11 Law Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamLawsTest.java`

Follow the established `@TestFactory` + `DynamicTest` pattern from VTaskLawsTest:

```java
class VStreamLawsTest {
    private static final VStreamMonad monad = VStreamMonad.INSTANCE;

    // Test values array for parameterised testing
    private static final Object[] TEST_VALUES = {0, 1, -1, 42, 100};

    // Custom equality checker that materialises streams
    private static final BiPredicate<Kind<VStreamKind.Witness, ?>,
                                      Kind<VStreamKind.Witness, ?>> EQUALITY =
        (a, b) -> {
            List<?> listA = VSTREAM.narrow(a).toList().run();
            List<?> listB = VSTREAM.narrow(b).toList().run();
            return Objects.equals(listA, listB);
        };
}
```

#### Law Test Groups

1. **Functor Laws** (`@TestFactory`)
   - Identity: `map(id, stream) == stream`
   - Composition: `map(g.compose(f), stream) == map(g, map(f, stream))`
   - Use `LawTestPattern.testFunctorIdentityLaw()` and
     `LawTestPattern.testFunctorCompositionLaw()`

2. **Applicative Laws** (`@TestFactory`)
   - Identity: `ap(of(id), stream) == stream`
   - Homomorphism: `ap(of(f), of(x)) == of(f(x))`
   - Interchange: `ap(ff, of(y)) == ap(of(f -> f(y)), ff)`
   - Use `LawTestPattern.testAllApplicativeLaws()`

3. **Monad Laws** (`@TestFactory`)
   - Left Identity: `flatMap(of(a), f) == f(a)`
   - Right Identity: `flatMap(stream, of) == stream`
   - Associativity: `flatMap(flatMap(stream, f), g) == flatMap(stream, x -> flatMap(f(x), g))`
   - Use `LawTestPattern.testAllMonadLaws()`

4. **Alternative Laws** (`@TestFactory`)
   - Left Identity: `orElse(empty(), () -> stream) == stream`
   - Right Identity: `orElse(stream, () -> empty()) == stream`
   - Associativity: `orElse(a, () -> orElse(b, () -> c)) == orElse(orElse(a, () -> b), () -> c)`

5. **Foldable Laws** (`@Nested class FoldableLaws`)
   - `foldMap` with sum monoid produces correct sum
   - `foldMap` with string monoid concatenates correctly
   - `foldMap` on empty stream returns monoid identity
   - Consistency with `toList().run()` manual fold

6. **Traverse Laws** (`@Nested class TraverseLaws`)
   - Structure preservation: traverse with Id applicative is equivalent to map
   - Use `LawTestPattern.testTraverseStructurePreservationLaw()` where applicable

### 2.12 Type Class Instance Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamTypeClassTest.java`

Operational tests (not law tests) for each type class instance:

1. **VStreamFunctor** (`@Nested`)
   - `map` with various functions
   - `map` on empty stream
   - `map` preserves stream length

2. **VStreamApplicative** (`@Nested`)
   - `of` creates single-element stream
   - `ap` applies function stream to value stream (Cartesian product)
   - `ap` with empty function stream returns empty
   - `ap` with empty value stream returns empty

3. **VStreamMonad** (`@Nested`)
   - `flatMap` with expansion (each element produces multiple)
   - `flatMap` with contraction (some elements produce empty)
   - `flatMap` preserves order

4. **VStreamFoldable** (`@Nested`)
   - `foldMap` with integer sum
   - `foldMap` with string concatenation
   - `foldMap` on empty stream

5. **VStreamTraverse** (`@Nested`)
   - `traverse` with Optional applicative (all Some)
   - `traverse` with Optional applicative (one None, entire result None)
   - `traverse` with Maybe applicative
   - Stack safety with large lists

6. **VStreamAlternative** (`@Nested`)
   - `empty` produces empty stream
   - `orElse` concatenates streams
   - `guard(true)` produces Unit
   - `guard(false)` produces empty

---

## Documentation

### 2.13 Javadoc

All public types and methods require comprehensive javadoc:
- VStreamKind: Explain HKT encoding, witness type pattern, relationship to Kind
- VStreamKindHelper: Document widen/narrow operations, thread safety
- Each type class instance: Document which laws it satisfies, usage examples
- VStreamTraverse: Prominent warning about finite-stream-only constraint
- British English throughout

### 2.14 Documentation Page

**File**: `docs/vstream/vstream_hkt.md`

Following STYLE-GUIDE.md:
- "What You'll Learn" admonishment
- Explain how VStream participates in the HKT simulation
- Diagram showing VStream's position in the type class hierarchy
- Code examples for each type class usage
- Comparison with VTask type class instances
- "Key Takeaways" admonishment
- "See Also" linking to HKT encoding docs, type class pages

---

## Examples

### 2.15 Example Code

**File**: `hkj-examples/src/main/java/org/higherkindedj/examples/vstream/VStreamHKTExample.java`

Demonstrate:
- Using VStreamMonad for polymorphic programming
- Foldable with various monoids
- Traverse to flip VStream and Optional/Maybe
- Alternative for stream concatenation and guard filtering
- Writing generic functions parameterised by Monad that work with VStream

---

## Tutorials

### 2.16 Tutorial

**File**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vstream/Tutorial02_VStreamHKT.java`

Following TUTORIAL-STYLE-GUIDE.md:
- 8-10 exercises:
  1. Widen a VStream to Kind using VStreamKindHelper
  2. Narrow a Kind back to VStream
  3. Use VStreamFunctor.map via the HKT interface
  4. Use VStreamApplicative.of to create a singleton stream
  5. Use VStreamMonad.flatMap for HKT-level composition
  6. Use VStreamFoldable.foldMap with a sum monoid
  7. Use VStreamTraverse.traverse with Optional
  8. Use VStreamAlternative.orElse for stream fallback
  9. Write a generic function accepting any Monad, test with VStream
  10. Combine Foldable and Traverse in a pipeline
- Solution file: `Tutorial02_VStreamHKT_Solution.java`
- Time estimate: 12-15 minutes

---

## Acceptance Criteria

- [ ] VStreamKind witness type defined with correct arity
- [ ] VStream extends VStreamKind (Stage 1 interface updated)
- [ ] VStreamKindHelper enum with widen/narrow operations
- [ ] VStreamFunctor implements Functor with correct delegation
- [ ] VStreamApplicative implements Applicative with Cartesian product ap
- [ ] VStreamMonad implements Monad with correct flatMap
- [ ] VStreamFoldable implements Foldable with iterative materialisation
- [ ] VStreamTraverse implements Traverse for finite streams
- [ ] VStreamAlternative implements Alternative with concatenation
- [ ] All functor laws pass (identity, composition)
- [ ] All applicative laws pass (identity, homomorphism, interchange)
- [ ] All monad laws pass (left identity, right identity, associativity)
- [ ] All alternative laws pass (left identity, right identity, associativity)
- [ ] Foldable correctness tests pass
- [ ] Traverse structure preservation test passes
- [ ] Operational tests for each type class instance pass
- [ ] Javadoc complete on all public API
- [ ] Documentation page written
- [ ] Example code compiles and runs
- [ ] Tutorial and solution file complete
- [ ] All existing tests continue to pass

---

## GitHub Issue Summary

**Title**: VStream Stage 2: HKT Encoding and Type Class Instances

Integrate `VStream<A>` into higher-kinded-j's type-safe HKT simulation with full type class
hierarchy. This enables VStream to participate in polymorphic, type-class-based programming
alongside VTask, Maybe, Either, and other HKT-encoded types.

**Key deliverables**:
- `VStreamKind<A>` witness type with `VStreamKind.Witness` phantom type
- `VStreamKindHelper` enum singleton for widen/narrow operations
- Type class instances:
  - `VStreamFunctor` (Functor) - element-wise transformation
  - `VStreamApplicative` (Applicative) - Cartesian product semantics for `ap`
  - `VStreamMonad` (Monad) - flatMap with stream substitution and flattening
  - `VStreamFoldable` (Foldable) - monoid-based aggregation (materialises stream)
  - `VStreamTraverse` (Traverse) - effectful traversal for finite streams
  - `VStreamAlternative` (Alternative) - stream concatenation as monoid
- Comprehensive law tests using `LawTestPattern` and `@TestFactory` dynamic tests
- Operational tests for each instance
- Documentation, example code, and tutorial

**Package**: `org.higherkindedj.hkt.vstream` in `hkj-core`

**Dependencies**: Stage 1 (Core VStream Type)
