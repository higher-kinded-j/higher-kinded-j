// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Property-based tests for {@link Saga}.
 *
 * <p>Verifies compensation semantics: that compensations run on failure, execute in reverse order,
 * and that only steps completed before the failure are compensated. Also verifies that a successful
 * saga produces a right value from {@code runSafe()}.
 */
class SagaPropertyTest {

  // ==================== Compensation Always Runs on Failure ====================

  /**
   * Property: When a saga step fails, compensation is invoked for every previously completed step.
   *
   * <p>We build a saga with {@code completedSteps} successful steps followed by one failing step.
   * After execution, we verify that compensation was called exactly {@code completedSteps} times.
   */
  @Property
  @Label("Compensation always runs for every completed step on failure")
  void compensationAlwaysRunsOnFailure(@ForAll @IntRange(min = 1, max = 8) int completedSteps) {

    AtomicInteger compensationCount = new AtomicInteger(0);

    // Build the first step
    Saga<String> saga =
        Saga.of(
            VTask.succeed("step-1-result"),
            (Consumer<String>) v -> compensationCount.incrementAndGet());

    // Chain additional successful steps
    for (int i = 2; i <= completedSteps; i++) {
      String stepResult = "step-" + i + "-result";
      saga =
          saga.andThen(
              prev ->
                  Saga.of(
                      VTask.succeed(stepResult),
                      (Consumer<String>) v -> compensationCount.incrementAndGet()));
    }

    // Add a failing step at the end
    saga =
        saga.andThen(
            prev -> Saga.of(VTask.fail(new RuntimeException("boom")), (Consumer<String>) v -> {}));

    Either<SagaError, String> result = saga.runSafe().run();

    assertThat(result.isLeft()).isTrue();
    assertThat(compensationCount.get()).isEqualTo(completedSteps);
  }

  // ==================== Compensation Order Is Reverse of Execution ====================

  /**
   * Property: Compensations run in reverse order of step execution.
   *
   * <p>Each step records its index during compensation. After a failure at the end, the recorded
   * compensation order must be the reverse of the original step execution order.
   */
  @Property
  @Label("Compensation order is reverse of execution order")
  void compensationOrderIsReverse(@ForAll @IntRange(min = 2, max = 8) int completedSteps) {

    CopyOnWriteArrayList<Integer> compensationOrder = new CopyOnWriteArrayList<>();

    // Build the first step
    final int stepIndex1 = 1;
    Saga<String> saga =
        Saga.of(VTask.succeed("step-1"), (Consumer<String>) v -> compensationOrder.add(stepIndex1));

    // Chain additional successful steps
    for (int i = 2; i <= completedSteps; i++) {
      final int idx = i;
      saga =
          saga.andThen(
              prev ->
                  Saga.of(
                      VTask.succeed("step-" + idx),
                      (Consumer<String>) v -> compensationOrder.add(idx)));
    }

    // Add a failing step
    saga =
        saga.andThen(
            prev ->
                Saga.of(VTask.fail(new RuntimeException("failure")), (Consumer<String>) v -> {}));

    Either<SagaError, String> result = saga.runSafe().run();

    assertThat(result.isLeft()).isTrue();

    // Build expected reverse order
    List<Integer> expectedOrder = new ArrayList<>();
    for (int i = completedSteps; i >= 1; i--) {
      expectedOrder.add(i);
    }

    assertThat(new ArrayList<>(compensationOrder)).isEqualTo(expectedOrder);
  }

  // ==================== Only Steps Up To Failure Point Are Compensated ====================

  /**
   * Property: Steps after the failure point are never compensated.
   *
   * <p>We place the failure at position {@code failAt} in a chain of {@code totalSteps} steps. Only
   * steps 1 through {@code failAt - 1} should be compensated; steps from {@code failAt} onward
   * should not run compensation.
   */
  @Property
  @Label("Only steps completed before failure are compensated")
  void onlyCompletedStepsAreCompensated(@ForAll @IntRange(min = 2, max = 8) int totalSteps) {

    // Fail at a random position (second step or later)
    int failAt = Arbitraries.integers().between(2, totalSteps).sample();

    CopyOnWriteArrayList<Integer> compensatedSteps = new CopyOnWriteArrayList<>();

    // Build the first step
    final int idx1 = 1;
    Saga<String> saga;
    if (failAt == 1) {
      saga =
          Saga.of(
              VTask.fail(new RuntimeException("fail at 1")),
              (Consumer<String>) v -> compensatedSteps.add(idx1));
    } else {
      saga = Saga.of(VTask.succeed("step-1"), (Consumer<String>) v -> compensatedSteps.add(idx1));
    }

    // Chain remaining steps
    for (int i = 2; i <= totalSteps; i++) {
      final int idx = i;
      if (i == failAt) {
        saga =
            saga.andThen(
                prev ->
                    Saga.of(
                        VTask.fail(new RuntimeException("fail at " + idx)),
                        (Consumer<String>) v -> compensatedSteps.add(idx)));
      } else {
        saga =
            saga.andThen(
                prev ->
                    Saga.of(
                        VTask.succeed("step-" + idx),
                        (Consumer<String>) v -> compensatedSteps.add(idx)));
      }
    }

    Either<SagaError, String> result = saga.runSafe().run();

    assertThat(result.isLeft()).isTrue();

    // Only steps before the failure point should be compensated
    int expectedCompensations = failAt - 1;
    assertThat(compensatedSteps).hasSize(expectedCompensations);

    // None of the compensated steps should be at or after the failure point
    for (int step : compensatedSteps) {
      assertThat(step).isLessThan(failAt);
    }
  }

  // ==================== Successful Saga Produces Right Value ====================

  /**
   * Property: A saga where all steps succeed produces a right value from {@code runSafe()}.
   *
   * <p>The final result is the value produced by the last step in the chain.
   */
  @Property
  @Label("Successful saga produces right value in runSafe()")
  void successfulSagaProducesRightValue(
      @ForAll @IntRange(min = 1, max = 8) int stepCount,
      @ForAll @IntRange(min = -100, max = 100) int baseValue) {

    // Build a saga that passes values through successive steps
    Saga<Integer> saga = Saga.of(VTask.succeed(baseValue), (Consumer<Integer>) v -> {});

    for (int i = 1; i < stepCount; i++) {
      final int increment = i;
      saga =
          saga.andThen(
              prev -> Saga.of(VTask.succeed(prev + increment), (Consumer<Integer>) v -> {}));
    }

    Either<SagaError, Integer> result = saga.runSafe().run();

    assertThat(result.isRight()).isTrue();

    // Calculate expected value: baseValue + 1 + 2 + ... + (stepCount - 1)
    int expectedValue = baseValue;
    for (int i = 1; i < stepCount; i++) {
      expectedValue += i;
    }
    assertThat(result.getRight()).isEqualTo(expectedValue);
  }
}
