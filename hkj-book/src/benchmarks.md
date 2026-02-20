# Benchmarks & Performance

Higher-Kinded-J ships with a comprehensive JMH benchmark suite in the `hkj-benchmarks` module. These benchmarks measure the real cost of the library's abstractions so you can make informed decisions about where and when to use them.

~~~admonish info title="What You'll Learn"
- What the benchmark suite covers and how it is organised
- How to run benchmarks: all, per-type, with GC profiling
- How to interpret results and spot regressions
- What performance characteristics to expect from each type
~~~

> *"Measure. Don't guess."*
> — **Kirk Pepperdine**, Java performance expert

---

## Why Benchmarks Matter

Functional abstractions wrap values. Wrapping has a cost. The question is never "is there overhead?" — there always is — but "does the overhead matter for my workload?" The benchmark suite answers that question with data rather than intuition.

The suite is designed around three principles:

1. **Honesty** — measure real abstraction costs, not contrived best cases
2. **Comparability** — include raw Java baselines alongside library operations
3. **Actionability** — organise results so regressions are immediately visible

---

## What Is Measured

The `hkj-benchmarks` module contains 18 benchmark classes covering every major type in the library:

### Core Types

| Benchmark | Type | What It Tells You |
|-----------|------|-------------------|
| `EitherBenchmark` | `Either<L,R>` | Instance reuse on the Left track, short-circuit efficiency |
| `MaybeBenchmark` | `Maybe<A>` | Instance reuse on Nothing, nullable interop cost |
| `TrampolineBenchmark` | `Trampoline<A>` | Stack-safe recursion overhead vs naive recursion |
| `FreeBenchmark` | `Free<F,A>` | Free monad interpretation cost |

### Effect Types

| Benchmark | Type | What It Tells You |
|-----------|------|-------------------|
| `IOBenchmark` | `IO<A>` | Lazy construction and platform thread execution |
| `VTaskBenchmark` | `VTask<A>` | Virtual thread execution, map/flatMap chains |
| `VStreamBenchmark` | `VStream<A>` | Pull-based stream construction, combinator pipelines, Java Stream comparison |
| `VTaskParBenchmark` | `Par` combinators | Parallel zip, all, race, traverse via StructuredTaskScope |

### Effect Path Wrappers

| Benchmark | Type | What It Tells You |
|-----------|------|-------------------|
| `VTaskPathBenchmark` | `VTaskPath<A>` | Wrapper overhead on top of VTask |
| `IOPathBenchmark` | `IOPath<A>` | Wrapper overhead on top of IO |
| `ForPathVTaskBenchmark` | ForPath with VTask | For-comprehension tuple allocation cost |

### Comparisons

| Benchmark | What It Compares |
|-----------|-----------------|
| `VTaskVsIOBenchmark` | Virtual threads vs platform threads |
| `VTaskVsPlatformThreadsBenchmark` | VTask vs ExecutorService at scale |
| `VTaskPathVsIOPathBenchmark` | Path wrapper costs across effect types |
| `AbstractionOverheadBenchmark` | HKJ abstractions vs raw Java |
| `ConcurrencyScalingBenchmark` | Thread scaling under concurrent load |
| `MemoryFootprintBenchmark` | Allocation rates for VTask, IO, CompletableFuture |

---

## Running Benchmarks

### All Benchmarks

```bash
./gradlew :hkj-benchmarks:jmh
```

### A Single Benchmark Class

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*VStreamBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*VTaskBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*EitherBenchmark.*"
```

### A Single Benchmark Method

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*VTaskBenchmark.runSucceed.*"
```

### With GC Profiling

This reveals allocation rates and GC pressure — essential for understanding memory behaviour:

```bash
./gradlew :hkj-benchmarks:jmh -Pjmh.profilers=gc
```

### Long / Stress Mode

Runs with `chainDepth=10000` and `recursionDepth=10000` for thorough stack-safety validation:

```bash
./gradlew :hkj-benchmarks:longBenchmark
```

### Formatted Report

```bash
./gradlew :hkj-benchmarks:benchmarkReport
```

---

## Reading the Output

JMH reports throughput in operations per microsecond. Higher is better.

```
Benchmark                                    Mode  Cnt   Score   Error   Units
EitherBenchmark.rightMap                   thrpt   20  15.234 ± 0.512  ops/us
EitherBenchmark.leftMap                    thrpt   20  89.123 ± 1.234  ops/us
```

**Score** is the measured throughput. **Error** is the 99.9% confidence interval. If the error is larger than ~30% of the score, the result is noisy — increase warmup or measurement iterations.

### What to Look For

| Signal | Meaning |
|--------|---------|
| Left/Nothing operations 5-10x faster than Right/Just | Instance reuse is working |
| VTask ~10-30% slower than IO for simple ops | Expected virtual thread overhead |
| Deep chain (50+ steps) completes without error | Stack safety is intact |
| VStream slower than Java Stream | Expected; virtual thread + pull overhead |
| Wrapper overhead < 15% | Acceptable Path wrapper cost |

### Warning Signs

| Signal | Possible Cause |
|--------|---------------|
| Left/Nothing same speed as Right/Just | Instance reuse broken |
| Error margin > 50% of score | Noisy environment, insufficient warmup |
| Deep chain throws StackOverflowError | Stack safety regression |
| VStream > 100x slower than Java Stream | Excessive allocation in pull loop |
| Wrapper overhead > 30% | Unnecessary allocation in Path wrapper |

---

## Expected Performance by Type

### Either and Maybe

These types use **instance reuse**: `Left` and `Nothing` operations return the same object without allocating, making short-circuit paths essentially free.

| Comparison | Expected Ratio |
|------------|---------------|
| `leftMap` vs `rightMap` | Left 5-10x faster |
| `nothingMap` vs `justMap` | Nothing 5-10x faster |
| `leftLongChain` vs `rightLongChain` | Left 10-50x faster |

### VTask

Virtual thread overhead is the dominant cost for simple operations. For real workloads involving I/O, this overhead is negligible.

| Comparison | Expected |
|------------|----------|
| Construction (succeed, delay) | Very fast (~100+ ops/us) |
| VTask vs IO (simple execution) | VTask ~10-30% slower |
| Deep chains (50+) | Completes without error |
| High concurrency (1000+ tasks) | VTask scales better than platform threads |

### VStream

VStream's pull-based model adds overhead per element compared to Java Stream's push model, but provides laziness, virtual thread execution, and error recovery that Java Stream cannot.

| Comparison | Expected |
|------------|----------|
| Construction (empty, of, range) | Very fast (~100+ ops/us) |
| VStream map vs Java Stream map | VStream slower |
| Deep map chain (50) | Completes without error |
| Deep flatMap chain (50) | Completes without error |
| `existsEarlyMatch` vs `existsNoMatch` | Early match much faster (short-circuit) |

### Effect Path Wrappers

| Comparison | Expected Overhead |
|------------|-------------------|
| VTaskPath vs raw VTask | 5-15% |
| IOPath vs raw IO | 5-15% |
| ForPath vs direct chaining | 10-25% |

---

## When Overhead Matters (and When It Doesn't)

The benchmarks consistently show that abstraction overhead is measured in **nanoseconds**. Real-world operations — database queries, HTTP calls, file reads — are measured in **milliseconds**. The overhead is three to four orders of magnitude smaller than any I/O operation.

Abstraction overhead matters in exactly two scenarios:

1. **Tight computational loops** processing millions of items per second with no I/O — use primitives directly
2. **Very long chains** (hundreds of steps) creating GC pressure — break into named submethods

For everything else, the type safety, composability, and testability benefits far outweigh the cost.

~~~admonish tip title="See Also"
- [Production Readiness](effect/production_readiness.md) — stack traces, allocation analysis, and stack safety for Effect Path types
- [hkj-benchmarks README](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/README.md) — full method reference for all 18 benchmark classes
- [Performance Testing Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/PERFORMANCE-TESTING-GUIDE.md) — benchmark categories and CI integration
~~~

---

**Previous:** [Release History](release-history.md)
