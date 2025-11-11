# Stream Support - Phase 3 Roadmap

## Overview

Phase 3 focuses on implementing additional type classes for Stream and enhancing integration with the higher-kinded-j ecosystem. This document provides a comprehensive breakdown of proposed features, implementation details, and priorities.

**Status**: Planning
**Dependencies**: Phase 1 (Core) and Phase 2 (Traverse & Ops) completed
**Estimated Total Effort**: 45-62 hours

---

## Table of Contents

1. [Type Class Implementations](#type-class-implementations)
   - [StreamSelective](#31-streamselective)
   - [StreamAlternative](#32-streamalternative)
   - [StreamMonadPlus](#33-streammonadplus)
   - [StreamZipApplicative](#34-streamzipapplicative)
   - [NonEmptyStreamComonad](#35-nonemptystreamcomonad-optional)
2. [Extended Utilities](#extended-utilities)
3. [Integration Enhancements](#integration-enhancements)
4. [Documentation](#documentation-and-examples)
5. [Testing & Benchmarking](#testing-enhancements)
6. [Implementation Timeline](#implementation-timeline)
7. [Priority Matrix](#priority-recommendations)

---

## Type Class Implementations

### 3.1 StreamSelective

**Priority**: High
**Effort**: 4-6 hours (150 LOC + 300 test LOC)
**Dependencies**: Either type (if not present in project)

#### Rationale
The Selective type class provides a middle ground between Applicative and Monad, allowing conditional effects with better performance characteristics than full monadic composition.

#### Implementation

```java
public enum StreamSelective implements Selective<StreamKind.Witness> {
  INSTANCE;

  @Override
  public <A, B> Kind<StreamKind.Witness, B> select(
      Kind<StreamKind.Witness, Either<A, B>> fab,
      Kind<StreamKind.Witness, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, StreamSelective.class, SELECT, "either");
    Validation.kind().requireNonNull(ff, StreamSelective.class, SELECT, "function");

    Stream<Either<A, B>> eithers = STREAM.narrow(fab);
    Stream<Function<A, B>> functions = STREAM.narrow(ff);

    // Eagerly collect functions to avoid single-use stream violation
    List<Function<A, B>> functionList = functions.collect(Collectors.toList());

    Stream<B> result = eithers.flatMap(either ->
      either.fold(
        left -> functionList.isEmpty()
          ? Stream.empty()
          : Stream.of(functionList.get(0).apply(left)),
        right -> Stream.of(right)
      )
    );

    return STREAM.widen(result);
  }
}
```

#### Benefits
- **Lazy branching**: Only evaluate alternatives when needed
- **Efficient short-circuiting**: Skip computation for Right values
- **Static analysis**: Better optimization opportunities than Monad

#### Use Cases

```java
// Conditional stream processing with defaults
Kind<StreamKind.Witness, Either<Config, Result>> configOrResults = ...;
Kind<StreamKind.Witness, Function<Config, Result>> defaultProcessors = ...;

// Only applies function to Left (missing config) values
Kind<StreamKind.Witness, Result> processed =
    StreamSelective.INSTANCE.select(configOrResults, defaultProcessors);
```

#### Files to Create
- `src/main/java/org/higherkindedj/hkt/stream/StreamSelective.java`
- `src/test/java/org/higherkindedj/hkt/stream/StreamSelectiveTest.java`
- Update `package-info.java` with Selective documentation

#### Test Coverage Required
- Empty stream handling
- All Left values
- All Right values
- Mixed Left/Right values
- Lazy evaluation verification
- Selective laws validation

---

### 3.2 StreamAlternative

**Priority**: High
**Effort**: 2-3 hours (100 LOC + 200 test LOC)
**Dependencies**: None

#### Rationale
Alternative provides choice operations and fallback logic, essential for robust stream processing pipelines.

#### Implementation

```java
public enum StreamAlternative implements Alternative<StreamKind.Witness> {
  INSTANCE;

  @Override
  public <A> Kind<StreamKind.Witness, A> empty() {
    return STREAM.widen(Stream.empty());
  }

  @Override
  public <A> Kind<StreamKind.Witness, A> or(
      Kind<StreamKind.Witness, A> fa1,
      Kind<StreamKind.Witness, A> fa2) {
    Validation.kind().requireNonNull(fa1, StreamAlternative.class, OR, "first");
    Validation.kind().requireNonNull(fa2, StreamAlternative.class, OR, "second");

    // Concatenate streams - first stream, then second
    return StreamOps.concat(fa1, fa2);
  }

  // Some combinator - at least one element
  public <A> Kind<StreamKind.Witness, A> some(
      Kind<StreamKind.Witness, A> fa) {
    Stream<A> stream = STREAM.narrow(fa);
    return STREAM.widen(stream.filter(__ -> true)); // Identity filter for non-empty
  }

  // Many combinator - zero or more elements, collected into lists
  public <A> Kind<StreamKind.Witness, List<A>> many(
      Kind<StreamKind.Witness, A> fa) {
    Stream<A> stream = STREAM.narrow(fa);
    List<A> collected = stream.collect(Collectors.toList());
    return STREAM.widen(Stream.of(collected));
  }
}
```

#### Use Cases

```java
// Try multiple data sources with fallback
Kind<StreamKind.Witness, User> users =
    StreamAlternative.INSTANCE.or(
        fetchFromCache(),
        StreamAlternative.INSTANCE.or(
            fetchFromDatabase(),
            fetchFromRemote()
        )
    );

// Parser with fallback
Kind<StreamKind.Witness, Config> config =
    StreamAlternative.INSTANCE.or(
        parseJsonConfig(),
        parseYamlConfig()
    );
```

#### Files to Create
- `src/main/java/org/higherkindedj/hkt/stream/StreamAlternative.java`
- `src/test/java/org/higherkindedj/hkt/stream/StreamAlternativeTest.java`

#### Test Coverage Required
- Empty alternatives
- First succeeds, second not evaluated
- First empty, second succeeds
- Both empty
- Alternative laws (identity, associativity)
- some/many combinators

---

### 3.3 StreamMonadPlus

**Priority**: High
**Effort**: 2 hours (80 LOC + 150 test LOC)
**Dependencies**: StreamAlternative

#### Rationale
Combines Monad and Alternative, enabling filtering and choice operations in monadic contexts.

#### Implementation

```java
public enum StreamMonadPlus implements MonadPlus<StreamKind.Witness> {
  INSTANCE;

  // Inherits flatMap from Monad
  // Inherits or/empty from Alternative

  @Override
  public <A> Kind<StreamKind.Witness, A> mfilter(
      Predicate<A> predicate,
      Kind<StreamKind.Witness, A> fa) {
    Validation.function().requireNonNull(predicate, "predicate",
        StreamMonadPlus.class, MFILTER);
    return StreamOps.filter(predicate, fa);
  }

  // Guard operation - like list comprehension guards
  public <A> Kind<StreamKind.Witness, A> guard(
      boolean condition,
      Supplier<Kind<StreamKind.Witness, A>> action) {
    return condition ? action.get() : StreamMonad.INSTANCE.zero();
  }

  // MonadPlus combination
  public <A> Kind<StreamKind.Witness, A> mplus(
      Kind<StreamKind.Witness, A> fa1,
      Kind<StreamKind.Witness, A> fa2) {
    return StreamAlternative.INSTANCE.or(fa1, fa2);
  }
}
```

#### Use Cases

```java
// Filter and transform in one pipeline
Kind<StreamKind.Witness, Result> results =
    StreamMonadPlus.INSTANCE.flatMap(
      data -> StreamMonadPlus.INSTANCE.mfilter(d -> d.isValid(), process(data)),
      inputStream
    );

// Guard usage
Kind<StreamKind.Witness, Config> config =
    StreamMonadPlus.INSTANCE.guard(hasPermission, () -> loadConfig());
```

#### Files to Create
- `src/main/java/org/higherkindedj/hkt/stream/StreamMonadPlus.java`
- `src/test/java/org/higherkindedj/hkt/stream/StreamMonadPlusTest.java`

#### Test Coverage Required
- mfilter operation
- guard with true/false conditions
- MonadPlus laws
- Integration with flatMap

---

### 3.4 StreamZipApplicative

**Priority**: Medium
**Effort**: 3-4 hours (120 LOC + 250 test LOC)
**Dependencies**: None (uses existing StreamOps.zip)

#### Rationale
Provides element-wise pairing semantics as an alternative to the cartesian product semantics of StreamMonad's ap operation.

#### Implementation

```java
public enum StreamZipApplicative implements Applicative<StreamKind.Witness> {
  INSTANCE;

  @Override
  public <A> Kind<StreamKind.Witness, A> of(A value) {
    return StreamMonad.INSTANCE.of(value);
  }

  @Override
  public <A, B> Kind<StreamKind.Witness, B> map(
      Function<? super A, ? extends B> f,
      Kind<StreamKind.Witness, A> fa) {
    return StreamFunctor.INSTANCE.map(f, fa);
  }

  @Override
  public <A, B> Kind<StreamKind.Witness, B> ap(
      Kind<StreamKind.Witness, Function<A, B>> ff,
      Kind<StreamKind.Witness, A> fa) {
    Validation.kind().requireNonNull(ff, StreamZipApplicative.class, AP, "function");
    Validation.kind().requireNonNull(fa, StreamZipApplicative.class, AP, "argument");

    // Zip functions with values element-wise (not cartesian product)
    return StreamOps.zip(ff, fa, (func, value) -> func.apply(value));
  }

  // Product operation for combining multiple streams
  public <A, B> Kind<StreamKind.Witness, Tuple2<A, B>> product(
      Kind<StreamKind.Witness, A> fa,
      Kind<StreamKind.Witness, B> fb) {
    return StreamOps.zip(fa, fb, Tuple2::of);
  }
}
```

#### Use Cases

```java
// Apply transformations in parallel (element-wise)
Kind<StreamKind.Witness, Function<Integer, String>> formatters =
    STREAM.widen(Stream.of(x -> "0x" + x, Object::toString, x -> x + "L"));
Kind<StreamKind.Witness, Integer> values =
    STREAM.widen(Stream.of(10, 20, 30));

// Result: ["0xa", "20", "30L"] - paired element-wise
Kind<StreamKind.Witness, String> formatted =
    StreamZipApplicative.INSTANCE.ap(formatters, values);

// Combine related data streams
Kind<StreamKind.Witness, User> users = ...;
Kind<StreamKind.Witness, Profile> profiles = ...;
Kind<StreamKind.Witness, UserWithProfile> combined =
    StreamZipApplicative.INSTANCE.map2(users, profiles, UserWithProfile::new);
```

#### Files to Create
- `src/main/java/org/higherkindedj/hkt/stream/StreamZipApplicative.java`
- `src/test/java/org/higherkindedj/hkt/stream/StreamZipApplicativeTest.java`
- Documentation comparing with StreamMonad's cartesian product semantics

#### Test Coverage Required
- Element-wise pairing
- Different length streams (shorter wins)
- Empty stream handling
- Applicative laws with zip semantics
- Comparison tests with cartesian product

---

### 3.5 NonEmptyStreamComonad (Optional)

**Priority**: Low
**Effort**: 6-8 hours (200 LOC + 300 test LOC)
**Dependencies**: NonEmptyStream wrapper type

#### Rationale
Comonad is the dual of Monad, providing extract and extend operations. Regular streams cannot safely implement Comonad due to lack of guaranteed head element.

#### Implementation

```java
// Non-empty stream wrapper
public record NonEmptyStream<A>(A head, Stream<A> tail)
    implements Kind<NonEmptyStream.Witness, A> {

  public static final class Witness {
    private Witness() {}
  }

  public Stream<A> toStream() {
    return Stream.concat(Stream.of(head), tail);
  }

  public static <A> Optional<NonEmptyStream<A>> fromStream(Stream<A> stream) {
    Iterator<A> iter = stream.iterator();
    if (!iter.hasNext()) return Optional.empty();

    A head = iter.next();
    Stream<A> tail = StreamSupport.stream(
      Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED),
      false
    );
    return Optional.of(new NonEmptyStream<>(head, tail));
  }

  public static <A> NonEmptyStream<A> narrow(Kind<Witness, A> kind) {
    return (NonEmptyStream<A>) kind;
  }
}

// Comonad implementation
public enum NonEmptyStreamComonad implements Comonad<NonEmptyStream.Witness> {
  INSTANCE;

  @Override
  public <A> A extract(Kind<NonEmptyStream.Witness, A> wa) {
    NonEmptyStream<A> nes = NonEmptyStream.narrow(wa);
    return nes.head(); // Safe - always has head element
  }

  @Override
  public <A, B> Kind<NonEmptyStream.Witness, B> extend(
      Function<Kind<NonEmptyStream.Witness, A>, B> f,
      Kind<NonEmptyStream.Witness, A> wa) {
    NonEmptyStream<A> nes = NonEmptyStream.narrow(wa);

    // Create stream of all tails with f applied to each
    List<NonEmptyStream<A>> tails = new ArrayList<>();
    tails.add(nes);

    Iterator<A> tailIter = nes.tail().iterator();
    while (tailIter.hasNext()) {
      A nextHead = tailIter.next();
      Stream<A> nextTail = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(tailIter, Spliterator.ORDERED),
        false
      );
      tails.add(new NonEmptyStream<>(nextHead, nextTail));
    }

    Stream<B> extended = tails.stream().map(f::apply);
    return NonEmptyStream.fromStream(extended).orElseThrow();
  }
}
```

#### Recommendation
**Consider skipping** unless there's strong user demand. Not a natural fit for Stream.

#### Files to Create (if implemented)
- `src/main/java/org/higherkindedj/hkt/stream/NonEmptyStream.java`
- `src/main/java/org/higherkindedj/hkt/stream/NonEmptyStreamComonad.java`
- `src/test/java/org/higherkindedj/hkt/stream/NonEmptyStreamComonadTest.java`

---

## Extended Utilities

### 3.6 StreamBuilder Pattern

**Priority**: High
**Effort**: 2 hours (80 LOC + 100 test LOC)

```java
public final class StreamBuilder<A> {
  private final List<A> elements = new ArrayList<>();

  public StreamBuilder<A> add(A element) {
    Validation.function().requireNonNullArgument(element, "element");
    elements.add(element);
    return this;
  }

  public StreamBuilder<A> addAll(Iterable<A> items) {
    Validation.function().requireNonNullArgument(items, "items");
    items.forEach(elements::add);
    return this;
  }

  public StreamBuilder<A> addStream(Stream<A> stream) {
    Validation.function().requireNonNullArgument(stream, "stream");
    stream.forEach(elements::add);
    return this;
  }

  public StreamBuilder<A> addIf(boolean condition, A element) {
    if (condition) add(element);
    return this;
  }

  public Kind<StreamKind.Witness, A> build() {
    return STREAM.widen(elements.stream());
  }
}
```

**Files**: `StreamBuilder.java`, `StreamBuilderTest.java`

---

### 3.7 StreamCollectors

**Priority**: High
**Effort**: 4-5 hours (200 LOC + 250 test LOC)

```java
public final class StreamCollectors {

  // Collector to StreamKind
  public static <A> Collector<A, ?, Kind<StreamKind.Witness, A>> toStreamKind() {
    return Collectors.collectingAndThen(
      Collectors.toList(),
      list -> STREAM.widen(list.stream())
    );
  }

  // Grouped stream
  public static <A, K> Kind<StreamKind.Witness, Tuple2<K, List<A>>> groupBy(
      Function<A, K> keyFunction,
      Kind<StreamKind.Witness, A> stream) {
    Stream<A> s = STREAM.narrow(stream);
    Map<K, List<A>> grouped = s.collect(Collectors.groupingBy(keyFunction));
    return STREAM.widen(
      grouped.entrySet().stream()
        .map(e -> Tuple2.of(e.getKey(), e.getValue()))
    );
  }

  // Partition by predicate
  public static <A> Tuple2<Kind<StreamKind.Witness, A>, Kind<StreamKind.Witness, A>>
      partition(
          Predicate<A> predicate,
          Kind<StreamKind.Witness, A> stream) {
    Stream<A> s = STREAM.narrow(stream);
    Map<Boolean, List<A>> partitioned =
      s.collect(Collectors.partitioningBy(predicate));
    return Tuple2.of(
      STREAM.widen(partitioned.get(true).stream()),
      STREAM.widen(partitioned.get(false).stream())
    );
  }

  // Frequency map
  public static <A> Kind<StreamKind.Witness, Tuple2<A, Long>> frequencies(
      Kind<StreamKind.Witness, A> stream) {
    Stream<A> s = STREAM.narrow(stream);
    Map<A, Long> freq = s.collect(
      Collectors.groupingBy(Function.identity(), Collectors.counting())
    );
    return STREAM.widen(
      freq.entrySet().stream()
        .map(e -> Tuple2.of(e.getKey(), e.getValue()))
    );
  }
}
```

**Files**: `StreamCollectors.java`, `StreamCollectorsTest.java`

---

### 3.8 ParallelStreamOps

**Priority**: Medium
**Effort**: 2 hours (60 LOC + 100 test LOC)

```java
public final class ParallelStreamOps {

  public static <A> Kind<StreamKind.Witness, A> parallel(
      Kind<StreamKind.Witness, A> stream) {
    return STREAM.widen(STREAM.narrow(stream).parallel());
  }

  public static <A> Kind<StreamKind.Witness, A> sequential(
      Kind<StreamKind.Witness, A> stream) {
    return STREAM.widen(STREAM.narrow(stream).sequential());
  }

  public static <A> boolean isParallel(Kind<StreamKind.Witness, A> stream) {
    return STREAM.narrow(stream).isParallel();
  }

  public static <A> Kind<StreamKind.Witness, A> unordered(
      Kind<StreamKind.Witness, A> stream) {
    return STREAM.widen(STREAM.narrow(stream).unordered());
  }
}
```

**Files**: `ParallelStreamOps.java`, `ParallelStreamOpsTest.java`

---

### 3.9 StreamAdvancedOps

**Priority**: High
**Effort**: 8-12 hours (400 LOC + 600 test LOC)

#### scan - Intermediate results

```java
public static <A, B> Kind<StreamKind.Witness, B> scan(
    B initial,
    BiFunction<B, A, B> accumulator,
    Kind<StreamKind.Witness, A> stream) {

  Stream<A> s = STREAM.narrow(stream);

  class ScanState {
    B acc = initial;
  }
  ScanState state = new ScanState();

  Stream<B> scanned = Stream.concat(
    Stream.of(initial),
    s.map(a -> {
      state.acc = accumulator.apply(state.acc, a);
      return state.acc;
    })
  );

  return STREAM.widen(scanned);
}
```

#### sliding - Sliding window

```java
public static <A> Kind<StreamKind.Witness, List<A>> sliding(
    int windowSize,
    int step,
    Kind<StreamKind.Witness, A> stream) {

  Validation.function().requirePositive(windowSize, "windowSize");
  Validation.function().requirePositive(step, "step");

  Stream<A> s = STREAM.narrow(stream);
  List<A> buffer = s.collect(Collectors.toList());

  Stream<List<A>> windows = IntStream
    .iterate(0, i -> i < buffer.size(), i -> i + step)
    .mapToObj(i -> buffer.subList(i, Math.min(i + windowSize, buffer.size())))
    .filter(window -> window.size() == windowSize);

  return STREAM.widen(windows);
}
```

#### intersperse - Insert separator

```java
public static <A> Kind<StreamKind.Witness, A> intersperse(
    A separator,
    Kind<StreamKind.Witness, A> stream) {

  Stream<A> s = STREAM.narrow(stream);
  List<A> list = s.collect(Collectors.toList());

  if (list.isEmpty()) return STREAM.widen(Stream.empty());

  Stream<A> result = Stream.of(list.get(0));
  for (int i = 1; i < list.size(); i++) {
    result = Stream.concat(result, Stream.of(separator, list.get(i)));
  }

  return STREAM.widen(result);
}
```

#### deduplicate - Remove consecutive duplicates

```java
public static <A> Kind<StreamKind.Witness, A> deduplicate(
    Kind<StreamKind.Witness, A> stream) {

  Stream<A> s = STREAM.narrow(stream);

  class State {
    A previous = null;
    boolean first = true;
  }
  State state = new State();

  return STREAM.widen(
    s.filter(current -> {
      if (state.first) {
        state.first = false;
        state.previous = current;
        return true;
      }
      boolean isDifferent = !Objects.equals(state.previous, current);
      state.previous = current;
      return isDifferent;
    })
  );
}
```

#### chunk - Split into fixed-size chunks

```java
public static <A> Kind<StreamKind.Witness, List<A>> chunk(
    int size,
    Kind<StreamKind.Witness, A> stream) {

  Validation.function().requirePositive(size, "size");

  Stream<A> s = STREAM.narrow(stream);
  Iterator<A> iter = s.iterator();

  return STREAM.widen(
    Stream.generate(() -> {
      List<A> chunk = new ArrayList<>(size);
      for (int i = 0; i < size && iter.hasNext(); i++) {
        chunk.add(iter.next());
      }
      return chunk.isEmpty() ? null : chunk;
    })
    .takeWhile(Objects::nonNull)
  );
}
```

**Files**: `StreamAdvancedOps.java`, `StreamAdvancedOpsTest.java`

---

## Integration Enhancements

### 3.10 StreamErrorHandling

**Priority**: Medium
**Effort**: 4-6 hours (200 LOC + 300 test LOC)

```java
public final class StreamErrorHandling {

  // Validate all elements
  public static <E, A> Kind<Validation.Witness<List<E>>, Kind<StreamKind.Witness, A>>
      validateAll(
          Function<A, Validation<E, A>> validator,
          Kind<StreamKind.Witness, A> stream) {

    return StreamTraverse.INSTANCE.traverse(
      ValidationApplicative.instance(),
      validator,
      stream
    );
  }

  // Separate successes and failures
  public static <E, A> Tuple2<Kind<StreamKind.Witness, E>, Kind<StreamKind.Witness, A>>
      separate(Kind<StreamKind.Witness, Either<E, A>> stream) {

    Stream<Either<E, A>> s = STREAM.narrow(stream);
    List<Either<E, A>> list = s.collect(Collectors.toList());

    Stream<E> lefts = list.stream()
      .filter(Either::isLeft)
      .map(Either::getLeft);

    Stream<A> rights = list.stream()
      .filter(Either::isRight)
      .map(Either::getRight);

    return Tuple2.of(
      STREAM.widen(lefts),
      STREAM.widen(rights)
    );
  }

  // Collect successes, ignore failures
  public static <A> Kind<StreamKind.Witness, A> catTries(
      Kind<StreamKind.Witness, Try<A>> streamOfTries) {

    Stream<Try<A>> s = STREAM.narrow(streamOfTries);
    return STREAM.widen(
      s.filter(Try::isSuccess)
       .map(Try::get)
    );
  }

  // Collect successes from Either
  public static <E, A> Kind<StreamKind.Witness, A> rights(
      Kind<StreamKind.Witness, Either<E, A>> stream) {

    Stream<Either<E, A>> s = STREAM.narrow(stream);
    return STREAM.widen(
      s.filter(Either::isRight)
       .map(Either::getRight)
    );
  }

  // Collect failures from Either
  public static <E, A> Kind<StreamKind.Witness, E> lefts(
      Kind<StreamKind.Witness, Either<E, A>> stream) {

    Stream<Either<E, A>> s = STREAM.narrow(stream);
    return STREAM.widen(
      s.filter(Either::isLeft)
       .map(Either::getLeft)
    );
  }
}
```

**Files**: `StreamErrorHandling.java`, `StreamErrorHandlingTest.java`

---

## Documentation and Examples

### 3.11 User Guide

**Priority**: High
**Effort**: 6-8 hours

#### Sections to Include:
1. **Migration Guide**: From plain Java Streams to HKT Streams
2. **Type Class Selection**: When to use which instance
3. **Performance Guide**: Overhead analysis, optimization tips
4. **Best Practices**:
   - Avoiding multiple stream consumption
   - When to use eager vs lazy operations
   - Infinite stream handling
   - Parallel stream considerations
5. **Common Pitfalls**:
   - Single-use semantics violations
   - Forgetting terminal operations
   - Cartesian product vs zip semantics

#### Example Sections

**ETL Pipeline Example**:
```java
public Kind<IO.Witness, Stats> processDataPipeline() {
  Kind<StreamKind.Witness, RawData> rawData = extractFromDatabase();

  Kind<StreamKind.Witness, Validation<Error, CleanData>> validated =
    StreamMonad.INSTANCE.map(this::validate, rawData);

  // Collect all errors or proceed
  Kind<Validation.Witness<List<Error>>, Kind<StreamKind.Witness, CleanData>>
    result = StreamErrorHandling.validateAll(Function.identity(), validated);

  return result.fold(
    errors -> IO.of(Stats.failed(errors)),
    cleanStream -> loadToWarehouse(cleanStream)
  );
}
```

**Files**: `docs/stream-user-guide.md`

---

### 3.12 API Reference

**Priority**: Medium
**Effort**: 4-6 hours

Generate comprehensive Javadoc covering:
- All type classes with laws
- All utility methods with examples
- Performance characteristics
- Thread-safety notes

**Files**: Enhance existing Javadoc across all Stream classes

---

## Testing Enhancements

### 3.13 Property-Based Testing

**Priority**: Medium
**Effort**: 4-6 hours (300 test LOC)

```java
@Property
void streamMonadLawsHoldForAllInputs(
    @ForAll List<@IntRange(min=0, max=1000) Integer> values) {

  Kind<StreamKind.Witness, Integer> stream = StreamOps.fromIterable(values);

  // Test monad laws with property-based inputs
  Function<Integer, Kind<StreamKind.Witness, String>> f =
    i -> StreamOps.fromArray("v" + i, "x" + i);

  // Left identity: flatMap(of(a), f) == f(a)
  for (Integer value : values) {
    var left = StreamMonad.INSTANCE.flatMap(f, StreamMonad.INSTANCE.of(value));
    var right = f.apply(value);
    assertStreamEquals(left, right);
  }
}
```

**Tools**: jqwik or QuickTheories
**Files**: `StreamPropertyTests.java`

---

### 3.14 Benchmark Suite

**Priority**: Medium
**Effort**: 3-4 hours (200 LOC)

```java
@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class StreamHKTBenchmarks {

  private List<Integer> data;

  @Setup
  public void setup() {
    data = IntStream.range(0, 10000).boxed().collect(Collectors.toList());
  }

  @Benchmark
  public long plainJavaStream() {
    return data.stream()
      .map(x -> x * 2)
      .filter(x -> x % 3 == 0)
      .count();
  }

  @Benchmark
  public long hktStream() {
    Kind<StreamKind.Witness, Integer> stream = StreamOps.fromIterable(data);
    var mapped = StreamMonad.INSTANCE.map(x -> x * 2, stream);
    var filtered = StreamOps.filter(x -> x % 3 == 0, mapped);
    return StreamOps.toList(filtered).size();
  }

  @Benchmark
  public long cartesianProductMonad() {
    var stream1 = StreamOps.fromIterable(List.of(1, 2, 3));
    var stream2 = StreamOps.fromIterable(List.of(10, 20, 30));
    var result = StreamMonad.INSTANCE.map2(stream1, stream2, Integer::sum);
    return StreamOps.toList(result).size();
  }

  @Benchmark
  public long zipApplicative() {
    var stream1 = StreamOps.fromIterable(List.of(1, 2, 3));
    var stream2 = StreamOps.fromIterable(List.of(10, 20, 30));
    var result = StreamZipApplicative.INSTANCE.map2(stream1, stream2, Integer::sum);
    return StreamOps.toList(result).size();
  }
}
```

**Tools**: JMH
**Files**: `StreamHKTBenchmarks.java`

---

## Implementation Timeline

### Phase 3a (High Priority - 15-20 hours)

**Week 1-2**:
- [ ] StreamAlternative implementation (2-3h)
- [ ] StreamMonadPlus implementation (2h)
- [ ] StreamAlternative/MonadPlus tests (3-4h)
- [ ] StreamSelective implementation (4-6h)
- [ ] StreamSelective tests (4-6h)

**Deliverables**:
- Working Alternative/MonadPlus with tests
- Working Selective with tests
- Updated package documentation

---

### Phase 3b (Medium Priority - 15-20 hours)

**Week 3-4**:
- [ ] StreamBuilder (2h)
- [ ] StreamCollectors (4-5h)
- [ ] StreamAdvancedOps (scan, sliding, intersperse, deduplicate, chunk) (8-12h)
- [ ] ParallelStreamOps (2h)
- [ ] StreamZipApplicative (3-4h)
- [ ] All utility tests (6-8h)

**Deliverables**:
- Complete utility suite
- Zip applicative variant
- Test coverage

---

### Phase 3c (Documentation & Testing - 15-20 hours)

**Week 5-6**:
- [ ] User guide (6-8h)
- [ ] API reference updates (4-6h)
- [ ] Real-world examples (4-6h)
- [ ] Property-based tests (4-6h)
- [ ] Benchmark suite (3-4h)
- [ ] StreamErrorHandling integration (4-6h)

**Deliverables**:
- Comprehensive documentation
- Property tests
- Benchmarks
- Error handling utilities

---

## Priority Recommendations

### Priority Matrix

| Component | Priority | Effort | Impact | Dependencies |
|-----------|----------|--------|--------|--------------|
| StreamAlternative | ⭐⭐⭐ High | 2-3h | High | None |
| StreamMonadPlus | ⭐⭐⭐ High | 2h | High | Alternative |
| StreamSelective | ⭐⭐⭐ High | 4-6h | Medium | Either type |
| StreamAdvancedOps | ⭐⭐⭐ High | 8-12h | High | None |
| StreamBuilder | ⭐⭐⭐ High | 2h | Medium | None |
| StreamCollectors | ⭐⭐⭐ High | 4-5h | High | None |
| User Guide | ⭐⭐⭐ High | 6-8h | High | All implementations |
| StreamZipApplicative | ⭐⭐ Medium | 3-4h | Medium | None |
| ParallelStreamOps | ⭐⭐ Medium | 2h | Low | None |
| StreamErrorHandling | ⭐⭐ Medium | 4-6h | Medium | Error types |
| Property Tests | ⭐⭐ Medium | 4-6h | Medium | Test framework |
| Benchmarks | ⭐⭐ Medium | 3-4h | Low | JMH setup |
| API Reference | ⭐⭐ Medium | 4-6h | Medium | All code complete |
| NonEmptyStreamComonad | ⭐ Low | 6-8h | Low | NonEmptyStream type |

---

## Effort Summary

| Phase | Components | Hours | Priority |
|-------|-----------|-------|----------|
| **Phase 3a** | Alternative, MonadPlus, Selective | 15-20h | High |
| **Phase 3b** | Utilities, ZipApplicative | 15-20h | Medium |
| **Phase 3c** | Docs, Tests, Benchmarks | 15-20h | High |
| **Total** | | **45-60h** | |

---

## Success Criteria

### Technical
- [ ] All type class laws pass
- [ ] 90%+ test coverage
- [ ] No performance regression vs plain Java Streams (<10% overhead)
- [ ] All public APIs documented with examples

### User Experience
- [ ] Clear migration guide from Java Streams
- [ ] Decision tree for choosing type class instance
- [ ] Real-world example for each major feature
- [ ] Common pitfalls documented

### Community
- [ ] Phase 3a deployed for feedback
- [ ] User testing with real applications
- [ ] Benchmarks published
- [ ] Contributing guide for future enhancements

---

## Future Considerations (Phase 4+)

- **Reactive Streams Integration**: Bridge to Project Reactor, RxJava
- **Distributed Streams**: Spark, Flink integration
- **Stream Optics**: Lenses, Prisms for nested stream transformations
- **Incremental Processing**: Support for streaming incremental updates
- **Resource Management**: Integration with ARM (try-with-resources)

---

## References

- [Original Stream Implementation PR](link-to-pr)
- [Type Class Laws Documentation](link-to-laws)
- [Performance Benchmarks](link-to-benchmarks)
- [User Feedback Issues](link-to-issues)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-11
**Author**: Claude (AI Assistant)
**Status**: Planning Phase
