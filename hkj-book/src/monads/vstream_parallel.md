# VStream Parallel Operations: Concurrent Processing on Virtual Threads
## _Bounded Concurrency, Chunking, and Fail-Fast Error Handling_

~~~admonish info title="What You'll Learn"
- How to process stream elements concurrently using `VStreamPar`
- The difference between order-preserving and unordered parallel processing
- How to use chunking for efficient batch operations
- Why VStream does not need explicit backpressure
- How to choose the right concurrency level for your workload
- Error handling semantics in parallel stream operations
~~~

~~~admonish example title="See Example Code"
[VStreamParallelExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VStreamParallelExample.java)
~~~

## The Problem: Sequential I/O Is Slow

Processing stream elements one at a time is simple but can be painfully slow for
I/O-bound workloads. If each element requires a 100ms API call and you have 1,000
elements, sequential processing takes 100 seconds. Traditional approaches (thread pools,
reactive streams) solve the throughput problem but add complexity: explicit backpressure
protocols, scheduler configuration, and error handling that is easy to get wrong.

## The Solution: VStreamPar

`VStreamPar` provides parallel combinators that leverage Java 25 virtual threads via
`StructuredTaskScope`. The API is straightforward:

- Process elements concurrently on virtual threads
- Limit in-flight elements with bounded concurrency
- Preserve input order (or emit in completion order for maximum throughput)
- Fail fast if any element's computation fails, cancelling remaining tasks

```java
VStream<String> userIds = VStream.fromList(List.of("u1", "u2", "u3", "u4"));

// Fetch user profiles concurrently, at most 4 in flight at a time
VStream<UserProfile> profiles = VStreamPar.parEvalMap(
    userIds, 4,
    userId -> VTask.of(() -> apiClient.fetchProfile(userId))
);

List<UserProfile> result = profiles.toList().run();
// Results are in the same order as input user IDs
```

**Package**: `org.higherkindedj.hkt.vstream`
**Module**: `hkj-core`

---

## Core Operations

### parEvalMap: Order-Preserving Parallel Map

The primary parallel operation. Applies an effectful function to each element with
bounded concurrency, preserving input order.

**How it works:**

1. Pulls up to `concurrency` elements from the source stream
2. Forks each element's VTask onto a virtual thread via `StructuredTaskScope`
3. Collects results in input order
4. Emits the batch, then pulls the next batch
5. Repeats until the source stream is exhausted

```
                          parEvalMap(source, 4, fn)

  Source         Pull batch of 4         StructuredTaskScope          Output
  ──────         ───────────────         ───────────────────          ──────

  ┌──┬──┬──┬──┬──┬──┬──┬──┐          ┌── VTask(e₁) ──▶ r₁ ──┐
  │e₈│e₇│e₆│e₅│e₄│e₃│e₂│e₁│──pull──▶├── VTask(e₂) ──▶ r₂ ──┤ collect   ┌──┬──┬──┬──┐
  └──┴──┴──┴──┴──┴──┴──┴──┘   4     ├── VTask(e₃) ──▶ r₃ ──┤ in order ▶│r₁│r₂│r₃│r₄│
                                    └── VTask(e₄) ──▶ r₄ ──┘           └──┴──┴──┴──┘
                                                                               │
                   pull next 4 ◀───────────────────────────────────────────────┘
                       │
                       ▼
                  ┌── VTask(e₅) ──▶ r₅ ──┐
                  ├── VTask(e₆) ──▶ r₆ ──┤ collect   ┌──┬──┬──┬──┐
                  ├── VTask(e₇) ──▶ r₇ ──┤ in order ▶│r₅│r₆│r₇│r₈│
                  └── VTask(e₈) ──▶ r₈ ──┘           └──┴──┴──┴──┘

  ... repeats until source is exhausted, then Done.
```

```java
VStream<Integer> doubled = VStreamPar.parEvalMap(
    numbers, 8,
    n -> VTask.of(() -> expensiveComputation(n))
);
// Output order matches input order
```

### parEvalMapUnordered: Maximum Throughput

When output order does not matter, `parEvalMapUnordered` emits results as they
complete. This maximises throughput because fast elements are not held back by slow
ones:

```
  parEvalMap (ordered)                 parEvalMapUnordered
  ════════════════════                 ════════════════════

  e₁ ──▶ VTask (slow)  ── 120ms ─┐   e₁ ──▶ VTask (slow)  ── 120ms ──▶ r₁
  e₂ ──▶ VTask (fast)  ──  20ms ─┤   e₂ ──▶ VTask (fast)  ──  20ms ──▶ r₂
  e₃ ──▶ VTask (medium)──  60ms ─┤   e₃ ──▶ VTask (medium)──  60ms ──▶ r₃
                                 │
               wait for all ◀────┘
                                      Emit order:  r₂, r₃, r₁
  Emit order:  r₁, r₂, r₃            (fastest first — 20ms to first result)
  (input order — 120ms to first result)
```

```java
VStream<Integer> processed = VStreamPar.parEvalMapUnordered(
    numbers, 8,
    n -> VTask.of(() -> expensiveComputation(n))
);
// Results may be in any order
```

### parEvalFlatMap: Parallel Stream Expansion

Applies a stream-producing function to each element with bounded concurrency. Up to
`concurrency` sub-stream creation calls run in parallel via `parEvalMap`; the resulting
sub-streams are then concatenated lazily via `flatMap` — sub-stream contents are never
materialised into intermediate lists:

```java
VStream<Order> orders = VStreamPar.parEvalFlatMap(
    customerIds, 4,
    customerId -> fetchOrders(customerId) // returns VStream<Order>
);
```

### merge: Concurrent Multi-Source Consumption

Combines multiple streams concurrently. Each source stream is consumed on its own
virtual thread within a `StructuredTaskScope`. Elements are pushed to a shared queue
as they are produced, so the first element is available as soon as any source produces
one — without waiting for all sources to finish:

```java
VStream<Event> allEvents = VStreamPar.merge(List.of(
    fetchEventsFromServiceA(),
    fetchEventsFromServiceB(),
    fetchEventsFromServiceC()
));
// Elements arrive as they become available from any source
```

### parCollect: Parallel Batch Collection

Terminal operation that collects all elements using parallel batch processing.
Delegates to `parEvalMap` with an identity function, pulling elements in batches of
`batchSize` that are processed concurrently:

```java
VTask<List<Integer>> result = VStreamPar.parCollect(stream, 10);
List<Integer> collected = result.run();
```

---

## Chunking Operations

Chunking groups elements into batches for efficient bulk operations such as database
inserts or batch API calls.

### chunk(size)

Groups elements into lists of at most `size` elements. The last chunk may have fewer:

```java
VStream<List<Integer>> chunks = VStream.range(1, 11).chunk(3);
// [[1,2,3], [4,5,6], [7,8,9], [10]]
```

### chunkWhile(predicate)

Groups consecutive elements while a predicate holds between adjacent pairs. Useful for
grouping sorted data:

```java
VStream<List<Integer>> groups = VStream.of(1, 1, 2, 2, 2, 3)
    .chunkWhile(Integer::equals);
// [[1,1], [2,2,2], [3]]
```

### mapChunked(size, batchFn)

Combines chunking, batch transformation, and flattening into a single operation:

```java
VStream<String> results = recordStream.mapChunked(100, batch -> {
    return db.batchInsert(batch); // Process 100 records at a time
});
```

---

## Combined Patterns

### Chunk then Parallel Process

The most powerful pattern combines chunking with parallel processing. This is ideal for
bulk I/O operations:

```
  Chunk then Parallel Process
  ═══════════════════════════

  recordStream                  chunk(100)                  parEvalMap(4)
  ────────────                  ──────────                  ─────────────

  r₁ r₂ ... r₁₀₀  ──▶  ┌─────────────────┐          ┌── VTask(batch₁) ──▶ insert₁ ──┐
  r₁₀₁ ... r₂₀₀   ──▶  │  [r₁..r₁₀₀]     │──┐       ├── VTask(batch₂) ──▶ insert₂ ──┤
  r₂₀₁ ... r₃₀₀   ──▶  │  [r₁₀₁..r₂₀₀]   │──┼──▶    ├── VTask(batch₃) ──▶ insert₃ ──┤
  r₃₀₁ ... r₄₀₀   ──▶  │  [r₂₀₁..r₃₀₀]   │──┤       └── VTask(batch₄) ──▶ insert₄ ──┘
  ...                  │  [r₃₀₁..r₄₀₀]   │──┘              │
                       │  ...            │          4 batches in flight,
                       └─────────────────┘          next 4 pulled when done
```

```java
VStream<InsertResult> results = VStreamPar.parEvalMap(
    recordStream.chunk(100), // Group into batches of 100
    4,                       // Process 4 batches concurrently
    batch -> VTask.of(() -> db.batchInsert(batch))
);
```

### Image Processing Pipeline

Different pipeline stages can use different concurrency limits to match their
throughput characteristics:

```java
VStreamPath<UploadResult> pipeline =
    Path.vstream(imageUrls)
        .parEvalMap(4, url -> VTask.of(() -> download(url)))
        .map(ImageProcessor::resize)
        .parEvalMap(2, img -> VTask.of(() -> upload(img)));
```

---

## Backpressure: Why VStream Does Not Need It

Unlike reactive streams (Project Reactor, RxJava), VStream does not need an explicit
backpressure protocol. Three properties make it unnecessary:

```
  Why VStream Does Not Need Explicit Backpressure
  ═══════════════════════════════════════════════

  ┌──────────┐  pull(4)  ┌──────────────────┐  pull   ┌────────┐
  │ Consumer │◀──────────│  parEvalMap(4)   │◀────────│ Source │
  │ (slow)   │           │ bounded at 4     │         │        │
  └──────────┘           └──────────────────┘         └────────┘
       │                         │
       │ Consumer busy,          │ At most 4 in flight.
       │ not pulling.            │ No new pulls until
       │                         │ consumer asks.
       ▼                         ▼
   Source and workers          Virtual threads
   park (free) until           park while waiting —
   next pull arrives.          no OS thread consumed.
```

**Pull-based model.** The consumer drives evaluation. Elements are only produced when
pulled. If the consumer is slow, the producer simply waits.

**Bounded concurrency.** `parEvalMap` limits in-flight elements. At most `concurrency`
elements are processed at any time, providing natural rate limiting.

**Virtual threads.** Blocking on a virtual thread is cheap. Unlike platform threads,
virtual threads do not consume OS resources while blocked. A producer waiting for the
consumer costs essentially nothing.

### When to Prefer VStream over Reactive Streams

| Characteristic | VStream | Reactive Streams |
|---|---|---|
| Backpressure | Implicit (pull-based) | Explicit protocol |
| Threading model | Virtual threads | Scheduler-based |
| Error handling | Fail-fast, StructuredTaskScope | Various strategies |
| Learning curve | Low (sequential-looking code) | Higher |
| Best for | Virtual-thread-friendly I/O | Event-driven systems |

---

## Choosing Concurrency Levels

| Workload Type | Recommended Concurrency | Rationale |
|---|---|---|
| I/O-bound (API calls, DB) | 8-64 | Virtual threads handle I/O waiting efficiently |
| CPU-bound computation | Number of processors | Avoid oversubscription |
| Mixed I/O and CPU | 4-8 | Balance between the two |
| Rate-limited APIs | Match rate limit | Avoid exceeding quotas |

A concurrency of 1 is equivalent to sequential `mapTask` processing.

---

## Error Handling

All parallel operations use **fail-fast** semantics:

- If any element's computation throws an exception, the entire stream fails
- Other in-flight tasks are cancelled via `StructuredTaskScope`
- The original error is preserved and propagated

```java
VStream<Integer> result = VStreamPar.parEvalMap(
    stream, 4,
    n -> {
        if (n == 3) return VTask.fail(new RuntimeException("Element 3 failed"));
        return VTask.succeed(n * 2);
    }
);

// Materialising the stream throws RuntimeException("Element 3 failed")
```

---

~~~admonish info title="Key Takeaways"
* **parEvalMap** preserves input order with bounded concurrency; use for most parallel workloads
* **parEvalMapUnordered** maximises throughput when order does not matter
* **chunk** and **mapChunked** enable efficient batch operations (bulk API calls, database inserts)
* VStream's pull-based model provides **implicit backpressure** without explicit protocols
* **StructuredTaskScope** ensures proper cancellation and error propagation
* Choose concurrency based on workload type: higher for I/O-bound, lower for CPU-bound
~~~

~~~admonish info title="Hands-On Learning"
Practice parallel VStream patterns in [TutorialVStreamParallel](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVStreamParallel.java) (10 exercises, ~15 minutes).
~~~

~~~admonish tip title="See Also"
- [VStream](vstream.md) - Core VStream type: factories, combinators, terminal operations
- [VStream HKT](vstream_hkt.md) - Type class instances for generic programming
- [VStreamPath](../effect/path_vstream.md) - Fluent Effect Path wrapper for VStream
- [VTask: Structured Concurrency](vtask_scope.md) - StructuredTaskScope patterns for VTask
- [Performance](vstream_performance.md) - Benchmark results and optimisation guidance
- [Benchmarks & Performance](../benchmarks.md) - Full benchmark suite overview
~~~

---

**Previous:** [HKT and Type Classes](vstream_hkt.md)
**Next:** [Performance](vstream_performance.md)
