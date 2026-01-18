// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Examples demonstrating structured concurrency with Scope and ScopeJoiner.
 *
 * <p>Scope provides a fluent API for coordinating concurrent tasks with configurable joining
 * strategies. It wraps Java's StructuredTaskScope with functional result handling.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>allSucceed - wait for all tasks to complete successfully
 *   <li>anySucceed - return first success, cancel others
 *   <li>firstComplete - return first result regardless of outcome
 *   <li>accumulating - collect all errors using Validated
 *   <li>Timeout handling with scopes
 *   <li>Safe result handling with joinSafe, joinEither, joinMaybe
 *   <li>ScopeJoiner for custom joining behaviour
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.ScopeExample}
 *
 * @see org.higherkindedj.hkt.vtask.Scope
 * @see org.higherkindedj.hkt.vtask.ScopeJoiner
 */
public class ScopeExample {

  public static void main(String[] args) {
    System.out.println("=== Structured Concurrency with Scope ===\n");

    allSucceedExample();
    anySucceedExample();
    firstCompleteExample();
    accumulatingExample();
    timeoutExample();
    safeResultHandling();
    scopeJoinerExample();
    realWorldDashboardExample();
  }

  // ============================================================
  // All Succeed: Wait for all tasks to complete successfully
  // ============================================================

  private static void allSucceedExample() {
    System.out.println("--- allSucceed: Parallel Fetches ---\n");

    // Use allSucceed when you need results from multiple independent operations
    // All must complete successfully; fails fast on first failure

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

    VTask<String> prefsTask =
        VTask.of(
            () -> {
              sleep(100);
              return "Theme: Dark";
            });

    long start = System.currentTimeMillis();

    VTask<List<String>> allResults =
        Scope.<String>allSucceed().fork(userTask).fork(profileTask).fork(prefsTask).join();

    List<String> results = allResults.runSafe().orElse(List.of());
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("Results: " + results);
    System.out.println("Elapsed: " + elapsed + "ms (parallel, ~100ms expected)\n");
  }

  // ============================================================
  // Any Succeed: First successful result wins
  // ============================================================

  private static void anySucceedExample() {
    System.out.println("--- anySucceed: Racing Mirrors ---\n");

    // Use anySucceed for redundant requests where any response will do
    // First success wins; other tasks are cancelled

    VTask<String> mirror1 =
        VTask.of(
            () -> {
              sleep(200);
              return "Response from Mirror 1";
            });

    VTask<String> mirror2 =
        VTask.of(
            () -> {
              sleep(50); // Faster mirror
              return "Response from Mirror 2";
            });

    VTask<String> mirror3 =
        VTask.of(
            () -> {
              sleep(150);
              return "Response from Mirror 3";
            });

    long start = System.currentTimeMillis();

    VTask<String> fastest =
        Scope.<String>anySucceed().fork(mirror1).fork(mirror2).fork(mirror3).join();

    String result = fastest.runSafe().orElse("error");
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("Fastest response: " + result);
    System.out.println("Elapsed: " + elapsed + "ms (~50ms expected)\n");
  }

  // ============================================================
  // First Complete: First result regardless of success/failure
  // ============================================================

  private static void firstCompleteExample() {
    System.out.println("--- firstComplete: Fast Path with Fallback ---\n");

    // Use firstComplete when you want the first result regardless of outcome
    // Useful for fast-path/slow-path patterns

    VTask<String> fastButRisky =
        VTask.of(
            () -> {
              sleep(50);
              // This one completes first but might fail in real scenarios
              return "Fast path result";
            });

    VTask<String> slowButSafe =
        VTask.of(
            () -> {
              sleep(200);
              return "Slow but reliable result";
            });

    long start = System.currentTimeMillis();

    VTask<String> first = Scope.<String>firstComplete().fork(fastButRisky).fork(slowButSafe).join();

    String result = first.runSafe().orElse("error");
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("First to complete: " + result);
    System.out.println("Elapsed: " + elapsed + "ms (~50ms expected)\n");
  }

  // ============================================================
  // Accumulating: Collect all errors with Validated
  // ============================================================

  private static void accumulatingExample() {
    System.out.println("--- accumulating: Form Validation ---\n");

    // Use accumulating for validation scenarios
    // Collects all errors instead of failing on the first

    // Simulated validation tasks (some succeed, some fail)
    VTask<String> validateUsername =
        VTask.of(
            () -> {
              sleep(50);
              throw new IllegalArgumentException("Username too short");
            });

    VTask<String> validateEmail =
        VTask.of(
            () -> {
              sleep(50);
              throw new IllegalArgumentException("Invalid email format");
            });

    VTask<String> validateAge =
        VTask.of(
            () -> {
              sleep(50);
              return "Age: 25 (valid)";
            });

    // Error mapper: convert exceptions to strings
    VTask<Validated<List<String>, List<String>>> validation =
        Scope.<String, String>accumulating(Throwable::getMessage)
            .fork(validateUsername)
            .fork(validateEmail)
            .fork(validateAge)
            .join();

    Validated<List<String>, List<String>> result =
        validation.runSafe().orElse(Validated.invalid(List.of("Unknown error")));

    result.fold(
        errors -> {
          System.out.println("Validation failed with errors:");
          errors.forEach(err -> System.out.println("  - " + err));
          return null;
        },
        successes -> {
          System.out.println("All validations passed: " + successes);
          return null;
        });
    System.out.println();
  }

  // ============================================================
  // Timeout Handling
  // ============================================================

  private static void timeoutExample() {
    System.out.println("--- Timeout Handling ---\n");

    // Scope supports timeout for all forked tasks
    VTask<String> slowTask1 =
        VTask.of(
            () -> {
              sleep(500);
              return "Slow result 1";
            });

    VTask<String> slowTask2 =
        VTask.of(
            () -> {
              sleep(500);
              return "Slow result 2";
            });

    // This will timeout before tasks complete
    VTask<List<String>> withTimeout =
        Scope.<String>allSucceed()
            .timeout(Duration.ofMillis(200))
            .fork(slowTask1)
            .fork(slowTask2)
            .join();

    Try<List<String>> result = withTimeout.runSafe();

    String message =
        result.fold(
            value -> "Success: " + value,
            error -> "Timed out: " + error.getClass().getSimpleName());
    System.out.println(message);

    // Timeout with recovery
    VTask<List<String>> withRecovery =
        Scope.<String>allSucceed()
            .timeout(Duration.ofMillis(200))
            .fork(slowTask1)
            .fork(slowTask2)
            .join()
            .recover(e -> List.of("Default value 1", "Default value 2"));

    List<String> recovered = withRecovery.runSafe().orElse(List.of());
    System.out.println("With recovery: " + recovered + "\n");
  }

  // ============================================================
  // Safe Result Handling
  // ============================================================

  private static void safeResultHandling() {
    System.out.println("--- Safe Result Handling ---\n");

    VTask<String> task1 = VTask.succeed("Result 1");
    VTask<String> task2 = VTask.succeed("Result 2");

    // joinSafe: Returns VTask<Try<R>> instead of throwing
    VTask<Try<List<String>>> trySafe =
        Scope.<String>allSucceed().fork(task1).fork(task2).joinSafe();

    Try<List<String>> tryResult = trySafe.runSafe().orElse(Try.failure(new RuntimeException()));
    System.out.println("joinSafe: " + tryResult);

    // joinEither: Returns VTask<Either<Throwable, R>>
    VTask<Either<Throwable, List<String>>> eitherResult =
        Scope.<String>allSucceed().fork(task1).fork(task2).joinEither();

    Either<Throwable, List<String>> either =
        eitherResult.runSafe().orElse(Either.left(new RuntimeException()));
    System.out.println("joinEither: " + either);

    // joinMaybe: Returns VTask<Maybe<R>> (Nothing on failure)
    VTask<Maybe<List<String>>> maybeResult =
        Scope.<String>allSucceed().fork(task1).fork(task2).joinMaybe();

    Maybe<List<String>> maybe = maybeResult.runSafe().orElse(Maybe.nothing());
    System.out.println("joinMaybe: " + maybe + "\n");
  }

  // ============================================================
  // ScopeJoiner Direct Usage
  // ============================================================

  private static void scopeJoinerExample() {
    System.out.println("--- ScopeJoiner Direct Usage ---\n");

    // ScopeJoiner can be created directly for advanced use cases
    ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

    // Use with Scope.withJoiner
    VTask<List<String>> result =
        Scope.withJoiner(joiner)
            .fork(VTask.succeed("A"))
            .fork(VTask.succeed("B"))
            .fork(VTask.succeed("C"))
            .join();

    List<String> values = result.runSafe().orElse(List.of());
    System.out.println("Using withJoiner: " + values + "\n");
  }

  // ============================================================
  // Real-World Example: Dashboard Aggregation
  // ============================================================

  private static void realWorldDashboardExample() {
    System.out.println("--- Real-World: Dashboard Aggregation ---\n");

    String userId = "user-123";

    System.out.println("Loading dashboard for " + userId + "...");
    long start = System.currentTimeMillis();

    VTask<Dashboard> dashboard = loadDashboard(userId);
    Try<Dashboard> result = dashboard.runSafe();

    long elapsed = System.currentTimeMillis() - start;

    String message =
        result.fold(
            d ->
                String.format(
                    "Dashboard loaded:%n  User: %s%n  Orders: %s%n  Notifications: %d",
                    d.userName, d.recentOrders, d.notificationCount),
            error -> "Dashboard failed: " + error.getMessage());
    System.out.println(message);
    System.out.println("Total time: " + elapsed + "ms (parallel fetch, ~150ms expected)\n");

    // With failure handling
    System.out.println("Loading dashboard with simulated failure...");
    VTask<Dashboard> failingDashboard = loadDashboardWithFailure(userId);
    Try<Dashboard> failResult = failingDashboard.runSafe();

    String failMessage =
        failResult.fold(
            d -> "Dashboard: " + d.userName, error -> "Expected failure: " + error.getMessage());
    System.out.println(failMessage + "\n");
  }

  // Helper: Load dashboard with parallel fetches
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
              return List.of("ORD-001", "ORD-002", "ORD-003");
            });

    VTask<Integer> notificationsTask =
        VTask.of(
            () -> {
              sleep(150);
              return 5;
            });

    return Scope.<Object>allSucceed()
        .fork(userTask)
        .fork(ordersTask)
        .fork(notificationsTask)
        .join()
        .map(
            results -> {
              @SuppressWarnings("unchecked")
              List<String> orders = (List<String>) results.get(1);
              return new Dashboard((String) results.get(0), orders, (Integer) results.get(2));
            });
  }

  // Helper: Dashboard with a failing service
  private static VTask<Dashboard> loadDashboardWithFailure(String userId) {
    VTask<String> userTask =
        VTask.of(
            () -> {
              sleep(50);
              return "Alice Smith";
            });

    VTask<List<String>> ordersTask =
        VTask.of(
            () -> {
              sleep(50);
              throw new RuntimeException("Order service unavailable");
            });

    VTask<Integer> notificationsTask =
        VTask.of(
            () -> {
              sleep(50);
              return 5;
            });

    return Scope.<Object>allSucceed()
        .fork(userTask)
        .fork(ordersTask)
        .fork(notificationsTask)
        .join()
        .map(
            results -> {
              @SuppressWarnings("unchecked")
              List<String> orders = (List<String>) results.get(1);
              return new Dashboard((String) results.get(0), orders, (Integer) results.get(2));
            });
  }

  // Dashboard record
  record Dashboard(String userName, List<String> recentOrders, int notificationCount) {}

  // Helper for sleep
  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
