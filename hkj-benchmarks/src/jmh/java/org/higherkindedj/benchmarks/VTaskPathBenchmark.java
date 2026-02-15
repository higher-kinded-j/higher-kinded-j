// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
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
 * JMH benchmarks for VTaskPath Effect Path operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Cost of VTaskPath construction (Path.vtask, vtaskPure, vtaskFail)
 *   <li>Execution overhead (run, runSafe, runAsync)
 *   <li>Performance of map/via chains
 *   <li>Error handling overhead (handleError, handleErrorWith)
 *   <li>Timeout overhead
 *   <li>Comparison with raw VTask
 * </ul>
 *
 * <p>VTaskPath provides a fluent Effect Path wrapper for VTask that executes on virtual threads.
 * This benchmark suite validates that the wrapper overhead is acceptable while demonstrating the
 * advantages of the fluent composition API.
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VTaskPathBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VTaskPathBenchmark {

  @Param({"50"})
  private int chainDepth;

  private VTaskPath<Integer> purePath;
  private VTaskPath<Integer> delayedPath;

  @Setup
  public void setup() {
    purePath = Path.vtaskPure(42);
    delayedPath = Path.vtask(() -> 42);
  }

  // ========== Construction Benchmarks ==========

  /**
   * Construction cost of Path.vtaskPure with simple value.
   *
   * <p>Measures overhead of creating an immediate VTaskPath value.
   */
  @Benchmark
  public VTaskPath<Integer> constructPure() {
    return Path.vtaskPure(42);
  }

  /**
   * Construction cost of Path.vtask with Callable.
   *
   * <p>Measures overhead of creating a deferred VTaskPath.
   */
  @Benchmark
  public VTaskPath<Integer> constructVtask() {
    return Path.vtask(() -> 42);
  }

  /**
   * Construction cost of Path.vtaskFail.
   *
   * <p>Measures overhead of creating a failed VTaskPath.
   */
  @Benchmark
  public VTaskPath<Integer> constructFail() {
    return Path.vtaskFail(new RuntimeException("error"));
  }

  // ========== Execution Benchmarks ==========

  /**
   * Execution cost via run().
   *
   * <p>Measures the cost of executing a pure VTaskPath.
   */
  @Benchmark
  public Integer runPure() {
    return purePath.run().runSafe().orElse(-1);
  }

  /**
   * Execution cost via unsafeRun().
   *
   * <p>Measures the cost of executing a pure VTaskPath unsafely.
   */
  @Benchmark
  public Integer unsafeRunPure() {
    return purePath.unsafeRun();
  }

  /**
   * Execution cost via runSafe().
   *
   * <p>Measures the cost of safe execution with Try wrapping.
   */
  @Benchmark
  public Try<Integer> runSafePure() {
    return purePath.runSafe();
  }

  /**
   * Execution cost of delayed VTaskPath.
   *
   * <p>Measures the overhead of callable invocation on virtual thread.
   */
  @Benchmark
  public Integer unsafeRunDelayed() {
    return delayedPath.unsafeRun();
  }

  // ========== Map Composition Benchmarks ==========

  /**
   * Map operation construction (lazy - doesn't execute).
   *
   * <p>Measures cost of building up VTaskPath map chains without executing.
   */
  @Benchmark
  public VTaskPath<Integer> mapConstruction() {
    return purePath.map(x -> x + 1);
  }

  /**
   * Map operation execution.
   *
   * <p>Measures cost of running a mapped VTaskPath.
   */
  @Benchmark
  public Integer mapExecution() {
    return purePath.map(x -> x + 1).unsafeRun();
  }

  /**
   * Long map chain construction.
   *
   * <p>Measures cost of building deep VTaskPath map chains.
   */
  @Benchmark
  public VTaskPath<Integer> longMapChainConstruction() {
    VTaskPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long map chain execution.
   *
   * <p>Measures cost of executing deep VTaskPath map chains.
   */
  @Benchmark
  public Integer longMapChainExecution() {
    VTaskPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result.unsafeRun();
  }

  // ========== Via/FlatMap Composition Benchmarks ==========

  /**
   * Via operation construction.
   *
   * <p>Measures cost of building via chains (lazy).
   */
  @Benchmark
  public VTaskPath<Integer> viaConstruction() {
    return purePath.via(x -> Path.vtaskPure(x * 2));
  }

  /**
   * Via operation execution.
   *
   * <p>Measures cost of running via chains.
   */
  @Benchmark
  public Integer viaExecution() {
    return purePath.via(x -> Path.vtaskPure(x * 2)).unsafeRun();
  }

  /**
   * FlatMap operation (alias for via).
   *
   * <p>Measures cost of flatMap for those who prefer that naming.
   */
  @Benchmark
  public Integer flatMapExecution() {
    return purePath.flatMap(x -> Path.vtaskPure(x * 2)).unsafeRun();
  }

  /**
   * Long via chain construction.
   *
   * <p>Measures cost of building deep via chains.
   */
  @Benchmark
  public VTaskPath<Integer> longViaChainConstruction() {
    VTaskPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.via(x -> Path.vtaskPure(x + 1));
    }
    return result;
  }

  /**
   * Long via chain execution.
   *
   * <p>Measures cost of executing deep via chains.
   */
  @Benchmark
  public Integer longViaChainExecution() {
    VTaskPath<Integer> result = purePath;
    for (int i = 0; i < chainDepth; i++) {
      result = result.via(x -> Path.vtaskPure(x + 1));
    }
    return result.unsafeRun();
  }

  // ========== Mixed Operations Benchmarks ==========

  /**
   * Chained operations with mixed map/via.
   *
   * <p>Measures realistic composition patterns.
   */
  @Benchmark
  public Integer chainedOperations() {
    return purePath
        .map(x -> x + 1)
        .via(x -> Path.vtaskPure(x * 2))
        .map(x -> x - 5)
        .via(x -> Path.vtaskPure(x / 3))
        .unsafeRun();
  }

  /**
   * Sequential composition with via.
   *
   * <p>Measures cost of sequential flatMap operations.
   */
  @Benchmark
  public Integer sequentialComposition() {
    return purePath.via(x -> Path.vtaskPure(x * 2)).via(y -> Path.vtaskPure(y + 10)).unsafeRun();
  }

  // ========== Error Handling Benchmarks ==========

  /**
   * handleError construction.
   *
   * <p>Measures overhead of adding error recovery.
   */
  @Benchmark
  public VTaskPath<Integer> handleErrorConstruction() {
    return purePath.handleError(e -> -1);
  }

  /**
   * handleError execution (no error case).
   *
   * <p>Measures overhead of error handling when no error occurs.
   */
  @Benchmark
  public Integer handleErrorNoError() {
    return purePath.handleError(e -> -1).unsafeRun();
  }

  /**
   * handleError execution (error case).
   *
   * <p>Measures cost of error recovery.
   */
  @Benchmark
  public Integer handleErrorWithError() {
    return Path.<Integer>vtaskFail(new RuntimeException("error")).handleError(e -> -1).unsafeRun();
  }

  /**
   * handleErrorWith construction.
   *
   * <p>Measures overhead of adding error recovery with alternative VTaskPath.
   */
  @Benchmark
  public VTaskPath<Integer> handleErrorWithConstruction() {
    return purePath.handleErrorWith(e -> Path.vtaskPure(-1));
  }

  /**
   * handleErrorWith execution (error case).
   *
   * <p>Measures cost of alternative path recovery.
   */
  @Benchmark
  public Integer handleErrorWithExecution() {
    return Path.<Integer>vtaskFail(new RuntimeException("error"))
        .handleErrorWith(e -> Path.vtaskPure(-1))
        .unsafeRun();
  }

  /**
   * Fallback chain pattern.
   *
   * <p>Measures cost of chained error handling.
   */
  @Benchmark
  public Integer fallbackChain() {
    return Path.<Integer>vtaskFail(new RuntimeException("primary"))
        .handleErrorWith(e -> Path.<Integer>vtaskFail(new RuntimeException("secondary")))
        .handleError(e -> -1)
        .unsafeRun();
  }

  // ========== Timeout Benchmarks ==========

  /**
   * Timeout construction.
   *
   * <p>Measures overhead of adding timeout to a VTaskPath.
   */
  @Benchmark
  public VTaskPath<Integer> timeoutConstruction() {
    return purePath.timeout(Duration.ofSeconds(1));
  }

  /**
   * Timeout execution (no timeout triggered).
   *
   * <p>Measures overhead of timeout wrapper when task completes quickly.
   */
  @Benchmark
  public Integer timeoutNoTrigger() {
    return purePath.timeout(Duration.ofSeconds(1)).unsafeRun();
  }

  // ========== Peek/Debug Benchmarks ==========

  /**
   * Peek operation for debugging.
   *
   * <p>Measures overhead of peek observation.
   */
  @Benchmark
  public Integer peekExecution(Blackhole blackhole) {
    return purePath.peek(blackhole::consume).unsafeRun();
  }

  // ========== Real-World Patterns ==========

  /**
   * Real-world pattern: resource acquisition and cleanup simulation.
   *
   * <p>Simulates acquiring a resource, using it, and cleaning up.
   */
  @Benchmark
  public String realWorldResourcePattern(Blackhole blackhole) {
    VTaskPath<String> program =
        Path.vtaskPure("resource")
            .via(
                resource -> {
                  blackhole.consume(resource);
                  return Path.vtaskPure("used: " + resource);
                })
            .via(result -> Path.vtaskPure("cleaned up: " + result));

    return program.unsafeRun();
  }

  /**
   * Real-world pattern: resilient service call.
   *
   * <p>Simulates a service call with timeout and fallback.
   */
  @Benchmark
  public Integer resilientServiceCall() {
    return Path.vtask(() -> 42).timeout(Duration.ofSeconds(1)).handleError(e -> -1).unsafeRun();
  }

  // ========== ZipWith Benchmarks ==========

  /**
   * ZipWith two VTaskPaths in parallel.
   *
   * <p>Measures overhead of combining two paths using Par.map2 for parallel execution. Unlike
   * IOPath.zipWith which is sequential, VTaskPath.zipWith executes both tasks concurrently.
   */
  @Benchmark
  public Integer zipWithExecution() {
    VTaskPath<Integer> other = Path.vtaskPure(10);
    return purePath.zipWith(other, Integer::sum).unsafeRun();
  }

  /**
   * ZipWith3 three VTaskPaths in parallel.
   *
   * <p>Measures overhead of combining three paths using Par.map3 for parallel execution. All three
   * tasks execute concurrently via StructuredTaskScope.
   */
  @Benchmark
  public Integer zipWith3Execution() {
    VTaskPath<Integer> second = Path.vtaskPure(10);
    VTaskPath<Integer> third = Path.vtaskPure(20);
    return purePath.zipWith3(second, third, (a, b, c) -> a + b + c).unsafeRun();
  }

  // ========== Async Execution Benchmarks ==========

  /**
   * Async execution via runAsync().
   *
   * <p>Measures overhead of async execution and CompletableFuture creation.
   */
  @Benchmark
  public Integer runAsyncExecution() throws Exception {
    return purePath.runAsync().get();
  }

  // ========== Multiple Independent Construction ==========

  /**
   * Multiple independent VTaskPath construction.
   *
   * <p>Measures cost of creating multiple independent paths.
   */
  @Benchmark
  public void independentConstruction(Blackhole blackhole) {
    VTaskPath<Integer> path1 = Path.vtaskPure(1);
    VTaskPath<Integer> path2 = Path.vtaskPure(2);
    VTaskPath<Integer> path3 = Path.vtaskPure(3);
    blackhole.consume(path1);
    blackhole.consume(path2);
    blackhole.consume(path3);
  }
}
