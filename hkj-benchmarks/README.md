# Higher-Kinded-J Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for measuring the performance characteristics of higher-kinded-j types and operations.

## Purpose

Unlike unit tests which verify correctness, these benchmarks:

- Measure **throughput** and **latency** of operations
- Detect **performance regressions** across commits
- Analyse **memory allocation** patterns
- Compare performance between different approaches
- Provide **statistical analysis** with confidence intervals

---

## Benchmark Architecture

### Benchmark Coverage by HKJ Type

| Benchmark File | HKJ Type(s) Tested | Focus Area |
|----------------|-------------------|------------|
| `EitherBenchmark` | `Either<L,R>` | Instance reuse, short-circuit efficiency |
| `MaybeBenchmark` | `Maybe<A>` | Instance reuse, nullable interop |
| `IOBenchmark` | `IO<A>` | Lazy construction, platform thread execution |
| `VTaskBenchmark` | `VTask<A>` | Virtual thread execution |
| `VTaskParBenchmark` | `Par` combinators | Parallel execution with StructuredTaskScope |
| `VTaskPathBenchmark` | `VTaskPath<A>` | Effect Path wrapper overhead |
| `IOPathBenchmark` | `IOPath<A>` | Effect Path wrapper for IO |
| `VTaskPathVsIOPathBenchmark` | VTaskPath vs IOPath | Wrapper comparison |
| `VTaskVsIOBenchmark` | VTask vs IO | Virtual vs platform threads |
| `VTaskVsPlatformThreadsBenchmark` | VTask vs ExecutorService | Virtual threads vs thread pools |
| `ForPathVTaskBenchmark` | ForPath with VTask | For-comprehension overhead |
| `TrampolineBenchmark` | `Trampoline<A>` | Stack-safe recursion |
| `FreeBenchmark` | `Free<F,A>` | Free monad overhead |
| `ConcurrencyScalingBenchmark` | VTask, IO | Thread scaling behaviour |
| `MemoryFootprintBenchmark` | VTask, IO, CompletableFuture | Memory allocation patterns |
| `AbstractionOverheadBenchmark` | VTask, IO vs raw Java | Abstraction cost measurement |

### Benchmark Categories

Benchmarks are organised into categories following the [Performance Testing Guide](../docs/PERFORMANCE-TESTING-GUIDE.md):

#### Category 1: Construction (Lazy)
Methods matching `construct*`, `*Construction`, `*constructionOnly`
- Measures cost of building effect graphs without execution
- Important for understanding lazy evaluation overhead

#### Category 2: Execution
Methods matching `run*`, `*Execution`, `execute*`, `unsafeRun*`
- Measures cost of actually running effects
- Includes virtual thread spawn overhead for VTask

#### Category 3: Composition
Methods matching `*Chain*`, `*Map*`, `*FlatMap*`, `*Via*`
- Measures map/flatMap/via chaining overhead
- Tests linear scaling of composition

#### Category 4: Parallel
Methods in `VTaskParBenchmark`, methods matching `*par*`, `*zip*`, `*race*`
- Measures StructuredTaskScope overhead
- Tests parallel combinator efficiency

#### Category 5: Concurrency Scaling
Methods with `@Threads` annotations in `ConcurrencyScalingBenchmark`
- Measures behaviour under concurrent load
- Validates virtual thread scalability

#### Category 6: Error Handling
Methods matching `handleError*`, `*Recovery*`, `*Error*`
- Measures error path performance
- Tests short-circuit efficiency on failure paths

---

## Expected Performance Characteristics

Use these expectations to identify potential problems in benchmark results:

### Instance Reuse (Either/Maybe)

| Comparison | Expected Ratio | Explanation |
|------------|---------------|-------------|
| `leftMap` vs `rightMap` | 5-10x faster | Left reuses instance, no allocation |
| `nothingMap` vs `justMap` | 5-10x faster | Nothing reuses instance |
| `leftLongChain` vs `rightLongChain` | 10-50x faster | Sustained reuse benefit |
| `nothingLongChain` vs `justLongChain` | 10-50x faster | Sustained reuse benefit |

**Warning signs:**
- If Left/Nothing operations allocate memory, instance reuse is broken
- If ratios are < 3x, short-circuit optimisation may not be working

### VTask vs IO

| Comparison | Expected Result | Explanation |
|------------|-----------------|-------------|
| Construction | Similar (~100 ops/us) | Both are lazy wrappers |
| Simple execution | VTask ~10-30% slower | Virtual thread spawn overhead |
| Deep chains (50+) | Similar performance | Composition overhead dominates |
| High concurrency (1000+ tasks) | VTask significantly better | Virtual threads scale better |

**Warning signs:**
- VTask > 2x slower than IO for simple operations suggests VT overhead issue
- VTask slower than IO at high concurrency indicates scheduling problem

### Effect Path Wrappers (VTaskPath/IOPath)

| Comparison | Expected Overhead | Explanation |
|------------|-------------------|-------------|
| VTaskPath vs raw VTask | 5-15% | Wrapper allocation + delegation |
| IOPath vs raw IO | 5-15% | Wrapper allocation + delegation |
| ForPath vs direct chaining | 10-25% | Tuple allocation overhead |
| `let()` vs `from()` | `let()` ~20% faster | No VTaskPath wrapping |

**Warning signs:**
- Wrapper overhead > 30% suggests unnecessary allocation
- ForPath overhead > 50% indicates tuple handling inefficiency

### Stack Safety (Trampoline/Free)

| Metric | Expected | Explanation |
|--------|----------|-------------|
| Deep recursion (10,000+) | Completes without error | Stack-safe trampolining |
| Performance scaling | Linear with depth | No exponential blowup |
| `factorialTrampoline` vs `factorialNaive` | Similar at depth 100 | Trampoline overhead minimal |

**Warning signs:**
- StackOverflowError at any depth indicates broken trampolining
- Non-linear scaling suggests memory leak or quadratic complexity

### Parallel Combinators (Par)

| Comparison | Expected | Explanation |
|------------|----------|-------------|
| `zipTwoTasks` vs `sequentialZipEquivalent` | Similar for instant tasks | Parallelism overhead with no I/O |
| `allTasks` (50 tasks) | Near-linear scaling | Virtual thread efficiency |
| `highConcurrencyAll` | Completes efficiently | StructuredTaskScope handles load |

**Warning signs:**
- Parallel slower than sequential for > 10 tasks indicates scope overhead
- Memory growth with task count suggests resource leak

### Abstraction Overhead

| Comparison | Expected Overhead | Acceptable Range |
|------------|-------------------|------------------|
| VTask chain vs raw Java | 50-200x slower | Expected for effect wrapping |
| IO chain vs raw Java | 50-200x slower | Expected for effect wrapping |
| VTaskPath vs VTask | 5-15% | Acceptable wrapper cost |

**Note:** Abstraction overhead is acceptable because:
1. Real workloads involve I/O that dominates compute time
2. Type safety and composability benefits outweigh cost
3. Overhead is constant, not proportional to data size

---

## Key Metrics to Monitor

### Critical for Regression Detection

These benchmarks should be monitored in CI for regressions:

1. **`VTaskBenchmark.runSucceed`** - Core VTask execution baseline
2. **`VTaskPathBenchmark.runPure`** - Effect Path overhead baseline
3. **`VTaskParBenchmark.zipTwoTasks`** - Parallel combinator baseline
4. **`TrampolineBenchmark.factorialTrampoline`** - Stack safety validation
5. **`EitherBenchmark.leftMap`** - Instance reuse validation
6. **`MaybeBenchmark.nothingMap`** - Instance reuse validation

### Important for Design Decisions

These benchmarks inform architectural choices:

1. **`VTaskVsIOBenchmark.*`** - When to use virtual threads vs platform threads
2. **`VTaskVsPlatformThreadsBenchmark.*`** - Comparison with traditional ExecutorService
3. **`ForPathVTaskBenchmark.forPath_* vs direct_*`** - For-comprehension syntax overhead
4. **`AbstractionOverheadBenchmark.*`** - Cost of HKJ abstractions vs raw Java
5. **`ConcurrencyScalingBenchmark.*`** - Thread scaling characteristics

### Memory-Sensitive Operations

Run with GC profiler (`-Pjmh.profilers=gc`) for these:

1. **`*LongChain*`** - Should show allocation only for Right/Just paths
2. **`MemoryFootprintBenchmark.*`** - Direct memory comparison
3. **`VTaskParBenchmark.allTasks`** - Allocation per task

---

## Running Benchmarks

### Run All Benchmarks

```bash
./gradlew :hkj-benchmarks:jmh
```

### Run Specific Benchmark Class

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*EitherBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*MaybeBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*IOBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*VTaskBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*VTaskVsPlatformThreadsBenchmark.*"
```

### Run Specific Benchmark Method

```bash
./gradlew jmh --includes=".*EitherBenchmark.rightChainedOperations.*"
```

### Run with GC Profiling

```bash
./gradlew jmh -Pjmh.profilers=gc
```

This shows allocation rates and GC pressure.

### Run with Custom Parameters

```bash
./gradlew jmh -Pjmh.warmup=3 -Pjmh.iterations=5 -Pjmh.fork=1
```

### Run Long/Stress Benchmarks

```bash
./gradlew :hkj-benchmarks:longBenchmark
```

This runs with `chainDepth=10000` and `recursionDepth=10000` for thorough stack-safety validation.

---

## Understanding Results

### Throughput Mode (ops/us)

Higher is better. Shows operations per microsecond:

```
Benchmark                                    Mode  Cnt   Score   Error   Units
EitherBenchmark.rightMap                    thrpt   20  15.234 +/- 0.512  ops/us
```

This means the `map` operation on Right can execute ~15 times per microsecond.

### Statistical Confidence

JMH provides error margins:

```
Score: 15.234 +/- 0.512
```

This means the true performance is likely between 14.722 and 15.746 ops/us with 99.9% confidence.

### GC Profiling Output

```
Benchmark                                   Mode  Cnt    Score     Error   Units
*gc.alloc.rate                             thrpt   20   45.123 +-  2.341  MB/sec
*gc.alloc.rate.norm                        thrpt   20  512.000 +-  0.001    B/op
*gc.count                                  thrpt   20    5.000            counts
```

- `alloc.rate`: Memory allocation rate
- `alloc.rate.norm`: Bytes allocated per operation
- `gc.count`: Number of GC cycles during benchmark

---

## Interpreting Comparison Results

### Example: VTask vs IO

```
Benchmark                                    Mode  Cnt   Score   Error   Units
VTaskVsIOBenchmark.io_simpleExecution       thrpt   20  25.123 +/- 1.234  ops/us
VTaskVsIOBenchmark.vtask_simpleExecution    thrpt   20  18.456 +/- 0.987  ops/us
```

**Analysis:**
- IO: ~25 ops/us
- VTask: ~18 ops/us
- Ratio: VTask is ~26% slower
- **Verdict:** Within expected range (10-30% slower due to virtual thread overhead)

### Example: Instance Reuse

```
Benchmark                                    Mode  Cnt   Score   Error   Units
EitherBenchmark.leftMap                     thrpt   20  89.123 +/- 1.234  ops/us
EitherBenchmark.rightMap                    thrpt   20  15.234 +/- 0.512  ops/us
```

**Analysis:**
- Left: ~89 ops/us
- Right: ~15 ops/us
- Ratio: Left is ~5.8x faster
- **Verdict:** Instance reuse working correctly (expected 5-10x)

### Example: ForPath Overhead

```
Benchmark                                           Mode  Cnt   Score   Error   Units
ForPathVTaskBenchmark.direct_threeStepChain        thrpt   20  12.345 +/- 0.456  ops/us
ForPathVTaskBenchmark.forPath_threeStepEquivalent  thrpt   20  10.234 +/- 0.321  ops/us
```

**Analysis:**
- Direct: ~12.3 ops/us
- ForPath: ~10.2 ops/us
- Overhead: ~17%
- **Verdict:** Acceptable overhead for improved readability (expected 10-25%)

---

## Warning Signs

### High Variance

Large error margins suggest instability:

```
Score: 15.234 +/- 8.512  # Bad - error is > 50% of score
```

**Causes:**
- JIT compilation instability
- GC interference
- Background processes
- Insufficient warmup

### Unexpected Allocation

If `leftMap` or `nothingMap` shows allocation:

```
EitherBenchmark.leftMap
*gc.alloc.rate.norm    thrpt   20  48.000 +/- 0.001  B/op  # Bad - should be 0
```

**Cause:** Instance reuse optimisation broken

### Non-Linear Scaling

If doubling iterations more than doubles time:

```
chainDepth=50:  Score: 10.0 ops/us
chainDepth=100: Score:  2.0 ops/us  # Bad - should be ~5.0
```

**Causes:**
- Memory leak
- Quadratic complexity
- GC pressure

---

## Best Practises

### 1. Warmup Matters

JMH runs warmup iterations to allow JIT compilation. Don't skip them.

### 2. Isolate Benchmarks

Run on quiet system:
- Close other applications
- Disable background processes
- Use consistent JVM settings

### 3. Compare Fairly

When comparing implementations:
- Use same JVM version
- Same warmup/measurement iterations
- Same fork count

### 4. Don't Micro-Optimise Prematurely

Focus on:
- Algorithmic improvements
- Reducing allocations
- Instance reuse
- Short-circuiting

Not:
- Nanosecond differences
- Statistically insignificant changes

---

## Advanced Usage

### Custom JMH Configuration

Edit `build.gradle.kts` in this module to change defaults:

```kotlin
jmh {
  iterations = 15           // More iterations = higher confidence
  warmupIterations = 10     // More warmup = more stable results
  fork = 3                  // More forks = better variance analysis
}
```

### Adding New Benchmarks

Create new benchmark class in `src/jmh/java/org/higherkindedj/benchmarks/`:

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G", "--enable-preview"})
public class MyBenchmark {

  @Benchmark
  public MyType myOperation() {
    // Code to benchmark
  }
}
```

### Profiling Options

Available profilers:
- `gc` - Garbage collection
- `stack` - Stack traces (hotspots)
- `perf` - Linux perf (requires perf tool)
- `perfasm` - Assembly output

```bash
./gradlew jmh -Pjmh.profilers=stack
```

---

## Benchmark Method Quick Reference

### EitherBenchmark (16 methods)

| Method | Category | Measures |
|--------|----------|----------|
| `rightMap` / `leftMap` | Composition | Basic map, instance reuse |
| `rightChainedOperations` / `leftChainedOperations` | Composition | Multi-operation chains |
| `rightLongChain` / `leftLongChain` | Composition | 50-deep chains |
| `rightFold` / `leftFold` | Execution | Pattern matching |
| `rightRecovery` / `leftRecovery` | Error Handling | Error recovery |
| `realWorldPattern` | Composition | Mixed operations |
| `constructRight` / `constructLeft` | Construction | Instance creation |
| `nestedFlatMap` | Composition | 4-level nesting |

### MaybeBenchmark (17 methods)

| Method | Category | Measures |
|--------|----------|----------|
| `justMap` / `nothingMap` | Composition | Basic map, instance reuse |
| `justFlatMap` / `nothingFlatMap` | Composition | Monadic bind |
| `justLongChain` / `nothingLongChain` | Composition | 50-deep chains |
| `justOrElse` / `nothingOrElse` | Execution | Default values |
| `justFilter*` / `nothingFilter` | Composition | Filtering |
| `constructJust` / `constructNothing` | Construction | Instance creation |
| `*Nullable` | Execution | Nullable interop |

### VTaskBenchmark (13 methods)

| Method | Category | Measures |
|--------|----------|----------|
| `constructSucceed` / `constructDelay` | Construction | Lazy construction |
| `runSucceed` / `runDelay` | Execution | Virtual thread execution |
| `mapConstruction` / `mapExecution` | Composition | Map overhead |
| `flatMapConstruction` / `flatMapExecution` | Composition | FlatMap overhead |
| `longChainConstruction` / `longChainExecution` | Composition | 50-deep chains |
| `deepRecursion` | Composition | Stack safety |
| `errorRecovery` | Error Handling | Recovery overhead |

### VTaskParBenchmark (13 methods)

| Method | Category | Measures |
|--------|----------|----------|
| `zipTwoTasks` / `zip3Tasks` | Parallel | Parallel zip |
| `sequentialZipEquivalent` | Parallel | Sequential baseline |
| `map2Tasks` / `map3Tasks` | Parallel | Parallel map combining |
| `allTasks` / `sequentialAllEquivalent` | Parallel | Collect all results |
| `traverseList` / `sequentialTraverseEquivalent` | Parallel | Parallel traverse |
| `raceWithImmediateWinner` | Parallel | Race combinator |
| `highConcurrencyAll` / `highConcurrencyTraverse` | Parallel | High task counts |

### Comparison Benchmarks

| File | Methods | Measures |
|------|---------|----------|
| `VTaskVsIOBenchmark` | 14 | Virtual vs platform thread overhead |
| `VTaskPathVsIOPathBenchmark` | 30 | Effect Path wrapper comparison |
| `VTaskVsPlatformThreadsBenchmark` | 8 | VTask vs ExecutorService at scale |
| `AbstractionOverheadBenchmark` | 9 | HKJ vs raw Java |
| `ConcurrencyScalingBenchmark` | 6 | Thread scaling (@Threads) |

---

## Examples

### Benchmark Output

```
Benchmark                                    Mode  Cnt   Score   Error   Units
EitherBenchmark.rightMap                    thrpt   20  15.234 +/- 0.512  ops/us
EitherBenchmark.leftMap                     thrpt   20  89.123 +/- 1.234  ops/us
```

**Interpretation:** `leftMap` is ~6x faster due to instance reuse (no allocation).

### GC Profiling

```
Benchmark                                   Mode  Cnt    Score     Error   Units
EitherBenchmark.rightMap                   thrpt   20   15.234 +-  0.512  ops/us
*gc.alloc.rate                             thrpt   20  156.234 +- 12.345  MB/sec
*gc.alloc.rate.norm                        thrpt   20  512.000 +-  0.001    B/op
```

**Interpretation:** Each `rightMap` allocates 512 bytes (new Either instance).

---

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)
- [Avoiding Benchmarking Pitfalls](https://shipilev.net/blog/2014/nanotrusting-nanotime/)
- [Performance Testing Guide](../docs/PERFORMANCE-TESTING-GUIDE.md)
