// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.vtask.VTask;
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
 * JMH benchmarks for VTask type operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Cost of VTask construction and composition (lazy)
 *   <li>Execution overhead when running VTask on virtual threads
 *   <li>Performance of map/flatMap chains
 *   <li>Stack safety for deep recursion
 *   <li>Virtual thread execution overhead
 * </ul>
 *
 * <p>Note: VTask is lazy and executes on virtual threads, so benchmarks measure both construction
 * and execution costs separately.
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VTaskBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VTaskBenchmark {

  @Param({"50"})
  private int chainDepth;

  @Param({"50"})
  private int recursionDepth;

  private VTask<Integer> pureVTask;
  private VTask<Integer> delayedVTask;
  private int counter;

  @Setup
  public void setup() {
    pureVTask = VTask.succeed(42);
    delayedVTask = VTask.delay(() -> 42);
    counter = 0;
  }

  /**
   * Construction cost of VTask.succeed with simple value.
   *
   * <p>Measures overhead of creating a pure VTask value.
   */
  @Benchmark
  public VTask<Integer> constructSucceed() {
    return VTask.succeed(42);
  }

  /**
   * Construction cost of VTask.delay with simple value.
   *
   * <p>Measures overhead of creating a delayed VTask value.
   */
  @Benchmark
  public VTask<Integer> constructDelay() {
    return VTask.delay(() -> 42);
  }

  /**
   * Construction cost of VTask.of with Callable.
   *
   * <p>Measures overhead of creating a VTask from a Callable.
   */
  @Benchmark
  public VTask<Integer> constructOf() {
    return VTask.of(() -> 42);
  }

  /**
   * Execution cost of VTask.succeed.
   *
   * <p>Measures the cost of running a pure VTask on a virtual thread.
   */
  @Benchmark
  public Integer runSucceed() {
    return pureVTask.run();
  }

  /**
   * Execution cost of VTask.delay.
   *
   * <p>Measures the cost of running a delayed VTask on a virtual thread.
   */
  @Benchmark
  public Integer runDelay() {
    return delayedVTask.run();
  }

  /**
   * Map operation construction (lazy - doesn't execute).
   *
   * <p>Measures cost of building up VTask chains without executing.
   */
  @Benchmark
  public VTask<Integer> mapConstruction() {
    return pureVTask.map(x -> x + 1);
  }

  /**
   * Map operation execution.
   *
   * <p>Measures cost of running a mapped VTask.
   */
  @Benchmark
  public Integer mapExecution() {
    return pureVTask.map(x -> x + 1).run();
  }

  /** FlatMap operation construction. */
  @Benchmark
  public VTask<Integer> flatMapConstruction() {
    return pureVTask.flatMap(x -> VTask.succeed(x * 2));
  }

  /** FlatMap operation execution. */
  @Benchmark
  public Integer flatMapExecution() {
    return pureVTask.flatMap(x -> VTask.succeed(x * 2)).run();
  }

  /**
   * Long chain construction (lazy).
   *
   * <p>Measures cost of building deep VTask chains.
   */
  @Benchmark
  public VTask<Integer> longChainConstruction() {
    VTask<Integer> result = pureVTask;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long chain execution.
   *
   * <p>Measures cost of executing deep VTask chains.
   */
  @Benchmark
  public Integer longChainExecution() {
    VTask<Integer> result = pureVTask;
    for (int i = 0; i < chainDepth; i++) {
      result = result.map(x -> x + 1);
    }
    return result.run();
  }

  /** Chained operations with mixed map/flatMap. */
  @Benchmark
  public Integer chainedOperations() {
    return pureVTask
        .map(x -> x + 1)
        .flatMap(x -> VTask.succeed(x * 2))
        .map(x -> x - 5)
        .flatMap(x -> VTask.succeed(x / 3))
        .run();
  }

  /**
   * Sequential computation composition.
   *
   * <p>Measures the cost of sequential flatMap operations.
   */
  @Benchmark
  public Integer sequentialComposition() {
    return pureVTask.flatMap(x -> VTask.succeed(x * 2)).flatMap(y -> VTask.succeed(y + 10)).run();
  }

  /** Nested flatMap construction. */
  @Benchmark
  public VTask<Integer> nestedFlatMapConstruction() {
    return pureVTask
        .flatMap(x -> VTask.succeed(x + 1))
        .flatMap(x -> VTask.succeed(x * 2))
        .flatMap(x -> VTask.succeed(x - 5))
        .flatMap(x -> VTask.succeed(x / 3));
  }

  /** Nested flatMap execution. */
  @Benchmark
  public Integer nestedFlatMapExecution() {
    return pureVTask
        .flatMap(x -> VTask.succeed(x + 1))
        .flatMap(x -> VTask.succeed(x * 2))
        .flatMap(x -> VTask.succeed(x - 5))
        .flatMap(x -> VTask.succeed(x / 3))
        .run();
  }

  /**
   * Stack safety test - deep recursion.
   *
   * <p>Tests if VTask can handle deep recursive chains without stack overflow.
   */
  @Benchmark
  public Integer deepRecursion() {
    VTask<Integer> result = VTask.succeed(0);
    for (int i = 0; i < recursionDepth; i++) {
      result = result.flatMap(x -> VTask.succeed(x + 1));
    }
    return result.run();
  }

  /**
   * Real-world pattern: resource acquisition and cleanup simulation.
   *
   * <p>Simulates acquiring a resource, using it, and cleaning up.
   */
  @Benchmark
  public String realWorldResourcePattern(Blackhole blackhole) {
    VTask<String> program =
        VTask.succeed("resource")
            .flatMap(
                resource -> {
                  blackhole.consume(resource);
                  return VTask.succeed("used: " + resource);
                })
            .flatMap(result -> VTask.succeed("cleaned up: " + result));

    return program.run();
  }

  /**
   * Simple computation overhead.
   *
   * <p>Measures cost of simple map operations in VTask.
   */
  @Benchmark
  public Integer simpleComputation() {
    return VTask.succeed(42).map(x -> x * 2).run();
  }

  /**
   * Multiple independent VTask construction.
   *
   * <p>Measures cost of creating multiple independent VTasks.
   */
  @Benchmark
  public void independentConstruction(Blackhole blackhole) {
    VTask<Integer> task1 = VTask.succeed(1);
    VTask<Integer> task2 = VTask.succeed(2);
    VTask<Integer> task3 = VTask.succeed(3);
    blackhole.consume(task1);
    blackhole.consume(task2);
    blackhole.consume(task3);
  }

  /** runSafe execution. */
  @Benchmark
  public Object runSafeExecution() {
    return pureVTask.runSafe();
  }

  /** Error recovery. */
  @Benchmark
  public Integer errorRecovery() {
    return VTask.<Integer>fail(new RuntimeException("error")).recover(e -> -1).run();
  }
}
