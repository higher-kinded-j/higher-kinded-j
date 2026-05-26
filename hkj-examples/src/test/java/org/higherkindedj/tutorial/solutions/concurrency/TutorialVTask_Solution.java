// Copyright (c) 2025 - 2026 Magnus Smith
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

/** Solution for Tutorial: VTask Fundamentals — teaching-solution format. */
@DisplayName("Tutorial: VTask Fundamentals (Solutions)")
public class TutorialVTask_Solution {

  @Nested
  @DisplayName("Part 1: Creating VTasks")
  class CreatingVTasks {

    /**
     * Why this is idiomatic: {@code VTask.of(callable)} is the standard "make a deferred
     * computation" call. It does not run anything until {@code run()} or {@code runSafe()}.
     *
     * <p>Alternative: {@code VTask.succeed(input.length())} eagerly computes the value and lifts
     * it. Use {@code of} when the work is non-trivial (I/O, computation); use {@code succeed} when
     * the value is already in hand.
     *
     * <p>Common wrong attempt: putting side-effecting code outside the lambda and capturing the
     * result. The "deferred" benefit disappears.
     */
    @Test
    @DisplayName("Exercise 1: Create a VTask with of()")
    void exercise1_createVTaskWithOf() {
      String input = "Hello, VTask!";
      VTask<Integer> lengthTask = VTask.of(() -> input.length());
      assertThat(lengthTask.run()).isEqualTo(13);
    }

    /**
     * Why this is idiomatic: factories that name the success / failure constructors directly. No
     * try/throw needed inside a lambda just to encode failure.
     *
     * <p>Alternative: {@code VTask.of(() -> { throw new RuntimeException(...); })}. Same outcome at
     * runtime; loses the "this is a failure by construction" signal.
     *
     * <p>Common wrong attempt: throwing the exception eagerly in the surrounding code, then
     * "wrapping" the result in a VTask afterward. The exception happens before the VTask exists.
     */
    @Test
    @DisplayName("Exercise 2: Create succeed and fail VTasks")
    void exercise2_succeedAndFail() {
      VTask<Integer> success = VTask.succeed(42);
      VTask<Integer> failure = VTask.fail(new RuntimeException("Expected failure"));

      assertThat(success.runSafe().isSuccess()).isTrue();
      assertThat(success.runSafe().orElse(-1)).isEqualTo(42);
      assertThat(failure.runSafe().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Part 2: Transforming VTasks")
  class TransformingVTasks {

    /**
     * Why this is idiomatic: {@code map} is the Functor capability. The function takes a value and
     * returns a value; the VTask structure is preserved. Method reference reads cleanest.
     *
     * <p>Alternative: {@code greeting.map(s -> s.length())}. Identical.
     *
     * <p>Common wrong attempt: {@code greeting.flatMap(s -> VTask.succeed(s.length()))}. Works, but
     * uses Monad for what is purely a Functor job; reads less clearly.
     */
    @Test
    @DisplayName("Exercise 3: Transform with map()")
    void exercise3_mapTransformation() {
      VTask<String> greeting = VTask.succeed("Hello, Virtual Threads!");
      VTask<Integer> length = greeting.map(String::length);
      assertThat(length.run()).isEqualTo(23);
    }

    /**
     * Why this is idiomatic: {@code flatMap} (or its alias {@code via}) chains computations where
     * the next step is itself a VTask. The chain is sequential by design; for parallel composition
     * see {@link Par}.
     *
     * <p>Alternative: {@code getNumber.via(n -> VTask.succeed(n * 2))}. Identical operation.
     *
     * <p>Common wrong attempt: {@code getNumber.map(n -> VTask.succeed(n * 2))}. Returns {@code
     * VTask<VTask<Integer>>} — nested. Use {@code flatMap} when the function returns a VTask.
     */
    @Test
    @DisplayName("Exercise 4: Chain with flatMap()")
    void exercise4_flatMapChaining() {
      VTask<Integer> getNumber = VTask.succeed(21);
      VTask<Integer> doubled = getNumber.flatMap(n -> VTask.succeed(n * 2));
      assertThat(doubled.run()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Part 3: Error Handling")
  class ErrorHandling {

    /**
     * Why this is idiomatic: {@code runSafe} is the production form — exceptions become {@link
     * Try}, no checked-exception ladder. {@code fold} extracts both branches in a single call.
     *
     * <p>Alternative: {@code result.toEither().getLeft()} after asserting failure. Same
     * information; more steps.
     *
     * <p>Common wrong attempt: calling {@code .run()} in production code. {@code run} throws
     * unchecked exceptions on failure; {@code runSafe} captures them and lets us decide.
     */
    @Test
    @DisplayName("Exercise 5: Safe execution with runSafe()")
    void exercise5_safeExecution() {
      VTask<String> failingTask = VTask.fail(new RuntimeException("Database connection failed"));
      Try<String> result = failingTask.runSafe();

      boolean isFailure = result.isFailure();
      assertThat(isFailure).isTrue();

      String errorMessage =
          result.foldFailureFirst(error -> error.getMessage(), value -> "no error");
      assertThat(errorMessage).isEqualTo("Database connection failed");
    }

    /**
     * Why this is idiomatic: {@code recover} swaps the failure for a value. The fallback receives
     * the exception so it can be inspected if needed.
     *
     * <p>Alternative: {@code failingTask.recoverWith(err -> VTask.succeed(-1))}. Use {@code
     * recoverWith} when the fallback is itself a VTask (e.g. retry).
     *
     * <p>Common wrong attempt: wrapping the call in try/catch around {@code run()}. Reintroduces
     * the imperative pattern we are trying to escape.
     */
    @Test
    @DisplayName("Exercise 6: Recover from failure")
    void exercise6_recoverFromFailure() {
      VTask<Integer> failingTask = VTask.fail(new RuntimeException("Computation failed"));
      VTask<Integer> recovered = failingTask.recover(error -> -1);
      assertThat(recovered.run()).isEqualTo(-1);
    }
  }

  @Nested
  @DisplayName("Part 4: Parallel Composition")
  class ParallelComposition {

    /**
     * Why this is idiomatic: {@code Par.map2} runs both tasks in parallel under structured
     * concurrency. If either fails, the other is cancelled.
     *
     * <p>Alternative: {@code Par.map2(taskA, taskB, (a, b) -> a + b)}. Identical with an explicit
     * lambda.
     *
     * <p>Common wrong attempt: {@code taskA.flatMap(a -> taskB.map(b -> a + b))}. Sequential, not
     * parallel. The Functor / Monad layer expresses dependency; {@link Par} expresses independence.
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
      VTask<Integer> combined = Par.map2(taskA, taskB, Integer::sum);
      assertThat(combined.run()).isEqualTo(42);
    }

    /**
     * Why this is idiomatic: {@code Par.race} starts every task concurrently and returns the first
     * success; the rest are cancelled. Expresses "fastest provider wins" cleanly.
     *
     * <p>Alternative: hand-roll with {@code CompletableFuture.anyOf}. Works; loses cancellation of
     * the losers and the structured-concurrency boundary.
     *
     * <p>Common wrong attempt: assuming {@code race} returns the first task to <em>start</em>. It
     * returns the first to <em>complete</em> successfully.
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
      VTask<String> winner = Par.race(List.of(slow, medium, fast));
      assertThat(winner.run()).isEqualTo("fast");
    }
  }

  /**
   * Diagnostic: capture the result of a {@code VTask.run()} once before reusing it; the task is a
   * description, not a memoised value.
   *
   * <p>Why this is idiomatic: a {@code VTask} is a value-returning recipe. Each call to {@code
   * run()} executes the recipe again. Capturing the result is the standard pattern.
   *
   * <p>Alternative: wrap the task with a memoising layer (e.g. {@code Lazy} or {@code
   * IOPath.memoize}) when the task represents a one-shot computation that should never re-run.
   *
   * <p>Common wrong attempt: assuming VTasks behave like {@link
   * java.util.concurrent.CompletableFuture}, which caches its result. They do not, by design — that
   * makes them composable and re-runnable.
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

    int captured = task.run();
    int sumOfTwoRuns = captured + captured;

    assertThat(sumOfTwoRuns).isEqualTo(2);
    assertThat(counter[0]).isEqualTo(1);
  }

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * Why this is idiomatic: a real workflow combines parallel composition ({@code Par.map2}) with
     * error recovery ({@code recover}). Both fetches run concurrently; failures fall through to a
     * default profile.
     *
     * <p>Alternative: sequential {@code flatMap} chain. Doubles the wall-clock time because the
     * second fetch waits for the first.
     *
     * <p>Common wrong attempt: skip recovery and let exceptions propagate. Production endpoints
     * should degrade gracefully; recovery is part of the workflow.
     */
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
      assertThat(safeProfile.run()).isEqualTo("Alice (age 30)");
    }
  }
}
