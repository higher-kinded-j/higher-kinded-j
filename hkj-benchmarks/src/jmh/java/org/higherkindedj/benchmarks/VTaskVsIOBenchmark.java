// Copyright (c) 2025 Magnus Smith
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

/**
 * Comparison benchmarks between VTask (virtual threads) and IO (platform threads).
 *
 * <p>These benchmarks compare:
 *
 * <ul>
 *   <li>Simple execution overhead
 *   <li>Map chain performance
 *   <li>FlatMap chain performance
 *   <li>Deep recursion performance
 * </ul>
 *
 * <p>This helps quantify the overhead of virtual thread execution compared to direct platform
 * thread execution.
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VTaskVsIOBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VTaskVsIOBenchmark {

  private VTask<Integer> vtask;
  private IO<Integer> io;

  @Setup
  public void setup() {
    vtask = VTask.succeed(42);
    io = IO.delay(() -> 42);
  }

  // ========== Simple Execution ==========

  @Benchmark
  public Integer io_simpleExecution() {
    return io.unsafeRunSync();
  }

  @Benchmark
  public Integer vtask_simpleExecution() throws Throwable {
    return vtask.run();
  }

  // ========== Map Chain ==========

  @Benchmark
  public Integer io_mapChain() {
    return io.map(x -> x + 1).map(x -> x * 2).map(x -> x - 5).unsafeRunSync();
  }

  @Benchmark
  public Integer vtask_mapChain() throws Throwable {
    return vtask.map(x -> x + 1).map(x -> x * 2).map(x -> x - 5).run();
  }

  // ========== FlatMap Chain ==========

  @Benchmark
  public Integer io_flatMapChain() {
    return io.flatMap(x -> IO.delay(() -> x + 1))
        .flatMap(x -> IO.delay(() -> x * 2))
        .flatMap(x -> IO.delay(() -> x - 5))
        .unsafeRunSync();
  }

  @Benchmark
  public Integer vtask_flatMapChain() throws Throwable {
    return vtask
        .flatMap(x -> VTask.succeed(x + 1))
        .flatMap(x -> VTask.succeed(x * 2))
        .flatMap(x -> VTask.succeed(x - 5))
        .run();
  }

  // ========== Deep Recursion ==========

  @Benchmark
  public Integer io_deepRecursion() {
    IO<Integer> result = io;
    for (int i = 0; i < 50; i++) {
      result = result.flatMap(x -> IO.delay(() -> x + 1));
    }
    return result.unsafeRunSync();
  }

  @Benchmark
  public Integer vtask_deepRecursion() throws Throwable {
    VTask<Integer> result = vtask;
    for (int i = 0; i < 50; i++) {
      result = result.flatMap(x -> VTask.succeed(x + 1));
    }
    return result.run();
  }

  // ========== Long Map Chain ==========

  @Benchmark
  public Integer io_longMapChain() {
    IO<Integer> result = io;
    for (int i = 0; i < 50; i++) {
      result = result.map(x -> x + 1);
    }
    return result.unsafeRunSync();
  }

  @Benchmark
  public Integer vtask_longMapChain() throws Throwable {
    VTask<Integer> result = vtask;
    for (int i = 0; i < 50; i++) {
      result = result.map(x -> x + 1);
    }
    return result.run();
  }

  // ========== Mixed Operations ==========

  @Benchmark
  public Integer io_mixedOperations() {
    return io.map(x -> x + 1)
        .flatMap(x -> IO.delay(() -> x * 2))
        .map(x -> x - 5)
        .flatMap(x -> IO.delay(() -> x / 3))
        .map(x -> x + 10)
        .unsafeRunSync();
  }

  @Benchmark
  public Integer vtask_mixedOperations() throws Throwable {
    return vtask
        .map(x -> x + 1)
        .flatMap(x -> VTask.succeed(x * 2))
        .map(x -> x - 5)
        .flatMap(x -> VTask.succeed(x / 3))
        .map(x -> x + 10)
        .run();
  }

  // ========== Construction Only ==========

  @Benchmark
  public IO<Integer> io_construction() {
    return IO.delay(() -> 42);
  }

  @Benchmark
  public VTask<Integer> vtask_construction() {
    return VTask.succeed(42);
  }
}
