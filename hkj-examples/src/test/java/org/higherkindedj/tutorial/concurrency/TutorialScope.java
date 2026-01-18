// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: Structured Concurrency with Scope
 *
 * <p>Learn to coordinate multiple concurrent tasks using Scope, Higher-Kinded-J's fluent API for
 * structured concurrency. Scope wraps Java 25's StructuredTaskScope with functional result
 * handling.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Scope provides different joining strategies: allSucceed, anySucceed, firstComplete,
 *       accumulating
 *   <li>Tasks are forked into a scope and execute concurrently on virtual threads
 *   <li>Join strategies determine how results are collected and when cancellation occurs
 *   <li>Safe wrappers (joinSafe, joinEither, joinMaybe) capture failures functionally
 * </ul>
 *
 * <p>Prerequisites: Complete TutorialVTask first
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: Structured Concurrency with Scope")
public class TutorialScope {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ===========================================================================
  // Part 1: Creating Scopes
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating Scopes")
  class CreatingScopes {

    /**
     * Exercise 1: Create a scope with allSucceed()
     *
     * <p>Scope.allSucceed() creates a scope that waits for all forked tasks to complete
     * successfully. If any task fails, the entire scope fails and remaining tasks are cancelled.
     *
     * <p>Task: Create a scope that forks three tasks and collects all results into a List
     */
    @Test
    @DisplayName("Exercise 1: Create scope with allSucceed()")
    void exercise1_allSucceed() throws Throwable {
      VTask<String> task1 = VTask.succeed("first");
      VTask<String> task2 = VTask.succeed("second");
      VTask<String> task3 = VTask.succeed("third");

      // TODO: Replace answerRequired() with:
      // Scope.<String>allSucceed()
      //     .fork(task1)
      //     .fork(task2)
      //     .fork(task3)
      //     .join()
      VTask<List<String>> scopeTask = answerRequired();

      List<String> results = scopeTask.run();
      assertThat(results).containsExactlyInAnyOrder("first", "second", "third");
    }

    /**
     * Exercise 2: Create a scope with anySucceed()
     *
     * <p>Scope.anySucceed() returns the first successful result and cancels remaining tasks. This
     * is useful for racing redundant requests (e.g., fetching from multiple mirrors).
     *
     * <p>Task: Create a scope that returns the first successful result
     */
    @Test
    @DisplayName("Exercise 2: Create scope with anySucceed()")
    void exercise2_anySucceed() throws Throwable {
      VTask<String> slow =
          VTask.of(
              () -> {
                Thread.sleep(50);
                return "slow";
              });

      VTask<String> fast =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "fast";
              });

      // TODO: Replace answerRequired() with:
      // Scope.<String>anySucceed()
      //     .fork(slow)
      //     .fork(fast)
      //     .join()
      VTask<String> scopeTask = answerRequired();

      String result = scopeTask.run();
      assertThat(result).isEqualTo("fast");
    }
  }

  // ===========================================================================
  // Part 2: Choosing Joiners
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Choosing Joiners")
  class ChoosingJoiners {

    /**
     * Exercise 3: Use firstComplete for racing strategies
     *
     * <p>Scope.firstComplete() returns the first result regardless of success or failure. This is
     * useful when you want to race a fast-but-risky operation against a slow-but-safe fallback.
     *
     * <p>Task: Create a scope that returns the first completed result
     */
    @Test
    @DisplayName("Exercise 3: Use firstComplete()")
    void exercise3_firstComplete() throws Throwable {
      VTask<String> fastButRisky =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "fast result";
              });

      VTask<String> slowButSafe =
          VTask.of(
              () -> {
                Thread.sleep(100);
                return "slow result";
              });

      // TODO: Replace answerRequired() with:
      // Scope.<String>firstComplete()
      //     .fork(fastButRisky)
      //     .fork(slowButSafe)
      //     .join()
      VTask<String> scopeTask = answerRequired();

      String result = scopeTask.run();
      assertThat(result).isEqualTo("fast result");
    }

    /**
     * Exercise 4: Add a timeout to a scope
     *
     * <p>Scopes support timeouts via the timeout() method. If the timeout expires before tasks
     * complete, the scope fails with a TimeoutException.
     *
     * <p>Task: Create a scope with a timeout that will trigger before the slow task completes
     */
    @Test
    @DisplayName("Exercise 4: Add timeout to scope")
    void exercise4_timeout() throws Throwable {
      VTask<String> slowTask =
          VTask.of(
              () -> {
                Thread.sleep(1000); // 1 second - too slow
                return "completed";
              });

      // TODO: Replace answerRequired() with:
      // Scope.<String>allSucceed()
      //     .timeout(Duration.ofMillis(50))
      //     .fork(slowTask)
      //     .joinSafe()
      VTask<Try<List<String>>> scopeTask = answerRequired();

      Try<List<String>> result = scopeTask.run();
      assertThat(result.isFailure()).isTrue();
    }
  }

  // ===========================================================================
  // Part 3: Error Accumulation
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Error Accumulation")
  class ErrorAccumulation {

    /**
     * Exercise 5: Accumulate errors with accumulating joiner
     *
     * <p>Scope.accumulating() runs all tasks to completion and collects both successes and
     * failures. Unlike allSucceed which fails fast, accumulating collects all errors - perfect for
     * validation scenarios.
     *
     * <p>Task: Create a scope that accumulates all validation errors
     */
    @Test
    @DisplayName("Exercise 5: Accumulate errors")
    void exercise5_accumulatingErrors() throws Throwable {
      VTask<String> valid1 = VTask.succeed("valid1");
      VTask<String> invalid1 = VTask.fail(new RuntimeException("Error 1"));
      VTask<String> invalid2 = VTask.fail(new RuntimeException("Error 2"));
      VTask<String> valid2 = VTask.succeed("valid2");

      // TODO: Replace answerRequired() with:
      // Scope.<String, String>accumulating(Throwable::getMessage)
      //     .fork(valid1)
      //     .fork(invalid1)
      //     .fork(invalid2)
      //     .fork(valid2)
      //     .join()
      VTask<Validated<List<String>, List<String>>> scopeTask = answerRequired();

      Validated<List<String>, List<String>> result = scopeTask.run();

      // The result should be Invalid with both error messages
      assertThat(result.isInvalid()).isTrue();
      result.fold(
          errors -> {
            assertThat(errors).containsExactlyInAnyOrder("Error 1", "Error 2");
            return null;
          },
          successes -> {
            throw new AssertionError("Expected Invalid but got Valid");
          });
    }

    /**
     * Exercise 6: Handle Validated result with fold
     *
     * <p>When all tasks succeed with an accumulating joiner, the result is a Valid containing all
     * successful values.
     *
     * <p>Task: Handle both success and failure cases of an accumulating scope
     */
    @Test
    @DisplayName("Exercise 6: Handle Validated result")
    void exercise6_handleValidated() throws Throwable {
      VTask<Integer> task1 = VTask.succeed(10);
      VTask<Integer> task2 = VTask.succeed(20);
      VTask<Integer> task3 = VTask.succeed(12);

      VTask<Validated<List<String>, List<Integer>>> scopeTask =
          Scope.<String, Integer>accumulating(Throwable::getMessage)
              .fork(task1)
              .fork(task2)
              .fork(task3)
              .join();

      Validated<List<String>, List<Integer>> result = scopeTask.run();

      // TODO: Replace answerRequired() with:
      // result.fold(
      //     errors -> -1,  // Sum of errors (won't be called)
      //     values -> values.stream().mapToInt(Integer::intValue).sum()
      // )
      Integer sum = answerRequired();
      assertThat(sum).isEqualTo(42);
    }
  }

  // ===========================================================================
  // Part 4: Safe Result Handling
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Safe Result Handling")
  class SafeResultHandling {

    /**
     * Exercise 7: Use joinSafe for Try-wrapped results
     *
     * <p>joinSafe() wraps the scope result in a Try, capturing any exceptions. This allows
     * functional error handling without try-catch blocks.
     *
     * <p>Task: Use joinSafe to capture a scope failure in a Try
     */
    @Test
    @DisplayName("Exercise 7: Use joinSafe()")
    void exercise7_joinSafe() throws Throwable {
      VTask<String> failingTask = VTask.fail(new RuntimeException("Task failed"));

      // TODO: Replace answerRequired() with:
      // Scope.<String>allSucceed()
      //     .fork(failingTask)
      //     .joinSafe()
      VTask<Try<List<String>>> scopeTask = answerRequired();

      Try<List<String>> result = scopeTask.run();
      assertThat(result.isFailure()).isTrue();
    }

    /**
     * Exercise 8: Use joinEither for Either-wrapped results
     *
     * <p>joinEither() wraps the result in Either<Throwable, R>, providing another functional
     * approach to error handling.
     *
     * <p>Task: Use joinEither to get results as Either
     */
    @Test
    @DisplayName("Exercise 8: Use joinEither()")
    void exercise8_joinEither() throws Throwable {
      VTask<String> task1 = VTask.succeed("hello");
      VTask<String> task2 = VTask.succeed("world");

      // TODO: Replace answerRequired() with:
      // Scope.<String>allSucceed()
      //     .fork(task1)
      //     .fork(task2)
      //     .joinEither()
      VTask<Either<Throwable, List<String>>> scopeTask = answerRequired();

      Either<Throwable, List<String>> result = scopeTask.run();
      assertThat(result.isRight()).isTrue();
      List<String> values = result.fold(e -> List.of(), v -> v);
      assertThat(values).containsExactlyInAnyOrder("hello", "world");
    }

    /**
     * Exercise 9: Use joinMaybe for Maybe-wrapped results
     *
     * <p>joinMaybe() returns Just(result) on success or Nothing on failure. This is useful when you
     * don't need error details.
     *
     * <p>Task: Use joinMaybe to get an optional result
     */
    @Test
    @DisplayName("Exercise 9: Use joinMaybe()")
    void exercise9_joinMaybe() throws Throwable {
      VTask<Integer> task = VTask.succeed(42);

      // TODO: Replace answerRequired() with:
      // Scope.<Integer>allSucceed()
      //     .fork(task)
      //     .joinMaybe()
      VTask<Maybe<List<Integer>>> scopeTask = answerRequired();

      Maybe<List<Integer>> result = scopeTask.run();
      assertThat(result.isJust()).isTrue();
      assertThat(result.orElse(List.of())).containsExactly(42);
    }
  }

  // ===========================================================================
  // Bonus: Complete Example
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates a complete workflow using Scope:
     *
     * <ol>
     *   <li>Fork multiple service calls in parallel
     *   <li>Use allSucceed to wait for all results
     *   <li>Add a timeout for resilience
     *   <li>Handle results safely with joinSafe
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
     */
    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() throws Throwable {
      // Simulated service calls
      VTask<String> fetchUser =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "Alice";
              });

      VTask<String> fetchProfile =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "Developer";
              });

      VTask<String> fetchPreferences =
          VTask.of(
              () -> {
                Thread.sleep(10);
                return "dark-mode";
              });

      // Fork all tasks with timeout and safe join
      VTask<Try<List<String>>> dashboard =
          Scope.<String>allSucceed()
              .timeout(Duration.ofSeconds(5))
              .fork(fetchUser)
              .fork(fetchProfile)
              .fork(fetchPreferences)
              .joinSafe();

      // Execute and handle result
      Try<List<String>> result = dashboard.run();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(List.of()))
          .containsExactlyInAnyOrder("Alice", "Developer", "dark-mode");
    }
  }

  /**
   * Congratulations! You've completed Tutorial: Structured Concurrency with Scope
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to create scopes with allSucceed(), anySucceed(), firstComplete()
   *   <li>✓ That fork() adds tasks to execute concurrently on virtual threads
   *   <li>✓ How different joiners determine result collection and cancellation
   *   <li>✓ How to accumulate errors with the accumulating joiner and Validated
   *   <li>✓ How to use safe wrappers: joinSafe(), joinEither(), joinMaybe()
   *   <li>✓ How to add timeouts for resilience
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>Scope wraps StructuredTaskScope with a fluent, functional API
   *   <li>Choose allSucceed when you need all results; anySucceed for racing
   *   <li>Use accumulating for validation scenarios where you want all errors
   *   <li>Safe wrappers (Try, Either, Maybe) enable functional error handling
   * </ul>
   *
   * <p>Next: Continue to TutorialResource for safe resource management with the bracket pattern.
   */
}
