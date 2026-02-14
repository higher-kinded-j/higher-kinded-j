// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VTask Fundamentals - SOLUTIONS
 *
 * <p>This file contains the complete solutions for all exercises in TutorialVTask.java.
 */
@DisplayName("Tutorial: VTask Fundamentals (Solutions)")
public class TutorialVTask_Solution {

  @Nested
  @DisplayName("Part 1: Creating VTasks")
  class CreatingVTasks {

    @Test
    @DisplayName("Exercise 1: Create a VTask with of()")
    void exercise1_createVTaskWithOf() {
      String input = "Hello, VTask!";

      // SOLUTION: Use VTask.of() with a lambda that computes the length
      VTask<Integer> lengthTask = VTask.of(() -> input.length());

      Integer result = lengthTask.run();
      assertThat(result).isEqualTo(13);
    }

    @Test
    @DisplayName("Exercise 2: Create succeed and fail VTasks")
    void exercise2_succeedAndFail() {
      // SOLUTION: Use VTask.succeed() for immediate success
      VTask<Integer> success = VTask.succeed(42);

      // SOLUTION: Use VTask.fail() for immediate failure
      VTask<Integer> failure = VTask.fail(new RuntimeException("Expected failure"));

      Try<Integer> successResult = success.runSafe();
      assertThat(successResult.isSuccess()).isTrue();
      assertThat(successResult.orElse(-1)).isEqualTo(42);

      Try<Integer> failureResult = failure.runSafe();
      assertThat(failureResult.isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Part 2: Transforming VTasks")
  class TransformingVTasks {

    @Test
    @DisplayName("Exercise 3: Transform with map()")
    void exercise3_mapTransformation() {
      VTask<String> greeting = VTask.succeed("Hello, Virtual Threads!");

      // SOLUTION: Use map() with String::length method reference
      VTask<Integer> length = greeting.map(String::length);

      Integer result = length.run();
      assertThat(result).isEqualTo(23);
    }

    @Test
    @DisplayName("Exercise 4: Chain with flatMap()")
    void exercise4_flatMapChaining() {
      VTask<Integer> getNumber = VTask.succeed(21);

      // SOLUTION: Use flatMap() to chain dependent computations
      VTask<Integer> doubled = getNumber.flatMap(n -> VTask.succeed(n * 2));

      Integer result = doubled.run();
      assertThat(result).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Part 3: Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Exercise 5: Safe execution with runSafe()")
    void exercise5_safeExecution() {
      VTask<String> failingTask = VTask.fail(new RuntimeException("Database connection failed"));

      Try<String> result = failingTask.runSafe();

      // SOLUTION: Use isFailure() to check if the result is a failure
      boolean isFailure = result.isFailure();
      assertThat(isFailure).isTrue();

      // SOLUTION: Use fold() to extract the error message
      String errorMessage = result.fold(value -> "no error", error -> error.getMessage());
      assertThat(errorMessage).isEqualTo("Database connection failed");
    }

    @Test
    @DisplayName("Exercise 6: Recover from failure")
    void exercise6_recoverFromFailure() {
      VTask<Integer> failingTask = VTask.fail(new RuntimeException("Computation failed"));

      // SOLUTION: Use recover() to provide a fallback value
      VTask<Integer> recovered = failingTask.recover(error -> -1);

      Integer result = recovered.run();
      assertThat(result).isEqualTo(-1);
    }
  }

  @Nested
  @DisplayName("Part 4: Parallel Composition")
  class ParallelComposition {

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

      // SOLUTION: Use Par.map2() to execute in parallel and combine results
      VTask<Integer> combined = Par.map2(taskA, taskB, Integer::sum);

      Integer result = combined.run();
      assertThat(result).isEqualTo(42);
    }

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

      // SOLUTION: Use Par.race() to return the first successful result
      VTask<String> winner = Par.race(List.of(slow, medium, fast));

      String result = winner.run();
      assertThat(result).isEqualTo("fast");
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

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
    }
  }
}
