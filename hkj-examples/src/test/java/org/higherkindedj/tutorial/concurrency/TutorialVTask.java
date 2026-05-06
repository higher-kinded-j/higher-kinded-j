// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VTask Fundamentals — virtual-thread-based concurrency.
 *
 * <p>Pain → Promise. Plain Java threads are heavy and ad-hoc; {@link
 * java.util.concurrent.CompletableFuture CompletableFuture} chains thread the success path through
 * {@code thenApply} but pile errors into a separate {@code exceptionally} hook; manual cancellation
 * through {@link java.util.concurrent.ExecutorService} is hand-rolled per call site.
 *
 * <pre>
 *   ExecutorService pool = Executors.newFixedThreadPool(4);
 *   Future&lt;User&gt;    fU = pool.submit(() -&gt; userService.find(id));
 *   Future&lt;Profile&gt; fP = pool.submit(() -&gt; profileService.find(id));
 *   try {
 *     User u = fU.get(5, TimeUnit.SECONDS);     // checked exceptions, possible interrupt
 *     Profile p = fP.get(5, TimeUnit.SECONDS);  // sequential get even though we ran in parallel
 *     return new Page(u, p);
 *   } catch (Exception e) { ... }
 * </pre>
 *
 * <p>{@link VTask} captures the same idea as a value: a deferred, virtual-thread-friendly
 * computation that we compose with {@code map} / {@code flatMap} (or {@code via}) and run with
 * {@code run()} / {@code runSafe()}. The {@link Par} combinators ({@code map2}, {@code race},
 * {@code traverse}, ...) handle parallelism with proper structured-concurrency cancellation.
 *
 * <p>Java idiom anchor:
 *
 * <ul>
 *   <li>{@code VTask.of(callable)} ↔ {@code Executor.submit(callable)}, but lazy and re-runnable.
 *   <li>{@code task.run()} ↔ {@code future.get()}.
 *   <li>{@code task.runSafe()} ↔ {@code try/catch} around {@code future.get()}, returning a {@link
 *       Try}.
 *   <li>{@code Par.map2(a, b, fn)} ↔ {@code CompletableFuture.thenCombine}, but with structured
 *       concurrency (one fails, the others are cancelled).
 *   <li>{@code Par.race(list)} ↔ {@code CompletableFuture.anyOf} on the success path, with
 *       cancellation of the losers.
 * </ul>
 *
 * <p>Requirements: Java 25+ for virtual threads and structured concurrency.
 */
@DisplayName("Tutorial: VTask Fundamentals")
public class TutorialVTask {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 1: Creating VTasks
  // ═════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Part 1: Creating VTasks")
  class CreatingVTasks {

    /**
     * Exercise 1: Create a VTask using {@link VTask#of}.
     *
     * <pre>
     *   // Nudge:    VTask.of takes a Callable; nothing executes until run().
     *   // Strategy: VTask.of(() -&gt; input.length())
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: Create a VTask with of()")
    void exercise1_createVTaskWithOf() {
      String input = "Hello, VTask!";

      VTask<Integer> lengthTask = answerRequired();

      Integer result = lengthTask.run();
      assertThat(result).isEqualTo(13);
    }

    /**
     * Exercise 2: Create succeeding and failing VTasks.
     *
     * <pre>
     *   // Nudge:    VTask.succeed lifts a value; VTask.fail lifts an exception.
     *   // Strategy: VTask.succeed(42) / VTask.fail(new RuntimeException("Expected failure"))
     *   // Spoiler:  see hint above.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: Create succeed and fail VTasks")
    void exercise2_succeedAndFail() {
      VTask<Integer> success = answerRequired();
      VTask<Integer> failure = answerRequired();

      Try<Integer> successResult = success.runSafe();
      assertThat(successResult.isSuccess()).isTrue();
      assertThat(successResult.orElse(-1)).isEqualTo(42);

      Try<Integer> failureResult = failure.runSafe();
      assertThat(failureResult.isFailure()).isTrue();
    }
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 2: Transforming VTasks
  // ═════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Part 2: Transforming VTasks")
  class TransformingVTasks {

    /**
     * Exercise 3: Transform with {@code map}.
     *
     * <pre>
     *   // Nudge:    map applies a function to the eventual result.
     *   // Strategy: greeting.map(String::length)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: Transform with map()")
    void exercise3_mapTransformation() {
      VTask<String> greeting = VTask.succeed("Hello, Virtual Threads!");

      VTask<Integer> length = answerRequired();

      Integer result = length.run();
      assertThat(result).isEqualTo(23);
    }

    /**
     * Exercise 4: Chain dependent computations with {@code flatMap}.
     *
     * <pre>
     *   // Nudge:    Each step takes the previous result and returns a new VTask.
     *   // Strategy: getNumber.flatMap(n -&gt; VTask.succeed(n * 2))
     *   // Spoiler:  exactly that. (via is an alias.)
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: Chain with flatMap()")
    void exercise4_flatMapChaining() {
      VTask<Integer> getNumber = VTask.succeed(21);

      VTask<Integer> doubled = answerRequired();

      Integer result = doubled.run();
      assertThat(result).isEqualTo(42);
    }
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 3: Error Handling
  // ═════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Part 3: Error Handling")
  class ErrorHandling {

    /**
     * Exercise 5: Execute safely with {@code runSafe}.
     *
     * <pre>
     *   // Nudge:    runSafe returns Try; isFailure / fold are the standard interrogators.
     *   // Strategy: result.isFailure() and result.fold(v -&gt; "no error", err -&gt; err.getMessage())
     *   // Spoiler:  see hint above.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 5: Safe execution with runSafe()")
    void exercise5_safeExecution() {
      VTask<String> failingTask = VTask.fail(new RuntimeException("Database connection failed"));

      Try<String> result = failingTask.runSafe();

      boolean isFailure = answerRequired();
      assertThat(isFailure).isTrue();

      String errorMessage = answerRequired();
      assertThat(errorMessage).isEqualTo("Database connection failed");
    }

    /**
     * Exercise 6: Recover from failures.
     *
     * <pre>
     *   // Nudge:    recover takes a function from the exception to a fallback value.
     *   // Strategy: failingTask.recover(error -&gt; -1)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 6: Recover from failure")
    void exercise6_recoverFromFailure() {
      VTask<Integer> failingTask = VTask.fail(new RuntimeException("Computation failed"));

      VTask<Integer> recovered = answerRequired();

      Integer result = recovered.run();
      assertThat(result).isEqualTo(-1);
    }
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 4: Parallel Composition
  // ═════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Part 4: Parallel Composition")
  class ParallelComposition {

    /**
     * Exercise 7: Execute tasks in parallel with {@link Par#map2}.
     *
     * <pre>
     *   // Nudge:    Par.map2 fans out two tasks and combines their results.
     *   // Strategy: Par.map2(taskA, taskB, Integer::sum)
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 7: Parallel execution with map2()")
    void exercise7_parallelMap2() {
      VTask<Integer> taskA =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return 20;
              });

      VTask<Integer> taskB =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return 22;
              });

      VTask<Integer> combined = answerRequired();

      Integer result = combined.run();
      assertThat(result).isEqualTo(42);
    }

    /**
     * Exercise 8: Race tasks with {@link Par#race}.
     *
     * <pre>
     *   // Nudge:    Par.race takes a List of tasks; the rest are cancelled.
     *   // Strategy: Par.race(List.of(slow, medium, fast))
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 8: Race tasks with Par.race()")
    void exercise8_raceTasks() {
      VTask<String> slow =
          VTask.of(
              () -> {
                Thread.sleep(100);
                return "slow";
              });

      VTask<String> medium =
          VTask.of(
              () -> {
                Thread.sleep(50);
                return "medium";
              });

      VTask<String> fast =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "fast";
              });

      VTask<String> winner = answerRequired();

      String result = winner.run();
      assertThat(result).isEqualTo("fast");
    }
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: VTask is lazy and re-runnable. Calling {@code .run()} twice runs the work twice.
   *
   * <p>If we want to share a result, capture it once and reuse the value. If we want a single
   * compiled value that can be safely shared (and re-run yields the same answer), the IO-style
   * {@code IOPath.memoize} or a one-shot {@code Lazy} value is the right tool.
   *
   * <p>The exercise below demonstrates the laziness — running a side-effecting VTask twice
   * <em>does</em> run the side effect twice. The fix is to capture the result before reuse.
   *
   * <pre>
   *   // Nudge:    Run once, then reuse the captured value.
   *   // Strategy: int captured = task.run(); use captured + captured;
   *   // Spoiler:  see the solution.
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: VTask is re-runnable; capture the result before reusing it")
  void diagnostic_runsAreNotMemoised() {
    int[] counter = new int[] {0};
    VTask<Integer> task =
        VTask.of(
            () -> {
              counter[0]++;
              return counter[0];
            });

    int sumOfTwoRuns = answerRequired();

    assertThat(sumOfTwoRuns).isEqualTo(2); // 1 + 1, captured once and reused twice
    assertThat(counter[0]).isEqualTo(1); // the side effect ran exactly once
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Bonus: Putting It All Together
  // ═════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /** Demonstrates a complete VTask workflow; reference only, no exercise. */
    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() {
      VTask<String> fetchUser =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "Alice";
              });

      VTask<Integer> fetchAge =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return 30;
              });

      VTask<String> profile =
          Par.map2(fetchUser, fetchAge, (name, age) -> name + " (age " + age + ")");

      VTask<String> safeProfile = profile.recover(error -> "Unknown user");

      String result = safeProfile.run();
      assertThat(result).isEqualTo("Alice (age 30)");

      // Showing List import is used (suppressing unused warning).
      assertThat(List.of(safeProfile)).hasSize(1);
    }
  }
}
