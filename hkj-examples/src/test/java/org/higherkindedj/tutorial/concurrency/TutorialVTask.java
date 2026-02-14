// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VTask Fundamentals - Virtual Thread-Based Concurrency
 *
 * <p>Learn to work with VTask, Higher-Kinded-J's effect type for virtual thread-based concurrency.
 * VTask enables lightweight concurrent programming by describing computations lazily and executing
 * them on virtual threads.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>VTask is lazy: nothing executes until you call run(), runSafe(), or runAsync()
 *   <li>Use map() to transform results, flatMap() to chain dependent computations
 *   <li>Use Par combinators for parallel execution with structured concurrency
 *   <li>Prefer runSafe() for production code: it captures failures in Try
 * </ul>
 *
 * <p>Requirements: Java 25+ (virtual threads and structured concurrency)
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VTask Fundamentals")
public class TutorialVTask {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Creating VTasks
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating VTasks")
  class CreatingVTasks {

    /**
     * Exercise 1: Create a VTask using VTask.of()
     *
     * <p>VTask.of() takes a Callable and wraps it in a lazy computation. The callable won't execute
     * until you call run(), runSafe(), or runAsync().
     *
     * <p>Task: Create a VTask that computes the length of the string "Hello, VTask!"
     */
    @Test
    @DisplayName("Exercise 1: Create a VTask with of()")
    void exercise1_createVTaskWithOf() {
      String input = "Hello, VTask!";

      // TODO: Replace answerRequired() with VTask.of(() -> input.length())
      VTask<Integer> lengthTask = answerRequired();

      // Remember: Creating the VTask doesn't execute anything yet!
      // We must call run() to get the result
      Integer result = lengthTask.run();
      assertThat(result).isEqualTo(13);
    }

    /**
     * Exercise 2: Create succeeding and failing VTasks
     *
     * <p>VTask.succeed() creates a task that immediately returns a value. VTask.fail() creates a
     * task that immediately fails with an exception.
     *
     * <p>Task: Create a succeeding VTask with value 42 and a failing VTask with a RuntimeException
     */
    @Test
    @DisplayName("Exercise 2: Create succeed and fail VTasks")
    void exercise2_succeedAndFail() {
      // TODO: Replace answerRequired() with VTask.succeed(42)
      VTask<Integer> success = answerRequired();

      // TODO: Replace answerRequired() with VTask.fail(new RuntimeException("Expected failure"))
      VTask<Integer> failure = answerRequired();

      // Test success
      Try<Integer> successResult = success.runSafe();
      assertThat(successResult.isSuccess()).isTrue();
      assertThat(successResult.orElse(-1)).isEqualTo(42);

      // Test failure
      Try<Integer> failureResult = failure.runSafe();
      assertThat(failureResult.isFailure()).isTrue();
    }
  }

  // ===========================================================================
  // Part 2: Transforming VTasks
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Transforming VTasks")
  class TransformingVTasks {

    /**
     * Exercise 3: Transform a VTask result using map()
     *
     * <p>map() transforms the result of a VTask without executing it. The transformation function
     * is applied when the task is eventually run.
     *
     * <p>Task: Transform a VTask<String> to a VTask<Integer> by getting the string length
     */
    @Test
    @DisplayName("Exercise 3: Transform with map()")
    void exercise3_mapTransformation() {
      VTask<String> greeting = VTask.succeed("Hello, Virtual Threads!");

      // TODO: Replace answerRequired() with greeting.map(String::length)
      // or greeting.map(s -> s.length())
      VTask<Integer> length = answerRequired();

      Integer result = length.run();
      assertThat(result).isEqualTo(23);
    }

    /**
     * Exercise 4: Chain dependent computations with flatMap()
     *
     * <p>flatMap() (or its alias via()) chains computations where the next step depends on the
     * previous result. Each step produces a new VTask.
     *
     * <p>Task: Chain two VTasks - first get a number, then create a VTask that doubles it
     */
    @Test
    @DisplayName("Exercise 4: Chain with flatMap()")
    void exercise4_flatMapChaining() {
      VTask<Integer> getNumber = VTask.succeed(21);

      // TODO: Replace answerRequired() with:
      // getNumber.flatMap(n -> VTask.succeed(n * 2))
      // or using via(): getNumber.via(n -> VTask.succeed(n * 2))
      VTask<Integer> doubled = answerRequired();

      Integer result = doubled.run();
      assertThat(result).isEqualTo(42);
    }
  }

  // ===========================================================================
  // Part 3: Error Handling
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Error Handling")
  class ErrorHandling {

    /**
     * Exercise 5: Execute safely with runSafe()
     *
     * <p>runSafe() captures any exception in a Try, allowing functional error handling instead of
     * try-catch blocks.
     *
     * <p>Task: Execute a failing VTask safely and extract the error message
     */
    @Test
    @DisplayName("Exercise 5: Safe execution with runSafe()")
    void exercise5_safeExecution() {
      VTask<String> failingTask = VTask.fail(new RuntimeException("Database connection failed"));

      // Execute safely - this won't throw!
      Try<String> result = failingTask.runSafe();

      // TODO: Replace answerRequired() with result.isFailure()
      boolean isFailure = answerRequired();
      assertThat(isFailure).isTrue();

      // TODO: Extract the error message using result.fold()
      // Hint: result.fold(value -> "no error", error -> error.getMessage())
      String errorMessage = answerRequired();
      assertThat(errorMessage).isEqualTo("Database connection failed");
    }

    /**
     * Exercise 6: Recover from failures
     *
     * <p>recover() provides a fallback value when the task fails. The recovery function receives
     * the exception and returns a replacement value.
     *
     * <p>Task: Recover from a failing task with a default value of -1
     */
    @Test
    @DisplayName("Exercise 6: Recover from failure")
    void exercise6_recoverFromFailure() {
      VTask<Integer> failingTask = VTask.fail(new RuntimeException("Computation failed"));

      // TODO: Replace answerRequired() with:
      // failingTask.recover(error -> -1)
      VTask<Integer> recovered = answerRequired();

      Integer result = recovered.run();
      assertThat(result).isEqualTo(-1);
    }
  }

  // ===========================================================================
  // Part 4: Parallel Composition
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Parallel Composition")
  class ParallelComposition {

    /**
     * Exercise 7: Execute tasks in parallel with Par.map2()
     *
     * <p>Par.map2() executes two VTasks concurrently and combines their results using a function.
     * Both tasks run on separate virtual threads simultaneously.
     *
     * <p>Task: Execute two tasks in parallel and combine their results by addition
     */
    @Test
    @DisplayName("Exercise 7: Parallel execution with map2()")
    void exercise7_parallelMap2() {
      VTask<Integer> taskA =
          VTask.of(
              () -> {
                Thread.sleep(10); // Simulate work
                return 20;
              });

      VTask<Integer> taskB =
          VTask.of(
              () -> {
                Thread.sleep(10); // Simulate work
                return 22;
              });

      // TODO: Replace answerRequired() with:
      // Par.map2(taskA, taskB, (a, b) -> a + b)
      // or Par.map2(taskA, taskB, Integer::sum)
      VTask<Integer> combined = answerRequired();

      Integer result = combined.run();
      assertThat(result).isEqualTo(42);
    }

    /**
     * Exercise 8: Race multiple tasks with Par.race()
     *
     * <p>Par.race() starts all tasks in parallel and returns the result of the first one to
     * complete successfully. Other tasks are cancelled.
     *
     * <p>Task: Race three tasks and get the fastest result
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

      // TODO: Replace answerRequired() with:
      // Par.race(List.of(slow, medium, fast))
      VTask<String> winner = answerRequired();

      String result = winner.run();
      assertThat(result).isEqualTo("fast");
    }
  }

  // ===========================================================================
  // Bonus: Putting It All Together
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates a complete workflow using VTask:
     *
     * <ol>
     *   <li>Fetch user data (simulated)
     *   <li>Fetch profile data in parallel
     *   <li>Combine results
     *   <li>Handle potential errors
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
     */
    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() {
      // Simulated service calls
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

      // Run both in parallel and combine
      VTask<String> profile =
          Par.map2(fetchUser, fetchAge, (name, age) -> name + " (age " + age + ")");

      // Add error recovery
      VTask<String> safeProfile = profile.recover(error -> "Unknown user");

      // Execute and verify
      String result = safeProfile.run();
      assertThat(result).isEqualTo("Alice (age 30)");
    }
  }

  /**
   * Congratulations! You've completed Tutorial: VTask Fundamentals
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to create VTasks with of(), succeed(), fail(), and delay()
   *   <li>✓ That VTask is lazy: nothing runs until you call run() or runSafe()
   *   <li>✓ How to transform results with map() and chain computations with flatMap()
   *   <li>✓ How to handle errors safely with runSafe() and recover()
   *   <li>✓ How to run tasks in parallel with Par.map2() and Par.race()
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>VTask describes computations; execution is deferred until explicitly requested
   *   <li>Virtual threads enable millions of concurrent tasks with minimal overhead
   *   <li>Par combinators use StructuredTaskScope for proper cancellation semantics
   *   <li>Prefer runSafe() over run() for production code
   * </ul>
   *
   * <p>Next: Explore the VTask documentation for advanced patterns like timeouts and error mapping.
   */
}
