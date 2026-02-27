# Stage 6: Advanced Features

## Overview

This stage delivers advanced capabilities that extend VStream's reach into the wider
higher-kinded-j ecosystem and beyond. It includes resource-safe streaming via `bracket`,
a `StreamTraversal` optic that preserves laziness (unlike standard Traversal which
materialises via Applicative), reactive interop with `java.util.concurrent.Flow.Publisher`,
PathProvider SPI registration, natural transformations between stream types, and a VStream
Selective instance for branched stream processing.

**Module**: `hkj-core` (primary), `hkj-api` (StreamTraversal optic)
**Packages**: `org.higherkindedj.hkt.vstream`, `org.higherkindedj.optics`

**Prerequisites**: Stages 1-5

---

## Detailed Tasks

### 6.1 Resource-Safe Streaming (Bracket)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStream.java` (additions)

Add resource management to VStream:

```java
// On VStream<A>

/**
 * Acquire a resource, use it to produce a stream, and guarantee release on
 * completion, error, or cancellation.
 */
static <R, A> VStream<A> bracket(
        VTask<R> acquire,
        Function<R, VStream<A>> use,
        Function<R, VTask<Unit>> release) { ... }

/**
 * Ensure a finaliser runs when this stream completes, errors, or is abandoned.
 */
default VStream<A> onFinalize(VTask<Unit> finalizer) { ... }

/**
 * Scope-based resource management for nested bracket regions.
 */
static <A> VStream<A> scoped(Function<Scope, VStream<A>> body) { ... }
```

**Scope interface**:
```java
public interface Scope {
    <R> VTask<R> acquire(VTask<R> resource, Function<R, VTask<Unit>> release);
}
```

**Implementation approach**:
- `bracket`: Acquire resource on first pull; attach release to stream's Done/error handling
- Track whether resource has been acquired and released via `AtomicBoolean`
- Release must run exactly once, even if the stream is partially consumed
- Use `try-finally` semantics internally
- `onFinalize` wraps the pull to intercept Done and error cases
- `scoped` creates a Scope that tracks multiple resources, releasing all on exit in reverse
  acquisition order

**Requirements**:
- Resource acquired lazily on first pull (not on bracket call)
- Release guaranteed on: stream completion (Done), error during pull, consumer abandonment
  (via finaliser thread if detectable)
- Release runs on a virtual thread
- Multiple bracket regions can be nested
- Comprehensive javadoc with file I/O and database cursor examples
- Document limitation: if consumer simply stops pulling without running the stream to
  completion, release depends on GC finalisation (document this clearly as a known limitation
  of pull-based streams)

### 6.2 StreamTraversal Optic

**File**: `hkj-api/src/main/java/org/higherkindedj/optics/StreamTraversal.java`

A new optic type that preserves laziness during traversal, unlike standard `Traversal`
which materialises via Applicative:

```java
public interface StreamTraversal<S, A> {

    /**
     * Stream all focused elements lazily.
     */
    VStream<A> stream(S source);

    /**
     * Modify all focused elements via a pure function, reconstructing the structure.
     * May materialise internally if the structure requires all elements for reconstruction.
     */
    S modify(Function<A, A> f, S source);

    /**
     * Modify all focused elements via an effectful function on virtual threads.
     * Returns a VTask of the modified structure.
     */
    VTask<S> modifyVTask(Function<A, VTask<A>> f, S source);

    /**
     * Compose with another StreamTraversal.
     */
    default <B> StreamTraversal<S, B> andThen(StreamTraversal<A, B> other) { ... }

    /**
     * Compose with a Lens.
     */
    default <B> StreamTraversal<S, B> andThen(Lens<A, B> lens) { ... }

    /**
     * Convert to a standard Traversal (materialising).
     */
    default Traversal<S, A> toTraversal() { ... }

    /**
     * Create from a standard Traversal.
     */
    static <S, A> StreamTraversal<S, A> fromTraversal(Traversal<S, A> traversal) { ... }

    /**
     * Create for VStream elements.
     */
    static <A> StreamTraversal<VStream<A>, A> forVStream() { ... }

    /**
     * Create for List elements.
     */
    static <A> StreamTraversal<List<A>, A> forList() { ... }
}
```

**Design notes**:
- `stream()` returns a lazy `VStream<A>` of focused elements, never materialising all at once
- `modify()` may need to materialise internally (depends on structure S), but `stream()`
  does not
- `modifyVTask()` processes each element on a virtual thread; this is the primary advantage
  over standard Traversal's `modifyF`
- Composition: `andThen` produces a new StreamTraversal whose `stream()` chains pulls
- `fromTraversal` bridges existing Traversals into the lazy world
- `forVStream` is the canonical instance for VStream containers

**Key difference from Traversal**:
- `Traversal.modifyF` requires an `Applicative<F>` and materialises all effects to combine
  them
- `StreamTraversal.stream()` keeps everything lazy; consumer controls evaluation pace
- `StreamTraversal.modifyVTask()` uses VTask directly, not a generic Applicative

### 6.3 Reactive Interop (Flow.Publisher Bridge)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamReactive.java`

Bridge VStream to and from `java.util.concurrent.Flow`:

```java
public final class VStreamReactive {

    private VStreamReactive() {}

    /**
     * Convert a VStream to a Flow.Publisher.
     * Each subscriber receives all elements. Backpressure is respected via
     * request-based pulling.
     */
    public static <A> Flow.Publisher<A> toPublisher(VStream<A> stream) { ... }

    /**
     * Create a VStream from a Flow.Publisher.
     * The stream pulls from the publisher, buffering up to bufferSize elements.
     */
    public static <A> VStream<A> fromPublisher(
            Flow.Publisher<A> publisher,
            int bufferSize) { ... }

    /**
     * Create a VStream from a Flow.Publisher with default buffer size.
     */
    public static <A> VStream<A> fromPublisher(Flow.Publisher<A> publisher) {
        return fromPublisher(publisher, 256);
    }
}
```

**Implementation approach for toPublisher**:
1. On `subscribe`, create a `Flow.Subscription` backed by the VStream
2. `request(n)`: Pull n elements from VStream, each on a virtual thread
3. `onNext` called for each emitted element
4. `onComplete` called when Done
5. `onError` called when pull fails
6. `cancel` stops pulling

**Implementation approach for fromPublisher**:
1. Subscribe to the publisher
2. Buffer incoming elements in a bounded queue
3. VStream's `pull()` reads from the queue
4. Request more elements from publisher when buffer falls below threshold
5. Map `onComplete` to Done, `onError` to failed VTask

**Requirements**:
- Compliant with Reactive Streams specification (Flow is Java's built-in reactive interface)
- Buffer size configurable for fromPublisher
- Proper cleanup on cancellation
- Thread-safe queue operations
- Comprehensive javadoc with interop examples

### 6.4 PathProvider SPI Registration

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamPathProvider.java`

Register VStream as a PathProvider for dynamic path creation:

```java
public class VStreamPathProvider
        implements PathProvider<VStreamKind.Witness> {

    @Override
    public Class<?> witnessType() {
        return VStreamKind.Witness.class;
    }

    @Override
    public <A> Chainable<A> createPath(Kind<VStreamKind.Witness, A> kind) {
        VStream<A> stream = VStreamKindHelper.VSTREAM.narrow(kind);
        return new DefaultVStreamPath<>(stream);
    }

    @Override
    public Monad<VStreamKind.Witness> monad() {
        return VStreamMonad.INSTANCE;
    }

    @Override
    public String name() {
        return "VStream";
    }
}
```

**ServiceLoader registration**:
**File**: `hkj-core/src/main/resources/META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider`

Add line:
```
org.higherkindedj.hkt.vstream.VStreamPathProvider
```

**Requirements**:
- Enables `Path.from(vstreamKind, VStreamKind.Witness.class)` to produce VStreamPath
- Follows existing PathProvider pattern (e.g., ApiResultPathProvider from examples)
- Test via PathRegistry.getProvider and PathRegistry.createPath

### 6.5 Natural Transformations

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamTransformations.java`

Provide natural transformations between stream-like types:

```java
public final class VStreamTransformations {

    private VStreamTransformations() {}

    /**
     * Natural transformation from Stream to VStream.
     * The stream is consumed lazily via iterator.
     */
    public static NaturalTransformation<StreamKind.Witness, VStreamKind.Witness>
            streamToVStream() { ... }

    /**
     * Natural transformation from VStream to Stream (materialises).
     */
    public static NaturalTransformation<VStreamKind.Witness, StreamKind.Witness>
            vstreamToStream() { ... }

    /**
     * Natural transformation from List to VStream.
     */
    public static NaturalTransformation<ListKind.Witness, VStreamKind.Witness>
            listToVStream() { ... }

    /**
     * Natural transformation from VStream to List (materialises).
     */
    public static NaturalTransformation<VStreamKind.Witness, ListKind.Witness>
            vstreamToList() { ... }

    /**
     * Natural transformation from VStream to VTask (collects to list).
     */
    public static NaturalTransformation<VStreamKind.Witness, VTaskKind.Witness>
            vstreamToVTask() { ... }
}
```

**Requirements**:
- Each transformation preserves element order
- Materialising transformations (VStream -> Stream, VStream -> List) execute all VTasks
- Non-materialising transformations (Stream -> VStream, List -> VStream) remain lazy
- Composable via `GenericPath.mapK()`
- Comprehensive javadoc

### 6.6 VStream Selective Instance (Optional)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamSelective.java`

If VTask has or gains a Selective instance, provide a corresponding VStream Selective:

```java
public class VStreamSelective extends VStreamApplicative
        implements Selective<VStreamKind.Witness> {

    public static final VStreamSelective INSTANCE = new VStreamSelective();

    @Override
    public <A, B> Kind<VStreamKind.Witness, B> select(
            Kind<VStreamKind.Witness, Choice<A, B>> fab,
            Kind<VStreamKind.Witness, Function<A, B>> ff) {
        // For each Choice element:
        //   Right(b) -> emit b, skip ff
        //   Left(a) -> apply each function from ff to a
    }
}
```

**Design note**: This enables `Traversal.branch()` and `Traversal.modifyWhen()` with VStream
as the effect type. Mark as optional; implement only if the Selective integration provides
clear value over using modifyF with standard Applicative.

### 6.7 VStreamContext (Layer 2)

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/context/VStreamContext.java`

Provide a Layer-2 user-friendly wrapper hiding HKT complexity, following the pattern of
VTaskContext:

```java
public final class VStreamContext<A> {
    private final VStreamPath<A> path;

    // Factory methods
    public static <A> VStreamContext<A> of(VStream<A> stream) { ... }
    public static <A> VStreamContext<A> fromList(List<A> list) { ... }
    public static <A> VStreamContext<A> pure(A value) { ... }
    public static <A> VStreamContext<A> empty() { ... }
    public static <S, A> VStreamContext<A> unfold(
            S seed, Function<S, VTask<Optional<Pair<A, S>>>> f) { ... }

    // Composition
    public <B> VStreamContext<B> map(Function<A, B> f) { ... }
    public <B> VStreamContext<B> flatMap(Function<A, VStreamContext<B>> f) { ... }
    public VStreamContext<A> filter(Predicate<A> predicate) { ... }
    public VStreamContext<A> take(long n) { ... }
    public VStreamContext<A> drop(long n) { ... }
    public VStreamContext<A> distinct() { ... }
    public VStreamContext<A> concat(VStreamContext<A> other) { ... }

    // Parallel
    public <B> VStreamContext<B> parEvalMap(int concurrency, Function<A, VTask<B>> f) { ... }
    public VStreamContext<List<A>> chunk(int size) { ... }

    // Terminal
    public List<A> toList() { ... }
    public Optional<A> headOption() { ... }
    public long count() { ... }
    public A fold(A identity, BinaryOperator<A> op) { ... }
    public boolean exists(Predicate<A> predicate) { ... }
    public boolean forAll(Predicate<A> predicate) { ... }

    // Conversion
    public VStream<A> toVStream() { ... }
    public VStreamPath<A> toVStreamPath() { ... }
}
```

**Requirements**:
- No HKT types exposed in public API
- Terminal operations execute synchronously (run underlying VTask)
- Wraps VStreamPath internally
- Follows VTaskContext pattern exactly
- Comprehensive javadoc

---

## Testing

### 6.8 Bracket/Resource Tests

Carefully read TESTING-GUIDE.md and PERFORMANCE-TESTING-GUIDE.md to understand the standards and conventions.
All code must have 100% coverage

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamBracketTest.java`

1. **Basic Resource Lifecycle** (`@Nested class BasicLifecycle`)
   - Resource acquired on first pull
   - Resource released on stream completion
   - Resource released on error
   - Release runs exactly once
   - Acquisition and release order verified with AtomicReference logging

2. **Partial Consumption** (`@Nested class PartialConsumption`)
   - `take(n)` from bracketed stream releases resource
   - `headOption()` from bracketed stream releases resource
   - `find()` short-circuit releases resource

3. **Nested Brackets** (`@Nested class NestedBrackets`)
   - Two nested bracket regions release in reverse order
   - Inner bracket error releases both resources
   - `scoped` tracks multiple resources

4. **onFinalize** (`@Nested class OnFinalize`)
   - Finaliser runs on completion
   - Finaliser runs on error
   - Multiple finalisers run in order

5. **Error in Release** (`@Nested class ErrorInRelease`)
   - Error during release is reported (not swallowed)
   - Original error preserved when both use and release fail

### 6.9 StreamTraversal Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/optics/StreamTraversalTest.java`

1. **VStream StreamTraversal** (`@Nested class ForVStream`)
   - `stream()` returns lazy VStream of elements
   - `modify()` transforms all elements
   - `modifyVTask()` applies effectful function
   - Composition with Lens
   - Composition with another StreamTraversal

2. **List StreamTraversal** (`@Nested class ForList`)
   - `stream()` returns lazy VStream of list elements
   - `modify()` transforms all elements
   - Round-trip: stream -> collect -> equals original

3. **Conversion** (`@Nested class Conversion`)
   - `toTraversal()` produces valid standard Traversal
   - `fromTraversal()` creates StreamTraversal from existing Traversal
   - Law preservation through conversion

4. **Laziness** (`@Nested class Laziness`)
   - `stream()` does not materialise elements eagerly
   - Elements produced on demand
   - `take(n)` from streamed traversal pulls only n elements

### 6.10 Reactive Interop Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamReactiveTest.java`

1. **toPublisher** (`@Nested class ToPublisher`)
   - All elements delivered to subscriber
   - onComplete called after last element
   - onError called on stream failure
   - Backpressure: request(1) delivers one element
   - cancel stops delivery
   - Multiple subscribers each receive all elements

2. **fromPublisher** (`@Nested class FromPublisher`)
   - All publisher elements available in VStream
   - Completion maps to Done
   - Error maps to failed VTask
   - Buffer respects configured size
   - Slow consumer triggers backpressure on publisher

3. **Round-trip** (`@Nested class RoundTrip`)
   - VStream -> Publisher -> VStream preserves elements
   - Publisher -> VStream -> Publisher preserves elements

### 6.11 PathProvider Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamPathProviderTest.java`

- `PathRegistry.getProvider(VStreamKind.Witness.class)` returns provider
- `PathRegistry.createPath()` produces VStreamPath
- Provider name is "VStream"
- Provider monad is VStreamMonad.INSTANCE
- `Path.from(vstreamKind, VStreamKind.Witness.class)` produces Chainable

### 6.12 Natural Transformation Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamTransformationsTest.java`

- Each transformation preserves elements and order
- Round-trip: Stream -> VStream -> Stream equals original
- Round-trip: List -> VStream -> List equals original
- Empty collection transformations
- Null handling
- `GenericPath.mapK()` integration

### 6.13 VStreamContext Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/context/VStreamContextTest.java`

Follow VTaskContextTest pattern:

1. **Factory Methods** - All constructors
2. **Composition** - map, flatMap, filter, take, etc.
3. **Parallel** - parEvalMap, chunk
4. **Terminal** - toList, headOption, count, fold, exists, forAll
5. **Conversion** - toVStream, toVStreamPath

---

## Documentation

### 6.14 Javadoc

All public types and methods require comprehensive javadoc:
- Bracket: Resource lifecycle semantics, GC limitation, usage examples
- StreamTraversal: Laziness guarantees, comparison with Traversal
- VStreamReactive: Reactive Streams compliance, buffer sizing guidance
- VStreamPathProvider: SPI registration, ServiceLoader usage
- VStreamTransformations: Materialisation warnings, composition examples
- VStreamContext: Layer-2 simplification, relationship to VStreamPath
- British English throughout

### 6.15 Documentation Pages

Read the STYLE-GUIDE.md file before creating documentation in hkj-book.  
Carefully analyse the existing hkj-book documentation to understand correct placement of any new or updated content.

**File**: `vstream_resources.md`
- Resource-safe streaming with bracket
- Scope-based resource management
- Common patterns: file I/O, database cursors, network connections
- Limitations and caveats

**File**: `vstream_advanced.md`
- StreamTraversal optic
- Reactive interop
- Natural transformations
- PathProvider SPI
- VStreamContext Layer-2 API

---

## Examples

### 6.16 Example Code

**File**: `hkj-examples/src/main/java/org/higherkindedj/examples/vstream/VStreamAdvancedExample.java`

Demonstrate:

1. **Resource-safe file streaming**:
   ```java
   VStream<String> lines = VStream.bracket(
       VTask.of(() -> Files.newBufferedReader(path)),
       reader -> VStream.unfold(reader, r ->
           VTask.of(() -> {
               String line = r.readLine();
               return line == null
                   ? Optional.empty()
                   : Optional.of(Pair.of(line, r));
           })),
       reader -> VTask.exec(() -> reader.close())
   );
   ```

2. **StreamTraversal for lazy optics**:
   ```java
   StreamTraversal<Database, Record> allRecords =
       StreamTraversal.forVStream()
           .andThen(recordDataLens);

   VStream<Record> records = allRecords.stream(database);
   VTask<Database> updated = allRecords.modifyVTask(
       record -> VTask.of(() -> enrichRecord(record)),
       database
   );
   ```

3. **Reactive interop**:
   ```java
   // From reactive source
   VStream<Event> events = VStreamReactive.fromPublisher(eventPublisher);

   // Process and publish back
   Flow.Publisher<ProcessedEvent> processed =
       VStreamReactive.toPublisher(events.map(Event::process));
   ```

4. **VStreamContext for simple usage**:
   ```java
   List<String> names = VStreamContext.fromList(users)
       .filter(User::isActive)
       .map(User::name)
       .distinct()
       .toList();
   ```

---

## Tutorials

### 6.17 Tutorial

**File**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vstream/Tutorial06_VStreamAdvanced.java`

Following TUTORIAL-STYLE-GUIDE.md:
- 10-12 exercises:
  1. Use bracket to manage a simulated resource
  2. Verify resource release on normal completion
  3. Verify resource release on error
  4. Use onFinalize for cleanup actions
  5. Create a StreamTraversal for VStream elements
  6. Use StreamTraversal.stream() for lazy element access
  7. Use StreamTraversal.modifyVTask() for effectful modification
  8. Convert between VStream and Flow.Publisher
  9. Use VStreamPathProvider via Path.from()
  10. Use natural transformations to convert between stream types
  11. Use VStreamContext for HKT-free streaming
  12. Combine bracket, StreamTraversal, and parallel processing
- Solution file: `Tutorial06_VStreamAdvanced_Solution.java`
- Time estimate: 15-20 minutes

---

## Acceptance Criteria

- [ ] `bracket` acquires lazily and releases on completion, error, and partial consumption
- [ ] `onFinalize` runs finaliser on completion and error
- [ ] `scoped` manages multiple resources with reverse-order release
- [ ] StreamTraversal preserves laziness via `stream()`
- [ ] StreamTraversal.modifyVTask() processes elements on virtual threads
- [ ] StreamTraversal composes with Lens and other StreamTraversals
- [ ] VStreamReactive.toPublisher() produces compliant Flow.Publisher
- [ ] VStreamReactive.fromPublisher() creates VStream from reactive source
- [ ] VStreamPathProvider registered via ServiceLoader
- [ ] Path.from() discovers VStreamPathProvider
- [ ] Natural transformations preserve elements and order
- [ ] VStreamContext provides complete HKT-free API
- [ ] All resource lifecycle tests pass (acquire, release, error, partial consumption)
- [ ] All StreamTraversal tests pass including laziness verification
- [ ] All reactive interop tests pass including backpressure
- [ ] All PathProvider tests pass
- [ ] All natural transformation tests pass
- [ ] All VStreamContext tests pass
- [ ] Javadoc complete on all public API
- [ ] Documentation pages written
- [ ] Example code with realistic scenarios
- [ ] Tutorial and solution file complete
- [ ] All existing tests continue to pass

---

## GitHub Issue Summary

**Title**: VStream Stage 6: Advanced Features - Resources, StreamTraversal, Reactive Interop

Deliver advanced VStream capabilities: resource-safe streaming, a lazy StreamTraversal optic,
reactive streams interop, PathProvider SPI registration, natural transformations, and a
Layer-2 VStreamContext API.

**Key deliverables**:
- Resource-safe streaming: `bracket(acquire, use, release)`, `onFinalize`, `scoped` with
  `Scope` for nested resource management
- `StreamTraversal<S, A>` optic: `stream()` for lazy element access, `modifyVTask()` for
  effectful modification on virtual threads, composition with Lens and other StreamTraversals
- Reactive interop: `VStreamReactive.toPublisher()` and `fromPublisher()` bridging
  VStream to/from `java.util.concurrent.Flow.Publisher`
- `VStreamPathProvider` registered via ServiceLoader for `Path.from()` discovery
- Natural transformations: Stream/List/VTask <-> VStream via `VStreamTransformations`
- `VStreamContext<A>` Layer-2 API hiding HKT complexity
- Comprehensive tests: resource lifecycle, StreamTraversal laziness, reactive compliance,
  SPI discovery
- Documentation: resource management guide, advanced features guide
- Example code: file streaming, lazy optics, reactive interop, VStreamContext
- Tutorial with 12 exercises

**Packages**: `org.higherkindedj.hkt.vstream`, `org.higherkindedj.optics` in `hkj-core`/`hkj-api`

**Dependencies**: Stages 1-5
