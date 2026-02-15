// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.vtask.VTask;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Benchmarks measuring the overhead of HKJ abstractions compared to raw Java.
 *
 * <p>These benchmarks quantify the cost of using functional effect types vs direct imperative code.
 * The overhead is expected and acceptable because:
 *
 * <ul>
 *   <li>Real workloads involve I/O that dominates compute time
 *   <li>Type safety and composability benefits outweigh cost
 *   <li>Overhead is constant, not proportional to data size
 * </ul>
 *
 * <p>Expected results:
 *
 * <ul>
 *   <li>Raw Java: fastest (baseline)
 *   <li>IO: 50-200x slower than raw Java
 *   <li>VTask: 50-200x slower than raw Java (plus virtual thread overhead)
 *   <li>VTaskPath: 5-15% slower than VTask (wrapper overhead)
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*AbstractionOverheadBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class AbstractionOverheadBenchmark {

  // ========== Simple Computation ==========

  /**
   * Raw Java - direct computation.
   *
   * <p>Provides baseline for measuring abstraction overhead.
   */
  @Benchmark
  public int rawJava_simple() {
    int result = 1;
    result = result + 1;
    result = result * 2;
    return result;
  }

  /**
   * IO - wrapped computation.
   *
   * <p>Shows overhead of IO monad for simple operations.
   */
  @Benchmark
  public int io_simple() {
    return IO.delay(() -> 1).map(x -> x + 1).map(x -> x * 2).unsafeRunSync();
  }

  /**
   * VTask - wrapped computation.
   *
   * <p>Shows overhead of VTask for simple operations (includes virtual thread spawn).
   */
  @Benchmark
  public int vtask_simple() {
    return VTask.succeed(1).map(x -> x + 1).map(x -> x * 2).run();
  }

  /**
   * VTaskPath - wrapped computation with Effect Path.
   *
   * <p>Shows additional wrapper overhead on top of VTask.
   */
  @Benchmark
  public int vtaskPath_simple() {
    return Path.vtaskPure(1).map(x -> x + 1).map(x -> x * 2).unsafeRun();
  }

  // ========== Longer Chain ==========

  /**
   * Raw Java - longer chain.
   *
   * <p>Tests scaling with more operations.
   */
  @Benchmark
  public int rawJava_chain() {
    int result = 1;
    result = result + 1;
    result = result * 2;
    result = result - 3;
    result = result + 10;
    result = result / 2;
    return result;
  }

  /**
   * IO - longer chain.
   *
   * <p>Tests if overhead scales linearly with operations.
   */
  @Benchmark
  public int io_chain() {
    return IO.delay(() -> 1)
        .map(x -> x + 1)
        .map(x -> x * 2)
        .map(x -> x - 3)
        .map(x -> x + 10)
        .map(x -> x / 2)
        .unsafeRunSync();
  }

  /**
   * VTask - longer chain.
   *
   * <p>Tests if overhead scales linearly with operations.
   */
  @Benchmark
  public int vtask_chain() {
    return VTask.succeed(1)
        .map(x -> x + 1)
        .map(x -> x * 2)
        .map(x -> x - 3)
        .map(x -> x + 10)
        .map(x -> x / 2)
        .run();
  }

  /**
   * VTaskPath - longer chain.
   *
   * <p>Tests Effect Path scaling.
   */
  @Benchmark
  public int vtaskPath_chain() {
    return Path.vtaskPure(1)
        .map(x -> x + 1)
        .map(x -> x * 2)
        .map(x -> x - 3)
        .map(x -> x + 10)
        .map(x -> x / 2)
        .unsafeRun();
  }

  // ========== Construction Only ==========

  /**
   * IO construction - no execution.
   *
   * <p>Isolates construction overhead from execution.
   */
  @Benchmark
  public IO<Integer> io_constructOnly() {
    return IO.delay(() -> 1).map(x -> x + 1).map(x -> x * 2);
  }

  /**
   * VTask construction - no execution.
   *
   * <p>Isolates construction overhead from execution.
   */
  @Benchmark
  public VTask<Integer> vtask_constructOnly() {
    return VTask.succeed(1).map(x -> x + 1).map(x -> x * 2);
  }

  /**
   * VTaskPath construction - no execution.
   *
   * <p>Isolates construction overhead from execution.
   */
  @Benchmark
  public VTaskPath<Integer> vtaskPath_constructOnly() {
    return Path.vtaskPure(1).map(x -> x + 1).map(x -> x * 2);
  }
}
