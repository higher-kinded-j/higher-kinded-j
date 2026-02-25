# VStream: Lazy Pull-Based Streaming on Virtual Threads
## _Consumer-Driven Pipelines That Wait Until You're Ready_

~~~admonish info title="What You'll Learn"
- How VStream provides lazy, pull-based streaming with virtual thread execution
- The Step protocol: Emit, Done, and Skip
- Factory methods for creating streams from various sources
- Transformation combinators: map, flatMap, filter, take
- Terminal operations: toList, fold, exists, find
- Error handling and recovery patterns
- How VStream compares to Java Stream and StreamPath
~~~

> *"Tape after tape. You don't get the whole story at once. You pull the next one, press play, and hope you're ready for what comes out."*
> — **Dan Powell**, *Archive 81*

~~~admonish example title="See Example Code"
[VStreamBasicExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamBasicExample.java)
~~~

## The Problem: Streams That Won't Wait

Dan Powell's archive is a pull-based stream: no tape plays itself, nothing advances until
he reaches for the next cassette. Java's `Stream` works the other way around. It pushes
elements whether you are ready or not, blocks a platform thread on every I/O call, and
falls apart the moment the data source is infinite or each element costs a network
round-trip. Once the pipeline starts, the consumer has no way to slow it down.

That eager-push model creates real problems. A paginated API that returns thousands of
pages will eagerly fetch all of them, even if the caller only needs the first ten. A
sensor feed that never ends will blow up any terminal operation that tries to collect
results. And because each blocked `Stream` operation pins a platform thread, scaling to
thousands of concurrent streams means thousands of expensive OS threads sitting idle
while they wait for data.

## The Solution: Pull One Element at a Time

`VStream<A>` works like Dan's archive. Each pull returns a `VTask<Step<A>>`, a single
element produced lazily on a virtual thread. The consumer controls the pace. The stream
waits. Infinite sequences, paginated APIs, slow upstream services: none of these are a
problem when you only process one element at a time and the thread model scales to
millions of concurrent tasks.

```
  Push Model (Java Stream)              Pull Model (VStream)
  ========================              =====================

  ┌────────┐         ┌──────────┐      ┌──────────┐         ┌────────┐
  │ Source │ ──▶──▶─▶│ Consumer │      │ Consumer │◀────────│ Source │
  └────────┘         └──────────┘      └─────┬────┘         └────────┘
                                             │ pull()            ▲
  Source drives the pace.                    ▼                   │
  Consumer must keep up.              VTask<Step<A>>        one element
  Blocks a platform thread.           (virtual thread)     at a time
  No backpressure.
                                       Consumer drives the pace.
                                       One virtual thread per pull.
                                       Natural backpressure.
```

**Package**: `org.higherkindedj.hkt.vstream`
**Module**: `hkj-core`

---

## The Pull Protocol: Step

Every pull from a VStream answers exactly one question: *what happened when I asked for the next element?* The answer is always one of three cases, represented by the `Step<A>` sealed interface:

```java
sealed interface Step<A> {
    record Emit<A>(@Nullable A value, VStream<A> tail) implements Step<A> {}
    record Done<A>()                                    implements Step<A> {}
    record Skip<A>(VStream<A> tail)                     implements Step<A> {}
}
```

```
                            pull()
                              │
                              ▼
                       ┌─────────────┐
                       │ VTask<Step> │  (runs on virtual thread)
                       └──────┬──────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
    │    Emit     │    │    Skip     │    │    Done     │
    │ value, tail │    │    tail     │    │             │
    └──────┬──────┘    └──────┬──────┘    └─────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
      Yield value,       Advance to          Stream
      continue from      tail (zero          exhausted,
      tail               allocation)         stop pulling
```

| Step | Meaning |
|------|---------|
| `Emit(value, tail)` | Here is a value, and here is the rest of the stream |
| `Done()` | The stream is exhausted; stop pulling |
| `Skip(tail)` | No value this time (e.g. filtered out); advance to the tail |

Why `Skip`? Consider a `filter` that rejects 90% of elements. Without `Skip`, the stream would need to allocate and discard wrapper objects for every rejected element. `Skip` short-circuits that: the pipeline simply advances to the next pull without producing anything, keeping memory allocation tight.

---

## Where Does Your Data Come From?

VStream provides factory methods that cover the most common sources. All of them are lazy: nothing is produced until a terminal operation triggers evaluation.

### From Values You Already Have

```java
// Single element
VStream<String> single = VStream.of("hello");

// Multiple elements (varargs)
VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);

// From an existing list (lazy; does not copy)
VStream<String> fromList = VStream.fromList(List.of("a", "b", "c"));

// Integer range [start, end)
VStream<Integer> range = VStream.range(1, 6); // 1, 2, 3, 4, 5

// Empty stream
VStream<Integer> empty = VStream.empty();
```

### From Sequences That Never End

The pull model means infinite sources are perfectly safe. The consumer decides when to stop.

```java
// Infinite sequence from seed and step function
VStream<Integer> powersOf2 = VStream.iterate(1, n -> n * 2);
// 1, 2, 4, 8, 16, 32, ...

// Infinite sequence from supplier
VStream<Double> randoms = VStream.generate(Math::random);

// Infinite repetition of a single value
VStream<String> hellos = VStream.repeat("hello");
```

~~~admonish warning title="Infinite Streams"
Infinite streams require a limiting operation such as `take()` or `takeWhile()` before
any terminal operation that consumes all elements (like `toList()` or `fold()`).
Short-circuiting terminal operations like `exists()` and `find()` are safe to use
directly on infinite streams.
~~~

### From Effectful Sources (Paginated APIs, Databases, etc.)

This is where VStream really separates itself from Java `Stream`. The `unfold()` factory
creates a stream by repeatedly applying an effectful function to a state, producing
elements until `Optional.empty()` signals the end. Each step runs inside a `VTask`, so
network calls, database queries, and file reads all happen on virtual threads without
blocking the caller.

```java
// Paginated API: fetch pages until the server says "no more"
VStream<String> pages = VStream.unfold(1, page ->
    VTask.of(() -> {
        if (page > 3) return Optional.empty();
        String data = fetchPage(page);
        return Optional.of(new VStream.Seed<>(data, page + 1));
    }));
```

The `Seed<A, S>` record carries both the emitted value and the next state:

```java
public record Seed<A, S>(@Nullable A value, S next) {}
```

Each call to the unfold function advances the state and either emits a value or signals completion:

```
  unfold(1, fetchPage)

  State: 1 ──▶ fetchPage(1) ──▶ Seed("page1", 2) ──▶ Emit("page1")
  State: 2 ──▶ fetchPage(2) ──▶ Seed("page2", 3) ──▶ Emit("page2")
  State: 3 ──▶ fetchPage(3) ──▶ Seed("page3", 4) ──▶ Emit("page3")
  State: 4 ──▶ fetchPage(4) ──▶ Optional.empty()  ──▶ Done
```

---

## Shaping the Data You Need

All transformation operations are lazy. They describe a new pipeline without producing any elements. No work happens until a terminal operation pulls through the chain.

### map

Transform each element:

```java
List<String> result = VStream.of(1, 2, 3)
    .map(n -> "#" + n)
    .toList().run();
// ["#1", "#2", "#3"]
```

### filter

Keep only elements matching a predicate. Rejected elements produce a `Skip` step internally, avoiding unnecessary allocation:

```java
List<Integer> evens = VStream.range(1, 11)
    .filter(n -> n % 2 == 0)
    .toList().run();
// [2, 4, 6, 8, 10]
```

### flatMap

Replace each element with a sub-stream and flatten the results. This is the monadic
bind operation for VStream:

```java
List<Integer> result = VStream.of(1, 2, 3)
    .flatMap(n -> VStream.of(n, n * 10))
    .toList().run();
// [1, 10, 2, 20, 3, 30]
```

The `via()` method is an alias for `flatMap`, consistent with the FocusDSL vocabulary.

### take and takeWhile

These operations give the consumer explicit control over how much data to pull. They are the safety valve for infinite streams.

```java
// First 5 elements of an infinite stream
List<Integer> first5 = VStream.iterate(1, n -> n + 1)
    .take(5).toList().run();
// [1, 2, 3, 4, 5]

// Elements while condition holds
List<Integer> small = VStream.iterate(1, n -> n * 2)
    .takeWhile(n -> n < 100).toList().run();
// [1, 2, 4, 8, 16, 32, 64]
```

### Composing Pipelines

Operations chain naturally into lazy pipelines. Each stage pulls from the previous one, and the entire chain processes elements one at a time. The consumer (on the left) drives everything:

```
  ┌──────────┐  pull  ┌─────────┐  pull  ┌────────┐  pull  ┌────────────┐  pull  ┌────────────┐
  │ toList() │◀───────│ take(5) │◀───────│ map(×3)│◀───────│filter(even)│◀───────│range(1,100)│
  └──────────┘        └─────────┘        └────────┘        └────────────┘        └────────────┘
       │                                                          │                     │
       │   range emits 1 ·········································· Skip (odd) ◀── Emit(1)
       │   range emits 2 ········· filter passes ·· Emit(2) ◀──── Emit(2)  ◀── Emit(2)
       │                           map: 2 × 3 = 6                 take: 1 of 5
       ◀── Emit(6) ◀── Emit(6) ◀─ Emit(6) ◀──────────────────────────────────────────────
       │
       │   ... continues until take has collected 5 elements, then stops.
       ▼
  [6, 12, 18, 24, 30]
```

```java
List<Integer> result = VStream.range(1, 100)
    .filter(n -> n % 2 == 0)   // keep evens
    .map(n -> n * 3)            // multiply by 3
    .take(5)                    // first 5 results
    .toList().run();
// [6, 12, 18, 24, 30]
```

No element is produced until `toList().run()` triggers evaluation. The pipeline pulls only as many elements as needed, then stops.

---

## Collecting Results

Terminal operations consume the stream and return a `VTask` that must be `run()` to
execute. All terminal operations use iterative loops for stack safety.

| Operation | Return Type | Description |
|-----------|-------------|-------------|
| `toList()` | `VTask<List<A>>` | Collect all elements |
| `fold(seed, op)` | `VTask<A>` | Left fold with seed |
| `foldLeft(seed, fn)` | `VTask<B>` | Left fold with accumulator |
| `headOption()` | `VTask<Optional<A>>` | First element or empty |
| `lastOption()` | `VTask<Optional<A>>` | Last element or empty |
| `count()` | `VTask<Long>` | Count elements |
| `exists(predicate)` | `VTask<Boolean>` | Any match (short-circuits) |
| `forAll(predicate)` | `VTask<Boolean>` | All match (short-circuits) |
| `find(predicate)` | `VTask<Optional<A>>` | First matching element |
| `forEach(consumer)` | `VTask<Unit>` | Side effect per element |
| `drain()` | `VTask<Unit>` | Consume and discard all elements |

### Fold Example

```java
Integer sum = VStream.of(1, 2, 3, 4, 5)
    .fold(0, Integer::sum).run();
// 15
```

### Short-Circuiting: Stopping Early on Infinite Streams

`exists()` and `forAll()` stop pulling as soon as the result is determined. This makes
them safe even on infinite streams, because the pull model means the producer never runs
ahead of the consumer:

```java
// Safe: stops as soon as 42 is found
Boolean found = VStream.iterate(0, n -> n + 1)
    .exists(n -> n == 42).run();
// true
```

---

## Merging Multiple Streams

### Concatenation

Append one stream after another. The second stream is not touched until the first is exhausted:

```java
VStream<Integer> combined = VStream.of(1, 2).concat(VStream.of(3, 4));
// [1, 2, 3, 4]
```

### Zip

Pair elements positionally. Stops at the shorter stream, so it is safe to zip a finite stream with an infinite one:

```java
List<String> zipped = VStream.of("a", "b", "c")
    .zipWith(VStream.of(1, 2, 3), (s, n) -> s + n)
    .toList().run();
// ["a1", "b2", "c3"]
```

### Interleave

Alternate elements from two streams:

```java
List<Integer> interleaved = VStream.of(1, 3, 5)
    .interleave(VStream.of(2, 4, 6))
    .toList().run();
// [1, 2, 3, 4, 5, 6]
```

---

## When Things Go Wrong

Real-world streams encounter failures: a network timeout on page 47, a malformed record in a CSV, a rate-limited API. VStream integrates with VTask's error model so that each individual pull can fail and recover independently, without tearing down the entire pipeline.

```
  pull ──▶ VTask succeeds ──▶ Emit(value) ──▶ continue pulling
                                                       │
  pull ──▶ VTask fails ───────────────────────────────▶│
              │                                        │
              ├── recover(e -> fallback)               │
              │      └──▶ Emit(fallback) ─────────────▶│
              │                                        │
              ├── recoverWith(e -> altStream)          │
              │      └──▶ switch to altStream ────────▶│
              │                                        │
              └── no recovery                          │
                   └──▶ VTask propagates error         ▼
                                                   next pull
```

### recover

Replace a failed pull with a recovery value:

```java
List<String> result = VStream.<String>fail(new RuntimeException("oops"))
    .recover(e -> "recovered: " + e.getMessage())
    .toList().run();
// ["recovered: oops"]
```

### recoverWith

Replace a failed pull with a fallback stream:

```java
List<String> result = VStream.<String>fail(new RuntimeException("primary failed"))
    .recoverWith(e -> VStream.of("fallback-1", "fallback-2"))
    .toList().run();
// ["fallback-1", "fallback-2"]
```

### mapError

Transform errors without recovering, useful for wrapping low-level exceptions into domain-specific ones:

```java
VStream<String> mapped = VStream.<String>fail(new RuntimeException("original"))
    .mapError(e -> new IllegalStateException("wrapped: " + e.getMessage()));
```

~~~admonish note title="Per-Pull Recovery"
Recovery applies per-pull, not per-stream. Each element pull can independently fail and
recover. After recovery, the stream continues from the recovery point.
~~~

---

## Proving Laziness: Nothing Runs Until You Say So

If you are sceptical about lazy evaluation, here is the proof. Building a pipeline does
zero work. Only the terminal operation triggers element production:

```java
AtomicInteger evaluations = new AtomicInteger(0);

// Pipeline construction: no elements produced yet
VStream<Integer> pipeline = VStream.generate(evaluations::incrementAndGet)
    .filter(n -> n % 2 == 0)
    .map(n -> n * 100)
    .take(5);

System.out.println(evaluations.get()); // 0

// Terminal operation triggers evaluation
List<Integer> result = pipeline.toList().run();
System.out.println(evaluations.get()); // 10 (pulled 10 to get 5 evens)
```

The counter stays at zero after pipeline construction. It only advances when `toList().run()` starts pulling, and it stops at exactly 10 because `take(5)` combined with a 50% filter pass rate means only 10 pulls are needed.

---

## Choosing the Right Streaming Tool

| Aspect | VStream | Java Stream | StreamPath |
|--------|---------|-------------|------------|
| Evaluation | Lazy, pull-based | Lazy, push-based | Eager (materialised list) |
| Execution | Virtual threads (VTask) | Platform threads | Synchronous |
| Reusability | Reusable | Single-use | Reusable |
| Infinite streams | Yes (take, takeWhile) | Yes (limit) | No |
| Error handling | recover, recoverWith | Exceptions | Via effect type |
| HKT integration | Yes (Functor, Monad, Traverse) | No | Yes |
| Effect composition | VTask per pull | None | Via Path API |

**Use VStream when** you need lazy streaming with virtual thread execution, effectful
element production (e.g., paginated API calls), or infinite stream support with
backpressure through pull-based consumption.

**Use StreamPath when** you have an already-materialised list and want fluent Path API
composition with optics integration.

**Use Java Stream when** you need standard library compatibility and do not require
virtual thread integration or error recovery.

---

~~~admonish info title="Key Takeaways"
* **VStream is lazy**: no elements are produced until a terminal operation runs
* **Pull-based**: the consumer drives evaluation, providing natural backpressure
* **Virtual thread execution**: each pull returns a VTask, leveraging virtual threads
* **Step protocol**: Emit (value + continuation), Done (exhausted), Skip (efficient filtering)
* **Stack-safe**: all terminal operations use iterative loops; flatMap handles deep chains
* **Composable**: map, flatMap, filter, take chain lazily into pipelines
* **Error recovery**: recover and recoverWith handle failures at the stream level
~~~

~~~admonish info title="Hands-On Learning"
Practice VStream basics in [TutorialVStream](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVStream.java) (11 exercises, ~12-15 minutes).
~~~

~~~admonish example title="Benchmarks"
VStream has a dedicated JMH benchmark suite measuring construction, combinator pipelines, terminal operations, and comparison with raw Java Streams. Run it with:
```bash
./gradlew :hkj-benchmarks:jmh --includes=".*VStreamBenchmark.*"
```
See [Benchmarks & Performance](../benchmarks.md) for full details and how to interpret results.
~~~


~~~admonish tip title="See Also"
- [HKT and Type Classes](vstream_hkt.md) - VStream's type class instances for generic programming
- [Parallel Operations](vstream_parallel.md) - parEvalMap, merge, chunking, and concurrency control
- [Performance](vstream_performance.md) - Benchmarks, overhead characteristics, and optimisation tips
- [VStreamPath](../effect/path_vstream.md) - Fluent Effect Path wrapper for VStream
- [VTask](vtask_monad.md) - The single-value effect type that powers VStream pulls
- [VTaskPath](../effect/path_vtask.md) - Fluent Path API wrapper for VTask
- [Stream](stream_monad.md) - Eager list-based streaming
- [Each Typeclass](../optics/each_typeclass.md) - Canonical element-wise traversal (includes VStream)
~~~

---

**Previous:** [Resource Management](vtask_resource.md)
**Next:** [HKT and Type Classes](vstream_hkt.md)
