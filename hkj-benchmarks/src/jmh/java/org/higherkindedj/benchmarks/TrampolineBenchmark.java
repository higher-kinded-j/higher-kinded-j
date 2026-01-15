// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.trampoline.Trampoline;
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
 * JMH benchmarks for Trampoline type operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Cost of Trampoline construction (done vs defer)
 *   <li>Execution overhead when running trampolines
 *   <li>Performance of map/flatMap chains
 *   <li>Stack safety for deep recursion (factorial, fibonacci, mutual recursion)
 *   <li>Comparison with naive recursion where applicable
 * </ul>
 *
 * <p>Note: Trampoline is lazy, so benchmarks measure both construction and execution costs
 * separately.
 *
 * <p>Run with: {@code ./gradlew jmh} or {@code ./gradlew :hkj-benchmarks:jmh}
 *
 * <p>Run specific benchmark: {@code ./gradlew jmh --includes=".*TrampolineBenchmark.*"}
 *
 * <p>Run with different depths: {@code ./gradlew jmh
 * -Pjmh.benchmarkParameters.recursionDepth=10000}
 *
 * <p>Run with GC profiling: {@code ./gradlew jmh -Pjmh.profilers=gc}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TrampolineBenchmark {

  @Param({"100"})
  private int recursionDepth;

  private Trampoline<Integer> simpleDone;
  private Trampoline<Integer> simpleDefer;

  @Setup
  public void setup() {
    simpleDone = Trampoline.done(42);
    simpleDefer = Trampoline.defer(() -> Trampoline.done(42));
  }

  // ==================== Construction Benchmarks ====================

  /**
   * Construction cost of Trampoline.done with simple value.
   *
   * <p>Measures overhead of creating a completed trampoline.
   */
  @Benchmark
  public Trampoline<Integer> constructDone() {
    return Trampoline.done(42);
  }

  /**
   * Construction cost of Trampoline.defer with simple value.
   *
   * <p>Measures overhead of creating a deferred trampoline.
   */
  @Benchmark
  public Trampoline<Integer> constructDefer() {
    return Trampoline.defer(() -> Trampoline.done(42));
  }

  // ==================== Execution Benchmarks ====================

  /**
   * Execution cost of Trampoline.done.
   *
   * <p>Measures the cost of running an already completed trampoline.
   */
  @Benchmark
  public Integer runDone() {
    return simpleDone.run();
  }

  /**
   * Execution cost of Trampoline.defer.
   *
   * <p>Measures the cost of running a deferred computation.
   */
  @Benchmark
  public Integer runDefer() {
    return simpleDefer.run();
  }

  // ==================== Map/FlatMap Benchmarks ====================

  /**
   * Map operation construction (lazy - doesn't execute).
   *
   * <p>Measures cost of building up Trampoline chains without executing.
   */
  @Benchmark
  public Trampoline<Integer> mapConstruction() {
    return simpleDone.map(x -> x + 1);
  }

  /**
   * Map operation execution.
   *
   * <p>Measures cost of running a mapped Trampoline.
   */
  @Benchmark
  public Integer mapExecution() {
    return simpleDone.map(x -> x + 1).run();
  }

  /** FlatMap operation construction. */
  @Benchmark
  public Trampoline<Integer> flatMapConstruction() {
    return simpleDone.flatMap(x -> Trampoline.done(x * 2));
  }

  /** FlatMap operation execution. */
  @Benchmark
  public Integer flatMapExecution() {
    return simpleDone.flatMap(x -> Trampoline.done(x * 2)).run();
  }

  /**
   * Long chain construction (lazy).
   *
   * <p>Measures cost of building deep Trampoline chains.
   */
  @Benchmark
  public Trampoline<Integer> longChainConstruction() {
    Trampoline<Integer> result = simpleDone;
    for (int i = 0; i < 50; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long chain execution.
   *
   * <p>Measures cost of executing deep Trampoline chains.
   */
  @Benchmark
  public Integer longChainExecution() {
    Trampoline<Integer> result = simpleDone;
    for (int i = 0; i < 50; i++) {
      result = result.map(x -> x + 1);
    }
    return result.run();
  }

  // ==================== Stack Safety Benchmarks ====================

  /**
   * Stack-safe factorial using Trampoline.
   *
   * <p>Computes factorial for {@code recursionDepth} using trampolining.
   */
  @Benchmark
  public BigInteger factorialTrampoline() {
    return factorial(BigInteger.valueOf(recursionDepth), BigInteger.ONE).run();
  }

  private static Trampoline<BigInteger> factorial(BigInteger n, BigInteger acc) {
    if (n.compareTo(BigInteger.ZERO) <= 0) {
      return Trampoline.done(acc);
    }
    return Trampoline.defer(() -> factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
  }

  /**
   * Naive recursive factorial (limited to small depths).
   *
   * <p>ONLY runs for small recursionDepth to avoid StackOverflowError.
   */
  @Benchmark
  public BigInteger factorialNaive(Blackhole blackhole) {
    if (recursionDepth <= 100) {
      return factorialNaiveImpl(BigInteger.valueOf(recursionDepth));
    } else {
      // Avoid stack overflow - return dummy value
      blackhole.consume("skipped due to stack overflow risk");
      return BigInteger.ONE;
    }
  }

  private static BigInteger factorialNaiveImpl(BigInteger n) {
    if (n.compareTo(BigInteger.ZERO) <= 0) {
      return BigInteger.ONE;
    }
    return n.multiply(factorialNaiveImpl(n.subtract(BigInteger.ONE)));
  }

  /**
   * Stack-safe Fibonacci using Trampoline.
   *
   * <p>Computes nth Fibonacci number using tail recursion with trampolining.
   */
  @Benchmark
  public BigInteger fibonacciTrampoline() {
    return fibonacci(recursionDepth, BigInteger.ZERO, BigInteger.ONE).run();
  }

  private static Trampoline<BigInteger> fibonacci(int n, BigInteger a, BigInteger b) {
    if (n == 0) return Trampoline.done(a);
    if (n == 1) return Trampoline.done(b);
    return Trampoline.defer(() -> fibonacci(n - 1, b, a.add(b)));
  }

  /**
   * Stack-safe mutual recursion using Trampoline.
   *
   * <p>Tests isEven/isOdd mutual recursion for {@code recursionDepth}.
   */
  @Benchmark
  public Boolean mutualRecursionTrampoline() {
    return isEven(recursionDepth).run();
  }

  private static Trampoline<Boolean> isEven(int n) {
    if (n == 0) return Trampoline.done(true);
    return Trampoline.defer(() -> isOdd(n - 1));
  }

  private static Trampoline<Boolean> isOdd(int n) {
    if (n == 0) return Trampoline.done(false);
    return Trampoline.defer(() -> isEven(n - 1));
  }

  /**
   * Naive mutual recursion (limited to small depths).
   *
   * <p>ONLY runs for small recursionDepth to avoid StackOverflowError.
   */
  @Benchmark
  public Boolean mutualRecursionNaive(Blackhole blackhole) {
    if (recursionDepth <= 100) {
      return isEvenNaive(recursionDepth);
    } else {
      // Avoid stack overflow - return dummy value
      blackhole.consume("skipped due to stack overflow risk");
      return false;
    }
  }

  private static boolean isEvenNaive(int n) {
    if (n == 0) return true;
    return isOddNaive(n - 1);
  }

  private static boolean isOddNaive(int n) {
    if (n == 0) return false;
    return isEvenNaive(n - 1);
  }

  // ==================== Real-World Pattern Benchmarks ====================

  /**
   * Chained operations with mixed map/flatMap.
   *
   * <p>Simulates real-world composition patterns.
   */
  @Benchmark
  public Integer chainedOperations() {
    return simpleDone
        .map(x -> x + 1)
        .flatMap(x -> Trampoline.done(x * 2))
        .map(x -> x - 5)
        .flatMap(x -> Trampoline.done(x / 3))
        .run();
  }

  /**
   * Sequential computation composition.
   *
   * <p>Measures the cost of sequential flatMap operations.
   */
  @Benchmark
  public Integer sequentialComposition() {
    return simpleDone
        .flatMap(x -> Trampoline.defer(() -> Trampoline.done(x * 2)))
        .flatMap(y -> Trampoline.defer(() -> Trampoline.done(y + 10)))
        .run();
  }

  /**
   * Nested flatMap construction.
   *
   * <p>Tests construction cost of deeply nested monadic compositions.
   */
  @Benchmark
  public Trampoline<Integer> nestedFlatMapConstruction() {
    return simpleDone
        .flatMap(x -> Trampoline.done(x + 1))
        .flatMap(x -> Trampoline.done(x * 2))
        .flatMap(x -> Trampoline.done(x - 5))
        .flatMap(x -> Trampoline.done(x / 3));
  }

  /**
   * Nested flatMap execution.
   *
   * <p>Tests execution cost of deeply nested monadic compositions.
   */
  @Benchmark
  public Integer nestedFlatMapExecution() {
    return simpleDone
        .flatMap(x -> Trampoline.done(x + 1))
        .flatMap(x -> Trampoline.done(x * 2))
        .flatMap(x -> Trampoline.done(x - 5))
        .flatMap(x -> Trampoline.done(x / 3))
        .run();
  }

  /**
   * Stack safety test - very deep recursion.
   *
   * <p>Tests if Trampoline can handle deep recursive chains (based on recursionDepth parameter).
   */
  @Benchmark
  public Integer deepRecursion() {
    return countDown(recursionDepth).run();
  }

  private static Trampoline<Integer> countDown(int n) {
    if (n <= 0) return Trampoline.done(0);
    return Trampoline.defer(() -> countDown(n - 1));
  }

  /**
   * Countdown with accumulator.
   *
   * <p>Tests tail-recursive pattern with accumulator.
   */
  @Benchmark
  public Integer countDownWithAccumulator() {
    return countDownAcc(recursionDepth, 0).run();
  }

  private static Trampoline<Integer> countDownAcc(int n, int acc) {
    if (n <= 0) return Trampoline.done(acc);
    return Trampoline.defer(() -> countDownAcc(n - 1, acc + n));
  }

  /**
   * Sum of range using Trampoline.
   *
   * <p>Computes sum of numbers from 0 to recursionDepth.
   */
  @Benchmark
  public Long sumRangeTrampoline() {
    return sumRange(recursionDepth, 0L).run();
  }

  private static Trampoline<Long> sumRange(int n, long acc) {
    if (n <= 0) return Trampoline.done(acc);
    return Trampoline.defer(() -> sumRange(n - 1, acc + n));
  }

  /**
   * Multiple independent Trampoline construction.
   *
   * <p>Measures cost of creating multiple independent trampolines.
   */
  @Benchmark
  public void independentConstruction(Blackhole blackhole) {
    Trampoline<Integer> t1 = Trampoline.done(1);
    Trampoline<Integer> t2 = Trampoline.defer(() -> Trampoline.done(2));
    Trampoline<Integer> t3 = Trampoline.done(3);
    blackhole.consume(t1);
    blackhole.consume(t2);
    blackhole.consume(t3);
  }

  /**
   * Mixed defer operations with different complexities.
   *
   * <p>Simulates varying computation patterns.
   */
  @Benchmark
  public Integer mixedDeferOperations() {
    return Trampoline.defer(() -> Trampoline.done(10))
        .flatMap(x -> Trampoline.defer(() -> Trampoline.done(x + 5)))
        .flatMap(x -> Trampoline.defer(() -> Trampoline.done(x * 2)))
        .flatMap(x -> Trampoline.defer(() -> Trampoline.done(x - 3)))
        .run();
  }

  /**
   * Real-world pattern: recursive tree traversal simulation.
   *
   * <p>Simulates depth-first traversal of a conceptual tree.
   */
  @Benchmark
  public Integer treeTraversalSimulation() {
    return traverseTree(recursionDepth / 10, 0).run();
  }

  private static Trampoline<Integer> traverseTree(int depth, int acc) {
    if (depth <= 0) return Trampoline.done(acc);
    // Simulate processing two child nodes
    return Trampoline.defer(() -> traverseTree(depth - 1, acc + 1))
        .flatMap(left -> Trampoline.defer(() -> traverseTree(depth - 1, left + 1)));
  }
}
