# Stage 3: VStreamPath and Effect Path API

## Overview

This stage delivers `VStreamPath<A>`, the Layer-1 fluent Effect Path wrapper for VStream.
It integrates VStream into the Effect Path ecosystem with Path factory methods, PathOps
sequence/traverse operations, conversions to other path types, and optics focus bridge
methods. VStreamPath implements `Chainable<A>` in the capability hierarchy.

**Module**: `hkj-core`
**Package**: `org.higherkindedj.hkt.effect` (for VStreamPath, Path additions, PathOps additions)

**Prerequisites**: Stages 1-2 (Core VStream Type, HKT Encoding)

---

## Detailed Tasks

### 3.1 VStreamPath Sealed Interface

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/VStreamPath.java`

Follow the pattern of VTaskPath (sealed interface with single permitted implementation):

```java
public sealed interface VStreamPath<A> extends Chainable<A>
        permits DefaultVStreamPath {

    // === Core access ===
    VStream<A> run();

    // === Composable operations (Functor) ===
    @Override
    <B> VStreamPath<B> map(Function<? super A, ? extends B> mapper);

    VStreamPath<A> peek(Consumer<? super A> consumer);

    VStreamPath<Unit> asUnit();

    // === Chainable operations (Monad) ===
    @Override
    <B> VStreamPath<B> via(Function<? super A, ? extends Chainable<B>> mapper);

    @Override
    default <B> VStreamPath<B> flatMap(
            Function<? super A, ? extends Chainable<B>> mapper) {
        return via(mapper);
    }

    @Override
    <B> VStreamPath<B> then(Supplier<? extends Chainable<B>> supplier);

    // === Combinable operations (Applicative) ===
    @Override
    <B, C> VStreamPath<C> zipWith(
            Combinable<B> other,
            BiFunction<? super A, ? super B, ? extends C> combiner);

    <B, C, D> VStreamPath<D> zipWith3(
            VStreamPath<B> second,
            VStreamPath<C> third,
            Function3<? super A, ? super B, ? super C, ? extends D> combiner);

    // === Stream-specific operations ===
    VStreamPath<A> filter(Predicate<? super A> predicate);

    VStreamPath<A> take(long n);

    VStreamPath<A> drop(long n);

    VStreamPath<A> takeWhile(Predicate<? super A> predicate);

    VStreamPath<A> dropWhile(Predicate<? super A> predicate);

    VStreamPath<A> distinct();

    VStreamPath<A> concat(VStreamPath<A> other);

    // === Materialisation to VTaskPath (terminal operations) ===
    VTaskPath<List<A>> toList();

    VTaskPath<A> fold(A identity, BinaryOperator<A> op);

    <B> VTaskPath<B> foldLeft(B identity, BiFunction<B, A, B> f);

    <M> VTaskPath<M> foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f);

    VTaskPath<Optional<A>> headOption();

    VTaskPath<Optional<A>> lastOption();

    VTaskPath<Long> count();

    VTaskPath<Boolean> exists(Predicate<? super A> predicate);

    VTaskPath<Boolean> forAll(Predicate<? super A> predicate);

    VTaskPath<Optional<A>> find(Predicate<? super A> predicate);

    VTaskPath<Unit> forEach(Consumer<? super A> consumer);

    // === Optics focus bridge ===
    <B> VStreamPath<B> focus(FocusPath<A, B> path);

    <B> VStreamPath<B> focus(AffinePath<A, B> path);

    // === Conversions ===
    VTaskPath<A> first();

    VTaskPath<A> last();

    StreamPath<A> toStreamPath();

    ListPath<A> toListPath();

    NonDetPath<A> toNonDetPath();
}
```

**Requirements**:
- Sealed interface permitting only `DefaultVStreamPath`
- Extends `Chainable<A>` (not `Effectful`, not `Recoverable`)
- All stream-specific operations return `VStreamPath<A>` (fluent API)
- Terminal operations return `VTaskPath<X>` (bridging to single-value effect)
- Focus bridge methods delegate to underlying optics
- `via` must handle `Chainable<B>` from other VStreamPaths (type check and extract)
- Comprehensive javadoc with code examples

### 3.2 DefaultVStreamPath Implementation

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/DefaultVStreamPath.java`

Follow the pattern of DefaultVTaskPath:

```java
public record DefaultVStreamPath<A>(VStream<A> stream) implements VStreamPath<A> {

    public DefaultVStreamPath {
        Objects.requireNonNull(stream, "stream must not be null");
    }

    @Override
    public VStream<A> run() {
        return stream;
    }

    @Override
    public <B> VStreamPath<B> map(Function<? super A, ? extends B> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new DefaultVStreamPath<>(stream.map(mapper));
    }

    @Override
    public <B> VStreamPath<B> via(
            Function<? super A, ? extends Chainable<B>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new DefaultVStreamPath<>(stream.flatMap(a -> {
            Chainable<B> chainable = mapper.apply(a);
            if (chainable instanceof VStreamPath<B> vsp) {
                return vsp.run();
            }
            throw new IllegalArgumentException(
                "via mapper must return VStreamPath, got: " + chainable.getClass());
        }));
    }

    // Terminal operations bridge to VTaskPath
    @Override
    public VTaskPath<List<A>> toList() {
        return Path.vtaskPath(stream.toList());
    }

    // Focus bridge
    @Override
    public <B> VStreamPath<B> focus(FocusPath<A, B> path) {
        Objects.requireNonNull(path, "path must not be null");
        return map(a -> path.get(a));
    }

    @Override
    public <B> VStreamPath<B> focus(AffinePath<A, B> path) {
        Objects.requireNonNull(path, "path must not be null");
        // Filter out elements where the affine doesn't match
        return new DefaultVStreamPath<>(
            stream.map(a -> path.getOptional(a))
                  .filter(Optional::isPresent)
                  .map(Optional::get)
        );
    }

    // ... remaining implementations
}
```

**Requirements**:
- Record-based implementation (compact constructor with null check)
- All operations delegate to underlying VStream methods
- Terminal operations wrap VStream's VTask results in VTaskPath via `Path.vtaskPath()`
- Focus with FocusPath: map using `path.get()` (always succeeds)
- Focus with AffinePath: map + filter using `path.getOptional()` (elements where affine
  doesn't match are excluded from the stream)
- `zipWith` validates that `other` is a `VStreamPath`; extracts underlying stream
- Type-safe: all casts verified with instanceof pattern matching

### 3.3 Update Chainable Sealed Permits

The `Chainable` interface is sealed. `VStreamPath` must be added to its permits list.

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/capability/Chainable.java`

Add `VStreamPath` to the permits clause. This requires careful review of the existing
permits list to ensure no conflicts.

### 3.4 Path Factory Methods

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/Path.java`

Add VStreamPath factory methods following the established naming conventions:

```java
// ===== VStreamPath factory methods =====

public static <A> VStreamPath<A> vstream(VStream<A> stream) {
    Objects.requireNonNull(stream, "stream must not be null");
    return new DefaultVStreamPath<>(stream);
}

public static <A> VStreamPath<A> vstreamOf(A... elements) {
    Objects.requireNonNull(elements, "elements must not be null");
    return new DefaultVStreamPath<>(VStream.of(elements));
}

public static <A> VStreamPath<A> vstreamFromList(List<A> list) {
    Objects.requireNonNull(list, "list must not be null");
    return new DefaultVStreamPath<>(VStream.fromList(list));
}

public static <A> VStreamPath<A> vstreamPure(A value) {
    return new DefaultVStreamPath<>(VStream.of(value));
}

public static <A> VStreamPath<A> vstreamEmpty() {
    return new DefaultVStreamPath<>(VStream.empty());
}

public static <A> VStreamPath<A> vstreamIterate(A seed, UnaryOperator<A> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVStreamPath<>(VStream.iterate(seed, f));
}

public static <A> VStreamPath<A> vstreamGenerate(Supplier<A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new DefaultVStreamPath<>(VStream.generate(supplier));
}

public static VStreamPath<Integer> vstreamRange(int startInclusive, int endExclusive) {
    return new DefaultVStreamPath<>(VStream.range(startInclusive, endExclusive));
}

public static <S, A> VStreamPath<A> vstreamUnfold(
        S seed, Function<S, VTask<Optional<Pair<A, S>>>> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVStreamPath<>(VStream.unfold(seed, f));
}
```

**Requirements**:
- Follow the pattern of existing vtask/stream factory methods
- Null parameter validation with descriptive messages
- Comprehensive javadoc with code examples
- Consistent naming: `vstream` prefix

### 3.5 PathOps Sequence and Traverse Operations

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/PathOps.java`

Add operations following the established pattern for VTaskPath:

```java
// ===== VStreamPath operations =====

// Sequence: List<VStreamPath<A>> -> VStreamPath<List<A>>
// (zips positionally, shortest wins)
public static <A> VStreamPath<List<A>> sequenceVStream(
        List<VStreamPath<A>> paths) { ... }

// Traverse: List<A> -> (A -> VStreamPath<B>) -> VStreamPath<List<B>>
public static <A, B> VStreamPath<List<B>> traverseVStream(
        List<A> items,
        Function<? super A, VStreamPath<B>> f) { ... }

// Collect: VStreamPath<A> -> VTaskPath<List<A>> (convenience)
public static <A> VTaskPath<List<A>> collectVStream(
        VStreamPath<A> stream) { ... }

// Each-based traverse (bridges to Stage 4)
public static <S, A> VStreamPath<A> traverseEachVStream(
        S structure,
        Each<S, A> each) { ... }
```

**Design note for sequence**: The sequence operation for streams is non-trivial. Unlike
sequence for Maybe/Either (which short-circuits), stream sequence must zip multiple streams
positionally. This produces a stream of lists where each list contains the i-th element from
each input stream.

### 3.6 Conversion Methods

Implement conversion methods on DefaultVStreamPath:

| Method | Target | Behaviour |
|--------|--------|-----------|
| `first()` | `VTaskPath<A>` | VTask of first element; fails if empty |
| `last()` | `VTaskPath<A>` | VTask of last element; fails if empty |
| `toStreamPath()` | `StreamPath<A>` | Materialises to list, wraps as StreamPath |
| `toListPath()` | `ListPath<A>` | Materialises to list, wraps as ListPath |
| `toNonDetPath()` | `NonDetPath<A>` | Materialises to list, wraps as NonDetPath |

**Requirements**:
- All conversion methods that materialise must document this (stream is consumed)
- `first()` and `last()` produce VTaskPath that fails with `NoSuchElementException` if empty
- Conversions preserve element order

---

## Testing

### 3.7 VStreamPath Unit Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/VStreamPathTest.java`

Follow the established pattern from VTaskPathTest (1122 lines) with `@Nested` groups:

1. **Factory Methods** (`@Nested class FactoryMethods`)
   - `Path.vstream()` wraps VStream correctly
   - `Path.vstreamOf()` with varargs
   - `Path.vstreamFromList()` from list
   - `Path.vstreamPure()` single element
   - `Path.vstreamEmpty()` empty stream
   - `Path.vstreamIterate()` infinite stream
   - `Path.vstreamGenerate()` from supplier
   - `Path.vstreamRange()` integer range
   - `Path.vstreamUnfold()` effectful unfold
   - Null parameter validation for all factory methods

2. **Run and Terminal Methods** (`@Nested class RunAndTerminal`)
   - `run()` returns underlying VStream
   - `toList().unsafeRun()` collects elements
   - `headOption().unsafeRun()` returns first
   - `headOption()` on empty returns empty Optional
   - `lastOption().unsafeRun()` returns last
   - `count().unsafeRun()` counts correctly
   - `fold()` reduces with seed
   - `foldLeft()` left-folds correctly
   - `foldMap()` with monoid
   - `exists()` short-circuits
   - `forAll()` short-circuits
   - `find()` returns first match
   - `forEach()` executes side effect

3. **Composable Operations** (`@Nested class ComposableOperations`)
   - `map()` transforms elements
   - `map()` preserves laziness (AtomicInteger counter)
   - `peek()` observes without modification
   - `asUnit()` discards values

4. **Chainable Operations** (`@Nested class ChainableOperations`)
   - `via()` chains VStreamPaths
   - `flatMap()` is alias for via
   - `then()` sequences discarding first result
   - Order verification with StringBuilder logging

5. **Combinable Operations** (`@Nested class CombinableOperations`)
   - `zipWith()` pairs positionally
   - `zipWith()` stops at shortest
   - `zipWith3()` three-way zip
   - `zipWith()` with empty stream returns empty

6. **Stream-Specific Operations** (`@Nested class StreamOperations`)
   - `filter()` keeps matching elements
   - `take()` limits elements
   - `drop()` skips elements
   - `takeWhile()` stops at first non-match
   - `dropWhile()` skips initial matches
   - `distinct()` removes duplicates
   - `concat()` appends streams

7. **Focus Bridge** (`@Nested class FocusBridge`)
   - `focus(FocusPath)` maps using lens get
   - `focus(AffinePath)` maps and filters using affine getOptional
   - Focus with null path throws NullPointerException
   - Composition: focus then filter then map

8. **Conversions** (`@Nested class Conversions`)
   - `first()` returns first element as VTaskPath
   - `first()` on empty fails
   - `last()` returns last element as VTaskPath
   - `toStreamPath()` materialises correctly
   - `toListPath()` materialises correctly
   - `toNonDetPath()` materialises correctly

9. **Complex Chaining** (`@Nested class ComplexChaining`)
   - Multi-step pipeline: filter, map, take, toList
   - Nested flatMap with sub-streams
   - Error propagation through chains
   - Laziness through complex chains

### 3.8 VStreamPath Law Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/VStreamPathLawsTest.java`

Follow the pattern from VTaskPathLawsTest and StreamPathLawsTest:

1. **Functor Laws** (`@TestFactory`)
   - Identity: `path.map(x -> x).toList() == path.toList()`
   - Composition: `path.map(f).map(g).toList() == path.map(g.compose(f)).toList()`

2. **Monad Laws** (`@TestFactory`)
   - Left Identity: `Path.vstreamPure(a).via(f).toList() == f.apply(a).toList()`
   - Right Identity: `path.via(x -> Path.vstreamPure(x)).toList() == path.toList()`
   - Associativity

3. **Stream Reusability**
   - `run()` returns same VStream reference (not re-created)
   - `toList()` can be called multiple times on same path
   - `map()` produces new path, original unchanged

### 3.9 PathOps Tests

**File**: Extend existing `PathOpsTest` or create
`hkj-core/src/test/java/org/higherkindedj/hkt/effect/VStreamPathOpsTest.java`

- `sequenceVStream` with multiple single-element streams
- `sequenceVStream` with empty list
- `sequenceVStream` with different-length streams (shortest wins)
- `traverseVStream` applying function to each item
- `collectVStream` materialises correctly
- `traverseEachVStream` bridges from Each instance

### 3.10 Custom Assertions

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/VStreamPathAssert.java`

Follow VTaskPathAssert pattern:

```java
public class VStreamPathAssert<A>
        extends AbstractAssert<VStreamPathAssert<A>, VStreamPath<A>> {

    public static <A> VStreamPathAssert<A> assertThatVStreamPath(VStreamPath<A> actual);

    // Collection assertions (materialise via toList)
    public VStreamPathAssert<A> producesElements(A... expected);
    public VStreamPathAssert<A> producesElementsInOrder(List<A> expected);
    public VStreamPathAssert<A> isEmpty();
    public VStreamPathAssert<A> hasCount(long expected);

    // Semantic equivalence
    public VStreamPathAssert<A> isEquivalentTo(VStreamPath<A> other);
}
```

---

## Documentation

### 3.11 Javadoc

All public types and methods require comprehensive javadoc:
- VStreamPath: Explain its role as Layer-1 Effect Path, relationship to Chainable
- DefaultVStreamPath: Implementation details, record semantics
- Path factory methods: Usage examples, relationship to VStream factories
- PathOps additions: Explain sequence/traverse semantics for streams
- British English throughout

### 3.12 Documentation Page

**File**: `docs/vstream/vstream_path.md`

Following STYLE-GUIDE.md:
- "What You'll Learn" admonishment
- Explain VStreamPath's role in the Effect Path hierarchy
- Diagram showing VStreamPath in the Chainable hierarchy
- Code examples for factory methods, composition, terminal operations, focus bridge
- Comparison table: VStreamPath vs StreamPath vs VTaskPath
- "Key Takeaways" admonishment
- "See Also" linking to VStreamPath, other path types, optics focus

---

## Examples

### 3.13 Example Code

**File**: `hkj-examples/src/main/java/org/higherkindedj/examples/vstream/VStreamPathExample.java`

Demonstrate:
- Creating VStreamPaths via Path factory
- Fluent composition: map, via, filter, take
- Terminal operations via VTaskPath: toList, fold, exists
- Focus bridge: navigating into stream elements with lenses
- Conversion to other path types
- Complex pipeline combining VStreamPath with VTaskPath

---

## Tutorials

### 3.14 Tutorial

**File**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vstream/Tutorial03_VStreamPath.java`

Following TUTORIAL-STYLE-GUIDE.md:
- 10-12 exercises:
  1. Create a VStreamPath using Path.vstream and collect to list
  2. Map over elements in a VStreamPath
  3. Filter elements using VStreamPath.filter
  4. Chain VStreamPaths using via
  5. Zip two VStreamPaths together
  6. Use terminal operations: fold, exists, forAll
  7. Focus into stream elements using a FocusPath (lens)
  8. Focus with AffinePath (filters non-matching elements)
  9. Convert VStreamPath to VTaskPath using first()
  10. Convert VStreamPath to StreamPath and ListPath
  11. Compose filter, map, focus, take in a single pipeline
  12. Use Path.vstreamUnfold for effectful stream creation
- Solution file: `Tutorial03_VStreamPath_Solution.java`
- Time estimate: 15 minutes

---

## Acceptance Criteria

- [ ] VStreamPath sealed interface defined with all methods
- [ ] DefaultVStreamPath record implementation complete
- [ ] Chainable permits updated to include VStreamPath
- [ ] Path factory methods added (vstream, vstreamOf, vstreamFromList, etc.)
- [ ] PathOps operations added (sequenceVStream, traverseVStream, collectVStream)
- [ ] Focus bridge methods work with FocusPath and AffinePath
- [ ] Conversion methods work correctly (toStreamPath, toListPath, etc.)
- [ ] Terminal operations correctly return VTaskPath
- [ ] All unit tests pass (target: 90%+ line coverage)
- [ ] VStreamPath law tests pass (functor, monad)
- [ ] PathOps tests pass
- [ ] Custom VStreamPathAssert class implemented
- [ ] Javadoc complete on all public API
- [ ] Documentation page written
- [ ] Example code compiles and runs
- [ ] Tutorial and solution file complete
- [ ] All existing tests continue to pass (especially sealed hierarchy tests)

---

## GitHub Issue Summary

**Title**: VStream Stage 3: VStreamPath and Effect Path API Integration

Deliver `VStreamPath<A>`, the Layer-1 fluent Effect Path wrapper for VStream, integrating
lazy virtual-thread streams into the Effect Path ecosystem alongside IOPath, VTaskPath,
StreamPath, and other path types.

**Key deliverables**:
- `VStreamPath<A>` sealed interface extending `Chainable<A>` with stream-specific operations
- `DefaultVStreamPath<A>` record implementation
- Path factory methods: `vstream`, `vstreamOf`, `vstreamFromList`, `vstreamPure`,
  `vstreamEmpty`, `vstreamIterate`, `vstreamGenerate`, `vstreamRange`, `vstreamUnfold`
- PathOps: `sequenceVStream`, `traverseVStream`, `collectVStream`, `traverseEachVStream`
- Terminal operations returning `VTaskPath<X>`: `toList`, `fold`, `foldLeft`, `foldMap`,
  `headOption`, `lastOption`, `count`, `exists`, `forAll`, `find`, `forEach`
- Optics focus bridge: `focus(FocusPath)` maps elements, `focus(AffinePath)` maps and
  filters
- Conversions: `first()`, `last()`, `toStreamPath()`, `toListPath()`, `toNonDetPath()`
- Update Chainable sealed permits to include VStreamPath
- Comprehensive tests following @Nested pattern, law tests, custom assertions
- Documentation, example code, and tutorial with 12 exercises

**Package**: `org.higherkindedj.hkt.effect` in `hkj-core`

**Dependencies**: Stages 1-2 (Core VStream Type, HKT Encoding)
