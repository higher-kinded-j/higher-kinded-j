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

The `hkj-benchmarks` module contains 19 benchmark classes covering every major type in the library:

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
| `VStreamBenchmark` | `VStream<A>` | Pull-based stream construction, combinator pipelines, parallel ops, chunking, Java Stream comparison |
| `VTaskParBenchmark` | `Par` combinators | Parallel zip, all, race, traverse via StructuredTaskScope |
| `ScopeBenchmark` | `Scope`, `Resource` | Scope joiner strategies (allSucceed, anySucceed, accumulating), Resource bracket overhead |

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
| parEvalMap scales with concurrency for I/O | Parallel pipeline working correctly |
| Scope joiners similar speed to Par.all | Minimal Scope abstraction cost |
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

## Benchmark Assertion Tests

The benchmark suite includes automated assertion tests that validate performance characteristics after each benchmark run. These are not just "did it finish?" checks — they verify relative performance, overhead ratios, and sanity bounds.

~~~admonish warning title="Tests Fail If Benchmarks Haven't Run"
The assertion tests **fail** (not skip) if benchmark results are missing. This is intentional. Run `./gradlew :hkj-benchmarks:jmh` before running `./gradlew :hkj-benchmarks:test`. Silent skips hide missing quality gates.
~~~

### What the Tests Validate

| Test Group | What It Checks |
|------------|---------------|
| **SanityChecks** | Every benchmark has positive throughput and bounded error margins |
| **VTaskRelativePerformance** | VTask construction costs (succeed, of, map) are positive |
| **ParCombinatorPerformance** | Par.zip and Par.map2 have positive throughput |
| **VTaskVsIOOverhead** | Both VTask and IO construction perform within expected bounds |
| **CoreTypePerformance** | Maybe, Either, and Trampoline operations have positive throughput |
| **FoldPlusPerformance** | Fold combination overhead is bounded; `sum` vs `plus` parity |
| **AbstractionOverhead** | Raw Java > IO > VTask ordering; VTaskPath wrapper overhead bounded |
| **ConcurrencyScaling** | Single and multi-threaded VTask/IO performance is positive |
| **IOPerformance** | IO construction vs execution ratios; deep recursion completes (stack safety) |
| **IOPathPerformance** | IOPath construction, map pipelines, and error handling overhead |
| **VTaskPathPerformance** | VTaskPath construction, map pipelines, and timeout overhead |
| **VTaskPathVsIOPath** | Cross-type comparison: construction ratios and conversion costs |
| **ForPathVTaskPerformance** | For-comprehension overhead vs direct chaining; parallel step overhead |
| **ScopePerformance** | Scope.allSucceed, Resource bracket, and Par.all throughput |
| **MemoryFootprint** | Bulk construction rates for VTask, IO, and CompletableFuture |
| **VStreamPerformance** | VStream map execution, construction vs execution, Java Stream baseline |
| **VTaskVsPlatformThreads** | VTask Par.all vs platform thread pool at scale |
| **FreeMonadPerformance** | Free monad construction, stack safety, and interpretation overhead |

### Running the Tests

```bash
# Step 1: Run benchmarks (generates results.json)
./gradlew :hkj-benchmarks:jmh

# Step 2: Run assertion tests against the results
./gradlew :hkj-benchmarks:test

# Or run both together via the benchmarkValidation task
./gradlew benchmarkValidation
```

---

## Release Quality Gate

The `releaseReadiness` task is a single-command quality gate that runs every verification step, ordered from fastest to slowest so failures surface early:

```bash
./gradlew releaseReadiness
```

| Step | Task | What It Checks | Speed |
|------|------|---------------|-------|
| 1 | `spotlessCheck` | Code formatting (Google Java Format) | Seconds |
| 2 | `build` | Compilation, all unit tests, JaCoCo coverage | Minutes |
| 3 | `:hkj-benchmarks:jmh` | JMH benchmarks execute successfully | Minutes |
| 4 | `:hkj-benchmarks:test` | Benchmark assertion tests pass | Seconds |
| 5 | `:hkj-processor:pitest` (full) | Mutation testing with STRONGER mutators | Slowest |

If any step fails, the build stops immediately. All five must pass before a release.

~~~admonish info title="Pitest Full Profile"
The release gate runs pitest with `-Ppitest.profile=full`, which uses STRONGER mutators and all available CPU cores. This is more thorough than the default conservative profile used during local development.
~~~

### Reports Generated

After a successful run, reports are available at:

| Tool | Location |
|------|----------|
| JaCoCo | `hkj-core/build/reports/jacoco/test/html/index.html` |
| JMH (JSON) | `hkj-benchmarks/build/reports/jmh/results.json` |
| JMH (human) | `hkj-benchmarks/build/reports/jmh/human.txt` |
| Pitest | `hkj-processor/build/reports/pitest/index.html` |

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
