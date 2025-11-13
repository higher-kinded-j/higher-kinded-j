# Higher-Kinded-J Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for measuring the performance characteristics of higher-kinded-j types and operations.

## Purpose

Unlike unit tests which verify correctness, these benchmarks:

- Measure **throughput** and **latency** of operations
- Detect **performance regressions** across commits
- Analyse **memory allocation** patterns
- Compare performance between different approaches
- Provide **statistical analysis** with confidence intervals

## Running Benchmarks

### Run All Benchmarks

```bash
./gradlew :hkj-benchmarks:jmh
```

### Run Specific Benchmark Class

```bash
./gradlew jmh --includes=".*EitherBenchmark.*"
./gradlew jmh --includes=".*MaybeBenchmark.*"
./gradlew jmh --includes=".*IOBenchmark.*"
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

## Understanding Results

### Throughput Mode (ops/µs)

Higher is better. Shows operations per microsecond:

```
Benchmark                                    Mode  Cnt   Score   Error   Units
EitherBenchmark.rightMap                    thrpt   20  15.234 ± 0.512  ops/µs
```

This means the `map` operation on Right can execute ~15 times per microsecond.

### Statistical Confidence

JMH provides error margins (±):

```
Score: 15.234 ± 0.512
```

This means the true performance is likely between 14.722 and 15.746 ops/µs with 99.9% confidence.

### GC Profiling Output

```
Benchmark                                   Mode  Cnt    Score     Error   Units
·gc.alloc.rate                             thrpt   20   45.123 ±  2.341  MB/sec
·gc.alloc.rate.norm                        thrpt   20  512.000 ±  0.001    B/op
·gc.count                                  thrpt   20    5.000            counts
```

- `alloc.rate`: Memory allocation rate
- `alloc.rate.norm`: Bytes allocated per operation
- `gc.count`: Number of GC cycles during benchmark

## Benchmark Structure

### Either Benchmarks

- `rightMap` / `leftMap` - Basic map operations
- `rightChainedOperations` / `leftChainedOperations` - Composition performance
- `rightLongChain` / `leftLongChain` - Deep operation chains (tests instance reuse)
- `realWorldPattern` - Realistic usage patterns

### Maybe Benchmarks

- Similar structure to Either
- Additional benchmarks for Optional interop
- Filter operation benchmarks

### IO Benchmarks

- **Construction** benchmarks - Building IO chains (lazy, no execution)
- **Execution** benchmarks - Running IO chains with `.run()`
- Stack safety tests
- Mixed pure/delay operations

## When to Run Benchmarks

### During Development

Run specific benchmarks when optimising particular operations:

```bash
./gradlew jmh --includes=".*leftLongChain.*"
```

### Before Commits

Run full benchmark suite to detect regressions:

```bash
./gradlew jmh
```

Compare with previous results to ensure no performance degradation.

### CI Integration

Store baseline results and compare in CI:

```bash
# Run and save baseline
./gradlew jmh
cp build/reports/jmh/results.json baseline.json

# After changes, compare
./gradlew jmh
# Use external tools to compare results.json with baseline.json
```

## Interpreting Benchmark Results

### Good Performance Indicators

1. **Instance Reuse**: `leftMap` should be much faster than `rightMap`
   - Left/Nothing instances are reused, avoiding allocations

2. **Short-Circuit Efficiency**: Left/Nothing chains should be very fast
   - No actual computation occurs

3. **Low Allocation**: Check `gc.alloc.rate.norm`
   - Lower is better
   - Right chains should have predictable allocation

4. **Stack Safety**: Deep recursion benchmarks should complete
   - No StackOverflowError
   - Performance should scale linearly, not exponentially

### Warning Signs

1. **High Variance**: Large error margins suggest:
   - JIT compilation instability
   - GC interference
   - Background processes affecting results

2. **Unexpected Allocation**: If `leftMap` allocates memory:
   - Instance reuse optimisation isn't working

3. **Non-Linear Scaling**: If doubling iterations more than doubles time:
   - Potential memory leak
   - Quadratic complexity

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
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
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



### Writing New Benchmarks Summary

1. Add to `hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/`
2. Annotate with `@Benchmark`
3. Use appropriate benchmark modes
4. Include warmup iterations
5. Fork JVM processes for isolation
6. Document what you're measuring

### Interpreting Results

**Good signs:**
- Low variance (small ± error)
- Left/Nothing faster than Right/Just (instance reuse)
- Linear scaling with iterations
- Low allocation rates

**Warning signs:**
- High variance (JVM instability)
- Unexpected allocations
- Non-linear scaling
- Slower than alternatives

## Examples

### Benchmark Output

```
Benchmark                                    Mode  Cnt   Score   Error   Units
EitherBenchmark.rightMap                    thrpt   20  15.234 ± 0.512  ops/µs
EitherBenchmark.leftMap                     thrpt   20  89.123 ± 1.234  ops/µs
```

**Interpretation:** `leftMap` is ~6x faster due to instance reuse (no allocation).

### GC Profiling

```
Benchmark                                   Mode  Cnt    Score     Error   Units
EitherBenchmark.rightMap                   thrpt   20   15.234 ±  0.512  ops/µs
·gc.alloc.rate                             thrpt   20  156.234 ± 12.345  MB/sec
·gc.alloc.rate.norm                        thrpt   20  512.000 ±  0.001    B/op
```

**Interpretation:** Each `rightMap` allocates 512 bytes (new Either instance).


- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)
- [Avoiding Benchmarking Pitfalls](https://shipilev.net/blog/2014/nanotrusting-nanotime/)
