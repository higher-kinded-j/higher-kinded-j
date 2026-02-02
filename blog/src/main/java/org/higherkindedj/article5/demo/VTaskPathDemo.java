// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.demo;

import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Demonstrates VTaskPath for virtual thread-based concurrency.
 *
 * <p>VTaskPath brings Project Loom's lightweight threading to the Effect Path API, enabling simple
 * blocking code that scales to millions of concurrent operations. This demo showcases:
 *
 * <ul>
 *   <li>Creating VTaskPaths with {@link Path#vtask}, {@link Path#vtaskPure}, etc.
 *   <li>Execution methods: {@code run()}, {@code runSafe()}, {@code runAsync()}
 *   <li>Parallel execution with {@link Par} combinators
 *   <li>Structured concurrency with {@link Scope}
 *   <li>Error handling and timeouts
 * </ul>
 */
public final class VTaskPathDemo {

  public static void main(String[] args) {
    System.out.println("VTaskPath Demo: Virtual Thread Concurrency");
    System.out.println("===========================================");
    System.out.println();

    demoBasicVTaskPath();
    demoParallelExecution();
    demoScopeAllSucceed();
    demoScopeAnySucceed();
    demoErrorHandling();
    demoTimeouts();
  }

  private static void demoBasicVTaskPath() {
    System.out.println("1. Basic VTaskPath Operations");
    System.out.println("   --------------------------");

    // Create a VTaskPath from a computation
    VTaskPath<String> greeting = Path.vtask(() -> {
      Thread.sleep(10); // Simulate some work
      return "Hello from virtual thread!";
    });

    // Pure value (no computation)
    VTaskPath<Integer> pure = Path.vtaskPure(42);

    // Execute and get results
    System.out.println("   Pure value: " + pure.run());
    System.out.println("   Computed: " + greeting.run());

    // Chaining with map and via
    VTaskPath<String> chained = Path.vtaskPure("world")
        .map(String::toUpperCase)
        .map(s -> "Hello, " + s + "!");
    System.out.println("   Chained: " + chained.run());

    System.out.println();
  }

  private static void demoParallelExecution() {
    System.out.println("2. Parallel Execution with Par");
    System.out.println("   ----------------------------");

    // Simulate fetching from multiple sources
    VTask<String> fetchA = VTask.of(() -> {
      Thread.sleep(50);
      return "Data from A";
    });

    VTask<String> fetchB = VTask.of(() -> {
      Thread.sleep(50);
      return "Data from B";
    });

    // Execute in parallel and combine
    long start = System.currentTimeMillis();
    VTask<String> combined = Par.map2(fetchA, fetchB, (a, b) -> a + " + " + b);
    String result = combined.run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("   Result: " + result);
    System.out.println("   Elapsed: " + elapsed + "ms (should be ~50ms, not ~100ms)");

    // Execute a list in parallel
    List<VTask<Integer>> tasks = List.of(
        VTask.of(() -> { Thread.sleep(30); return 1; }),
        VTask.of(() -> { Thread.sleep(30); return 2; }),
        VTask.of(() -> { Thread.sleep(30); return 3; })
    );

    start = System.currentTimeMillis();
    List<Integer> results = Par.all(tasks).run();
    elapsed = System.currentTimeMillis() - start;

    System.out.println("   Par.all results: " + results);
    System.out.println("   Elapsed: " + elapsed + "ms (should be ~30ms, not ~90ms)");

    System.out.println();
  }

  private static void demoScopeAllSucceed() {
    System.out.println("3. Structured Concurrency: Scope.allSucceed()");
    System.out.println("   ------------------------------------------");

    // Fork multiple tasks, wait for all to succeed
    VTask<List<String>> all = Scope.<String>allSucceed()
        .fork(VTask.of(() -> { Thread.sleep(20); return "Task 1 complete"; }))
        .fork(VTask.of(() -> { Thread.sleep(20); return "Task 2 complete"; }))
        .fork(VTask.of(() -> { Thread.sleep(20); return "Task 3 complete"; }))
        .join();

    long start = System.currentTimeMillis();
    List<String> results = all.run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("   Results: " + results);
    System.out.println("   Elapsed: " + elapsed + "ms (all ran in parallel)");

    System.out.println();
  }

  private static void demoScopeAnySucceed() {
    System.out.println("4. Structured Concurrency: Scope.anySucceed()");
    System.out.println("   ------------------------------------------");

    // Race multiple tasks, first success wins
    VTask<String> any = Scope.<String>anySucceed()
        .fork(VTask.of(() -> { Thread.sleep(100); return "Slow server"; }))
        .fork(VTask.of(() -> { Thread.sleep(20); return "Fast server"; }))
        .fork(VTask.of(() -> { Thread.sleep(50); return "Medium server"; }))
        .join();

    long start = System.currentTimeMillis();
    String winner = any.run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("   Winner: " + winner);
    System.out.println("   Elapsed: " + elapsed + "ms (fastest wins, others cancelled)");

    System.out.println();
  }

  private static void demoErrorHandling() {
    System.out.println("5. Error Handling");
    System.out.println("   --------------");

    // Task that fails
    VTaskPath<String> failing = Path.vtask(() -> {
      throw new RuntimeException("Network error");
    });

    // Recover with default value
    VTaskPath<String> recovered = failing.handleError(ex -> "Default value");
    System.out.println("   Recovered: " + recovered.run());

    // Recover with fallback task
    VTaskPath<String> withFallback = failing
        .handleErrorWith(ex -> Path.vtask(() -> "Fallback result"));
    System.out.println("   Fallback: " + withFallback.run());

    // Safe execution with Try
    var tryResult = failing.runSafe();
    System.out.println("   runSafe(): " + tryResult.fold(
        v -> "Success: " + v,
        e -> "Failure: " + e.getMessage()
    ));

    System.out.println();
  }

  private static void demoTimeouts() {
    System.out.println("6. Timeouts");
    System.out.println("   --------");

    // Task that takes too long
    VTaskPath<String> slow = Path.vtask(() -> {
      Thread.sleep(500);
      return "Finally done";
    });

    // Add timeout
    VTaskPath<String> withTimeout = slow.timeout(Duration.ofMillis(100));

    var result = withTimeout.runSafe();
    System.out.println("   With 100ms timeout on 500ms task:");
    System.out.println("   Result: " + result.fold(
        v -> "Success: " + v,
        e -> "Timeout: " + e.getClass().getSimpleName()
    ));

    // Task that completes in time
    VTaskPath<String> fast = Path.vtask(() -> {
      Thread.sleep(20);
      return "Quick result";
    });

    var fastResult = fast.timeout(Duration.ofMillis(100)).runSafe();
    System.out.println("   With 100ms timeout on 20ms task:");
    System.out.println("   Result: " + fastResult.fold(
        v -> "Success: " + v,
        e -> "Timeout: " + e.getClass().getSimpleName()
    ));

    System.out.println();
  }
}
