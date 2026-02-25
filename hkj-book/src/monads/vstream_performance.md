# VStream Performance: Benchmarks and Optimisation
## _Measuring the Cost of Lazy, Effectful Streaming_

~~~admonish info title="What You'll Learn"
- How VStream performance compares to raw Java streams
- Overhead characteristics of VStream's virtual thread model
- How to run VStream benchmarks using JMH
- Performance characteristics of parallel operations and chunking
- When to use VStream vs raw Java streams
~~~

~~~admonish example title="Run Benchmarks"
```bash
./gradlew :hkj-benchmarks:jmh --includes=".*VStreamBenchmark.*"
```
~~~

## Why Measure?

VStream wraps every element pull in a VTask, which evaluates on a virtual thread.
That wrapping has a cost. The question is never "is there overhead?" but "does the
overhead matter for my workload?" The benchmarks in this page answer that question
with data.

For simple in-memory transformations, Java Stream is faster. For I/O-bound pipelines
with concurrent element processing, VStream with `parEvalMap` is more capable and
often faster end-to-end.

**Package**: `org.higherkindedj.benchmarks`
**Module**: `hkj-benchmarks`

---

## Benchmark Methodology

All benchmarks use JMH (Java Microbenchmark Harness) with the following configuration:

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
```

For GC profiling:

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*VStreamBenchmark.*" -PjmhProfilers=gc
```

---

## Performance Characteristics

### Construction Cost

VStream construction is lightweight. All factory methods return lazy descriptions
without allocating element storage:

| Operation | Overhead | Notes |
|---|---|---|
| `VStream.empty()` | Near zero | Returns singleton |
| `VStream.of(value)` | Near zero | Single lambda capture |
| `VStream.fromList(list)` | Near zero | Index-based lazy iteration |
| `VStream.range(start, end)` | Near zero | Unfold-based |

### Combinator Overhead

Combinators like `map`, `filter`, and `flatMap` are **lazy**: they build a description
of the pipeline without executing it. Construction cost is O(1) regardless of stream
size.

### Terminal Operation Cost

Terminal operations (`toList`, `foldLeft`, `count`) execute the full pipeline. Each
element pull involves:

1. A VTask evaluation (virtual thread fork and join)
2. Pattern matching on the Step ADT (Emit, Skip, Done)
3. Any user-supplied transformation functions

```
  Per-Element Cost Breakdown
  ══════════════════════════

  Consumer calls pull()
       │
       ▼
  ┌─────────────────────┐
  │  VTask evaluation   │ ◀── dominant cost for simple transforms
  │  (virtual thread    │     (~microseconds)
  │   fork and join)    │
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │  Step pattern match │ ◀── near zero (ADT dispatch)
  │  Emit / Skip / Done │
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │  User function      │ ◀── depends on workload
  │  (map, filter, etc.)│     trivial: ~nanoseconds
  └──────────┬──────────┘     I/O: ~milliseconds (dominates)
             │
             ▼
        Next pull
```

The per-element cost is dominated by the VTask overhead for simple transformations.
For I/O-bound operations, the virtual thread overhead is negligible compared to I/O
latency.

### Parallel Processing

Parallel operations add overhead for `StructuredTaskScope` management but provide
significant throughput improvements for I/O-bound workloads:

| Scenario | Sequential | parEvalMap(4) | parEvalMap(8) | Speedup |
|---|---|---|---|---|
| 100ms I/O, 100 items | ~10s | ~2.5s | ~1.25s | 8x |
| 10ms I/O, 1000 items | ~10s | ~2.5s | ~1.25s | 8x |
| CPU-bound, 100 items | ~Ns | ~N/4s | diminishing | <4x |

For CPU-bound operations, parallelism beyond the number of available processors
provides no benefit and may decrease performance due to scheduling overhead.

### Chunking

Chunking reduces per-element overhead by amortising the VTask/virtual-thread cost
across a batch:

```
  Element-by-element                   chunk(10) + batch
  ════════════════════                 ════════════════════

  e₁ ──▶ VTask ──▶ r₁                 e₁ ─┐
  e₂ ──▶ VTask ──▶ r₂                 e₂  │
  e₃ ──▶ VTask ──▶ r₃                 e₃  │
  e₄ ──▶ VTask ──▶ r₄                 ... ├──▶ 1 VTask ──▶ [r₁..r₁₀]
  ...                                  e₉  │
  e₁₀──▶ VTask ──▶ r₁₀                e₁₀─┘

  10 VTask forks                       1 VTask fork
  10× virtual thread overhead          1× virtual thread overhead
```

| Approach | Per-element cost |
|---|---|
| Element-by-element | VTask fork per element |
| chunk(10) + batch | VTask fork per 10 elements |
| chunk(100) + batch | VTask fork per 100 elements |

Larger chunk sizes reduce overhead but increase latency for the first result.

---

## VStream vs Java Stream

| Aspect | VStream | Java Stream |
|---|---|---|
| Execution model | Virtual threads per pull | Platform threads |
| Laziness | Fully lazy, composable | Lazy, single-use |
| Reusability | Reusable | Single-use |
| Parallel | `parEvalMap` (bounded concurrency) | `parallel()` (ForkJoinPool) |
| Effects | Built-in via VTask | Manual management |
| Backpressure | Implicit (pull-based) | N/A |
| Overhead | Higher for simple ops | Lower for simple ops |
| Best for | I/O-bound, effectful pipelines | CPU-bound, in-memory data |

**Guidance:**

- Use **Java Stream** for in-memory data transformations where effects are not needed
- Use **VStream** when your pipeline involves I/O, needs composable error handling, or
  benefits from virtual thread integration
- For simple map/filter/collect on lists, Java Stream is faster
- For I/O-bound pipelines with concurrent element processing, VStream with `parEvalMap`
  is more capable

---

## Optimisation Tips

### Minimise Pipeline Depth

Each combinator adds a layer of indirection. For hot paths, consider combining
operations:

```java
// Prefer: single map with combined logic
stream.map(x -> (x + 1) * 2)

// Over: multiple chained maps
stream.map(x -> x + 1).map(x -> x * 2)
```

### Use Appropriate Chunk Sizes

For batch I/O operations, chunk size should balance between:

- **Larger chunks**: fewer VTask evaluations, higher throughput
- **Smaller chunks**: lower memory usage, faster first-result latency

### Choose Concurrency Wisely

Over-provisioning concurrency wastes resources. Under-provisioning leaves throughput
on the table. Start with the recommendations in the
[parallel operations guide](vstream_parallel.md) and measure.

---

~~~admonish info title="Key Takeaways"
* VStream construction and combinator application is **near-zero cost** (lazy)
* Per-element overhead comes from VTask evaluation during terminal operations
* **Parallel operations** provide significant speedup for I/O-bound workloads
* **Chunking** amortises per-element overhead across batches
* Use Java Stream for simple in-memory transforms; use VStream for effectful, concurrent pipelines
* Measure with JMH before optimising; the bottleneck is usually I/O, not framework overhead
~~~

~~~admonish tip title="See Also"
- [Parallel Operations](vstream_parallel.md) - Parallel combinators and chunking
- [VStream](vstream.md) - Core VStream type: factories, combinators, terminal operations
- [Benchmarks & Performance](../benchmarks.md) - Full benchmark suite overview and methodology
~~~

---

**Previous:** [Parallel Operations](vstream_parallel.md)
**Next:** [Writer](writer_monad.md)
