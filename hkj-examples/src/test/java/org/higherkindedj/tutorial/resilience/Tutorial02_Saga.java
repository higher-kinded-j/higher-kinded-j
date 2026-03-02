// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Saga;
import org.higherkindedj.hkt.resilience.SagaBuilder;
import org.higherkindedj.hkt.resilience.SagaError;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Saga Pattern for Distributed Compensation
 *
 * <p>Learn to use the Saga pattern to orchestrate multi-step operations where each step has a
 * corresponding compensation action. If any step fails, all previously completed steps are
 * compensated in reverse order.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Saga.of() creates a single step with an action and compensation
 *   <li>andThen() chains sagas so that failure triggers reverse compensation
 *   <li>SagaBuilder provides a fluent API for multi-step sagas
 *   <li>runSafe() returns Either&lt;SagaError, A&gt; for structured error handling
 *   <li>SagaError captures the original error and compensation results
 * </ul>
 *
 * <p>Requirements: Java 25+ (virtual threads and structured concurrency)
 *
 * <p>Replace each {@code fail("TODO: ...")} with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 02: Saga Pattern")
public class Tutorial02_Saga {

  // ===========================================================================
  // Part 1: Creating Simple Sagas
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating Simple Sagas")
  class CreatingSimpleSagas {

    /**
     * Exercise 1: Create a simple saga with compensation
     *
     * <p>Use Saga.of() to create a saga with a forward action and a compensation action. The
     * compensation is a Consumer that receives the result of the forward action.
     */
    @Test
    @DisplayName("Exercise 1: Create a simple saga with compensation")
    void exercise1_createSimpleSaga() {
      AtomicBoolean compensated = new AtomicBoolean(false);

      // Create a saga with Saga.of()
      Saga<String> saga =
          Saga.of(
              VTask.succeed("payment-123"), (Consumer<String>) paymentId -> compensated.set(true));

      // Execute the saga - since it succeeds, compensation should NOT be called
      String result = saga.run().run();
      assertThat(result).isEqualTo("payment-123");
      assertThat(compensated.get()).isFalse();
    }

    /**
     * Exercise 2: Chain saga steps with andThen
     *
     * <p>Use andThen() to compose a multi-step saga where each step depends on the previous step's
     * result. If a later step fails, earlier steps are compensated.
     */
    @Test
    @DisplayName("Exercise 2: Chain saga steps with andThen")
    void exercise2_chainSagaSteps() {
      // Chain two saga steps with andThen
      Saga<String> saga =
          Saga.of(VTask.succeed("step1-result"), (String _) -> {})
              .andThen(prev -> Saga.of(VTask.succeed(prev + ":step2-result"), (String _) -> {}));

      String result = saga.run().run();
      assertThat(result).isEqualTo("step1-result:step2-result");
    }
  }

  // ===========================================================================
  // Part 2: Compensation on Failure
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Compensation on Failure")
  class CompensationOnFailure {

    /**
     * Exercise 3: Verify compensation on failure
     *
     * <p>When a later step in the saga fails, earlier completed steps are compensated in reverse
     * order. Use AtomicBoolean to track whether compensation was executed.
     */
    @Test
    @DisplayName("Exercise 3: Verify compensation runs on failure")
    void exercise3_compensationOnFailure() {
      AtomicBoolean step1Compensated = new AtomicBoolean(false);

      // Create a saga where step 2 fails, triggering step 1 compensation
      Saga<String> saga =
          Saga.of(VTask.succeed("ok"), (String _) -> step1Compensated.set(true))
              .andThen(
                  _ ->
                      Saga.of(
                          VTask.<String>fail(new RuntimeException("step2 failed")),
                          (String _) -> {}));

      // Execute safely - the saga should fail, triggering compensation
      var tryResult = saga.run().runSafe();

      assertThat(tryResult.isFailure()).isTrue();
      assertThat(step1Compensated.get()).isTrue();
    }
  }

  // ===========================================================================
  // Part 3: SagaBuilder and runSafe()
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: SagaBuilder and runSafe()")
  class SagaBuilderAndRunSafe {

    /**
     * Exercise 4: Use SagaBuilder for multi-step saga
     *
     * <p>SagaBuilder provides a more fluent way to construct sagas with named steps. Each step has
     * a name, an action, and a compensation. Use SagaBuilder.start() to begin.
     */
    @Test
    @DisplayName("Exercise 4: Use SagaBuilder for multi-step saga")
    void exercise4_sagaBuilder() {
      // Use SagaBuilder for fluent multi-step construction
      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("charge", VTask.succeed("payment-456"), _ -> {})
              .step("reserve", prev -> VTask.succeed(prev + ":reserved"), _ -> {})
              .build();

      String result = saga.run().run();
      assertThat(result).isEqualTo("payment-456:reserved");
    }

    /**
     * Exercise 5: Handle SagaError with runSafe()
     *
     * <p>Saga.runSafe() returns a VTask that produces Either&lt;SagaError, A&gt;. The left side
     * contains the error details including the original error and compensation results.
     */
    @Test
    @DisplayName("Exercise 5: Handle SagaError with runSafe()")
    void exercise5_sagaRunSafe() {
      Saga<String> failingSaga =
          Saga.of(VTask.succeed("step1-ok"), (String s) -> {})
              .andThen(
                  _ ->
                      Saga.of(
                          VTask.<String>fail(new RuntimeException("Network timeout")),
                          (String s) -> {}));

      // Use runSafe() to get Either<SagaError, String>
      Either<SagaError, String> result = failingSaga.runSafe().run();

      // The result should be a Left containing the SagaError
      assertThat(result.isLeft()).isTrue();

      SagaError error = result.getLeft();
      assertThat(error.originalError()).isInstanceOf(RuntimeException.class);
      assertThat(error.originalError().getMessage()).isEqualTo("Network timeout");
      assertThat(error.allCompensationsSucceeded()).isTrue();
    }
  }

  // ===========================================================================
  // Bonus: Complete Example
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates a complete saga workflow simulating an order process:
     *
     * <ol>
     *   <li>Charge payment (with refund compensation)
     *   <li>Reserve inventory (with release compensation)
     *   <li>Schedule shipping (fails, triggering compensations)
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
     */
    @Test
    @DisplayName("Complete saga workflow with compensation")
    void completeSagaWorkflow() {
      AtomicBoolean paymentRefunded = new AtomicBoolean(false);
      AtomicBoolean inventoryReleased = new AtomicBoolean(false);

      Saga<String> orderSaga =
          SagaBuilder.<Unit>start()
              .step("charge-payment", VTask.succeed("pay-789"), _ -> paymentRefunded.set(true))
              .step(
                  "reserve-inventory",
                  _ -> VTask.succeed("inv-456"),
                  _ -> inventoryReleased.set(true))
              .step(
                  "schedule-shipping",
                  _ -> VTask.fail(new RuntimeException("No carriers available")),
                  (String _) -> {})
              .build();

      Either<SagaError, String> result = orderSaga.runSafe().run();

      // Saga failed at shipping step
      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.failedStep()).isEqualTo("schedule-shipping");

      // Both earlier steps were compensated
      assertThat(paymentRefunded.get()).isTrue();
      assertThat(inventoryReleased.get()).isTrue();
      assertThat(error.allCompensationsSucceeded()).isTrue();
    }
  }

  /**
   * Congratulations! You've completed Tutorial 02: Saga Pattern
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to create sagas with Saga.of() pairing actions with compensations
   *   <li>How to chain saga steps with andThen()
   *   <li>How compensation runs in reverse order on failure
   *   <li>How to use SagaBuilder for fluent multi-step saga construction
   *   <li>How to use runSafe() for structured error handling with SagaError
   * </ul>
   *
   * <p>Next: Tutorial 03 - Retry, Bulkhead, and Resilience composition
   */
}
