// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.vtask.VTask;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

/**
 * Concurrency scaling benchmarks using JMH @Threads annotations.
 *
 * <p>These benchmarks measure how VTask and IO perform under concurrent load. The @Threads
 * annotation causes JMH to run the benchmark method from multiple threads simultaneously, measuring
 * aggregate throughput.
 *
 * <p>Key insights from these benchmarks:
 *
 * <ul>
 *   <li>VTask should scale better under high thread counts due to virtual thread efficiency
 *   <li>IO may show contention effects with many platform threads
 *   <li>Thread-local state isolation should be maintained
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*ConcurrencyScalingBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class ConcurrencyScalingBenchmark {

  private VTask<Integer> vtask;
  private IO<Integer> io;

  @Setup
  public void setup() {
    vtask = VTask.succeed(42);
    io = IO.delay(() -> 42);
  }

  // ========== Single Thread Baseline ==========

  /**
   * VTask execution with single thread.
   *
   * <p>Provides baseline for scaling comparison.
   */
  @Benchmark
  @Threads(1)
  public Integer vtask_singleThread() {
    return vtask.map(x -> x + 1).map(x -> x * 2).run();
  }

  /**
   * IO execution with single thread.
   *
   * <p>Provides baseline for scaling comparison.
   */
  @Benchmark
  @Threads(1)
  public Integer io_singleThread() {
    return io.map(x -> x + 1).map(x -> x * 2).unsafeRunSync();
  }

  // ========== Four Threads ==========

  /**
   * VTask execution with four concurrent threads.
   *
   * <p>Tests moderate concurrency - typical for quad-core systems.
   */
  @Benchmark
  @Threads(4)
  public Integer vtask_fourThreads() {
    return vtask.map(x -> x + 1).map(x -> x * 2).run();
  }

  /**
   * IO execution with four concurrent threads.
   *
   * <p>Tests moderate concurrency - typical for quad-core systems.
   */
  @Benchmark
  @Threads(4)
  public Integer io_fourThreads() {
    return io.map(x -> x + 1).map(x -> x * 2).unsafeRunSync();
  }

  // ========== Maximum Threads ==========

  /**
   * VTask execution with maximum threads.
   *
   * <p>Tests high concurrency - uses all available processors. VTask should scale well here due to
   * virtual thread efficiency.
   */
  @Benchmark
  @Threads(Threads.MAX)
  public Integer vtask_maxThreads() {
    return vtask.map(x -> x + 1).map(x -> x * 2).run();
  }

  /**
   * IO execution with maximum threads.
   *
   * <p>Tests high concurrency - uses all available processors. IO may show increased contention.
   */
  @Benchmark
  @Threads(Threads.MAX)
  public Integer io_maxThreads() {
    return io.map(x -> x + 1).map(x -> x * 2).unsafeRunSync();
  }
}
