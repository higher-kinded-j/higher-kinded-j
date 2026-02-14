// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Comparison benchmarks between VTask (virtual threads) and traditional platform thread pools.
 *
 * <p>These benchmarks demonstrate the advantages of virtual threads for concurrent workloads:
 *
 * <ul>
 *   <li>Scalability with thousands of concurrent tasks
 *   <li>Memory efficiency compared to platform threads
 *   <li>Performance under I/O-bound workloads (simulated)
 * </ul>
 *
 * <p>The key insight is that virtual threads shine when:
 *
 * <ul>
 *   <li>Task count exceeds available CPU cores
 *   <li>Tasks involve blocking operations (I/O, sleep)
 *   <li>Simple programming model is desired (no callback complexity)
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VTaskVsPlatformThreadsBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class VTaskVsPlatformThreadsBenchmark {

  @Param({"10", "100", "1000"})
  private int taskCount;

  private ExecutorService platformExecutor;
  private List<Callable<Integer>> callables;
  private List<VTask<Integer>> vtasks;

  @Setup
  public void setup() {
    // Fixed thread pool with processor count - typical production configuration
    platformExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // Pre-create callables for platform executor
    callables =
        IntStream.range(0, taskCount)
            .mapToObj(i -> (Callable<Integer>) () -> computeWork(i))
            .toList();

    // Pre-create VTasks
    vtasks = IntStream.range(0, taskCount).mapToObj(i -> VTask.of(() -> computeWork(i))).toList();
  }

  @TearDown
  public void teardown() {
    platformExecutor.shutdown();
    try {
      if (!platformExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        platformExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      platformExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Simulates CPU-bound work.
   *
   * <p>Pure computation to measure scheduling overhead without I/O effects.
   */
  private int computeWork(int input) {
    int result = input;
    for (int i = 0; i < 100; i++) {
      result = (result * 31 + i) % 10000;
    }
    return result;
  }

  // ========== Pure Computation Workload ==========

  /**
   * VTask with Par.all for parallel execution of CPU-bound work.
   *
   * <p>Uses StructuredTaskScope internally for structured concurrency.
   */
  @Benchmark
  public List<Integer> vtask_parAll() {
    return Par.all(vtasks).run();
  }

  /**
   * Platform thread pool with invokeAll for CPU-bound work.
   *
   * <p>Traditional approach using ExecutorService.
   */
  @Benchmark
  public List<Integer> platform_invokeAll() throws Exception {
    List<Future<Integer>> futures = platformExecutor.invokeAll(callables);
    List<Integer> results = new ArrayList<>(taskCount);
    for (Future<Integer> future : futures) {
      results.add(future.get());
    }
    return results;
  }

  // ========== Simulated I/O Workload ==========

  /**
   * VTask with simulated blocking I/O.
   *
   * <p>Virtual threads excel here because blocking doesn't consume OS thread.
   */
  @Benchmark
  public List<Integer> vtask_blockingIO() {
    List<VTask<Integer>> ioTasks =
        IntStream.range(0, taskCount)
            .mapToObj(
                i ->
                    VTask.of(
                        () -> {
                          Thread.sleep(1); // Simulate 1ms I/O
                          return i;
                        }))
            .toList();
    return Par.all(ioTasks).run();
  }

  /**
   * Platform thread pool with simulated blocking I/O.
   *
   * <p>Thread pool becomes bottleneck when task count exceeds pool size.
   */
  @Benchmark
  public List<Integer> platform_blockingIO() throws Exception {
    List<Callable<Integer>> ioCallables =
        IntStream.range(0, taskCount)
            .mapToObj(
                i ->
                    (Callable<Integer>)
                        () -> {
                          Thread.sleep(1); // Simulate 1ms I/O
                          return i;
                        })
            .toList();
    List<Future<Integer>> futures = platformExecutor.invokeAll(ioCallables);
    List<Integer> results = new ArrayList<>(taskCount);
    for (Future<Integer> future : futures) {
      results.add(future.get());
    }
    return results;
  }

  // ========== Sequential Baseline ==========

  /**
   * Sequential VTask execution for baseline comparison.
   *
   * <p>Shows overhead of Par.all by comparing to sequential execution.
   */
  @Benchmark
  public List<Integer> vtask_sequential() {
    List<Integer> results = new ArrayList<>(taskCount);
    for (VTask<Integer> task : vtasks) {
      results.add(task.run());
    }
    return results;
  }

  /** Sequential callable execution for baseline comparison. */
  @Benchmark
  public List<Integer> platform_sequential() throws Exception {
    List<Integer> results = new ArrayList<>(taskCount);
    for (Callable<Integer> callable : callables) {
      results.add(callable.call());
    }
    return results;
  }
}
