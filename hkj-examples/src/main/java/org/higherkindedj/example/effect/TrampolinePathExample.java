// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.math.BigInteger;
import org.higherkindedj.hkt.effect.TrampolinePath;

/**
 * Examples demonstrating TrampolinePath for stack-safe recursive computations.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Stack-safe recursion using {@code done} and {@code defer}
 *   <li>Classic recursive algorithms: factorial, fibonacci
 *   <li>Mutual recursion (isEven/isOdd) without stack overflow
 *   <li>Deep recursion that would overflow with regular calls
 *   <li>Converting TrampolinePath to other path types
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.TrampolinePathExample}
 */
public class TrampolinePathExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: TrampolinePath ===\n");

    basicTrampolineExamples();
    factorialExample();
    fibonacciExample();
    mutualRecursionExample();
    deepRecursionDemo();
    chainedOperationsExample();
    conversionExample();
  }

  private static void basicTrampolineExamples() {
    System.out.println("--- Basic Trampoline Usage ---");

    // done() - immediate value (no recursion needed)
    TrampolinePath<Integer> immediate = TrampolinePath.done(42);
    System.out.println("Immediate value: " + immediate.run());

    // pure() - alias for done()
    TrampolinePath<String> pure = TrampolinePath.pure("Hello, Trampoline!");
    System.out.println("Pure value: " + pure.run());

    // defer() - deferred computation (key to stack safety)
    TrampolinePath<Integer> deferred = TrampolinePath.defer(() -> TrampolinePath.done(100));
    System.out.println("Deferred value: " + deferred.run());

    // Nested defer - still evaluates correctly
    TrampolinePath<Integer> nested =
        TrampolinePath.defer(
            () -> TrampolinePath.defer(() -> TrampolinePath.defer(() -> TrampolinePath.done(999))));
    System.out.println("Nested deferred: " + nested.run());

    System.out.println();
  }

  private static void factorialExample() {
    System.out.println("--- Stack-Safe Factorial ---");

    // Regular recursion would overflow for large n
    // TrampolinePath makes it stack-safe

    System.out.println("factorial(5) = " + factorial(5).run());
    System.out.println("factorial(10) = " + factorial(10).run());
    System.out.println("factorial(20) = " + factorial(20).run());

    // This would cause StackOverflowError with regular recursion!
    BigInteger result = factorialBig(BigInteger.valueOf(5000), BigInteger.ONE).run();
    System.out.println("factorial(5000) = " + result.toString().length() + " digits");

    System.out.println();
  }

  /**
   * Stack-safe factorial using TrampolinePath.
   *
   * <p>The key insight: instead of making a direct recursive call, we wrap it in {@code defer}.
   * This creates a "thunk" that will be evaluated by the trampoline loop, not the call stack.
   */
  private static TrampolinePath<Long> factorial(long n) {
    return factorialAcc(n, 1L);
  }

  private static TrampolinePath<Long> factorialAcc(long n, long acc) {
    if (n <= 1) {
      return TrampolinePath.done(acc);
    }
    // defer() prevents stack growth - the recursive call is wrapped
    return TrampolinePath.defer(() -> factorialAcc(n - 1, n * acc));
  }

  /** BigInteger version for very large factorials. */
  private static TrampolinePath<BigInteger> factorialBig(BigInteger n, BigInteger acc) {
    if (n.compareTo(BigInteger.ONE) <= 0) {
      return TrampolinePath.done(acc);
    }
    return TrampolinePath.defer(() -> factorialBig(n.subtract(BigInteger.ONE), n.multiply(acc)));
  }

  private static void fibonacciExample() {
    System.out.println("--- Stack-Safe Fibonacci ---");

    // Efficient tail-recursive fibonacci
    System.out.println("fibonacci(10) = " + fibonacci(10).run());
    System.out.println("fibonacci(50) = " + fibonacci(50).run());

    // Large fibonacci that would be impossible with naive recursion
    BigInteger fib100 = fibonacciBig(100).run();
    System.out.println("fibonacci(100) = " + fib100);

    System.out.println();
  }

  private static TrampolinePath<Long> fibonacci(int n) {
    return fibAcc(n, 0L, 1L);
  }

  private static TrampolinePath<Long> fibAcc(int n, long a, long b) {
    if (n == 0) {
      return TrampolinePath.done(a);
    }
    return TrampolinePath.defer(() -> fibAcc(n - 1, b, a + b));
  }

  private static TrampolinePath<BigInteger> fibonacciBig(int n) {
    return fibAccBig(n, BigInteger.ZERO, BigInteger.ONE);
  }

  private static TrampolinePath<BigInteger> fibAccBig(int n, BigInteger a, BigInteger b) {
    if (n == 0) {
      return TrampolinePath.done(a);
    }
    return TrampolinePath.defer(() -> fibAccBig(n - 1, b, a.add(b)));
  }

  private static void mutualRecursionExample() {
    System.out.println("--- Mutual Recursion: isEven/isOdd ---");

    // Classic example: isEven and isOdd call each other
    // Without trampolining, this would overflow for large n

    System.out.println("isEven(0) = " + isEven(0).run());
    System.out.println("isOdd(0) = " + isOdd(0).run());
    System.out.println("isEven(5) = " + isEven(5).run());
    System.out.println("isOdd(5) = " + isOdd(5).run());
    System.out.println("isEven(100) = " + isEven(100).run());

    // This would StackOverflow without trampolining!
    System.out.println("isEven(100000) = " + isEven(100000).run());
    System.out.println("isOdd(100000) = " + isOdd(100000).run());

    System.out.println();
  }

  /** Stack-safe mutual recursion. isEven calls isOdd, isOdd calls isEven. */
  private static TrampolinePath<Boolean> isEven(int n) {
    if (n == 0) {
      return TrampolinePath.done(true);
    }
    return TrampolinePath.defer(() -> isOdd(n - 1));
  }

  private static TrampolinePath<Boolean> isOdd(int n) {
    if (n == 0) {
      return TrampolinePath.done(false);
    }
    return TrampolinePath.defer(() -> isEven(n - 1));
  }

  private static void deepRecursionDemo() {
    System.out.println("--- Deep Recursion Demo ---");

    // Count from 0 to target using recursion
    // Regular recursion would overflow around 10,000-20,000 depending on JVM

    int target = 100_000;
    System.out.println("Counting to " + target + " using trampolined recursion...");

    long start = System.currentTimeMillis();
    int result = countTo(0, target).run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("Result: " + result);
    System.out.println("Time: " + elapsed + "ms");
    System.out.println("No stack overflow!");

    System.out.println();
  }

  private static TrampolinePath<Integer> countTo(int current, int target) {
    if (current >= target) {
      return TrampolinePath.done(current);
    }
    return TrampolinePath.defer(() -> countTo(current + 1, target));
  }

  private static void chainedOperationsExample() {
    System.out.println("--- Chained Operations ---");

    // TrampolinePath supports map, via, zipWith like other paths

    TrampolinePath<Integer> computation =
        TrampolinePath.done(10)
            .map(x -> x * 2) // 20
            .map(x -> x + 5) // 25
            .map(x -> x / 5); // 5

    System.out.println("Chained map: 10 -> *2 -> +5 -> /5 = " + computation.run());

    // Using via for dependent computations
    TrampolinePath<String> viaChain =
        TrampolinePath.done(100)
            .via(n -> TrampolinePath.done("Number: " + n))
            .via(s -> TrampolinePath.done(s.toUpperCase()));

    System.out.println("Chained via: " + viaChain.run());

    // Using zipWith to combine results
    TrampolinePath<Integer> a = TrampolinePath.done(10);
    TrampolinePath<Integer> b = TrampolinePath.done(20);
    TrampolinePath<Integer> sum = a.zipWith(b, Integer::sum);

    System.out.println("zipWith (10 + 20): " + sum.run());

    // Using then to sequence computations
    TrampolinePath<String> sequence =
        TrampolinePath.done("first")
            .peek(s -> System.out.println("  Computed: " + s))
            .then(() -> TrampolinePath.done("second"));

    System.out.println("Sequenced result: " + sequence.run());

    System.out.println();
  }

  private static void conversionExample() {
    System.out.println("--- Converting to Other Path Types ---");

    TrampolinePath<Long> trampoline = factorial(10);

    // Convert to IOPath for side effects
    var ioPath = trampoline.toIOPath();
    System.out.println("As IOPath: " + ioPath.unsafeRun());

    // Convert to LazyPath for memoization
    var lazyPath = trampoline.toLazyPath();
    System.out.println("As LazyPath: " + lazyPath.get());

    // The LazyPath caches the result
    System.out.println("LazyPath (cached): " + lazyPath.get());

    System.out.println();
  }
}
