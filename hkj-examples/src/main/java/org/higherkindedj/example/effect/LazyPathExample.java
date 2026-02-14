// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.LazyPath;

/**
 * Examples demonstrating LazyPath for deferred, memoised computations.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Creating lazy computations with {@code defer}
 *   <li>Memoisation - results cached after first evaluation
 *   <li>Transforming lazy values with {@code map} and {@code via}
 *   <li>Expensive computation deferral patterns
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.LazyPathExample}
 */
public class LazyPathExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: LazyPath ===\n");

    basicLazyExamples();
    memoisationDemo();
    lazyChaining();
    expensiveComputationPatterns();
  }

  private static void basicLazyExamples() {
    System.out.println("--- Basic Lazy Evaluation ---");

    // LazyPath.now - already evaluated (no computation deferred)
    LazyPath<String> eager = LazyPath.now("I'm already evaluated");
    System.out.println("Eager value: " + eager.get());

    // LazyPath.defer - computation is deferred until first access
    AtomicInteger callCount = new AtomicInteger(0);

    LazyPath<String> deferred =
        LazyPath.defer(
            () -> {
              System.out.println("  [Computing deferred value...]");
              callCount.incrementAndGet();
              return "Computed at " + System.currentTimeMillis();
            });

    System.out.println("Lazy created, call count: " + callCount.get()); // 0

    System.out.println("First access:");
    String value1 = deferred.get(); // Triggers computation
    System.out.println("  Result: " + value1);
    System.out.println("  Call count: " + callCount.get()); // 1

    System.out.println("Second access (cached):");
    String value2 = deferred.get(); // Returns cached value
    System.out.println("  Result: " + value2);
    System.out.println("  Call count: " + callCount.get()); // Still 1

    System.out.println();
  }

  private static void memoisationDemo() {
    System.out.println("--- Memoisation Pattern ---");

    // Expensive Fibonacci calculation
    AtomicInteger computations = new AtomicInteger(0);

    LazyPath<BigInteger> lazyFib =
        LazyPath.defer(
            () -> {
              System.out.println("  Computing Fibonacci(40)...");
              computations.incrementAndGet();
              return fibonacci(40);
            });

    System.out.println("LazyPath created. Computations so far: " + computations.get());

    // First access - computes
    long start = System.currentTimeMillis();
    BigInteger result1 = lazyFib.get();
    long elapsed1 = System.currentTimeMillis() - start;
    System.out.println("First access: " + result1 + " (took " + elapsed1 + "ms)");
    System.out.println("Computations: " + computations.get());

    // Second access - cached (instant)
    start = System.currentTimeMillis();
    BigInteger result2 = lazyFib.get();
    long elapsed2 = System.currentTimeMillis() - start;
    System.out.println("Second access: " + result2 + " (took " + elapsed2 + "ms)");
    System.out.println("Computations: " + computations.get()); // Still 1

    System.out.println();
  }

  private static BigInteger fibonacci(int n) {
    if (n <= 1) return BigInteger.valueOf(n);
    BigInteger a = BigInteger.ZERO;
    BigInteger b = BigInteger.ONE;
    for (int i = 2; i <= n; i++) {
      BigInteger temp = b;
      b = a.add(b);
      a = temp;
    }
    return b;
  }

  private static void lazyChaining() {
    System.out.println("--- Lazy Transformation Chains ---");

    AtomicInteger step = new AtomicInteger(0);

    // Chain of lazy transformations - none execute until final .get()
    LazyPath<String> chain =
        LazyPath.defer(
                () -> {
                  System.out.println("  Step " + step.incrementAndGet() + ": Initial computation");
                  return 10;
                })
            .map(
                n -> {
                  System.out.println("  Step " + step.incrementAndGet() + ": Doubling");
                  return n * 2;
                })
            .map(
                n -> {
                  System.out.println("  Step " + step.incrementAndGet() + ": Converting to string");
                  return "Result: " + n;
                });

    System.out.println("Chain created. Steps executed: " + step.get()); // 0

    System.out.println("Forcing evaluation...");
    String result = chain.get();
    System.out.println("Final result: " + result);
    System.out.println("Total steps: " + step.get()); // 3

    // Second call - all cached
    System.out.println("\nSecond evaluation (cached):");
    step.set(0);
    String result2 = chain.get();
    System.out.println("Result: " + result2);
    System.out.println("Steps executed: " + step.get()); // 0 (cached)

    System.out.println();
  }

  private static void expensiveComputationPatterns() {
    System.out.println("--- Expensive Computation Patterns ---");

    // Pattern 1: Configuration that's expensive to load
    LazyPath<Config> config =
        LazyPath.defer(
            () -> {
              System.out.println("  [Loading configuration from disk...]");
              // Simulated expensive operation
              sleep(100);
              return new Config("production", 8080, true);
            });

    System.out.println("Config object created (not loaded yet)");

    // Use config only if needed
    boolean needsConfig = true;
    if (needsConfig) {
      System.out.println("Environment: " + config.map(Config::environment).get());
      System.out.println("Port: " + config.map(Config::port).get()); // Config already cached
    }

    // Pattern 2: Conditional expensive computation
    System.out.println("\nConditional Computation:");

    LazyPath<String> expensiveResult =
        LazyPath.defer(
            () -> {
              System.out.println("  [Running expensive analysis...]");
              sleep(100);
              return "Analysis complete: 42 issues found";
            });

    boolean userWantsAnalysis = false; // Simulate user choice

    if (userWantsAnalysis) {
      System.out.println(expensiveResult.get());
    } else {
      System.out.println("Analysis skipped - expensive computation avoided!");
    }

    // Pattern 3: Breaking circular dependencies
    System.out.println("\nCircular Dependency Breaking:");

    // Service A depends on B, B depends on A
    // Use lazy to break the cycle
    // Note: ServiceA and ServiceB are defined at class level to allow mutual references

    // Create services with lazy references
    LazyPath<ServiceB> lazyB = LazyPath.defer(() -> new ServiceB(LazyPath.now(null), "ServiceB"));

    // In real code, you'd wire these properly - this just demonstrates the pattern
    System.out.println("Services can be created with lazy cross-references");

    System.out.println();
  }

  // Records for circular dependency example (must be at class level for mutual references)
  private record ServiceA(LazyPath<ServiceB> serviceB, String name) {
    String callB() {
      return "A calling -> " + serviceB.get().name();
    }
  }

  private record ServiceB(LazyPath<ServiceA> serviceA, String name) {
    String callA() {
      return "B calling -> " + serviceA.get().name();
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private record Config(String environment, int port, boolean debug) {}
}
