// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trampoline;

import java.math.BigInteger;
import org.higherkindedj.hkt.trampoline.Trampoline;

/**
 * Demonstrates the usage of {@link Trampoline} for stack-safe recursive computations.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Convert recursive algorithms to stack-safe trampolined versions
 *   <li>Handle mutual recursion without stack overflow
 *   <li>Process large numbers that would otherwise cause stack overflow
 *   <li>Compose trampolined computations using map and flatMap
 * </ul>
 */
public class TrampolineExample {

  public static void main(String[] args) {
    System.out.println("=== Trampoline Examples ===\n");

    // Example 1: Stack-safe factorial
    factorialExample();

    // Example 2: Mutual recursion (even/odd)
    mutualRecursionExample();

    // Example 3: Fibonacci
    fibonacciExample();

    // Example 4: Using map and flatMap
    compositionExample();

    // Example 5: Countdown
    countdownExample();
  }

  // ============================================================================
  // Example 1: Stack-Safe Factorial
  // ============================================================================

  private static void factorialExample() {
    System.out.println("1. Stack-Safe Factorial");
    System.out.println("-----------------------");

    // Compute factorial of 20
    Trampoline<Long> factorial20 = factorial(20, 1L);
    System.out.println("Factorial of 20: " + factorial20.run());

    // For very large factorials, use BigInteger
    Trampoline<BigInteger> factorial1000 =
        bigIntegerFactorial(BigInteger.valueOf(1000), BigInteger.ONE);
    BigInteger result = factorial1000.run();
    System.out.println(
        "Factorial of 1000 (first 50 digits): " + result.toString().substring(0, 50) + "...");

    // This would cause stack overflow with naive recursion, but works fine with Trampoline
    Trampoline<Long> factorial10000 = factorial(10_000, 1L);
    System.out.println(
        "Factorial of 10,000 computed successfully (overflow occurred, but stack-safe!)");
    System.out.println();
  }

  /**
   * Stack-safe factorial using Trampoline.
   *
   * @param n The number to compute factorial for.
   * @param acc The accumulator.
   * @return A Trampoline computing the factorial.
   */
  private static Trampoline<Long> factorial(long n, long acc) {
    if (n <= 0) {
      return Trampoline.done(acc);
    }
    return Trampoline.defer(() -> factorial(n - 1, n * acc));
  }

  /**
   * Stack-safe BigInteger factorial using Trampoline.
   *
   * @param n The number to compute factorial for.
   * @param acc The accumulator.
   * @return A Trampoline computing the factorial.
   */
  private static Trampoline<BigInteger> bigIntegerFactorial(BigInteger n, BigInteger acc) {
    if (n.compareTo(BigInteger.ZERO) <= 0) {
      return Trampoline.done(acc);
    }
    return Trampoline.defer(() -> bigIntegerFactorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
  }

  // ============================================================================
  // Example 2: Mutual Recursion (Even/Odd)
  // ============================================================================

  private static void mutualRecursionExample() {
    System.out.println("2. Mutual Recursion (Even/Odd)");
    System.out.println("-------------------------------");

    // Check if large numbers are even or odd
    Trampoline<Boolean> isEven1000000 = isEven(1_000_000);
    System.out.println("Is 1,000,000 even? " + isEven1000000.run());

    Trampoline<Boolean> isOdd999999 = isOdd(999_999);
    System.out.println("Is 999,999 odd? " + isOdd999999.run());

    // This would cause stack overflow with naive mutual recursion
    Trampoline<Boolean> isEven100000 = isEven(100_000);
    System.out.println("Is 100,000 even? " + isEven100000.run());
    System.out.println();
  }

  /**
   * Stack-safe even checker using mutual recursion.
   *
   * @param n The number to check.
   * @return A Trampoline computing whether n is even.
   */
  private static Trampoline<Boolean> isEven(int n) {
    if (n == 0) {
      return Trampoline.done(true);
    }
    return Trampoline.defer(() -> isOdd(n - 1));
  }

  /**
   * Stack-safe odd checker using mutual recursion.
   *
   * @param n The number to check.
   * @return A Trampoline computing whether n is odd.
   */
  private static Trampoline<Boolean> isOdd(int n) {
    if (n == 0) {
      return Trampoline.done(false);
    }
    return Trampoline.defer(() -> isEven(n - 1));
  }

  // ============================================================================
  // Example 3: Fibonacci
  // ============================================================================

  private static void fibonacciExample() {
    System.out.println("3. Fibonacci");
    System.out.println("------------");

    // Compute Fibonacci numbers
    System.out.println("Fibonacci of 10: " + fibonacci(10).run());
    System.out.println("Fibonacci of 20: " + fibonacci(20).run());
    System.out.println("Fibonacci of 30: " + fibonacci(30).run());

    // Large Fibonacci with BigInteger
    System.out.println("Fibonacci of 100: " + bigIntegerFibonacci(100).run());
    System.out.println();
  }

  /**
   * Stack-safe Fibonacci using Trampoline (with accumulator).
   *
   * @param n The Fibonacci number to compute.
   * @return A Trampoline computing the Fibonacci number.
   */
  private static Trampoline<Long> fibonacci(int n) {
    return fibHelper(n, 0L, 1L);
  }

  private static Trampoline<Long> fibHelper(int n, long a, long b) {
    if (n == 0) {
      return Trampoline.done(a);
    }
    return Trampoline.defer(() -> fibHelper(n - 1, b, a + b));
  }

  /**
   * Stack-safe BigInteger Fibonacci using Trampoline.
   *
   * @param n The Fibonacci number to compute.
   * @return A Trampoline computing the Fibonacci number.
   */
  private static Trampoline<BigInteger> bigIntegerFibonacci(int n) {
    return bigIntegerFibHelper(n, BigInteger.ZERO, BigInteger.ONE);
  }

  private static Trampoline<BigInteger> bigIntegerFibHelper(int n, BigInteger a, BigInteger b) {
    if (n == 0) {
      return Trampoline.done(a);
    }
    return Trampoline.defer(() -> bigIntegerFibHelper(n - 1, b, a.add(b)));
  }

  // ============================================================================
  // Example 4: Composition with map and flatMap
  // ============================================================================

  private static void compositionExample() {
    System.out.println("4. Composition with map and flatMap");
    System.out.println("-----------------------------------");

    // Create a computation
    Trampoline<Integer> computation =
        Trampoline.done(10)
            .map(x -> x * 2) // 20
            .flatMap(x -> Trampoline.done(x + 5)) // 25
            .map(x -> x * 3); // 75

    System.out.println("Result: " + computation.run());

    // Chain multiple computations
    Trampoline<Integer> chained =
        Trampoline.done(5)
            .flatMap(x -> factorial(x, 1L).map(Long::intValue)) // 5! = 120
            .map(x -> x / 10); // 12

    System.out.println("Chained result: " + chained.run());
    System.out.println();
  }

  // ============================================================================
  // Example 5: Countdown
  // ============================================================================

  private static void countdownExample() {
    System.out.println("5. Countdown");
    System.out.println("------------");

    // Countdown from 100,000 (stack-safe!)
    Trampoline<Integer> countdown = countdownFrom(100_000);
    System.out.println("Countdown from 100,000: " + countdown.run());

    // With side effects (printing every 10,000)
    Trampoline<Integer> countdownWithPrint = countdownWithPrint(50_000);
    System.out.print("Countdown with print: ");
    countdownWithPrint.run();
    System.out.println();
  }

  /**
   * Stack-safe countdown using Trampoline.
   *
   * @param n The number to count down from.
   * @return A Trampoline computing the final value (0).
   */
  private static Trampoline<Integer> countdownFrom(int n) {
    if (n <= 0) {
      return Trampoline.done(0);
    }
    return Trampoline.defer(() -> countdownFrom(n - 1));
  }

  /**
   * Stack-safe countdown with side effects (printing).
   *
   * @param n The number to count down from.
   * @return A Trampoline computing the final value (0).
   */
  private static Trampoline<Integer> countdownWithPrint(int n) {
    if (n <= 0) {
      return Trampoline.done(0);
    }
    if (n % 10_000 == 0) {
      System.out.print(n + " ");
    }
    return Trampoline.defer(() -> countdownWithPrint(n - 1));
  }
}
