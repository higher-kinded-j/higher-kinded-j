// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.trymonad.Try;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for IOPath Effect Path operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>IOPath construction overhead
 *   <li>Execution cost compared to raw IO
 *   <li>Map and via composition
 *   <li>Error handling performance
 *   <li>Combinators (zipWith, zipWith3)
 * </ul>
 *
 * <p>IOPath wraps IO with a fluent API. This benchmark suite validates that the wrapper overhead is
 * acceptable (expected: 5-15% overhead).
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*IOPathBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class IOPathBenchmark {

  @Param({"50"})
  private int chainDepth;

  private IOPath<Integer> purePath;
  private IOPath<Integer> delayedPath;
  private IOPath<Integer> failingPath;

  @Setup
  public void setup() {
    purePath = Path.ioPure(42);
    delayedPath = Path.io(() -> 42);
    failingPath =
        Path.io(
            () -> {
              throw new RuntimeException("test error");
            });
  }

  // ========== Construction Benchmarks ==========

  /**
   * Construction of pure IOPath.
   *
   * <p>Measures overhead of wrapping a pure value.
   */
  @Benchmark
  public IOPath<Integer> constructPure() {
    return Path.ioPure(42);
  }

  /**
   * Construction of delayed IOPath.
   *
   * <p>Measures overhead of wrapping a lazy computation.
   */
  @Benchmark
  public IOPath<Integer> constructDelay() {
    return Path.io(() -> 42);
  }

  /**
   * Construction of failing IOPath.
   *
   * <p>Measures overhead of creating an error path.
   */
  @Benchmark
  public IOPath<Integer> constructFail() {
    return Path.io(
        () -> {
          throw new RuntimeException("error");
        });
  }

  // ========== Execution Benchmarks ==========

  /**
   * Execution of pure IOPath.
   *
   * <p>Baseline for measuring wrapper overhead.
   */
  @Benchmark
  public Integer runPure() {
    return purePath.unsafeRun();
  }

  /**
   * Execution of delayed IOPath.
   *
   * <p>Measures lazy evaluation overhead.
   */
  @Benchmark
  public Integer runDelay() {
    return delayedPath.unsafeRun();
  }

  /**
   * Safe execution returning Try.
   *
   * <p>Measures runSafe overhead.
   */
  @Benchmark
  public Try<Integer> runSafePure() {
    return purePath.runSafe();
  }

  // ========== Map Composition Benchmarks ==========

  /**
   * Map construction (no execution).
   *
   * <p>Measures lazy map construction cost.
   */
  @Benchmark
  public IOPath<Integer> mapConstruction() {
    return purePath.map(x -> x + 1).map(x -> x * 2).map(x -> x - 5);
  }

  /**
   * Map execution.
   *
   * <p>Measures full map chain cost including execution.
   */
  @Benchmark
  public Integer mapExecution() {
    return purePath.map(x -> x + 1).map(x -> x * 2).map(x -> x - 5).unsafeRun();
  }

  /**
   * Long map chain construction.
   *
   * <p>Tests linear scaling of map composition.
   */
  @Benchmark
  public IOPath<Integer> longMapChainConstruction() {
    IOPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long map chain execution.
   *
   * <p>Tests linear scaling of map execution.
   */
  @Benchmark
  public Integer longMapChainExecution() {
    IOPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result.unsafeRun();
  }

  // ========== Via/FlatMap Composition Benchmarks ==========

  /**
   * Via construction (no execution).
   *
   * <p>Measures lazy via construction cost.
   */
  @Benchmark
  public IOPath<Integer> viaConstruction() {
    return purePath.via(a -> Path.ioPure(a * 2));
  }

  /**
   * Via execution.
   *
   * <p>Measures full via chain cost including execution.
   */
  @Benchmark
  public Integer viaExecution() {
    return purePath.via(a -> Path.ioPure(a * 2)).unsafeRun();
  }

  /**
   * Long via chain construction.
   *
   * <p>Tests linear scaling of via composition.
   */
  @Benchmark
  public IOPath<Integer> longViaChainConstruction() {
    IOPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.via(x -> Path.ioPure(x + 1));
    }
    return result;
  }

  /**
   * Long via chain execution.
   *
   * <p>Tests linear scaling of via execution.
   */
  @Benchmark
  public Integer longViaChainExecution() {
    IOPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.via(x -> Path.ioPure(x + 1));
    }
    return result.unsafeRun();
  }

  // ========== Error Handling Benchmarks ==========

  /**
   * HandleError construction.
   *
   * <p>Measures cost of adding error handler.
   */
  @Benchmark
  public IOPath<Integer> handleErrorConstruction() {
    return purePath.handleError(e -> -1);
  }

  /**
   * HandleError execution - no error occurs.
   *
   * <p>Measures overhead when error path not taken.
   */
  @Benchmark
  public Integer handleErrorNoError() {
    return purePath.handleError(e -> -1).unsafeRun();
  }

  /**
   * HandleError execution - error occurs.
   *
   * <p>Measures cost of error recovery.
   */
  @Benchmark
  public Integer handleErrorWithError() {
    return failingPath.handleError(e -> -1).unsafeRun();
  }

  /**
   * HandleErrorWith construction.
   *
   * <p>Measures cost of adding error handler that returns IOPath.
   */
  @Benchmark
  public IOPath<Integer> handleErrorWithConstruction() {
    return purePath.handleErrorWith(e -> Path.ioPure(-1));
  }

  /**
   * HandleErrorWith execution.
   *
   * <p>Measures full error handling chain.
   */
  @Benchmark
  public Integer handleErrorWithExecution() {
    return failingPath.handleErrorWith(e -> Path.ioPure(-1)).unsafeRun();
  }

  /**
   * Fallback chain pattern.
   *
   * <p>Tests performance of multiple fallback handlers.
   */
  @Benchmark
  public Integer fallbackChain() {
    return failingPath
        .handleErrorWith(
            e ->
                Path.io(
                    () -> {
                      throw new RuntimeException("still failing");
                    }))
        .handleErrorWith(e -> Path.ioPure(0))
        .unsafeRun();
  }

  // ========== Combinator Benchmarks ==========

  /**
   * ZipWith combinator execution.
   *
   * <p>Measures parallel combination overhead.
   */
  @Benchmark
  public Integer zipWithExecution() {
    return purePath.zipWith(Path.ioPure(10), Integer::sum).unsafeRun();
  }

  /**
   * ZipWith3 combinator execution.
   *
   * <p>Measures three-way combination overhead.
   */
  @Benchmark
  public Integer zipWith3Execution() {
    return purePath.zipWith3(Path.ioPure(10), Path.ioPure(5), (a, b, c) -> a + b + c).unsafeRun();
  }

  // ========== Mixed Operations Benchmarks ==========

  /**
   * Chained operations - realistic pattern.
   *
   * <p>Tests mixed map/via composition.
   */
  @Benchmark
  public Integer chainedOperations() {
    return purePath.map(x -> x + 1).via(x -> Path.io(() -> x * 2)).map(x -> x - 5).unsafeRun();
  }

  /**
   * Sequential composition pattern.
   *
   * <p>Tests then() combinator.
   */
  @Benchmark
  public Integer sequentialComposition() {
    return purePath.then(() -> Path.ioPure(100)).then(() -> Path.ioPure(200)).unsafeRun();
  }

  /**
   * Real-world resource pattern.
   *
   * <p>Simulates acquire/use/release pattern performance.
   */
  @Benchmark
  public String realWorldResourcePattern() {
    return Path.ioPure("resource")
        .map(r -> r.toUpperCase())
        .via(r -> Path.ioPure(r + "_processed"))
        .map(r -> r + "_done")
        .unsafeRun();
  }

  /**
   * Resilient service call pattern.
   *
   * <p>Tests error handling in realistic scenario.
   */
  @Benchmark
  public Integer resilientServiceCall() {
    return Path.io(() -> 42).map(x -> x * 2).handleError(e -> 0).unsafeRun();
  }

  // ========== Peek/Debug Benchmarks ==========

  /**
   * Peek execution.
   *
   * <p>Measures overhead of side-effecting peek.
   */
  @Benchmark
  public Integer peekExecution(Blackhole bh) {
    return purePath.peek(bh::consume).unsafeRun();
  }

  // ========== Construction Independence ==========

  /**
   * Multiple independent constructions.
   *
   * <p>Ensures construction doesn't have hidden sharing.
   */
  @Benchmark
  public void independentConstruction(Blackhole bh) {
    bh.consume(Path.ioPure(1));
    bh.consume(Path.ioPure(2));
    bh.consume(Path.ioPure(3));
  }
}
