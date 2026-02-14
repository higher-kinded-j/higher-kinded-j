// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

/**
 * JMH benchmarks for Par parallel combinators.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Parallel zip overhead
 *   <li>Parallel vs sequential comparison
 *   <li>Race combinator overhead
 *   <li>All combinator overhead
 *   <li>Traverse at various sizes
 *   <li>High concurrency scenarios
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*VTaskParBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class VTaskParBenchmark {

  @Param({"50"})
  private int listSize;

  private VTask<Integer> taskA;
  private VTask<Integer> taskB;
  private VTask<Integer> taskC;
  private List<VTask<Integer>> smallTaskList;
  private List<Integer> items;

  @Setup
  public void setup() {
    taskA = VTask.succeed(1);
    taskB = VTask.succeed(2);
    taskC = VTask.succeed(3);

    smallTaskList = new ArrayList<>();
    for (int i = 0; i < listSize; i++) {
      smallTaskList.add(VTask.succeed(i));
    }

    items = new ArrayList<>();
    for (int i = 0; i < listSize; i++) {
      items.add(i);
    }
  }

  // ========== Zip Operations ==========

  /**
   * Parallel zip of two tasks.
   *
   * <p>Measures overhead of StructuredTaskScope for parallel execution.
   */
  @Benchmark
  public Par.Tuple2<Integer, Integer> zipTwoTasks() {
    return Par.zip(taskA, taskB).run();
  }

  /**
   * Sequential equivalent of zip.
   *
   * <p>Provides baseline for comparison with parallel version.
   */
  @Benchmark
  public Par.Tuple2<Integer, Integer> sequentialZipEquivalent() {
    Integer a = taskA.run();
    Integer b = taskB.run();
    return new Par.Tuple2<>(a, b);
  }

  /** Parallel zip of three tasks. */
  @Benchmark
  public Par.Tuple3<Integer, Integer, Integer> zip3Tasks() {
    return Par.zip3(taskA, taskB, taskC).run();
  }

  // ========== Map2/Map3 Operations ==========

  /** Parallel map2 combining two results. */
  @Benchmark
  public Integer map2Tasks() {
    return Par.map2(taskA, taskB, Integer::sum).run();
  }

  /** Sequential equivalent of map2. */
  @Benchmark
  public Integer sequentialMap2Equivalent() {
    Integer a = taskA.run();
    Integer b = taskB.run();
    return a + b;
  }

  /** Parallel map3 combining three results. */
  @Benchmark
  public Integer map3Tasks() {
    return Par.map3(taskA, taskB, taskC, (a, b, c) -> a + b + c).run();
  }

  // ========== All Operation ==========

  /**
   * Parallel all collecting results.
   *
   * <p>Uses parameterized list size.
   */
  @Benchmark
  public List<Integer> allTasks() {
    return Par.all(smallTaskList).run();
  }

  /** Sequential equivalent of all. */
  @Benchmark
  public List<Integer> sequentialAllEquivalent() {
    List<Integer> results = new ArrayList<>(smallTaskList.size());
    for (VTask<Integer> task : smallTaskList) {
      results.add(task.run());
    }
    return results;
  }

  // ========== Traverse Operation ==========

  /**
   * Parallel traverse applying function.
   *
   * <p>Uses parameterized list size.
   */
  @Benchmark
  public List<Integer> traverseList() {
    return Par.traverse(items, i -> VTask.succeed(i * 2)).run();
  }

  /** Sequential equivalent of traverse. */
  @Benchmark
  public List<Integer> sequentialTraverseEquivalent() {
    List<Integer> results = new ArrayList<>(items.size());
    for (Integer item : items) {
      results.add(VTask.succeed(item * 2).run());
    }
    return results;
  }

  // ========== Race Operation ==========

  /**
   * Race with immediate winner.
   *
   * <p>All tasks succeed immediately; measures race combinator overhead.
   */
  @Benchmark
  public Integer raceWithImmediateWinner() {
    List<VTask<Integer>> tasks = List.of(VTask.succeed(1), VTask.succeed(2), VTask.succeed(3));
    return Par.race(tasks).run();
  }

  // ========== High Concurrency ==========

  /**
   * High concurrency with many tasks.
   *
   * <p>Demonstrates virtual thread scalability.
   */
  @Benchmark
  public List<Integer> highConcurrencyAll() {
    List<VTask<Integer>> tasks = new ArrayList<>(listSize);
    for (int i = 0; i < listSize; i++) {
      final int val = i;
      tasks.add(VTask.succeed(val));
    }
    return Par.all(tasks).run();
  }

  /** High concurrency traverse. */
  @Benchmark
  public List<Integer> highConcurrencyTraverse() {
    return Par.traverse(items, i -> VTask.succeed(i * 2)).run();
  }
}
