// Copyright (c) 2025 - 2026 Magnus Smith
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
 * Solutions for Tutorial: VTaskPath Effect API.
 *
 * <p>This solution file follows the chapter's teaching-solution conventions established by the
 * Foundations journey: read the working code first, then the commentary on why it is the idiomatic
 * choice. The working bodies below are unchanged from the original; future passes will add
 * per-exercise <em>Why this is idiomatic / Alternative / Common wrong attempt</em> notes in the
 * same shape as {@code Tutorial01_KindBasics_Solution} and {@code
 * Tutorial01_EffectPathBasics_Solution}.
 *
 * <p>Pattern reminders that apply to every exercise here:
 *
 * <ul>
 *   <li>{@code Path.vtask(callable)} for deferred work; {@code Path.vtaskPure(value)} for immediate
 *       values; {@code Path.vtaskFail(throwable)} for immediate failure.
 *   <li>{@code via} for dependent steps; {@code map} when the function returns a plain value.
 *   <li>{@code timeout(Duration)} + {@code handleError} / {@code handleErrorWith} is the
 *       production-safe shape for any external-service call.
 *   <li>{@code Par.map2} / {@code Par.map3} for independent parallel work; let {@code handleError}
 *       catch the failure of any one task and decide on a fallback.
 * </ul>
 */
@DisplayName("Solutions: VTaskPath Effect API")
public class TutorialVTaskPath_Solution {

  // ===========================================================================
  // Part 1: Creating VTaskPaths
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VTaskPaths")
  class CreatingVTaskPaths {

    /**
     * Why this is idiomatic: {@code Path.vtask(callable)} wraps a deferred computation in the path
     * layer. The body runs when the path is executed via {@code runSafe} or {@code run}.
     *
     * <p>Alternative: {@code VTask.of(...)} for the underlying task. The path- first form composes
     * with the rest of the {@code VTaskPath} vocabulary.
     *
     * <p>Common wrong attempt: invoke the callable eagerly. The point of {@code Path.vtask} is
     * deferral; pass the lambda, not its result.
     */
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

    /**
     * Why this is idiomatic: {@code Path.vtaskPure(value)} and {@code Path.vtaskFail(throwable)}
     * are the success/failure constructors. No lambda — just a value or an exception.
     *
     * <p>Alternative: {@code Path.vtask(() -> value)} or {@code Path.vtask(() -> { throw ...; })}.
     * Same outcome; the named constructors signal the intent.
     *
     * <p>Common wrong attempt: use {@code Path.vtask} when {@code vtaskPure} suffices. The lambda
     * is unnecessary indirection for static values.
     */
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

    /**
     * Why this is idiomatic: {@code via(fn)} threads the result of one path into the next. Parse
     * the string, then double the integer — two named steps.
     *
     * <p>Alternative: a single {@code map} for one transform. {@code via} is the right fit when
     * each step is itself a path.
     *
     * <p>Common wrong attempt: chain {@code map} when the next step returns a {@code VTaskPath}.
     * {@code map} would nest; use {@code via} to flatten.
     */
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

    /**
     * Why this is idiomatic: {@code peek(consumer)} runs a side effect on the value without
     * modifying it. Useful for logging, metrics, or test assertions.
     *
     * <p>Alternative: {@code map(v -> { sideEffect(v); return v; })}. Equivalent; {@code peek}
     * signals "observe-only" intent.
     *
     * <p>Common wrong attempt: rely on peek to short-circuit on certain values. Peek only observes;
     * use {@code via} or {@code handleError} for control flow.
     */
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

    /**
     * Why this is idiomatic: {@code timeout(duration)} aborts a slow task with an exception; {@code
     * handleError} substitutes a fallback value. Pair them for deadline-aware recovery.
     *
     * <p>Alternative: catch {@code TimeoutException} in a wrapper. Same outcome; the path operators
     * stay declarative.
     *
     * <p>Common wrong attempt: pick a timeout shorter than the slowest acceptable task. The
     * fallback fires inappropriately; tune the timeout.
     */
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

    /**
     * Why this is idiomatic: {@code handleErrorWith} chains an alternative path (which may itself
     * fail); {@code handleError} provides a final value. The pair forms a tiered fallback.
     *
     * <p>Alternative: nested try/catch. Equivalent; the path version reads as a pipeline.
     *
     * <p>Common wrong attempt: assume {@code handleErrorWith} catches every exception. It catches
     * whatever the underlying path throws; tighten with a specific predicate if needed.
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

    /**
     * Why this is idiomatic: {@code Par.map3(t1, t2, t3, ctor)} runs three tasks in parallel and
     * combines their results. Wrap in a {@code VTaskPath} to add timeout/recovery operators.
     *
     * <p>Alternative: chain {@code via} sequentially. Triples the wall-clock time; parallel
     * composition is the win for independent fetches.
     *
     * <p>Common wrong attempt: build the profile from sequentially-fetched values. Sequential
     * ordering is unnecessary when the fetches do not depend on each other.
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

    /**
     * Why this is idiomatic: parallel fetches + timeout + fallback. {@code Par.map2} runs the two
     * fetches concurrently; {@code timeout} bounds total time; {@code handleError} provides an
     * empty dashboard when anything fails.
     *
     * <p>Alternative: sequential fetches with manual timeout tracking. Brittle; the operator stack
     * provides the same semantics declaratively.
     *
     * <p>Common wrong attempt: skip the timeout. A slow backend hangs the endpoint; always bound
     * external calls.
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
  // Diagnostic: timeout cancels the underlying VTask, not just the result
  // ===========================================================================

  /**
   * Why this is idiomatic: {@code timeout(duration).handleError(...)} aborts the underlying VTask
   * when the deadline expires. The counter never reaches its expected total — proof that the
   * remaining iterations were cancelled, not merely abandoned by the caller.
   *
   * <p>Alternative: race the task against a delay and ignore the slower result. Same observable
   * behaviour at the boundary; loses the cancellation — the task continues consuming resources
   * until it finishes.
   *
   * <p>Common wrong attempt: assume the task carries on after the timeout and the counter still
   * reaches 5. The cooperative cancellation interrupts the sleep and the loop exits early.
   */
  @Test
  @DisplayName("Diagnostic: timeout cancels the underlying VTask, not just the result")
  void diagnostic_timeoutCancelsTheTask() {
    AtomicInteger counter = new AtomicInteger(0);
    VTaskPath<Integer> work =
        Path.vtask(
            () -> {
              for (int i = 0; i < 5; i++) {
                Thread.sleep(50);
                counter.incrementAndGet();
              }
              return counter.get();
            });

    Integer result = work.timeout(Duration.ofMillis(100)).handleError(e -> -1).run().run();

    assertThat(result).isEqualTo(-1);
    assertThat(counter.get()).isLessThan(5);
  }

  // ===========================================================================
  // Bonus: Type Conversions
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Type Conversions")
  class TypeConversions {

    /**
     * Why this is idiomatic: {@code Path.vtaskPath(vtask)} lifts a VTask into the path layer;
     * {@code path.run()} extracts the VTask back out. The two forms coexist for different use
     * cases.
     *
     * <p>Alternative: stay in one layer. Convert at the boundary when the next stage demands the
     * other shape.
     *
     * <p>Common wrong attempt: convert repeatedly. Each conversion is cheap but pointless; pick a
     * layer per pipeline.
     */
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
