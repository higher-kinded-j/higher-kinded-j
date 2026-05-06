// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.resilience;

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
 * Solution for Tutorial02 Saga — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 02: Saga Pattern (Solutions)")
public class Tutorial02_Saga_Solution {

  @Nested
  @DisplayName("Part 1: Creating Simple Sagas")
  class CreatingSimpleSagas {

    /**
     * Why this is idiomatic: a saga pairs an action with a compensation. On success the
     * compensation never runs; on failure the saga walks the compensation chain in reverse to undo
     * prior steps.
     *
     * <p>Alternative: a try/catch with cleanup blocks. Same idea; the saga structure makes the
     * compensation explicit and ordered.
     *
     * <p>Common wrong attempt: assume the compensation runs on every step. Compensations only run
     * on failure of a later step.
     */
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

    /**
     * Why this is idiomatic: {@code andThen} chains saga steps; each step receives the previous
     * step's result. The compensation chain grows automatically.
     *
     * <p>Alternative: a list of (action, compensation) pairs run sequentially. Same outcome; the
     * saga monad keeps both halves linked.
     *
     * <p>Common wrong attempt: forget to thread the previous result. Each step lambda receives the
     * prior value; ignore it only if genuinely independent.
     */
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

    /**
     * Why this is idiomatic: a failure in a later step triggers compensations for prior successful
     * steps in reverse order. The saga's invariant: if step N fails, steps 1..N-1 are undone.
     *
     * <p>Alternative: try/catch with manual cleanup. Same outcome; the saga automates the order.
     *
     * <p>Common wrong attempt: assume the failed step's own compensation runs. Only completed steps
     * compensate; the failed step never started a commit.
     */
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

    /**
     * Why this is idiomatic: {@code SagaBuilder} provides a fluent API for multi-step sagas. Each
     * step has a name, an action, and a compensation; the build returns a saga ready to run.
     *
     * <p>Alternative: chained {@code Saga.of(...).andThen(...)}. Equivalent; the builder shines for
     * sagas with many steps.
     *
     * <p>Common wrong attempt: forget to name each step. Names appear in {@code
     * SagaError.failedStep()} for debugging; supply them.
     */
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

    /**
     * Why this is idiomatic: {@code runSafe()} returns an {@code Either<SagaError, Result>}. The
     * error carries the failed step's name, the original exception, and whether all compensations
     * succeeded.
     *
     * <p>Alternative: catch the exception at {@code run().run()}. Loses the structured saga error;
     * the typed {@code SagaError} captures more.
     *
     * <p>Common wrong attempt: ignore {@code allCompensationsSucceeded()}. Compensation can itself
     * fail; production code should surface that.
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

    /**
     * Why this is idiomatic: a complete order workflow — charge payment, reserve inventory,
     * schedule shipping. When shipping fails, both compensations fire (refund and release) in
     * reverse order.
     *
     * <p>Alternative: a database transaction. Works for same-DB updates; sagas are the answer for
     * cross-service coordination where ACID is not available.
     *
     * <p>Common wrong attempt: assume all compensations succeed silently. Always inspect {@code
     * SagaError} to confirm the rollback completed.
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

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.failedStep()).isEqualTo("schedule-shipping");

      assertThat(paymentRefunded.get()).isTrue();
      assertThat(inventoryReleased.get()).isTrue();
      assertThat(error.allCompensationsSucceeded()).isTrue();
    }
  }
}
