// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.io.IO;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for IO type operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Cost of IO construction and composition (lazy)
 *   <li>Execution overhead when running IO
 *   <li>Performance of map/flatMap chains
 *   <li>Stack safety for deep recursion
 * </ul>
 *
 * <p>Note: IO is lazy, so benchmarks measure both construction and execution costs separately.
 *
 * <p>Run with: {@code ./gradlew jmh} or {@code ./gradlew :hkj-benchmarks:jmh}
 *
 * <p>Run specific benchmark: {@code ./gradlew jmh --includes=".*IOBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(
    value = 2,
    jvmArgs = {"-Xms2G", "-Xmx2G"})
public class IOBenchmark {

  private IO<Integer> pureIO;
  private IO<Integer> delayedIO;
  private int counter;

  @Setup
  public void setup() {
    pureIO = IO.delay(() -> 42);
    delayedIO = IO.delay(() -> 42);
    counter = 0;
  }

  /**
   * Construction cost of IO.delay with simple value.
   *
   * <p>Measures overhead of creating a simple IO value.
   */
  @Benchmark
  public IO<Integer> constructSimple() {
    return IO.delay(() -> 42);
  }

  /**
   * Execution cost of IO.delay with simple value.
   *
   * <p>Measures the cost of running a simple computation.
   */
  @Benchmark
  public Integer runSimple() {
    return pureIO.unsafeRunSync();
  }

  /**
   * Execution cost of IO.delay.
   *
   * <p>Measures the cost of running a delayed computation.
   */
  @Benchmark
  public Integer runDelay() {
    return delayedIO.unsafeRunSync();
  }

  /**
   * Map operation construction (lazy - doesn't execute).
   *
   * <p>Measures cost of building up IO chains without executing.
   */
  @Benchmark
  public IO<Integer> mapConstruction() {
    return pureIO.map(x -> x + 1);
  }

  /**
   * Map operation execution.
   *
   * <p>Measures cost of running a mapped IO.
   */
  @Benchmark
  public Integer mapExecution() {
    return pureIO.map(x -> x + 1).unsafeRunSync();
  }

  /** FlatMap operation construction. */
  @Benchmark
  public IO<Integer> flatMapConstruction() {
    return pureIO.flatMap(x -> IO.delay(() -> x * 2));
  }

  /** FlatMap operation execution. */
  @Benchmark
  public Integer flatMapExecution() {
    return pureIO.flatMap(x -> IO.delay(() -> x * 2)).unsafeRunSync();
  }

  /**
   * Long chain construction (lazy).
   *
   * <p>Measures cost of building deep IO chains.
   */
  @Benchmark
  public IO<Integer> longChainConstruction() {
    IO<Integer> result = pureIO;
    for (int i = 0; i < 100; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long chain execution.
   *
   * <p>Measures cost of executing deep IO chains.
   */
  @Benchmark
  public Integer longChainExecution() {
    IO<Integer> result = pureIO;
    for (int i = 0; i < 100; i++) {
      result = result.map(x -> x + 1);
    }
    return result.unsafeRunSync();
  }

  /**
   * Side-effecting IO with delay.
   *
   * <p>Simulates real IO operations that modify state.
   */
  @Benchmark
  public Integer sideEffectingIO() {
    return IO.delay(() -> ++counter).unsafeRunSync();
  }

  /** Chained operations with mixed map/flatMap. */
  @Benchmark
  public Integer chainedOperations() {
    return pureIO
        .map(x -> x + 1)
        .flatMap(x -> IO.delay(() -> x * 2))
        .map(x -> x - 5)
        .flatMap(x -> IO.delay(() -> x / 3))
        .unsafeRunSync();
  }

  /**
   * Sequential computation composition.
   *
   * <p>Measures the cost of sequential flatMap operations.
   */
  @Benchmark
  public Integer sequentialComposition() {
    return pureIO
        .flatMap(x -> IO.delay(() -> x * 2))
        .flatMap(y -> IO.delay(() -> y + 10))
        .unsafeRunSync();
  }

  /** Nested flatMap construction. */
  @Benchmark
  public IO<Integer> nestedFlatMapConstruction() {
    return pureIO
        .flatMap(x -> IO.delay(() -> x + 1))
        .flatMap(x -> IO.delay(() -> x * 2))
        .flatMap(x -> IO.delay(() -> x - 5))
        .flatMap(x -> IO.delay(() -> x / 3));
  }

  /** Nested flatMap execution. */
  @Benchmark
  public Integer nestedFlatMapExecution() {
    return pureIO
        .flatMap(x -> IO.delay(() -> x + 1))
        .flatMap(x -> IO.delay(() -> x * 2))
        .flatMap(x -> IO.delay(() -> x - 5))
        .flatMap(x -> IO.delay(() -> x / 3))
        .unsafeRunSync();
  }

  /**
   * Stack safety test - deep recursion.
   *
   * <p>Tests if IO can handle deep recursive chains without stack overflow.
   */
  @Benchmark
  public Integer deepRecursion() {
    IO<Integer> result = IO.delay(() -> 0);
    for (int i = 0; i < 1000; i++) {
      result = result.flatMap(x -> IO.delay(() -> x + 1));
    }
    return result.unsafeRunSync();
  }

  /**
   * Real-world pattern: resource acquisition and cleanup simulation.
   *
   * <p>Simulates acquiring a resource, using it, and cleaning up.
   */
  @Benchmark
  public String realWorldResourcePattern(Blackhole blackhole) {
    IO<String> program =
        IO.delay(() -> "resource")
            .flatMap(
                resource -> {
                  blackhole.consume(resource);
                  return IO.delay(() -> "used: " + resource);
                })
            .flatMap(result -> IO.delay(() -> "cleaned up: " + result));

    return program.unsafeRunSync();
  }

  /**
   * Simple computation overhead.
   *
   * <p>Measures cost of simple map operations in IO.
   */
  @Benchmark
  public Integer simpleComputation() {
    return IO.delay(() -> 42).map(x -> x * 2).unsafeRunSync();
  }

  /**
   * Multiple independent IO construction.
   *
   * <p>Measures cost of creating multiple independent IOs.
   */
  @Benchmark
  public void independentConstruction(Blackhole blackhole) {
    IO<Integer> io1 = IO.delay(() -> 1);
    IO<Integer> io2 = IO.delay(() -> 2);
    IO<Integer> io3 = IO.delay(() -> 3);
    blackhole.consume(io1);
    blackhole.consume(io2);
    blackhole.consume(io3);
  }

  /** Mixed delay operations with different complexities. */
  @Benchmark
  public Integer mixedDelayOperations() {
    return IO.delay(() -> 10)
        .flatMap(x -> IO.delay(() -> x + 5))
        .flatMap(x -> IO.delay(() -> x * 2))
        .flatMap(x -> IO.delay(() -> x - 3))
        .unsafeRunSync();
  }
}
