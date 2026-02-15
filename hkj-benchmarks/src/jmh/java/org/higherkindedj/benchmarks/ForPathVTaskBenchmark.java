// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * JMH benchmarks for ForPath comprehensions with VTaskPath.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Overhead of ForPath comprehension construction
 *   <li>Execution cost of comprehensions with different step counts
 *   <li>Comparison of ForPath vs direct VTaskPath chaining
 *   <li>Performance of let() vs from() operations
 *   <li>Real-world workflow patterns
 * </ul>
 *
 * <p>ForPath provides a readable for-comprehension syntax for VTaskPath operations. This benchmark
 * suite validates that the abstraction overhead is acceptable while providing compositional
 * benefits.
 *
 * <p>Run with: {@code ./gradlew jmh --includes=".*ForPathVTaskBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class ForPathVTaskBenchmark {

  @Param({"50"})
  private int chainDepth;

  private VTaskPath<Integer> purePath;

  @Setup
  public void setup() {
    purePath = Path.vtaskPure(42);
  }

  // ========== Construction Benchmarks ==========

  /**
   * Baseline: Direct VTaskPath chaining with via().
   *
   * <p>Measures the cost of direct VTaskPath composition without ForPath.
   */
  @Benchmark
  public Integer baseline_directVia() {
    return purePath.via(a -> Path.vtaskPure(a * 2)).unsafeRun();
  }

  /**
   * ForPath two-step comprehension.
   *
   * <p>Measures overhead of ForPath vs direct chaining for simple case.
   */
  @Benchmark
  public Integer forPath_twoStep() {
    return ForPath.from(purePath).from(a -> Path.vtaskPure(a * 2)).yield((a, b) -> b).unsafeRun();
  }

  /**
   * ForPath three-step comprehension.
   *
   * <p>Measures cost of building up tuple state across multiple steps.
   */
  @Benchmark
  public Integer forPath_threeStep() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._2() + 10))
        .yield((a, b, c) -> c)
        .unsafeRun();
  }

  /**
   * ForPath four-step comprehension.
   *
   * <p>Measures scaling cost as step count increases.
   */
  @Benchmark
  public Integer forPath_fourStep() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._2() + 10))
        .from(t -> Path.vtaskPure(t._3() * 3))
        .yield((a, b, c, d) -> d)
        .unsafeRun();
  }

  /**
   * ForPath five-step comprehension.
   *
   * <p>Measures maximum supported step count.
   */
  @Benchmark
  public Integer forPath_fiveStep() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._2() + 10))
        .from(t -> Path.vtaskPure(t._3() * 3))
        .from(t -> Path.vtaskPure(t._4() - 5))
        .yield((a, b, c, d, e) -> e)
        .unsafeRun();
  }

  // ========== let() vs from() Benchmarks ==========

  /**
   * ForPath with let() for pure computation.
   *
   * <p>Measures cost of let() which doesn't wrap in VTaskPath.
   */
  @Benchmark
  public Integer forPath_withLet() {
    return ForPath.from(purePath)
        .let(a -> a * 2)
        .let(t -> t._2() + 10)
        .yield((a, b, c) -> c)
        .unsafeRun();
  }

  /**
   * ForPath with from() for same computations.
   *
   * <p>Measures additional cost of wrapping pure computations in VTaskPath.
   */
  @Benchmark
  public Integer forPath_withFrom() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._2() + 10))
        .yield((a, b, c) -> c)
        .unsafeRun();
  }

  /**
   * Mixed let() and from() operations.
   *
   * <p>Measures realistic mixed usage pattern.
   */
  @Benchmark
  public Integer forPath_mixedLetFrom() {
    return ForPath.from(purePath)
        .let(a -> a * 2)
        .from(t -> Path.vtaskPure(t._2() + 10))
        .let(t -> t._3() * 3)
        .yield((a, b, c, d) -> d)
        .unsafeRun();
  }

  // ========== Comparison: ForPath vs Direct Chaining ==========

  /**
   * Direct VTaskPath chain (3 steps).
   *
   * <p>Baseline for comparing ForPath overhead.
   */
  @Benchmark
  public Integer direct_threeStepChain() {
    return purePath.via(a -> Path.vtaskPure(a * 2)).via(b -> Path.vtaskPure(b + 10)).unsafeRun();
  }

  /**
   * ForPath equivalent (3 steps, same computation).
   *
   * <p>Compare to direct_threeStepChain to measure ForPath overhead.
   */
  @Benchmark
  public Integer forPath_threeStepEquivalent() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._2() + 10))
        .yield((a, b, c) -> c)
        .unsafeRun();
  }

  /**
   * Direct VTaskPath chain with value combination.
   *
   * <p>When needing to combine values from different steps, direct chaining requires manual tuple
   * handling or nested closures.
   */
  @Benchmark
  public Integer direct_valueCombination() {
    return purePath
        .via(
            a ->
                Path.vtaskPure(a * 2)
                    .via(b -> Path.vtaskPure(a + b + 10))) // Nested closure to access 'a'
        .unsafeRun();
  }

  /**
   * ForPath equivalent with value combination.
   *
   * <p>ForPath provides cleaner access to all previous values via tuples.
   */
  @Benchmark
  public Integer forPath_valueCombination() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._1() + t._2() + 10)) // Clean tuple access
        .yield((a, b, c) -> c)
        .unsafeRun();
  }

  // ========== Real-World Pattern Benchmarks ==========

  /**
   * Simulated service workflow.
   *
   * <p>Measures realistic service call pattern with ForPath.
   */
  @Benchmark
  public String forPath_serviceWorkflow() {
    VTaskPath<String> fetchUserId = Path.vtaskPure("user-123");
    VTaskPath<String> fetchUserName = Path.vtaskPure("Alice");
    VTaskPath<Integer> fetchOrderCount = Path.vtaskPure(5);

    return ForPath.from(fetchUserId)
        .from(id -> fetchUserName)
        .from(t -> fetchOrderCount)
        .yield((id, name, orders) -> name + " has " + orders + " orders")
        .unsafeRun();
  }

  /**
   * Direct equivalent of service workflow.
   *
   * <p>Compare to forPath_serviceWorkflow for overhead measurement.
   */
  @Benchmark
  public String direct_serviceWorkflow() {
    VTaskPath<String> fetchUserId = Path.vtaskPure("user-123");
    VTaskPath<String> fetchUserName = Path.vtaskPure("Alice");
    VTaskPath<Integer> fetchOrderCount = Path.vtaskPure(5);

    return fetchUserId
        .via(
            id ->
                fetchUserName.via(
                    name -> fetchOrderCount.map(orders -> name + " has " + orders + " orders")))
        .unsafeRun();
  }

  /**
   * Calculation workflow with let().
   *
   * <p>Measures pattern where intermediate calculations are needed.
   */
  @Benchmark
  public Double forPath_calculationWorkflow() {
    VTaskPath<Double> fetchPrice = Path.vtaskPure(100.0);
    VTaskPath<Integer> fetchQuantity = Path.vtaskPure(5);

    return ForPath.from(fetchPrice)
        .from(price -> fetchQuantity)
        .let(t -> t._1() * t._2()) // subtotal
        .let(t -> t._3() * 0.1) // tax
        .let(t -> t._3() + t._4()) // total
        .yield((price, qty, subtotal, tax, total) -> total)
        .unsafeRun();
  }

  // ========== Construction-Only Benchmarks ==========

  /**
   * Construction of ForPath (no execution).
   *
   * <p>Measures pure construction overhead without virtual thread execution.
   */
  @Benchmark
  public VTaskPath<Integer> forPath_constructionOnly() {
    return ForPath.from(purePath)
        .from(a -> Path.vtaskPure(a * 2))
        .from(t -> Path.vtaskPure(t._2() + 10))
        .yield((a, b, c) -> c);
  }

  /**
   * Direct chain construction (no execution).
   *
   * <p>Compare to forPath_constructionOnly for pure construction overhead.
   */
  @Benchmark
  public VTaskPath<Integer> direct_constructionOnly() {
    return purePath.via(a -> Path.vtaskPure(a * 2)).via(b -> Path.vtaskPure(b + 10));
  }
}
