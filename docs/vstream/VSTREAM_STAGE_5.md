# Stage 5: Parallel Operations and Chunking

## Overview

This stage adds parallel processing capabilities to VStream, leveraging virtual threads for
concurrent element processing, and introduces chunking for efficient batch operations. It
includes `parEvalMap` for bounded-concurrency parallel mapping, `VStreamPar` combinators
analogous to the existing `Par` class for VTask, chunk/batch operations for I/O-efficient
processing, and JMH benchmarks comparing VStream against raw Java streams and other
streaming approaches.

**Module**: `hkj-core` (implementation), `hkj-benchmarks` (performance tests)
**Package**: `org.higherkindedj.hkt.vstream`

**Prerequisites**: Stages 1-3 (Core VStream, HKT Encoding, VStreamPath)

---

## Detailed Tasks

### 5.1 VStreamPar Parallel Combinators

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/vstream/VStreamPar.java`

Create parallel combinators for VStream following the pattern of `Par` for VTask:

```java
public final class VStreamPar {

    private VStreamPar() {}

    /**
     * Apply an effectful function to each element with bounded concurrency.
     * Elements are processed on virtual threads with at most {@code concurrency}
     * elements in flight at any time. Output order matches input order.
     */
    public static <A, B> VStream<B> parEvalMap(
            VStream<A> stream,
            int concurrency,
            Function<A, VTask<B>> f) { ... }

    /**
     * Like parEvalMap but does not preserve order. Elements are emitted as
     * soon as they complete, potentially out of input order.
     */
    public static <A, B> VStream<B> parEvalMapUnordered(
            VStream<A> stream,
            int concurrency,
            Function<A, VTask<B>> f) { ... }

    /**
     * Apply a stream-producing function to each element with bounded concurrency,
     * interleaving results.
     */
    public static <A, B> VStream<B> parEvalFlatMap(
            VStream<A> stream,
            int concurrency,
            Function<A, VStream<B>> f) { ... }

    /**
     * Merge multiple streams concurrently into a single stream.
     * Elements are emitted as they become available from any source.
     */
    public static <A> VStream<A> merge(List<VStream<A>> streams) { ... }

    /**
     * Merge two streams concurrently.
     */
    public static <A> VStream<A> merge(VStream<A> first, VStream<A> second) { ... }

    /**
     * Collect all elements from a stream in parallel batches.
     * Pulls up to batchSize elements concurrently, collects results.
     */
    public static <A> VTask<List<A>> parCollect(
            VStream<A> stream,
            int batchSize) { ... }
}
```

**Implementation approach for parEvalMap**:
1. Pull up to `concurrency` elements from the source stream
2. Fork each element's VTask onto a virtual thread via `StructuredTaskScope`
3. Collect results in order, emit them
4. Repeat until source stream is exhausted
5. Use sliding window: as completed results are emitted, pull more from source

**Requirements**:
- All methods validate parameters: concurrency must be positive, stream non-null
- `parEvalMap` preserves input order despite parallel execution
- `parEvalMapUnordered` maximises throughput by emitting in completion order
- `merge` uses virtual threads per source stream, interleaving into a single output
- Uses `StructuredTaskScope` for proper cancellation and error propagation
- If any element fails, the entire stream fails (fail-fast semantics)
- Comprehensive javadoc with performance guidance (when to use what concurrency level)

### 5.2 VStream Chunking Operations

Add chunking methods to VStream:

```java
// On VStream<A>
default VStream<List<A>> chunk(int size) { ... }

default VStream<List<A>> chunkWhile(BiPredicate<A, A> sameChunk) { ... }

default <B> VStream<B> mapChunked(int size, Function<List<A>, List<B>> f) { ... }
```

| Method | Description |
|--------|-------------|
| `chunk(size)` | Group elements into lists of at most `size` elements |
| `chunkWhile(predicate)` | Group consecutive elements while predicate holds between adjacent pairs |
| `mapChunked(size, f)` | Chunk, apply batch function, flatten results |

**Requirements**:
- `chunk` collects elements eagerly within each chunk, but chunks are produced lazily
- Last chunk may have fewer than `size` elements
- `chunk(1)` is equivalent to `map(List::of)`
- `chunkWhile` is useful for grouping sorted data
- `mapChunked` enables efficient batch operations (bulk insert, batch API calls)

### 5.3 VStreamPath Parallel and Chunk Methods

**File**: Update `VStreamPath.java` and `DefaultVStreamPath.java`

Add parallel and chunking methods to VStreamPath:

```java
// On VStreamPath<A>

/** Apply effectful function with bounded concurrency, preserving order. */
VStreamPath<B> parEvalMap(int concurrency, Function<A, VTask<B>> f);

/** Apply effectful function with bounded concurrency, unordered. */
VStreamPath<B> parEvalMapUnordered(int concurrency, Function<A, VTask<B>> f);

/** Group elements into chunks of at most size. */
VStreamPath<List<A>> chunk(int size);

/** Apply batch function to chunks. */
<B> VStreamPath<B> mapChunked(int size, Function<List<A>, List<B>> f);

/** Collect all elements in parallel batches. */
VTaskPath<List<A>> parCollect(int batchSize);
```

**Requirements**:
- Delegate to `VStreamPar` methods and VStream chunk operations
- Return VStreamPath for fluent chaining
- Terminal operations (parCollect) return VTaskPath

### 5.4 PathOps Parallel Operations

**File**: Update `PathOps.java`

Add parallel variants for VStreamPath:

```java
/** Traverse with parallel evaluation of the mapping function. */
public static <A, B> VStreamPath<B> parTraverseVStream(
        VStreamPath<A> stream,
        int concurrency,
        Function<A, VTask<B>> f) { ... }

/** Collect stream in parallel batches. */
public static <A> VTaskPath<List<A>> parCollectVStream(
        VStreamPath<A> stream,
        int batchSize) { ... }
```

### 5.5 Backpressure Strategy

Document the backpressure model clearly:

- **Pull-based**: Consumer drives evaluation; producer only generates when pulled
- **Bounded concurrency**: `parEvalMap` limits in-flight elements
- **Virtual threads**: Blocking on a virtual thread is cheap; no platform thread starvation
- **No explicit backpressure protocol needed**: The pull model inherently provides backpressure

Create a design note in javadoc explaining why VStream does not need explicit backpressure
(unlike reactive streams), and when to prefer VStream over reactive approaches.

---

## Testing

### 5.6 VStreamPar Unit Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamParTest.java`

Follow the pattern of `Par` tests:

1. **parEvalMap** (`@Nested class ParEvalMap`)
   - Processes all elements
   - Preserves input order despite parallel execution
   - Respects concurrency limit (verify max concurrent executions with AtomicInteger)
   - Empty stream returns empty stream
   - Single element stream works correctly
   - Handles errors (one element fails, entire stream fails)
   - Null parameter validation

2. **parEvalMapUnordered** (`@Nested class ParEvalMapUnordered`)
   - Processes all elements
   - May emit in different order than input
   - Respects concurrency limit
   - Contains same elements as ordered variant (verify with set equality)
   - Error handling

3. **parEvalFlatMap** (`@Nested class ParEvalFlatMap`)
   - Expands and interleaves correctly
   - Respects concurrency limit
   - Empty inner streams handled
   - Error handling

4. **merge** (`@Nested class Merge`)
   - Merges two streams
   - Merges list of streams
   - Empty stream list returns empty
   - Single stream returned as-is
   - Contains all elements from all sources
   - Error in one source fails entire merge

5. **parCollect** (`@Nested class ParCollect`)
   - Collects all elements
   - Respects batch size
   - Empty stream returns empty list
   - Error handling

6. **Concurrency Verification** (`@Nested class ConcurrencyVerification`)
   - Use `AtomicInteger` to track concurrent execution count
   - Verify max concurrent never exceeds specified limit
   - Verify virtual thread usage with `Thread.currentThread().isVirtual()`

7. **Error Propagation** (`@Nested class ErrorPropagation`)
   - Error in any element cancels remaining
   - Error message preserved
   - Structured concurrency scope properly closed

### 5.7 Chunking Tests

**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/vstream/VStreamChunkTest.java`

1. **chunk** (`@Nested class ChunkTests`)
   - Chunks correctly with exact divisor
   - Last chunk smaller when not exact divisor
   - `chunk(1)` equivalent to wrapping each in singleton list
   - `chunk(n)` where n >= stream length returns single chunk
   - Empty stream returns empty stream of chunks
   - Preserves element order within and across chunks
   - Laziness: chunks produced on demand

2. **chunkWhile** (`@Nested class ChunkWhileTests`)
   - Groups consecutive equal elements
   - Single-element chunks when no adjacent matches
   - All-same elements produce single chunk
   - Empty stream returns empty

3. **mapChunked** (`@Nested class MapChunkedTests`)
   - Applies batch function to each chunk
   - Results flattened correctly
   - Batch function can change element count (expand/contract)
   - Empty stream returns empty

### 5.8 VStreamPath Parallel Tests

**File**: Extend `VStreamPathTest` with new `@Nested` groups:

1. **Parallel Operations** (`@Nested class ParallelOperations`)
   - `parEvalMap()` processes all elements
   - `parEvalMap()` preserves order
   - `parEvalMapUnordered()` processes all elements
   - `parCollect()` collects correctly

2. **Chunk Operations** (`@Nested class ChunkOperations`)
   - `chunk()` groups correctly
   - `mapChunked()` applies batch function
   - Pipeline: chunk then parEvalMap for batch processing

---

## Benchmarks

### 5.9 JMH Benchmarks

**File**: `hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/vstream/VStreamBenchmark.java`

Follow PERFORMANCE-TESTING-GUIDE.md conventions:

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G", "--enable-preview"})
public class VStreamBenchmark {

    @Param({"100", "1000", "10000"})
    private int streamSize;

    // ... setup and benchmarks
}
```

#### Benchmark Categories

1. **Construction** (`@Nested`)
   - `VStream.fromList()` vs `Stream.of()` vs `StreamPath.fromList()`
   - `VStream.range()` vs `IntStream.range()`

2. **Sequential Processing** (`@Nested`)
   - map + filter + take pipeline comparison:
     - VStream pipeline
     - Java Stream pipeline
     - StreamPath pipeline
   - fold comparison across implementations
   - flatMap expansion comparison

3. **Parallel Processing** (`@Nested`)
   - `parEvalMap` with simulated I/O (Thread.sleep) at various concurrency levels
   - `parEvalMap` vs `Par.traverse` (VTask list approach)
   - `parEvalMap` vs Java parallel stream
   - Concurrency scaling: 1, 2, 4, 8, 16, 32, 64 threads

4. **Chunking** (`@Nested`)
   - `chunk(n)` + batch processing vs element-by-element processing
   - Chunk sizes: 1, 10, 100, 1000

5. **Memory** (`@Nested`)
   - Allocation per element: VStream vs Java Stream vs ArrayList
   - GC pressure under throughput load
   - Use JMH GC profiler: `-PjmhProfilers=gc`

**Requirements**:
- Comprehensive javadoc on each benchmark explaining what is measured
- Expected results documented
- Run commands documented
- Store baseline results in `hkj-benchmarks/baselines/vstream-baselines.json`

### 5.10 Benchmark Validation Tests

**File**: `hkj-benchmarks/src/test/java/org/higherkindedj/benchmarks/vstream/VStreamBenchmarkTest.java`

Validate that benchmarks compile and run without error (short iteration):
- Each benchmark method callable without exception
- Result validation (not just throughput measurement)

---

## Documentation

### 5.11 Javadoc

All public types and methods require comprehensive javadoc:
- VStreamPar: Each method with usage guidance, concurrency recommendations
- Chunking methods: Behaviour with edge cases, laziness guarantees
- VStreamPath parallel methods: Relationship to VStreamPar
- Performance characteristics and when to use parallel vs sequential
- British English throughout

### 5.12 Documentation Page

**File**: `docs/vstream/vstream_parallel.md`

Following STYLE-GUIDE.md:
- "What You'll Learn" admonishment
- Problem: processing stream elements concurrently on virtual threads
- Solution: parEvalMap with bounded concurrency, chunking for batch I/O
- Diagram: pipeline with parallel evaluation stage
- Code examples: parallel API calls, batch database operations
- Concurrency guidance: how to choose concurrency level
- Backpressure explanation: why VStream doesn't need explicit backpressure
- Performance comparison table with benchmark results
- "Key Takeaways" admonishment
- "See Also" linking to VTask Par, virtual threads, VStreamPath

### 5.13 Performance Documentation

**File**: `docs/vstream/vstream_performance.md`

Following PERFORMANCE-TESTING-GUIDE.md:
- Benchmark methodology and configuration
- Results tables and analysis
- Comparison with alternatives
- Guidance for users on performance characteristics
- When VStream is appropriate vs raw Java streams

---

## Examples

### 5.14 Example Code

**File**: `hkj-examples/src/main/java/org/higherkindedj/examples/vstream/VStreamParallelExample.java`

Demonstrate realistic scenarios:

1. **Parallel API Calls**: Fetch user profiles concurrently with bounded concurrency
   ```java
   VStream<UserProfile> profiles = VStreamPar.parEvalMap(
       userIdStream, 8,
       userId -> VTask.of(() -> apiClient.fetchProfile(userId))
   );
   ```

2. **Batch Database Insert**: Chunk records and insert in batches
   ```java
   VStream<InsertResult> results = recordStream
       .chunk(100)
       .mapTask(batch -> VTask.of(() -> db.batchInsert(batch)));
   ```

3. **Image Processing Pipeline**: Download, resize, upload with different concurrency
   ```java
   VStreamPath<UploadResult> pipeline =
       Path.vstream(imageUrls)
           .parEvalMap(4, url -> VTask.of(() -> download(url)))
           .map(ImageProcessor::resize)
           .parEvalMap(2, img -> VTask.of(() -> upload(img)));
   ```

4. **Merge Multiple Sources**: Combine data from multiple APIs
   ```java
   VStream<Event> allEvents = VStreamPar.merge(List.of(
       fetchEventsFromServiceA(),
       fetchEventsFromServiceB(),
       fetchEventsFromServiceC()
   ));
   ```

---

## Tutorials

### 5.15 Tutorial

**File**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/vstream/Tutorial05_VStreamParallel.java`

Following TUTORIAL-STYLE-GUIDE.md:
- 8-10 exercises:
  1. Use parEvalMap to process elements concurrently
  2. Verify that parEvalMap preserves element order
  3. Use parEvalMapUnordered for maximum throughput
  4. Chunk a stream into batches of a given size
  5. Use mapChunked for batch processing
  6. Combine chunking with parEvalMap for batch API calls
  7. Merge two streams concurrently
  8. Use parCollect to collect results in parallel batches
  9. Build a complete pipeline: generate, parallel process, chunk, collect
  10. Verify concurrency bounds using AtomicInteger counter
- Solution file: `Tutorial05_VStreamParallel_Solution.java`
- Time estimate: 15 minutes

---

## Acceptance Criteria

- [ ] VStreamPar class with parEvalMap, parEvalMapUnordered, parEvalFlatMap, merge, parCollect
- [ ] Chunking operations: chunk, chunkWhile, mapChunked
- [ ] VStreamPath parallel and chunk methods
- [ ] PathOps parallel operations
- [ ] parEvalMap preserves order and respects concurrency limit
- [ ] StructuredTaskScope used for proper cancellation and error propagation
- [ ] Fail-fast error semantics in parallel operations
- [ ] Virtual thread usage verified in tests
- [ ] All parallel tests pass with concurrency verification
- [ ] All chunking tests pass including edge cases
- [ ] JMH benchmarks compile and produce meaningful results
- [ ] Baseline results stored
- [ ] Javadoc complete on all public API
- [ ] Documentation pages written (parallel guide, performance guide)
- [ ] Example code with realistic scenarios
- [ ] Tutorial and solution file complete
- [ ] All existing tests continue to pass

---

## GitHub Issue Summary

**Title**: VStream Stage 5: Parallel Operations, Chunking, and Benchmarks

Add parallel processing capabilities to VStream leveraging virtual threads for concurrent
element evaluation, and introduce chunking for efficient batch operations. Includes JMH
benchmarks comparing VStream against raw Java streams.

**Key deliverables**:
- `VStreamPar` parallel combinators:
  - `parEvalMap` - bounded-concurrency parallel map preserving order
  - `parEvalMapUnordered` - bounded-concurrency parallel map, completion order
  - `parEvalFlatMap` - parallel flatMap with interleaving
  - `merge` - concurrent merge of multiple streams
  - `parCollect` - parallel batch collection
- Chunking operations on VStream: `chunk(size)`, `chunkWhile(predicate)`,
  `mapChunked(size, batchFn)`
- VStreamPath parallel methods: `parEvalMap`, `parEvalMapUnordered`, `chunk`,
  `mapChunked`, `parCollect`
- PathOps parallel variants: `parTraverseVStream`, `parCollectVStream`
- Uses `StructuredTaskScope` for cancellation and error propagation
- JMH benchmarks: construction, sequential, parallel, chunking, memory
- Concurrency verification tests (max-in-flight tracking, virtual thread assertion)
- Documentation: parallel processing guide, performance analysis
- Example code: parallel API calls, batch DB insert, image pipeline, multi-source merge
- Tutorial with 10 exercises

**Package**: `org.higherkindedj.hkt.vstream` in `hkj-core`,
`org.higherkindedj.benchmarks.vstream` in `hkj-benchmarks`

**Dependencies**: Stages 1-3 (Core VStream, HKT Encoding, VStreamPath)
