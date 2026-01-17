// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VTaskPath Effect API - Fluent Virtual Thread Concurrency
 *
 * <p>Learn to work with VTaskPath, the Effect Path wrapper for VTask. VTaskPath provides a fluent,
 * composable API for concurrent operations that integrates with the broader Effect Path ecosystem.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>VTaskPath wraps VTask to provide Effect Path style composition
 *   <li>Use Path.vtask() to create paths from callables
 *   <li>Chain operations with via() (Effect Path's flatMap)
 *   <li>Add timeouts with timeout(Duration) for production safety
 *   <li>Build resilient workflows with handleError() and handleErrorWith()
 * </ul>
 *
 * <p>Prerequisites: Complete TutorialVTask first for VTask fundamentals.
 *
 * <p>See the documentation: VTaskPath in hkj-book
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VTaskPath Effect API")
public class TutorialVTaskPath {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Creating VTaskPaths
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VTaskPaths")
  class CreatingVTaskPaths {

    /**
     * Exercise 1: Create a VTaskPath using Path.vtask()
     *
     * <p>Path.vtask() is the primary way to create a VTaskPath. It takes a Callable and wraps it in
     * a lazy computation that will execute on a virtual thread.
     *
     * <p>Task: Create a VTaskPath that computes the length of "Hello, VTaskPath!"
     */
    @Test
    @DisplayName("Exercise 1: Create a VTaskPath with Path.vtask()")
    void exercise1_createVTaskPathWithVtask() {
      String input = "Hello, VTaskPath!";

      // TODO: Replace answerRequired() with Path.vtask(() -> input.length())
      VTaskPath<Integer> lengthPath = answerRequired();

      // Execute safely and verify
      Try<Integer> result = lengthPath.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(-1)).isEqualTo(17);
    }

    /**
     * Exercise 2: Create pure and failing VTaskPaths
     *
     * <p>Path.vtaskPure() creates a VTaskPath with an immediate value. Path.vtaskFail() creates a
     * VTaskPath that fails with the given exception.
     *
     * <p>Task: Create a pure VTaskPath with value "success" and a failing VTaskPath
     */
    @Test
    @DisplayName("Exercise 2: Create pure and failing VTaskPaths")
    void exercise2_pureAndFailingPaths() {
      // TODO: Replace answerRequired() with Path.vtaskPure("success")
      VTaskPath<String> purePath = answerRequired();

      // TODO: Replace answerRequired() with Path.vtaskFail(new RuntimeException("Expected"))
      VTaskPath<String> failedPath = answerRequired();

      // Verify pure path
      Try<String> pureResult = purePath.runSafe();
      assertThat(pureResult.isSuccess()).isTrue();
      assertThat(pureResult.orElse("default")).isEqualTo("success");

      // Verify failed path
      Try<String> failedResult = failedPath.runSafe();
      assertThat(failedResult.isFailure()).isTrue();
    }
  }

  // ===========================================================================
  // Part 2: Fluent Composition
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Fluent Composition")
  class FluentComposition {

    /**
     * Exercise 3: Chain VTaskPath operations with via()
     *
     * <p>The via() method chains dependent computations, similar to flatMap but following Effect
     * Path naming conventions. Each via() returns a new VTaskPath.
     *
     * <p>Task: Chain operations to parse a string to an integer and then double it
     */
    @Test
    @DisplayName("Exercise 3: Chain operations with via()")
    void exercise3_chainWithVia() {
      VTaskPath<String> input = Path.vtaskPure("21");

      // TODO: Replace answerRequired() with:
      // input.via(s -> Path.vtask(() -> Integer.parseInt(s)))
      //      .via(n -> Path.vtaskPure(n * 2))
      VTaskPath<Integer> doubled = answerRequired();

      Try<Integer> result = doubled.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(-1)).isEqualTo(42);
    }

    /**
     * Exercise 4: Use peek() for debugging
     *
     * <p>The peek() method allows you to observe values as they flow through the pipeline without
     * modifying them. This is useful for logging and debugging.
     *
     * <p>Task: Use peek() to count how many times a value passes through the pipeline
     */
    @Test
    @DisplayName("Exercise 4: Debug with peek()")
    void exercise4_debugWithPeek() {
      AtomicInteger peekCount = new AtomicInteger(0);

      VTaskPath<Integer> path = Path.vtaskPure(10);

      // TODO: Replace answerRequired() with:
      // path.peek(v -> peekCount.incrementAndGet())
      //     .map(v -> v * 2)
      //     .peek(v -> peekCount.incrementAndGet())
      VTaskPath<Integer> debugged = answerRequired();

      Try<Integer> result = debugged.runSafe();
      assertThat(result.orElse(-1)).isEqualTo(20);
      assertThat(peekCount.get()).isEqualTo(2); // peek was called twice
    }
  }

  // ===========================================================================
  // Part 3: Error Handling and Timeouts
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Error Handling and Timeouts")
  class ErrorHandlingAndTimeouts {

    /**
     * Exercise 5: Add a timeout to an operation
     *
     * <p>The timeout() method adds a deadline to an operation. If the operation takes longer than
     * the specified duration, it fails with a TimeoutException.
     *
     * <p>Task: Add a timeout to a slow operation and recover with a default value
     */
    @Test
    @DisplayName("Exercise 5: Add timeout with recovery")
    void exercise5_timeoutWithRecovery() {
      VTaskPath<String> slowOperation =
          Path.vtask(
              () -> {
                Thread.sleep(500); // Slow operation
                return "slow result";
              });

      // TODO: Replace answerRequired() with:
      // slowOperation.timeout(Duration.ofMillis(100))
      //              .handleError(e -> "timeout fallback")
      VTaskPath<String> withTimeout = answerRequired();

      Try<String> result = withTimeout.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("error")).isEqualTo("timeout fallback");
    }

    /**
     * Exercise 6: Build a fallback chain
     *
     * <p>Use handleErrorWith() to try alternative operations when the primary fails. This creates a
     * chain of fallback attempts.
     *
     * <p>Task: Build a chain that tries primary, then secondary, then returns a default
     */
    @Test
    @DisplayName("Exercise 6: Build fallback chain")
    void exercise6_fallbackChain() {
      AtomicInteger attemptCount = new AtomicInteger(0);

      VTaskPath<String> primary =
          Path.vtask(
              () -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("Primary failed");
              });

      VTaskPath<String> secondary =
          Path.vtask(
              () -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("Secondary failed");
              });

      // TODO: Replace answerRequired() with:
      // primary.handleErrorWith(e -> secondary)
      //        .handleError(e -> "fallback value")
      VTaskPath<String> resilient = answerRequired();

      Try<String> result = resilient.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("error")).isEqualTo("fallback value");
      assertThat(attemptCount.get()).isEqualTo(2); // Both primary and secondary were attempted
    }
  }

  // ===========================================================================
  // Part 4: Real-World Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Real-World Patterns")
  class RealWorldPatterns {

    record UserProfile(String name, int orderCount, String status) {}

    /**
     * Exercise 7: Aggregate data from multiple services in parallel
     *
     * <p>Use Par.map3() to fetch data from multiple services concurrently and combine the results.
     * This is faster than sequential fetching.
     *
     * <p>Task: Fetch user, orders, and status in parallel and combine into UserProfile
     */
    @Test
    @DisplayName("Exercise 7: Parallel service aggregation")
    void exercise7_parallelAggregation() {
      VTask<String> fetchName =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "Alice";
              });

      VTask<Integer> fetchOrderCount =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return 5;
              });

      VTask<String> fetchStatus =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "active";
              });

      // TODO: Replace answerRequired() with:
      // Path.vtaskPath(Par.map3(fetchName, fetchOrderCount, fetchStatus, UserProfile::new))
      VTaskPath<UserProfile> profile = answerRequired();

      Try<UserProfile> result = profile.runSafe();
      assertThat(result.isSuccess()).isTrue();

      UserProfile user = result.orElse(null);
      assertThat(user).isNotNull();
      assertThat(user.name()).isEqualTo("Alice");
      assertThat(user.orderCount()).isEqualTo(5);
      assertThat(user.status()).isEqualTo("active");
    }

    /**
     * Exercise 8: Build a resilient dashboard loader
     *
     * <p>Combine parallel fetching with timeouts and fallbacks to build a production-ready data
     * loader. This demonstrates the full power of VTaskPath composition.
     *
     * <p>Task: Load a dashboard with timeout protection and graceful fallback
     */
    @Test
    @DisplayName("Exercise 8: Resilient dashboard loader")
    void exercise8_resilientDashboard() {
      record Dashboard(String greeting, int notificationCount) {
        static Dashboard empty() {
          return new Dashboard("Guest", 0);
        }
      }

      VTask<String> fetchGreeting =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "Welcome, Alice!";
              });

      VTask<Integer> fetchNotifications =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return 3;
              });

      // TODO: Replace answerRequired() with a VTaskPath that:
      // 1. Uses Par.map2 to fetch greeting and notifications in parallel
      // 2. Wraps the result in VTaskPath using Path.vtaskPath()
      // 3. Adds a timeout of 1 second
      // 4. Handles errors with Dashboard.empty() on any failure
      //
      // Hint:
      // Path.vtaskPath(Par.map2(fetchGreeting, fetchNotifications, Dashboard::new))
      //     .timeout(Duration.ofSeconds(1))
      //     .handleError(e -> Dashboard.empty())
      VTaskPath<Dashboard> dashboard = answerRequired();

      Try<Dashboard> result = dashboard.runSafe();
      assertThat(result.isSuccess()).isTrue();

      Dashboard d = result.orElse(null);
      assertThat(d).isNotNull();
      assertThat(d.greeting()).isEqualTo("Welcome, Alice!");
      assertThat(d.notificationCount()).isEqualTo(3);
    }
  }

  // ===========================================================================
  // Bonus: Converting Between Types
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Type Conversions")
  class TypeConversions {

    /**
     * This test demonstrates converting between VTask and VTaskPath.
     *
     * <ul>
     *   <li>VTask to VTaskPath: Use Path.vtaskPath(vtask)
     *   <li>VTaskPath to VTask: Use vtaskPath.run()
     * </ul>
     *
     * <p>This is provided as a reference - no exercise to complete.
     */
    @Test
    @DisplayName("Converting between VTask and VTaskPath")
    void conversionExample() {
      // VTask to VTaskPath
      VTask<Integer> vtask = VTask.succeed(42);
      VTaskPath<Integer> path = Path.vtaskPath(vtask);

      // VTaskPath to VTask
      VTask<Integer> backToVtask = path.run();

      // Both produce the same result
      assertThat(path.runSafe().orElse(-1)).isEqualTo(42);
      assertThat(backToVtask.runSafe().orElse(-1)).isEqualTo(42);
    }
  }

  /**
   * Congratulations! You've completed Tutorial: VTaskPath Effect API
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to create VTaskPaths with Path.vtask(), vtaskPure(), and vtaskFail()
   *   <li>✓ How to chain operations fluently with via() and map()
   *   <li>✓ How to debug pipelines with peek()
   *   <li>✓ How to add timeout protection with timeout(Duration)
   *   <li>✓ How to build fallback chains with handleError() and handleErrorWith()
   *   <li>✓ How to combine VTaskPath with Par for parallel execution
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>VTaskPath integrates VTask into the Effect Path ecosystem
   *   <li>Use via() for dependent chains, Par combinators for independent operations
   *   <li>Always add timeouts to production code that calls external services
   *   <li>Build resilient systems with layered fallbacks
   * </ul>
   *
   * <p>Next: Explore VTaskContext for dependency injection patterns.
   */
}
