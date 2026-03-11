// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
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
 * JMH benchmarks for ForPath comprehensions with VTaskPath.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Overhead of ForPath comprehension construction
 *   <li>Execution cost of comprehensions with different step counts
 *   <li>Comparison of ForPath vs direct VTaskPath chaining
 *   <li>Performance of let() vs from() operations
 *   <li>Parallel composition with par() vs sequential from()
 *   <li>ForPath.par() overhead vs direct Par.map2/map3
 *   <li>Real-world workflow patterns
 * </ul>
 *
 * <p>ForPath provides a readable for-comprehension syntax for VTaskPath operations. This benchmark
 * suite validates that the abstraction overhead is acceptable while providing compositional
 * benefits.
 *
 * <p>Run with: {@code ./gradlew jmh -Pincludes=".*ForPathVTaskBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class ForPathVTaskBenchmark {

  @Param({"50"})
  private int chainDepth;

  private VTaskPath<Integer> purePath;
  private VTaskPath<Integer> purePathA;
  private VTaskPath<Integer> purePathB;
  private VTaskPath<Integer> purePathC;
  private VTask<Integer> rawTaskA;
  private VTask<Integer> rawTaskB;
  private VTask<Integer> rawTaskC;

  @Setup
  public void setup() {
    purePath = Path.vtaskPure(42);
    purePathA = Path.vtaskPure(1);
    purePathB = Path.vtaskPure(2);
    purePathC = Path.vtaskPure(3);
    rawTaskA = VTask.succeed(1);
    rawTaskB = VTask.succeed(2);
    rawTaskC = VTask.succeed(3);
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

  // ========== par() Parallel Composition Benchmarks ==========

  /**
   * ForPath.par(2) with VTaskPath.
   *
   * <p>Measures applicative parallel composition overhead via ForPath.par().
   */
  @Benchmark
  public Integer forPath_par2() {
    return ForPath.par(purePathA, purePathB).yield((a, b) -> a + b).unsafeRun();
  }

  /**
   * ForPath.par(3) with VTaskPath.
   *
   * <p>Measures applicative parallel composition with three inputs.
   */
  @Benchmark
  public Integer forPath_par3() {
    return ForPath.par(purePathA, purePathB, purePathC).yield((a, b, c) -> a + b + c).unsafeRun();
  }

  /**
   * Direct Par.map2 without ForPath wrapper.
   *
   * <p>Baseline for measuring ForPath.par() comprehension overhead.
   */
  @Benchmark
  public Integer direct_parMap2() {
    return Par.map2(rawTaskA, rawTaskB, Integer::sum).run();
  }

  /**
   * Direct Par.map3 without ForPath wrapper.
   *
   * <p>Baseline for measuring ForPath.par(3) comprehension overhead.
   */
  @Benchmark
  public Integer direct_parMap3() {
    return Par.map3(rawTaskA, rawTaskB, rawTaskC, (a, b, c) -> a + b + c).run();
  }

  /**
   * Sequential from() for same independent computations as par(2).
   *
   * <p>Compare to forPath_par2 to measure the benefit of applicative vs monadic composition. For
   * VTaskPath, par() enables true parallelism while from() forces sequential execution.
   */
  @Benchmark
  public Integer forPath_from2_sequential() {
    return ForPath.from(purePathA).from(a -> purePathB).yield((a, b) -> a + b).unsafeRun();
  }

  /**
   * Sequential from() for same independent computations as par(3).
   *
   * <p>Compare to forPath_par3 to measure the benefit of applicative vs monadic composition.
   */
  @Benchmark
  public Integer forPath_from3_sequential() {
    return ForPath.from(purePathA)
        .from(a -> purePathB)
        .from(t -> purePathC)
        .yield((a, b, c) -> a + b + c)
        .unsafeRun();
  }

  /**
   * par(2) followed by let() and from() chaining.
   *
   * <p>Measures realistic pattern: parallel fan-out then sequential processing.
   */
  @Benchmark
  public Integer forPath_par2_thenChain() {
    return ForPath.par(purePathA, purePathB)
        .let(t -> t._1() + t._2())
        .from(t -> Path.vtaskPure(t._3() * 10))
        .yield((a, b, sum, scaled) -> scaled)
        .unsafeRun();
  }

  /**
   * Equivalent workflow without par: all sequential.
   *
   * <p>Compare to forPath_par2_thenChain for overhead measurement.
   */
  @Benchmark
  public Integer forPath_from_thenChain() {
    return ForPath.from(purePathA)
        .from(a -> purePathB)
        .let(t -> t._1() + t._2())
        .from(t -> Path.vtaskPure(t._3() * 10))
        .yield((a, b, sum, scaled) -> scaled)
        .unsafeRun();
  }

  /**
   * par(2) construction only (no execution).
   *
   * <p>Isolates the comprehension wrapper construction cost from virtual thread execution.
   */
  @Benchmark
  public VTaskPath<Integer> forPath_par2_constructionOnly() {
    return ForPath.par(purePathA, purePathB).yield((a, b) -> a + b);
  }

  /**
   * par(3) construction only (no execution).
   *
   * <p>Isolates the comprehension wrapper construction cost.
   */
  @Benchmark
  public VTaskPath<Integer> forPath_par3_constructionOnly() {
    return ForPath.par(purePathA, purePathB, purePathC).yield((a, b, c) -> a + b + c);
  }

  // ========== par() Service Workflow Benchmarks ==========

  /**
   * Service workflow using par() for independent fetches.
   *
   * <p>Realistic pattern: fetch user and config in parallel, then combine.
   */
  @Benchmark
  public String forPath_par_serviceWorkflow() {
    VTaskPath<String> fetchUser = Path.vtaskPure("Alice");
    VTaskPath<Integer> fetchOrderCount = Path.vtaskPure(5);

    return ForPath.par(fetchUser, fetchOrderCount)
        .yield((name, orders) -> name + " has " + orders + " orders")
        .unsafeRun();
  }

  /**
   * Same service workflow using sequential from().
   *
   * <p>Compare to forPath_par_serviceWorkflow for overhead difference.
   */
  @Benchmark
  public String forPath_from_serviceWorkflow() {
    VTaskPath<String> fetchUser = Path.vtaskPure("Alice");
    VTaskPath<Integer> fetchOrderCount = Path.vtaskPure(5);

    return ForPath.from(fetchUser)
        .from(name -> fetchOrderCount)
        .yield((name, orders) -> name + " has " + orders + " orders")
        .unsafeRun();
  }
}
