// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.assertions.VTaskPathAssert.assertThatVTaskPath;

import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VTaskPath Effect API — fluent virtual-thread concurrency.
 *
 * <p>Pain → Promise. {@code VTask} composes with {@code map} / {@code flatMap}, but production
 * concurrency wants more: timeouts, fallback chains, peek-based debugging, and a fluent shape that
 * mixes with the rest of the Effect Path API. {@link org.higherkindedj.hkt.effect.VTaskPath} is
 * exactly that — a {@link org.higherkindedj.hkt.effect.Path} wrapper around {@code VTask} that
 * carries the same surface as {@link org.higherkindedj.hkt.effect.MaybePath} / {@link
 * org.higherkindedj.hkt.effect.EitherPath} but with virtual-thread execution underneath.
 *
 * <p>Java idiom anchor:
 *
 * <ul>
 *   <li>{@code Path.vtask(callable)} ↔ {@code Executor.submit(callable)}, fluent.
 *   <li>{@code path.timeout(Duration)} ↔ {@code future.orTimeout(...)}, with cancellation
 *       semantics.
 *   <li>{@code path.handleError(fn)} ↔ {@code future.exceptionally(fn)} but typed.
 *   <li>{@code path.handleErrorWith(fn)} ↔ {@code future.exceptionallyCompose(fn)}.
 * </ul>
 *
 * <p>Prerequisites: complete {@code TutorialVTask} for VTask fundamentals.
 */
@DisplayName("Tutorial: VTaskPath Effect API")
public class TutorialVTaskPath {

  /** Helper for incomplete exercises that throws a clear exception. */
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
     * Exercise 1: Create a VTaskPath with {@link Path#vtask}.
     *
     * <pre>
     *   // Nudge:    Path.vtask wraps a Callable as a deferred Path.
     *   // Strategy: Path.vtask(() -&gt; input.length())
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: Create a VTaskPath with Path.vtask()")
    void exercise1_createVTaskPathWithVtask() {
      String input = "Hello, VTaskPath!";

      VTaskPath<Integer> lengthPath = answerRequired();

      // Execute safely and verify
      Try<Integer> result = lengthPath.runSafe();
      assertThatTry(result).isSuccess();
      assertThatTry(result).hasValue(17);
    }

    /**
     * Exercise 2: pure and failing VTaskPaths.
     *
     * <pre>
     *   // Nudge:    Path.vtaskPure / Path.vtaskFail are immediate-value factories.
     *   // Strategy: Path.vtaskPure("success") and Path.vtaskFail(new RuntimeException("Expected"))
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: Create pure and failing VTaskPaths")
    void exercise2_pureAndFailingPaths() {
      VTaskPath<String> purePath = answerRequired();
      VTaskPath<String> failedPath = answerRequired();

      // Verify pure path
      Try<String> pureResult = purePath.runSafe();
      assertThatTry(pureResult).isSuccess();
      assertThatTry(pureResult).hasValue("success");

      // Verify failed path
      Try<String> failedResult = failedPath.runSafe();
      assertThatTry(failedResult).isFailure();
    }
  }

  // ===========================================================================
  // Part 2: Fluent Composition
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Fluent Composition")
  class FluentComposition {

    /**
     * Exercise 3: chain operations with {@code via}.
     *
     * <pre>
     *   // Nudge:    Two dependent steps - parse, then double.
     *   // Strategy: input.via(s -&gt; Path.vtask(() -&gt; Integer.parseInt(s)))
     *   //                .via(n -&gt; Path.vtaskPure(n * 2))
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: Chain operations with via()")
    void exercise3_chainWithVia() {
      VTaskPath<String> input = Path.vtaskPure("21");

      VTaskPath<Integer> doubled = answerRequired();

      Try<Integer> result = doubled.runSafe();
      assertThatTry(result).isSuccess();
      assertThatTry(result).hasValue(42);
    }

    /**
     * Exercise 4: debug with {@code peek}.
     *
     * <pre>
     *   // Nudge:    peek lets us observe without modifying; map transforms in between.
     *   // Strategy: path.peek(v -&gt; counter.incrementAndGet()).map(v -&gt; v * 2)
     *   //               .peek(v -&gt; counter.incrementAndGet())
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: Debug with peek()")
    void exercise4_debugWithPeek() {
      AtomicInteger peekCount = new AtomicInteger(0);

      VTaskPath<Integer> path = Path.vtaskPure(10);

      VTaskPath<Integer> debugged = answerRequired();

      Try<Integer> result = debugged.runSafe();
      assertThatTry(result).hasValue(20);
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
     * Exercise 5: timeout with recovery.
     *
     * <pre>
     *   // Nudge:    timeout() returns a Path that fails with TimeoutException; handleError
     *   //           swaps the error for a value.
     *   // Strategy: slowOperation.timeout(Duration.ofMillis(100))
     *   //                        .handleError(e -&gt; "timeout fallback")
     *   // Spoiler:  exactly that. (Import java.time.Duration.)
     * </pre>
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

      VTaskPath<String> withTimeout = answerRequired();

      Try<String> result = withTimeout.runSafe();
      assertThatTry(result).isSuccess();
      assertThatTry(result).hasValue("timeout fallback");
    }

    /**
     * Exercise 6: fallback chain.
     *
     * <pre>
     *   // Nudge:    handleErrorWith chains another Path on failure; handleError returns a value.
     *   // Strategy: primary.handleErrorWith(e -&gt; secondary).handleError(e -&gt; "fallback value")
     *   // Spoiler:  exactly that.
     * </pre>
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

      VTaskPath<String> resilient = answerRequired();

      Try<String> result = resilient.runSafe();
      assertThatTry(result).isSuccess();
      assertThatTry(result).hasValue("fallback value");
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
     * Exercise 7: parallel service aggregation with {@code Par.map3}.
     *
     * <pre>
     *   // Nudge:    Par.map3 fans out three tasks; wrap the result in Path.vtaskPath.
     *   // Strategy: Path.vtaskPath(Par.map3(fetchName, fetchOrderCount, fetchStatus,
     *   //                                   UserProfile::new))
     *   // Spoiler:  exactly that.
     * </pre>
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

      VTaskPath<UserProfile> profile = answerRequired();

      Try<UserProfile> result = profile.runSafe();
      assertThatTry(result).isSuccess();

      UserProfile user = result.orElse(null);
      assertThat(user).isNotNull();
      assertThat(user.name()).isEqualTo("Alice");
      assertThat(user.orderCount()).isEqualTo(5);
      assertThat(user.status()).isEqualTo("active");
    }

    /**
     * Exercise 8: resilient dashboard loader — parallelism + timeout + fallback.
     *
     * <pre>
     *   // Nudge:    Three pieces - parallel fetch, timeout wrapper, fallback handler.
     *   // Strategy: Path.vtaskPath(Par.map2(fetchGreeting, fetchNotifications, Dashboard::new))
     *   //               .timeout(Duration.ofSeconds(1))
     *   //               .handleError(e -&gt; Dashboard.empty())
     *   // Spoiler:  exactly that.
     * </pre>
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
      assertThatTry(result).isSuccess();

      Dashboard d = result.orElse(null);
      assertThat(d).isNotNull();
      assertThat(d.greeting()).isEqualTo("Welcome, Alice!");
      assertThat(d.notificationCount()).isEqualTo(3);
    }
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: timeout cancels the underlying VTask. Long-running side effects do not finish on
   * the side after the timeout fires; structured concurrency cancels them.
   *
   * <p>This is the right behaviour, but worth seeing once: code that was racing the timeout cannot
   * assume the side effect "kept running silently".
   *
   * <pre>
   *   // Nudge:    On timeout, the inner work is interrupted.
   *   // Strategy: Use a counter incremented inside the task; observe it never reaches the
   *   //           expected value when the timeout fires.
   *   // Spoiler:  see the solution.
   * </pre>
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

    Integer result = answerRequired();

    assertThat(result).isEqualTo(-1);
    // The counter is non-zero (some iterations completed) but less than 5: the rest were cancelled.
    assertThat(counter.get()).isLessThan(5);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Bonus: Converting Between Types
  // ═════════════════════════════════════════════════════════════════════════

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
      assertThatVTaskPath(path).hasValue(42);
      assertThatVTask(backToVtask).whenRun().succeeds().hasValue(42);
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
