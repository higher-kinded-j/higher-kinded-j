# Performance Testing Guide

This document establishes best practices for performance testing in higher-kinded-j, with particular focus on demonstrating the advantages of virtual threads and structured concurrency over traditional approaches.

## Table of Contents

1. [Philosophy](#philosophy)
2. [JMH Configuration](#jmh-configuration)
3. [Benchmark Categories](#benchmark-categories)
4. [Virtual Thread Benchmarks](#virtual-thread-benchmarks)
5. [Comparison Benchmarks](#comparison-benchmarks)
6. [Memory Profiling](#memory-profiling)
7. [Reporting Standards](#reporting-standards)
8. [CI Integration](#ci-integration)
9. [Interpreting Results](#interpreting-results)

---

## Philosophy

### Goals of Performance Testing

1. **Demonstrate Value**: Show concrete advantages of HKJ abstractions over alternatives
2. **Prevent Regressions**: Ensure new features don't degrade existing performance
3. **Guide Optimisation**: Identify hotspots and opportunities for improvement
4. **Validate Design**: Confirm that abstraction overhead is acceptable

### Non-Goals

1. **Micro-optimisation**: We don't chase nanoseconds at the expense of clarity
2. **Artificial Benchmarks**: Benchmarks should reflect realistic usage patterns
3. **Marketing Numbers**: Results should be honest and reproducible

### Key Principles

- **Reproducibility**: Any developer should be able to reproduce results
- **Fairness**: Compare like with like; don't handicap alternatives
- **Context**: Raw numbers without context are meaningless
- **Actionability**: Benchmarks should inform decisions

---

## JMH Configuration

### Standard Benchmark Annotations

All benchmarks should use consistent JMH configuration:

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G", "--enable-preview"})
public class ExampleBenchmark {
    // ...
}
```

### Benchmark Mode Selection

| Mode | Use Case |
|------|----------|
| `Throughput` | Default for most benchmarks; operations per time unit |
| `AverageTime` | When latency is the primary concern |
| `SampleTime` | When latency distribution matters (percentiles) |
| `SingleShotTime` | Cold-start performance (rare) |

### JVM Arguments for Virtual Threads

```java
@Fork(value = 2, jvmArgs = {
    "-Xms2G",
    "-Xmx2G",
    "--enable-preview",
    // Virtual thread debugging (optional)
    "-Djdk.tracePinnedThreads=short"
})
```

### Gradle Configuration

```kotlin
// In hkj-benchmarks/build.gradle.kts
jmh {
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(2)
    jvmArgs.addAll(listOf(
        "-Xms2G",
        "-Xmx2G",
        "--enable-preview"
    ))
    // Results in JSON for tracking
    resultFormat.set("JSON")
    resultsFile.set(file("$buildDir/reports/jmh/results.json"))
}
```

---

## Benchmark Categories

### Category 1: Construction Benchmarks

Measure the cost of creating effect instances:

```java
@Benchmark
public VTask<Integer> constructVTask() {
    return VTask.of(() -> 42);
}

@Benchmark
public IO<Integer> constructIO() {
    return IO.delay(() -> 42);
}

@Benchmark
public CompletableFuture<Integer> constructCompletableFuture() {
    return CompletableFuture.supplyAsync(() -> 42);
}
```

### Category 2: Execution Benchmarks

Measure the cost of running effects:

```java
@Benchmark
public Integer executeVTask() {
    return VTask.of(() -> 42).run();
}

@Benchmark
public Integer executeIO() {
    return IO.delay(() -> 42).unsafeRunSync();
}
```

### Category 3: Composition Benchmarks

Measure the cost of effect composition:

```java
@Benchmark
public Integer mapChain() {
    VTask<Integer> result = baseTask;
    for (int i = 0; i < 10; i++) {
        result = result.map(x -> x + 1);
    }
    return result.run();
}

@Benchmark
public Integer flatMapChain() {
    VTask<Integer> result = baseTask;
    for (int i = 0; i < 10; i++) {
        result = result.flatMap(x -> VTask.succeed(x + 1));
    }
    return result.run();
}
```

### Category 4: Parallel Benchmarks

Measure parallel execution overhead:

```java
@Benchmark
public List<Integer> parAll() {
    return Par.all(tasks).run();
}

@Benchmark
public Integer parRace() {
    return Par.race(tasks).run();
}

@Benchmark
@OperationsPerInvocation(100)
public List<Integer> parTraverse() {
    return Par.traverse(items, this::processItem).run();
}
```

### Category 5: Concurrency Scaling Benchmarks

Measure behaviour under concurrent load:

```java
@Benchmark
@Threads(1)
public void singleThread() { /* ... */ }

@Benchmark
@Threads(4)
public void fourThreads() { /* ... */ }

@Benchmark
@Threads(Threads.MAX)
public void maxThreads() { /* ... */ }
```

---

## Virtual Thread Benchmarks

### Demonstrating Virtual Thread Advantages

The key advantages to demonstrate:

1. **Scalability**: Handle thousands of concurrent tasks
2. **Memory Efficiency**: Lower per-task memory overhead
3. **Blocking Without Penalty**: Blocking I/O doesn't waste resources
4. **Simple Programming Model**: Direct style as fast as async

### VTask vs Platform Thread Pool

```java
@State(Scope.Benchmark)
public class VTaskVsPlatformThreadsBenchmark {

    private ExecutorService platformExecutor;
    private List<Callable<Integer>> tasks;

    @Param({"10", "100", "1000", "10000"})
    private int taskCount;

    @Setup
    public void setup() {
        platformExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
        tasks = IntStream.range(0, taskCount)
            .mapToObj(i -> (Callable<Integer>) () -> simulateWork(i))
            .toList();
    }

    @TearDown
    public void teardown() {
        platformExecutor.shutdown();
    }

    @Benchmark
    public List<Integer> vtaskParAll() {
        return Par.all(
            tasks.stream()
                .map(c -> VTask.of(c))
                .toList()
        ).run();
    }

    @Benchmark
    public List<Integer> platformThreadPool() throws Exception {
        return platformExecutor.invokeAll(tasks).stream()
            .map(f -> {
                try { return f.get(); }
                catch (Exception e) { throw new RuntimeException(e); }
            })
            .toList();
    }

    @Benchmark
    public List<Integer> completableFutureAll() {
        return tasks.stream()
            .map(c -> CompletableFuture.supplyAsync(() -> {
                try { return c.call(); }
                catch (Exception e) { throw new RuntimeException(e); }
            }, platformExecutor))
            .map(CompletableFuture::join)
            .toList();
    }

    private int simulateWork(int input) {
        // Simulate blocking I/O
        try { Thread.sleep(1); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return input * 2;
    }
}
```

### Blocking I/O Comparison

```java
public class BlockingIOBenchmark {

    @Param({"100", "1000"})
    private int concurrentCalls;

    @Benchmark
    public List<String> vtaskBlockingIO() {
        return Par.all(
            IntStream.range(0, concurrentCalls)
                .mapToObj(i -> VTask.of(() -> simulateBlockingIO()))
                .toList()
        ).run();
    }

    @Benchmark
    public List<String> reactorBlockingIO() {
        // Reactor must use subscribeOn(Schedulers.boundedElastic())
        return Flux.range(0, concurrentCalls)
            .flatMap(i -> Mono.fromCallable(this::simulateBlockingIO)
                .subscribeOn(Schedulers.boundedElastic()))
            .collectList()
            .block();
    }

    private String simulateBlockingIO() {
        // Simulate database query or HTTP call
        try { Thread.sleep(10); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "result";
    }
}
```

### Memory Footprint

```java
public class MemoryFootprintBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public long vtaskMemory() {
        long before = getUsedMemory();
        List<VTask<Integer>> tasks = IntStream.range(0, 100_000)
            .mapToObj(i -> VTask.of(() -> i))
            .toList();
        return getUsedMemory() - before;
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public long completableFutureMemory() {
        long before = getUsedMemory();
        List<CompletableFuture<Integer>> futures = IntStream.range(0, 100_000)
            .mapToObj(i -> CompletableFuture.supplyAsync(() -> i))
            .toList();
        return getUsedMemory() - before;
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
```

---

## Comparison Benchmarks

### HKJ vs Reactor/RxJava

Fair comparison guidelines:
- Use equivalent operations
- Use appropriate schedulers
- Don't handicap either library

```java
public class HkjVsReactorBenchmark {

    @Benchmark
    public Integer hkjVTaskMap() {
        return VTask.succeed(1)
            .map(x -> x + 1)
            .map(x -> x * 2)
            .map(x -> x - 1)
            .run();
    }

    @Benchmark
    public Integer reactorMonoMap() {
        return Mono.just(1)
            .map(x -> x + 1)
            .map(x -> x * 2)
            .map(x -> x - 1)
            .block();
    }

    @Benchmark
    public Integer hkjVTaskFlatMap() {
        return VTask.succeed(1)
            .flatMap(x -> VTask.succeed(x + 1))
            .flatMap(x -> VTask.succeed(x * 2))
            .run();
    }

    @Benchmark
    public Integer reactorMonoFlatMap() {
        return Mono.just(1)
            .flatMap(x -> Mono.just(x + 1))
            .flatMap(x -> Mono.just(x * 2))
            .block();
    }
}
```

### HKJ vs Cats Effect / ZIO (JVM)

For Scala library comparison (run separately):

```java
// Document comparison methodology
// Run Scala benchmarks in separate JVM process
// Use ScalaMeter or JMH Scala plugin
// Compare equivalent operations
```

### HKJ vs Raw Java

Show abstraction overhead:

```java
public class AbstractionOverheadBenchmark {

    @Benchmark
    public Integer rawJava() {
        int result = 1;
        result = result + 1;
        result = result * 2;
        return result;
    }

    @Benchmark
    public Integer vtaskChain() {
        return VTask.succeed(1)
            .map(x -> x + 1)
            .map(x -> x * 2)
            .run();
    }

    @Benchmark
    public Integer ioChain() {
        return IO.delay(() -> 1)
            .map(x -> x + 1)
            .map(x -> x * 2)
            .unsafeRunSync();
    }
}
```

---

## Memory Profiling

### Allocation Tracking

Use JMH's GC profiler:

```bash
./gradlew :hkj-benchmarks:jmh -PjmhProfilers=gc
```

### Heap Analysis

```java
@Benchmark
@BenchmarkMode(Mode.SingleShotTime)
@Measurement(iterations = 1)
@Fork(value = 1, jvmArgs = {
    "-Xms512M", "-Xmx512M",
    "-XX:+HeapDumpOnOutOfMemoryError"
})
public void memoryPressure() {
    // Allocate many objects to measure memory behaviour
}
```

### Async Profiler Integration

```bash
# With async-profiler
./gradlew :hkj-benchmarks:jmh -PjmhProfilers=async:output=flamegraph
```

---

## Reporting Standards

### Benchmark Documentation

Every benchmark file should include:

```java
/**
 * Benchmarks for VTask virtual thread execution.
 *
 * <p>These benchmarks measure:
 * <ul>
 *   <li>Construction overhead compared to IO and CompletableFuture</li>
 *   <li>Execution latency on virtual threads</li>
 *   <li>Composition (map/flatMap) overhead</li>
 *   <li>Parallel execution scaling</li>
 * </ul>
 *
 * <p>Expected results:
 * <ul>
 *   <li>VTask construction should be similar to IO (~100ns)</li>
 *   <li>VTask execution should be faster than platform thread spawn</li>
 *   <li>Scaling should be near-linear up to 10,000 concurrent tasks</li>
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-benchmarks:jmh --tests ".*VTaskBenchmark.*"}
 */
```

### Result Format

Store results in JSON for tracking:

```json
{
  "benchmark": "org.higherkindedj.benchmarks.VTaskBenchmark.constructSimple",
  "mode": "thrpt",
  "threads": 1,
  "forks": 2,
  "warmupIterations": 5,
  "measurementIterations": 10,
  "primaryMetric": {
    "score": 12500000.0,
    "scoreError": 150000.0,
    "scoreUnit": "ops/s"
  },
  "jdkVersion": "25",
  "vmVersion": "25+36",
  "date": "2026-01-12"
}
```

### Baseline Tracking

Maintain baseline results for regression detection:

```
hkj-benchmarks/
├── baselines/
│   ├── vtask-baseline-2.0.0-M1.json
│   ├── scope-baseline-2.0.0-M3.json
│   └── context-baseline-2.0.0-M4.json
```

---

## CI Integration

### Automated Benchmark Runs

```yaml
# In CI workflow
benchmark:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '25'
    - name: Run benchmarks
      run: ./gradlew :hkj-benchmarks:jmh
    - name: Compare with baseline
      run: ./scripts/compare-benchmarks.sh
    - name: Upload results
      uses: actions/upload-artifact@v4
      with:
        name: benchmark-results
        path: hkj-benchmarks/build/reports/jmh/
```

### Regression Detection

Script to compare against baselines:

```bash
#!/bin/bash
# compare-benchmarks.sh

BASELINE="hkj-benchmarks/baselines/current-baseline.json"
RESULTS="hkj-benchmarks/build/reports/jmh/results.json"
THRESHOLD=0.10  # 10% regression threshold

# Compare and fail if regression > threshold
python3 scripts/benchmark-compare.py \
  --baseline "$BASELINE" \
  --results "$RESULTS" \
  --threshold "$THRESHOLD"
```

---

## Interpreting Results

### Understanding Throughput

- **Higher is better** for throughput (ops/s)
- Consider the error margin (scoreError)
- Look for consistent results across forks

### Understanding Latency

- **Lower is better** for latency (ns, µs, ms)
- Consider percentiles, not just average
- p99 latency often more important than p50

### Common Pitfalls

1. **Dead Code Elimination**: Use `Blackhole` or return values
2. **Constant Folding**: Use `@State` fields, not literals
3. **Loop Optimisation**: Use `@OperationsPerInvocation`
4. **GC Interference**: Sufficient heap, warmup iterations
5. **CPU Throttling**: Consistent power management

### Benchmark Red Flags

- Very low error margin (too good to be true)
- Inconsistent results across forks
- Results that don't make logical sense
- Missing warmup causing cold-start effects

---

## Benchmark Checklist

When creating new benchmarks:

- [ ] Use standard JMH annotations
- [ ] Include `--enable-preview` for Java 25 features
- [ ] Document what is being measured
- [ ] Document expected results
- [ ] Include comparison with alternatives where relevant
- [ ] Use realistic workloads, not just no-ops
- [ ] Verify results make logical sense
- [ ] Add to CI pipeline
- [ ] Update baselines after significant changes

---

## Running Benchmarks

### All Benchmarks

```bash
./gradlew :hkj-benchmarks:jmh
```

### Specific Benchmark

```bash
./gradlew :hkj-benchmarks:jmh --tests ".*VTaskBenchmark.*"
```

### With Profiler

```bash
./gradlew :hkj-benchmarks:jmh -PjmhProfilers=gc,stack
```

### Quick Run (Development)

```bash
./gradlew :hkj-benchmarks:jmh -PjmhFork=1 -PjmhWarmup=2 -PjmhIterations=3
```

### Results Location

```
hkj-benchmarks/build/reports/jmh/
├── results.json          # Machine-readable results
├── results.txt           # Human-readable summary
└── flamegraph/           # If async-profiler used
```

---

## Summary

Effective performance testing for higher-kinded-j requires:

1. **Consistent Configuration**: Standard JMH settings across all benchmarks
2. **Fair Comparisons**: Equivalent operations when comparing with alternatives
3. **Virtual Thread Focus**: Demonstrate scaling and efficiency advantages
4. **Actionable Results**: Benchmarks that inform design decisions
5. **Regression Prevention**: Automated comparison against baselines
6. **Honest Reporting**: Document methodology and limitations

Performance testing is not about proving HKJ is "the fastest"; it's about ensuring the abstraction overhead is acceptable while the virtual thread advantages are realised.
