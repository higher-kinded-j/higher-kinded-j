# Stage 1: Core VStream Type

## Overview

This stage delivers the foundational `VStream<A>` type: the lazy, pull-based stream
abstraction that executes element production on virtual threads via `VTask`. It includes the
`Step` sealed interface, all factory methods, core combinators (map, flatMap, filter, take,
fold, etc.), and comprehensive tests following established higher-kinded-j patterns.

**Module**: `hkj-core`
**Package**: `org.higherkindedj.hkt.vstream`

**Prerequisites**: None (builds on existing VTask infrastructure)

---

## Detailed Tasks

### 1.1 Define the VStream Interface and Step Type

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStream.java`

Create the core `VStream<A>` functional interface and `Step<A>` sealed interface:

```java
@FunctionalInterface
public interface VStream<A> {
    VTask<Step<A>> pull();

    sealed interface Step<A> {
        record Emit<A>(@Nullable A value, VStream<A> tail) implements Step<A> {}
        record Done<A>()                                    implements Step<A> {}
        record Skip<A>(VStream<A> tail)                     implements Step<A> {}
    }
}
```

**Requirements**:
- `VStream<A>` is a `@FunctionalInterface` with a single abstract method `pull()`
- `Step<A>` is a sealed interface with three permitted record implementations
- `Emit` carries the current value and the continuation stream
- `Done` signals stream exhaustion
- `Skip` advances the stream without producing a value (used by filter)
- All records use `@Nullable` annotation from jspecify where appropriate
- Null checks via `Objects.requireNonNull()` with descriptive messages on public API
- Comprehensive javadoc with `@param`, `@return`, `@see` tags
- Copyright header per project convention

### 1.2 Factory Methods

Add static factory methods to `VStream<A>`:

| Method | Signature | Description |
|--------|-----------|-------------|
| `empty()` | `<A> VStream<A>` | Stream that immediately completes |
| `of(A)` | `<A> VStream<A>` | Single-element stream |
| `of(A...)` | `@SafeVarargs <A> VStream<A>` | Stream from varargs |
| `fromList(List<A>)` | `<A> VStream<A>` | Stream from a list (lazy iteration) |
| `fromStream(Stream<A>)` | `<A> VStream<A>` | Stream from a Java stream (consumed lazily via iterator) |
| `succeed(A)` | `<A> VStream<A>` | Alias for `of(A)`, consistent with VTask naming |
| `fail(Throwable)` | `<A> VStream<A>` | Stream whose first pull fails with the given error |
| `iterate(A, Function<A, A>)` | `<A> VStream<A>` | Infinite stream from seed and step function |
| `unfold(S, Function<S, VTask<Optional<Pair<A, S>>>>)` | `<S, A> VStream<A>` | Effectful unfold from seed |
| `generate(Supplier<A>)` | `<A> VStream<A>` | Infinite stream from supplier |
| `concat(VStream<A>, VStream<A>)` | `<A> VStream<A>` | Concatenate two streams |
| `repeat(A)` | `<A> VStream<A>` | Infinite stream repeating a single value |
| `range(int, int)` | `VStream<Integer>` | Stream of integers in range [start, end) |
| `defer(Supplier<VStream<A>>)` | `<A> VStream<A>` | Deferred stream construction |

**Requirements**:
- All factory methods are static with comprehensive javadoc
- Null parameters validated with `Objects.requireNonNull()`
- `fromList` does not copy the list; it iterates lazily using an index counter
- `fromStream` consumes via iterator, wrapping in VTask for each element
- `unfold` is the most general factory; others can be expressed in terms of it
- `defer` enables recursive stream definitions without stack overflow

### 1.3 Core Combinators

Add default methods to `VStream<A>`:

#### Transformation

| Method | Signature | Description |
|--------|-----------|-------------|
| `map(Function<A, B>)` | `<B> VStream<B>` | Transform each element |
| `flatMap(Function<A, VStream<B>>)` | `<B> VStream<B>` | Substitute each element with a sub-stream |
| `via(Function<A, VStream<B>>)` | `<B> VStream<B>` | Alias for flatMap (FocusDSL vocabulary) |
| `mapTask(Function<A, VTask<B>>)` | `<B> VStream<B>` | Transform each element via a VTask |

#### Filtering

| Method | Signature | Description |
|--------|-----------|-------------|
| `filter(Predicate<A>)` | `VStream<A>` | Keep elements matching predicate (uses Skip) |
| `takeWhile(Predicate<A>)` | `VStream<A>` | Take elements while predicate holds |
| `dropWhile(Predicate<A>)` | `VStream<A>` | Drop elements while predicate holds |
| `take(long)` | `VStream<A>` | Take first n elements |
| `drop(long)` | `VStream<A>` | Drop first n elements |
| `distinct()` | `VStream<A>` | Remove duplicate elements (uses HashSet internally) |

#### Combination

| Method | Signature | Description |
|--------|-----------|-------------|
| `concat(VStream<A>)` | `VStream<A>` | Append another stream |
| `prepend(A)` | `VStream<A>` | Add element at the beginning |
| `append(A)` | `VStream<A>` | Add element at the end |
| `zipWith(VStream<B>, BiFunction<A, B, C>)` | `<B, C> VStream<C>` | Positional zip (shortest wins) |
| `zip(VStream<B>)` | `<B> VStream<Pair<A, B>>` | Positional zip to pairs |
| `interleave(VStream<A>)` | `VStream<A>` | Alternate elements from both streams |

#### Observation

| Method | Signature | Description |
|--------|-----------|-------------|
| `peek(Consumer<A>)` | `VStream<A>` | Side effect on each element |
| `onComplete(Runnable)` | `VStream<A>` | Run action when stream completes |

#### Terminal Operations (return VTask)

| Method | Signature | Description |
|--------|-----------|-------------|
| `toList()` | `VTask<List<A>>` | Collect all elements to a list |
| `fold(A, BinaryOperator<A>)` | `VTask<A>` | Left fold with seed |
| `foldLeft(B, BiFunction<B, A, B>)` | `<B> VTask<B>` | Left fold with accumulator |
| `headOption()` | `VTask<Optional<A>>` | First element or empty |
| `lastOption()` | `VTask<Optional<A>>` | Last element or empty |
| `count()` | `VTask<Long>` | Count elements |
| `exists(Predicate<A>)` | `VTask<Boolean>` | Whether any element matches (short-circuits) |
| `forAll(Predicate<A>)` | `VTask<Boolean>` | Whether all elements match (short-circuits) |
| `find(Predicate<A>)` | `VTask<Optional<A>>` | First element matching predicate |
| `forEach(Consumer<A>)` | `VTask<Unit>` | Execute side effect for each element |
| `run()` | `VTask<Unit>` | Drain the stream, discarding all elements |
| `asUnit()` | `VStream<Unit>` | Map all elements to Unit |

**Implementation requirements**:
- `map` wraps the original pull, transforming Emit values; Skip and Done pass through
- `flatMap` maintains an "inner stream" state; when the inner stream completes, pull from
  outer for the next element
- `filter` returns Skip for non-matching elements, preserving laziness
- `take`/`drop` use a counter; `take(0)` returns empty immediately
- `toList` uses an iterative loop (not recursion) for stack safety
- `fold`/`foldLeft` use iterative consumption
- `exists` and `forAll` short-circuit: stop pulling once result is determined
- `distinct` maintains a `Set<A>` across pulls (caution: unbounded for infinite streams;
  document this clearly)
- All combinators preserve laziness; no element is produced until a terminal operation runs

### 1.4 Error Handling

| Method | Signature | Description |
|--------|-----------|-------------|
| `recover(Function<Throwable, A>)` | `VStream<A>` | Replace failed pull with a recovery value |
| `recoverWith(Function<Throwable, VStream<A>>)` | `VStream<A>` | Replace failed pull with a recovery stream |
| `mapError(Function<Throwable, Throwable>)` | `VStream<A>` | Transform errors |
| `onError(Consumer<Throwable>)` | `VStream<A>` | Observe errors without recovery |

**Requirements**:
- Error handling wraps the pull VTask with `recover`/`recoverWith`
- Recovery applies per-pull, not per-stream (each element pull can independently fail and
  recover)
- Document clearly that recovery replaces a single failed pull; the stream continues from
  the recovery point

### 1.5 Utility Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `runSafe()` | `VTask<Try<List<A>>>` | Collect to list, capturing errors |
| `runAsync()` | `CompletableFuture<List<A>>` | Collect to list asynchronously |
| `toStreamPath()` | `StreamPath<A>` | Convert to StreamPath (materialises) |

### 1.6 Stack Safety

The `flatMap` implementation must handle deep chains without stack overflow. The approach:

- Maintain an iterative loop in the pull evaluation that unfolds nested flatMap continuations
- When a `flatMap` produces another `flatMap`, accumulate continuations in a stack/queue
  rather than nesting VTask closures
- Test with chains of 10,000+ flatMap operations to verify stack safety

---

## Testing

### 1.7 Unit Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamTest.java`

Follow the established `@Nested` class structure pattern (as in VTaskTest, VTaskPathTest):

#### Test Groups

1. **Factory Methods** (`@Nested class FactoryMethods`)
   - `empty()` produces Done on first pull
   - `of(value)` produces Emit then Done
   - `of(varargs)` produces elements in order then Done
   - `fromList()` produces elements in list order
   - `fromStream()` consumes stream lazily
   - `succeed()` is equivalent to `of()`
   - `fail()` produces failed VTask on first pull
   - `iterate()` produces infinite sequence
   - `unfold()` produces elements until Optional.empty
   - `generate()` produces infinite sequence from supplier
   - `range()` produces correct integer range
   - `repeat()` produces repeating elements
   - `defer()` defers construction
   - `concat()` produces elements from both streams in order
   - Null parameter validation for all factory methods

2. **Transformation Operations** (`@Nested class TransformationOperations`)
   - `map()` transforms each element
   - `map()` preserves laziness (counter verification with AtomicInteger)
   - `flatMap()` substitutes and flattens
   - `flatMap()` with empty inner streams
   - `via()` is equivalent to `flatMap()`
   - `mapTask()` applies effectful transformation

3. **Filtering Operations** (`@Nested class FilteringOperations`)
   - `filter()` keeps matching elements
   - `filter()` on empty stream returns empty
   - `filter()` with no matches returns empty
   - `takeWhile()` stops at first non-match
   - `dropWhile()` skips initial matches
   - `take(n)` returns first n elements
   - `take(0)` returns empty
   - `drop(n)` skips first n elements
   - `distinct()` removes duplicates

4. **Combination Operations** (`@Nested class CombinationOperations`)
   - `concat()` appends streams
   - `prepend()` adds element at start
   - `append()` adds element at end
   - `zipWith()` pairs positionally (shortest wins)
   - `zip()` produces pairs
   - `interleave()` alternates elements

5. **Terminal Operations** (`@Nested class TerminalOperations`)
   - `toList()` collects all elements
   - `fold()` reduces with seed
   - `foldLeft()` left-folds with accumulator
   - `headOption()` returns first element
   - `headOption()` on empty returns empty
   - `lastOption()` returns last element
   - `count()` counts elements
   - `exists()` short-circuits on match
   - `forAll()` short-circuits on non-match
   - `find()` returns first matching element
   - `forEach()` executes side effect for all elements
   - `run()` drains the stream

6. **Error Handling** (`@Nested class ErrorHandling`)
   - `recover()` replaces failed pull with value
   - `recoverWith()` replaces failed pull with stream
   - `mapError()` transforms error
   - `onError()` observes error
   - Error propagation through map/flatMap chains

7. **Laziness Verification** (`@Nested class LazinessVerification`)
   - No elements produced until terminal operation
   - Multiple chained operations remain lazy
   - Execution count tracking with `AtomicInteger`
   - `take(n)` stops pulling after n elements from infinite stream

8. **Stack Safety** (`@Nested class StackSafety`)
   - `flatMap` chain of 10,000 operations
   - `map` chain of 10,000 operations
   - `toList` on stream of 100,000 elements
   - `fold` on stream of 100,000 elements

9. **Edge Cases** (`@Nested class EdgeCases`)
   - Null elements in stream (permitted if `@Nullable`)
   - Single-element streams
   - Empty streams through all combinators
   - Very large take/drop values
   - `take` from infinite stream terminates
   - `exists` on infinite stream short-circuits

10. **Integration Tests** (`@Nested class IntegrationTests`)
    - Complex pipeline: filter, map, flatMap, take, toList
    - Effectful unfold with simulated I/O
    - Error recovery mid-stream
    - Zip with different-length streams

### 1.8 Custom Assertions

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamAssert.java`

Follow the pattern of `VTaskAssert` and `VTaskPathAssert`:

```java
public class VStreamAssert<A> extends AbstractAssert<VStreamAssert<A>, VStream<A>> {
    public static <A> VStreamAssert<A> assertThatVStream(VStream<A> actual);

    // Collection assertions (materialise then assert)
    public VStreamAssert<A> producesElements(A... expected);
    public VStreamAssert<A> producesElementsInOrder(List<A> expected);
    public VStreamAssert<A> isEmpty();
    public VStreamAssert<A> hasCount(long expected);
    public VStreamAssert<A> firstElement(A expected);

    // Error assertions
    public VStreamAssert<A> failsOnPull();
    public VStreamAssert<A> failsWithExceptionType(Class<? extends Throwable> type);

    // Laziness assertions
    public VStreamAssert<A> hasNotExecuted(AtomicInteger counter);
}
```

---

## Documentation

### 1.9 Javadoc

All public types and methods require comprehensive javadoc following project conventions:
- Class-level javadoc with description, usage examples in `<pre>{@code}` blocks
- `@param`, `@return`, `@throws`, `@see` tags on all public methods
- Document laziness guarantees, stack safety properties, and infinite stream behaviour
- British English spelling throughout
- No emojis

### 1.10 Documentation Page

**File**: `docs/vstream/vstream_core.md` (reference page for the core VStream type)

Following the STYLE-GUIDE.md conventions:
- "What You'll Learn" admonishment (info type)
- Problem-solution structure showing the gap VStream fills
- Comparison table: VStream vs StreamPath vs Java Stream
- Code examples for factory methods, combinators, terminal operations
- "Key Takeaways" admonishment
- "See Also" linking to VTask, StreamPath, VStreamPath (Stage 3)
- Previous/Next navigation links

---

## Examples

### 1.11 Example Code

**File**: `hkj-examples/src/main/java/org/higherkindedj/examples/vstream/VStreamBasicExample.java`

Demonstrate:
- Creating streams from various sources
- Transformation pipelines (map, filter, take)
- Effectful unfold (simulated paginated API)
- Error recovery patterns
- Terminal operations (toList, fold, exists)
- Lazy evaluation benefits (infinite stream with take)

---

## Tutorials

### 1.12 Tutorial

**File**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vstream/Tutorial01_VStreamBasics.java`

Following TUTORIAL-STYLE-GUIDE.md conventions:
- Copyright header
- Class-level javadoc with "Key Concepts" list and prerequisites
- `answerRequired()` helper
- 8-10 exercises progressing from simple to complex:
  1. Create a VStream from a list and collect to list
  2. Map over stream elements
  3. Filter elements from a stream
  4. Use take to limit an infinite stream
  5. FlatMap to expand elements into sub-streams
  6. Fold a stream to a single value
  7. Use unfold to create an effectful stream
  8. Compose filter, map, and take in a pipeline
  9. Handle errors in a stream with recover
  10. Check existence with short-circuiting
- Corresponding solution file: `Tutorial01_VStreamBasics_Solution.java`
- AssertJ assertions throughout
- British English, no emojis (except final congratulations)
- Time estimate: 12-15 minutes

---

## Acceptance Criteria

- [ ] `VStream<A>` interface with `pull()` method compiles and is usable
- [ ] `Step<A>` sealed interface with `Emit`, `Done`, `Skip` records
- [ ] All factory methods implemented and null-validated
- [ ] All core combinators (map, flatMap, filter, take, fold, etc.) implemented
- [ ] Error handling methods (recover, recoverWith, mapError) implemented
- [ ] Laziness verified: no elements produced until terminal operation
- [ ] Stack safety verified: 10,000+ deep flatMap chains
- [ ] All unit tests pass (target: 90%+ line coverage for VStream)
- [ ] Custom VStreamAssert class implemented
- [ ] Javadoc complete on all public API
- [ ] Documentation page written following style guide
- [ ] Example code compiles and runs
- [ ] Tutorial and solution file complete
- [ ] Code formatted with Google Java Format via Spotless
- [ ] All existing tests continue to pass

---

## GitHub Issue Summary

**Title**: VStream Stage 1: Core VStream\<A\> Type - Lazy Pull-Based Streaming on Virtual Threads

Introduce `VStream<A>`, a lazy, pull-based streaming abstraction that executes element
production on virtual threads via `VTask`. This is the foundational type for virtual
thread-based streaming in higher-kinded-j.

**Key deliverables**:
- `VStream<A>` functional interface with `pull()` returning `VTask<Step<A>>`
- `Step<A>` sealed interface: `Emit` (value + continuation), `Done` (completion),
  `Skip` (efficient filtering)
- Factory methods: `empty`, `of`, `fromList`, `fromStream`, `iterate`, `unfold`, `generate`,
  `range`, `repeat`, `defer`, `concat`, `fail`
- Combinators: `map`, `flatMap`/`via`, `mapTask`, `filter`, `take`, `drop`, `takeWhile`,
  `dropWhile`, `distinct`, `concat`, `zipWith`, `zip`, `interleave`, `peek`
- Terminal operations: `toList`, `fold`, `foldLeft`, `headOption`, `lastOption`, `count`,
  `exists`, `forAll`, `find`, `forEach`, `run`
- Error handling: `recover`, `recoverWith`, `mapError`, `onError`
- Stack-safe flatMap implementation (verified with 10,000+ chain depth)
- Custom `VStreamAssert` test assertions
- Comprehensive unit tests following established @Nested class pattern
- Documentation, example code, and tutorial with exercises

**Package**: `org.higherkindedj.hkt.vstream` in `hkj-core`

**Dependencies**: Existing VTask infrastructure only. No changes to existing code required.
