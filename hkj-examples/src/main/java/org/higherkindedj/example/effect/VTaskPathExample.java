// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Examples demonstrating VTaskPath for virtual thread-based concurrency.
 *
 * <p>VTaskPath wraps VTask to provide a fluent API for concurrent operations that execute on
 * virtual threads. This enables simple blocking code that scales to millions of concurrent tasks.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Creating VTask instances via factory methods
 *   <li>Transforming values with map and chaining with flatMap/via
 *   <li>Error handling with recover and recoverWith
 *   <li>Timeout management for long-running operations
 *   <li>Parallel execution with Par combinators
 *   <li>Real-world service aggregation patterns
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.VTaskPathExample}
 *
 * @see org.higherkindedj.hkt.vtask.VTask
 * @see org.higherkindedj.hkt.vtask.Par
 */
public class VTaskPathExample {

  public static void main(String[] args) {
    System.out.println("=== VTaskPath: Virtual Thread-Based Concurrency ===\n");

    basicCreationAndExecution();
    transformationAndChaining();
    errorHandlingPatterns();
    timeoutHandling();
    parallelExecutionWithPar();
    realWorldServiceAggregation();
  }

  // ============================================================
  // Basic Creation and Execution
  // ============================================================

  private static void basicCreationAndExecution() {
    System.out.println("--- Basic Creation and Execution ---\n");

    // Create from a computation
    VTask<String> fetchData =
        VTask.of(
            () -> {
              System.out.println("  Fetching data on thread: " + Thread.currentThread());
              return "Data from virtual thread";
            });

    // Create a pure value (no computation)
    VTask<Integer> pureValue = VTask.succeed(42);

    // Create from a Runnable (returns Unit)
    VTask<Unit> logAction = VTask.exec(() -> System.out.println("  Logging action executed"));

    // Execution methods
    System.out.println("1. run() - blocks and may throw:");
    try {
      String result = fetchData.run();
      System.out.println("   Result: " + result);
    } catch (RuntimeException e) {
      // run() wraps checked exceptions in VTaskExecutionException (a RuntimeException);
      // other RuntimeExceptions and Errors are thrown directly.
      System.out.println("   Error: " + e.getMessage());
    }

    System.out.println("\n2. runSafe() - returns Try<A>:");
    Try<Integer> tryResult = pureValue.runSafe();
    String message =
        tryResult.fold(
            value -> "   Success: " + value, error -> "   Failure: " + error.getMessage());
    System.out.println(message);

    System.out.println("\n3. runAsync() - returns CompletableFuture<A>:");
    logAction.runAsync().thenRun(() -> System.out.println("   Async completed"));

    // Wait briefly for async to complete
    sleep(100);
    System.out.println();
  }

  // ============================================================
  // Transformation and Chaining
  // ============================================================

  private static void transformationAndChaining() {
    System.out.println("--- Transformation and Chaining ---\n");

    // map: Transform the result
    VTask<String> greeting = VTask.succeed("Hello");
    VTask<Integer> length = greeting.map(String::length);
    System.out.println("map - Length of 'Hello': " + length.runSafe().orElse(-1));

    // flatMap/via: Chain dependent computations
    VTask<String> enriched =
        greeting
            .flatMap(s -> VTask.of(() -> s + ", World!"))
            .flatMap(s -> VTask.of(() -> s.toUpperCase()));
    System.out.println("flatMap - Enriched: " + enriched.runSafe().orElse("error"));

    // peek: Observe values without modifying (useful for debugging)
    VTask<Integer> debugged =
        VTask.succeed(10)
            .peek(v -> System.out.println("peek - Before transform: " + v))
            .map(v -> v * 2)
            .peek(v -> System.out.println("peek - After transform: " + v));
    debugged.runSafe();

    // then: Sequence, discarding first result
    VTask<Unit> setup = VTask.exec(() -> System.out.println("then - Setup complete"));
    VTask<String> withSetup = setup.then(() -> VTask.succeed("Main computation"));
    System.out.println("then - Result: " + withSetup.runSafe().orElse("error"));

    System.out.println();
  }

  // ============================================================
  // Error Handling Patterns
  // ============================================================

  private static void errorHandlingPatterns() {
    System.out.println("--- Error Handling Patterns ---\n");

    // Create a failing task
    VTask<String> failingTask = VTask.fail(new RuntimeException("Something went wrong"));

    // recover: Transform failure to success value
    VTask<String> recovered = failingTask.recover(error -> "Default value");
    System.out.println("recover - Result: " + recovered.runSafe().orElse("error"));

    // recoverWith: Transform failure to another VTask
    VTask<String> recoveredWith =
        failingTask.recoverWith(error -> VTask.of(() -> "Fallback from secondary source"));
    System.out.println("recoverWith - Result: " + recoveredWith.runSafe().orElse("error"));

    // mapError: Transform the exception type
    VTask<String> mappedError =
        failingTask.mapError(error -> new IllegalStateException("Wrapped: " + error.getMessage()));
    Try<String> errorResult = mappedError.runSafe();
    String errorMessage =
        errorResult.fold(
            value -> "mapError - Value: " + value,
            error -> "mapError - Error type: " + error.getClass().getSimpleName());
    System.out.println(errorMessage);

    // Fallback chain: Try multiple sources
    System.out.println("\nFallback chain:");
    VTask<String> resilientConfig =
        VTask.<String>fail(new RuntimeException("Primary failed"))
            .recoverWith(
                e -> {
                  System.out.println("  Primary failed, trying secondary...");
                  return VTask.<String>fail(new RuntimeException("Secondary failed"));
                })
            .recoverWith(
                e -> {
                  System.out.println("  Secondary failed, trying cache...");
                  return VTask.succeed("Cached value");
                })
            .recover(e -> "Final default");
    System.out.println("  Final result: " + resilientConfig.runSafe().orElse("error"));

    System.out.println();
  }

  // ============================================================
  // Timeout Handling
  // ============================================================

  private static void timeoutHandling() {
    System.out.println("--- Timeout Handling ---\n");

    // Fast operation - completes before timeout
    VTask<String> fastOp =
        VTask.of(
            () -> {
              sleep(100);
              return "Fast result";
            });

    VTask<String> fastWithTimeout = fastOp.timeout(Duration.ofSeconds(1));
    System.out.println("Fast operation: " + fastWithTimeout.runSafe().orElse("timed out"));

    // Slow operation - exceeds timeout
    VTask<String> slowOp =
        VTask.of(
            () -> {
              sleep(2000);
              return "Slow result";
            });

    VTask<String> slowWithTimeout = slowOp.timeout(Duration.ofMillis(500));
    Try<String> slowResult = slowWithTimeout.runSafe();
    String slowMessage =
        slowResult.fold(
            value -> "Slow operation: " + value,
            error -> "Slow operation timed out: " + error.getClass().getSimpleName());
    System.out.println(slowMessage);

    // Timeout with recovery
    VTask<String> withRecovery =
        slowOp.timeout(Duration.ofMillis(500)).recover(e -> "Default after timeout");
    System.out.println("Timeout with recovery: " + withRecovery.runSafe().orElse("error"));

    System.out.println();
  }

  // ============================================================
  // Parallel Execution with Par
  // ============================================================

  private static void parallelExecutionWithPar() {
    System.out.println("--- Parallel Execution with Par ---\n");

    // zip: Combine two tasks into a tuple
    VTask<String> userTask =
        VTask.of(
            () -> {
              sleep(100);
              return "User: Alice";
            });
    VTask<String> profileTask =
        VTask.of(
            () -> {
              sleep(100);
              return "Profile: Developer";
            });

    long start = System.currentTimeMillis();
    VTask<Par.Tuple2<String, String>> zipped = Par.zip(userTask, profileTask);
    Par.Tuple2<String, String> zipResult = zipped.runSafe().orElse(null);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("zip - Both results: " + zipResult);
    System.out.println("zip - Elapsed: " + elapsed + "ms (parallel, ~100ms expected)");

    // map2: Combine two tasks with a function
    VTask<String> combined =
        Par.map2(userTask, profileTask, (user, profile) -> user + " | " + profile);
    System.out.println("map2 - Combined: " + combined.runSafe().orElse("error"));

    // all: Execute a list of tasks in parallel
    List<VTask<Integer>> tasks =
        List.of(
            VTask.of(
                () -> {
                  sleep(100);
                  return 1;
                }),
            VTask.of(
                () -> {
                  sleep(100);
                  return 2;
                }),
            VTask.of(
                () -> {
                  sleep(100);
                  return 3;
                }),
            VTask.of(
                () -> {
                  sleep(100);
                  return 4;
                }));

    start = System.currentTimeMillis();
    VTask<List<Integer>> allResults = Par.all(tasks);
    List<Integer> results = allResults.runSafe().orElse(List.of());
    elapsed = System.currentTimeMillis() - start;

    System.out.println("all - Results: " + results);
    System.out.println("all - Elapsed: " + elapsed + "ms (parallel, ~100ms expected)");

    // race: Return first successful result
    VTask<String> server1 =
        VTask.of(
            () -> {
              sleep(200);
              return "Response from Server 1";
            });
    VTask<String> server2 =
        VTask.of(
            () -> {
              sleep(50);
              return "Response from Server 2";
            });

    start = System.currentTimeMillis();
    VTask<String> fastest = Par.race(List.of(server1, server2));
    String raceResult = fastest.runSafe().orElse("error");
    elapsed = System.currentTimeMillis() - start;

    System.out.println("race - Fastest: " + raceResult);
    System.out.println("race - Elapsed: " + elapsed + "ms (first to complete, ~50ms expected)");

    System.out.println();
  }

  // ============================================================
  // Real-World Service Aggregation
  // ============================================================

  private static void realWorldServiceAggregation() {
    System.out.println("--- Real-World Service Aggregation ---\n");

    // Simulate a dashboard that aggregates data from multiple services
    String userId = "user-123";

    System.out.println("Loading dashboard for " + userId + "...");
    long start = System.currentTimeMillis();

    VTask<Dashboard> dashboard = loadDashboard(userId);
    Try<Dashboard> result = dashboard.runSafe();

    long elapsed = System.currentTimeMillis() - start;

    String dashboardMessage =
        result.fold(
            d ->
                "Dashboard loaded successfully:\n"
                    + "  User: "
                    + d.userName
                    + "\n"
                    + "  Orders: "
                    + d.recentOrders
                    + "\n"
                    + "  Analytics: "
                    + d.analyticsData,
            error -> "Dashboard failed: " + error.getMessage());
    System.out.println(dashboardMessage);

    System.out.println("Total time: " + elapsed + "ms (parallel fetch, ~150ms expected)\n");

    // Demonstrate timeout and fallback
    System.out.println("Loading dashboard with timeout...");
    VTask<Dashboard> dashboardWithTimeout =
        loadDashboard(userId)
            .timeout(Duration.ofMillis(100)) // Too short for the 150ms services
            .recover(e -> Dashboard.empty());

    Try<Dashboard> timeoutResult = dashboardWithTimeout.runSafe();
    String timeoutMessage =
        timeoutResult.fold(
            d -> "Result: " + (d.isEmpty() ? "Empty (fallback)" : "Loaded"),
            error -> "Error: " + error.getMessage());
    System.out.println(timeoutMessage);

    System.out.println();
  }

  // Simulated dashboard loading using parallel fetches
  private static VTask<Dashboard> loadDashboard(String userId) {
    VTask<String> userTask =
        VTask.of(
            () -> {
              sleep(150);
              return "Alice Smith";
            });

    VTask<List<String>> ordersTask =
        VTask.of(
            () -> {
              sleep(150);
              return List.of("Order-001", "Order-002", "Order-003");
            });

    VTask<String> analyticsTask =
        VTask.of(
            () -> {
              sleep(150);
              return "Page views: 1,234 | Conversions: 56";
            });

    return Par.map3(userTask, ordersTask, analyticsTask, Dashboard::new);
  }

  // Simple dashboard record
  record Dashboard(String userName, List<String> recentOrders, String analyticsData) {
    static Dashboard empty() {
      return new Dashboard("Unknown", List.of(), "No data");
    }

    boolean isEmpty() {
      return "Unknown".equals(userName);
    }
  }

  // Helper for sleep
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
