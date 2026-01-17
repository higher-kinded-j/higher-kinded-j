// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.vtask.VTask;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Memory footprint benchmarks for HKJ types.
 *
 * <p>These benchmarks measure memory allocation patterns for different effect types. They are
 * particularly useful when run with GC profiling to understand allocation rates.
 *
 * <p>Key insights from these benchmarks:
 *
 * <ul>
 *   <li>VTask vs IO construction memory overhead
 *   <li>Effect wrapper allocation cost
 *   <li>Scaling behaviour with large numbers of effects
 *   <li>CompletableFuture comparison for context
 * </ul>
 *
 * <p>Run with GC profiling: {@code ./gradlew jmh --includes=".*MemoryFootprintBenchmark.*"
 * -Pjmh.profilers=gc}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class MemoryFootprintBenchmark {

  @Param({"100", "1000", "10000"})
  private int count;

  // ========== Construction Memory ==========

  /**
   * VTask construction memory footprint.
   *
   * <p>Creates many VTask instances to measure per-instance allocation. Run with gc profiler to see
   * gc.alloc.rate.norm (bytes per operation).
   */
  @Benchmark
  public List<VTask<Integer>> vtask_constructMany() {
    List<VTask<Integer>> tasks = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      tasks.add(VTask.succeed(i));
    }
    return tasks;
  }

  /**
   * IO construction memory footprint.
   *
   * <p>Creates many IO instances to measure per-instance allocation.
   */
  @Benchmark
  public List<IO<Integer>> io_constructMany() {
    List<IO<Integer>> ios = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      final int val = i;
      ios.add(IO.delay(() -> val));
    }
    return ios;
  }

  /**
   * CompletableFuture construction memory footprint.
   *
   * <p>Provides baseline comparison with standard Java concurrency.
   */
  @Benchmark
  public List<CompletableFuture<Integer>> completableFuture_constructMany() {
    List<CompletableFuture<Integer>> futures = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      futures.add(CompletableFuture.completedFuture(i));
    }
    return futures;
  }

  // ========== Map Chain Memory ==========

  /**
   * VTask map chain memory footprint.
   *
   * <p>Tests allocation during composition - each map creates new wrapper.
   */
  @Benchmark
  public void vtask_mapChainMemory(Blackhole bh) {
    for (int i = 0; i < count; i++) {
      VTask<Integer> task = VTask.succeed(i).map(x -> x + 1).map(x -> x * 2).map(x -> x - 1);
      bh.consume(task);
    }
  }

  /**
   * IO map chain memory footprint.
   *
   * <p>Tests allocation during composition - each map creates new wrapper.
   */
  @Benchmark
  public void io_mapChainMemory(Blackhole bh) {
    for (int i = 0; i < count; i++) {
      final int val = i;
      IO<Integer> io = IO.delay(() -> val).map(x -> x + 1).map(x -> x * 2).map(x -> x - 1);
      bh.consume(io);
    }
  }

  // ========== Single Shot Memory Measurement ==========

  /**
   * VTask bulk allocation - single shot.
   *
   * <p>Measures memory used when creating a large batch of VTasks in one operation. Uses
   * SingleShotTime mode for cold-start measurement.
   */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 0)
  @Measurement(iterations = 1)
  @Fork(
      value = 3,
      jvmArgs = {"-Xms256M", "-Xmx256M", "--enable-preview"})
  public List<VTask<Integer>> vtask_bulkAllocation() {
    return IntStream.range(0, count).mapToObj(VTask::succeed).toList();
  }

  /**
   * IO bulk allocation - single shot.
   *
   * <p>Measures memory used when creating a large batch of IOs in one operation.
   */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 0)
  @Measurement(iterations = 1)
  @Fork(
      value = 3,
      jvmArgs = {"-Xms256M", "-Xmx256M", "--enable-preview"})
  public List<IO<Integer>> io_bulkAllocation() {
    return IntStream.range(0, count).mapToObj(i -> IO.delay(() -> i)).toList();
  }

  /**
   * CompletableFuture bulk allocation - single shot.
   *
   * <p>Provides baseline for bulk allocation comparison.
   */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 0)
  @Measurement(iterations = 1)
  @Fork(
      value = 3,
      jvmArgs = {"-Xms256M", "-Xmx256M", "--enable-preview"})
  public List<CompletableFuture<Integer>> completableFuture_bulkAllocation() {
    return IntStream.range(0, count).mapToObj(CompletableFuture::completedFuture).toList();
  }
}
