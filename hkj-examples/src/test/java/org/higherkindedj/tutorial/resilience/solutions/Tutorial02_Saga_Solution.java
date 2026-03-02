// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience.solutions;

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
 * Tutorial 02: Saga Pattern - SOLUTIONS
 *
 * <p>This file contains the complete solutions for all exercises in Tutorial02_Saga.java.
 */
@DisplayName("Tutorial 02: Saga Pattern (Solutions)")
public class Tutorial02_Saga_Solution {

  @Nested
  @DisplayName("Part 1: Creating Simple Sagas")
  class CreatingSimpleSagas {

    @Test
    @DisplayName("Exercise 1: Create a simple saga with compensation")
    void exercise1_createSimpleSaga() {
      AtomicBoolean compensated = new AtomicBoolean(false);

      // SOLUTION: Create a saga with Saga.of()
      Saga<String> saga =
          Saga.of(
              VTask.succeed("payment-123"), (Consumer<String>) paymentId -> compensated.set(true));

      // Execute the saga - since it succeeds, compensation should NOT be called
      String result = saga.run().run();
      assertThat(result).isEqualTo("payment-123");
      assertThat(compensated.get()).isFalse();
    }

    @Test
    @DisplayName("Exercise 2: Chain saga steps with andThen")
    void exercise2_chainSagaSteps() {
      // SOLUTION: Chain two saga steps with andThen
      Saga<String> saga =
          Saga.of(VTask.succeed("step1-result"), (String _) -> {})
              .andThen(prev -> Saga.of(VTask.succeed(prev + ":step2-result"), (String _) -> {}));

      String result = saga.run().run();
      assertThat(result).isEqualTo("step1-result:step2-result");
    }
  }

  @Nested
  @DisplayName("Part 2: Compensation on Failure")
  class CompensationOnFailure {

    @Test
    @DisplayName("Exercise 3: Verify compensation runs on failure")
    void exercise3_compensationOnFailure() {
      AtomicBoolean step1Compensated = new AtomicBoolean(false);

      // SOLUTION: Create a saga where step 2 fails, triggering step 1 compensation
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

  @Nested
  @DisplayName("Part 3: SagaBuilder and runSafe()")
  class SagaBuilderAndRunSafe {

    @Test
    @DisplayName("Exercise 4: Use SagaBuilder for multi-step saga")
    void exercise4_sagaBuilder() {
      // SOLUTION: Use SagaBuilder for fluent multi-step construction
      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("charge", VTask.succeed("payment-456"), _ -> {})
              .step("reserve", prev -> VTask.succeed(prev + ":reserved"), _ -> {})
              .build();

      String result = saga.run().run();
      assertThat(result).isEqualTo("payment-456:reserved");
    }

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

      // SOLUTION: Use runSafe() to get Either<SagaError, String>
      Either<SagaError, String> result = failingSaga.runSafe().run();

      // The result should be a Left containing the SagaError
      assertThat(result.isLeft()).isTrue();

      SagaError error = result.getLeft();
      assertThat(error.originalError()).isInstanceOf(RuntimeException.class);
      assertThat(error.originalError().getMessage()).isEqualTo("Network timeout");
      assertThat(error.allCompensationsSucceeded()).isTrue();
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

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

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.failedStep()).isEqualTo("schedule-shipping");

      assertThat(paymentRefunded.get()).isTrue();
      assertThat(inventoryReleased.get()).isTrue();
      assertThat(error.allCompensationsSucceeded()).isTrue();
    }
  }
}
