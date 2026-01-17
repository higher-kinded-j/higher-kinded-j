// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial: VTaskPath Effect API
 *
 * <p>This file contains complete solutions for all VTaskPath tutorial exercises.
 */
@DisplayName("Solutions: VTaskPath Effect API")
public class TutorialVTaskPath_Solution {

  // ===========================================================================
  // Part 1: Creating VTaskPaths
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VTaskPaths")
  class CreatingVTaskPaths {

    @Test
    @DisplayName("Exercise 1: Create a VTaskPath with Path.vtask()")
    void exercise1_createVTaskPathWithVtask() {
      String input = "Hello, VTaskPath!";

      // SOLUTION: Use Path.vtask() with a callable that computes the length
      VTaskPath<Integer> lengthPath = Path.vtask(() -> input.length());

      Try<Integer> result = lengthPath.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(-1)).isEqualTo(17);
    }

    @Test
    @DisplayName("Exercise 2: Create pure and failing VTaskPaths")
    void exercise2_pureAndFailingPaths() {
      // SOLUTION: Use Path.vtaskPure() for immediate values
      VTaskPath<String> purePath = Path.vtaskPure("success");

      // SOLUTION: Use Path.vtaskFail() for immediate failures
      VTaskPath<String> failedPath = Path.vtaskFail(new RuntimeException("Expected"));

      Try<String> pureResult = purePath.runSafe();
      assertThat(pureResult.isSuccess()).isTrue();
      assertThat(pureResult.orElse("default")).isEqualTo("success");

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

    @Test
    @DisplayName("Exercise 3: Chain operations with via()")
    void exercise3_chainWithVia() {
      VTaskPath<String> input = Path.vtaskPure("21");

      // SOLUTION: Chain via() calls for dependent computations
      VTaskPath<Integer> doubled =
          input.via(s -> Path.vtask(() -> Integer.parseInt(s))).via(n -> Path.vtaskPure(n * 2));

      Try<Integer> result = doubled.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(-1)).isEqualTo(42);
    }

    @Test
    @DisplayName("Exercise 4: Debug with peek()")
    void exercise4_debugWithPeek() {
      AtomicInteger peekCount = new AtomicInteger(0);

      VTaskPath<Integer> path = Path.vtaskPure(10);

      // SOLUTION: Use peek() to observe values without modifying them
      VTaskPath<Integer> debugged =
          path.peek(v -> peekCount.incrementAndGet())
              .map(v -> v * 2)
              .peek(v -> peekCount.incrementAndGet());

      Try<Integer> result = debugged.runSafe();
      assertThat(result.orElse(-1)).isEqualTo(20);
      assertThat(peekCount.get()).isEqualTo(2);
    }
  }

  // ===========================================================================
  // Part 3: Error Handling and Timeouts
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Error Handling and Timeouts")
  class ErrorHandlingAndTimeouts {

    @Test
    @DisplayName("Exercise 5: Add timeout with recovery")
    void exercise5_timeoutWithRecovery() {
      VTaskPath<String> slowOperation =
          Path.vtask(
              () -> {
                Thread.sleep(500);
                return "slow result";
              });

      // SOLUTION: Add timeout and handle error with fallback value
      VTaskPath<String> withTimeout =
          slowOperation.timeout(Duration.ofMillis(100)).handleError(e -> "timeout fallback");

      Try<String> result = withTimeout.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("error")).isEqualTo("timeout fallback");
    }

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

      // SOLUTION: Chain handleErrorWith for alternative paths, handleError for final fallback
      VTaskPath<String> resilient =
          primary.handleErrorWith(e -> secondary).handleError(e -> "fallback value");

      Try<String> result = resilient.runSafe();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("error")).isEqualTo("fallback value");
      assertThat(attemptCount.get()).isEqualTo(2);
    }
  }

  // ===========================================================================
  // Part 4: Real-World Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Real-World Patterns")
  class RealWorldPatterns {

    record UserProfile(String name, int orderCount, String status) {}

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

      // SOLUTION: Use Par.map3 for parallel execution, wrap in VTaskPath
      VTaskPath<UserProfile> profile =
          Path.vtaskPath(Par.map3(fetchName, fetchOrderCount, fetchStatus, UserProfile::new));

      Try<UserProfile> result = profile.runSafe();
      assertThat(result.isSuccess()).isTrue();

      UserProfile user = result.orElse(null);
      assertThat(user).isNotNull();
      assertThat(user.name()).isEqualTo("Alice");
      assertThat(user.orderCount()).isEqualTo(5);
      assertThat(user.status()).isEqualTo("active");
    }

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

      // SOLUTION: Combine Par.map2, timeout, and handleError for resilient loading
      VTaskPath<Dashboard> dashboard =
          Path.vtaskPath(Par.map2(fetchGreeting, fetchNotifications, Dashboard::new))
              .timeout(Duration.ofSeconds(1))
              .handleError(e -> Dashboard.empty());

      Try<Dashboard> result = dashboard.runSafe();
      assertThat(result.isSuccess()).isTrue();

      Dashboard d = result.orElse(null);
      assertThat(d).isNotNull();
      assertThat(d.greeting()).isEqualTo("Welcome, Alice!");
      assertThat(d.notificationCount()).isEqualTo(3);
    }
  }

  // ===========================================================================
  // Bonus: Type Conversions
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Type Conversions")
  class TypeConversions {

    @Test
    @DisplayName("Converting between VTask and VTaskPath")
    void conversionExample() {
      VTask<Integer> vtask = VTask.succeed(42);
      VTaskPath<Integer> path = Path.vtaskPath(vtask);

      VTask<Integer> backToVtask = path.run();

      assertThat(path.runSafe().orElse(-1)).isEqualTo(42);
      assertThat(backToVtask.runSafe().orElse(-1)).isEqualTo(42);
    }
  }
}
