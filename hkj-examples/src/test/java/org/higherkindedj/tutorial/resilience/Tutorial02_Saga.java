// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
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
 * Tutorial 02: Saga Pattern for Distributed Compensation.
 *
 * <p>Pain → Promise. Long-running multi-service workflows cannot use database transactions — if
 * step 3 fails, we have to undo steps 1 and 2 by issuing compensating calls. Hand-rolled sagas are
 * a stack of "did we do this?" booleans plus a giant try/catch with manual rollback in reverse
 * order.
 *
 * <p>{@link Saga} captures the action/compensation pair per step and the reverse-order cleanup
 * automatically:
 *
 * <pre>
 *   Saga&lt;OrderConfirmed&gt; placeOrder = SagaBuilder.&lt;OrderConfirmed&gt;create()
 *       .step(reserveInventory, releaseInventory)
 *       .step(chargeCustomer, refundCustomer)
 *       .step(shipOrder, cancelShipment)
 *       .build();
 * </pre>
 *
 * <p>Java idiom anchor: same pattern as the Sagas in Eventuate or Axon, expressed as a value we can
 * compose, test, and run. Failure of any step triggers compensation of all preceding steps in
 * reverse order.
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
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 02: Saga Pattern")
public class Tutorial02_Saga {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ===========================================================================
  // Part 1: Creating Simple Sagas
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating Simple Sagas")
  class CreatingSimpleSagas {

    /**
     * Exercise 1: Build a Saga with a compensation.
     *
     * <pre>
     *   // Nudge:    Saga.of takes a forward VTask and a Consumer compensation.
     *   // Strategy: Saga.of(VTask.succeed("payment-123"),
     *   //                   (Consumer&lt;String&gt;) s -&gt; compensated.set(true))
     *   // Spoiler:  exactly that. The cast disambiguates the overload.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 1: Create a simple saga with compensation")
    void exercise1_createSimpleSaga() {
      AtomicBoolean compensated = new AtomicBoolean(false);

      // TODO: Create a Saga<String> using Saga.of() with:
      //       - forward action: VTask.succeed("payment-123")
      //       - compensation: a Consumer<String> that sets compensated.set(true)
      //       Hint: cast the lambda as (Consumer<String>) to disambiguate the overload.
      Saga<String> saga = answerRequired();

      // Execute the saga - since it succeeds, compensation should NOT be called
      String result = saga.run().run();
      assertThat(result).isEqualTo("payment-123");
      assertThat(compensated.get()).isFalse();
    }

    /**
     * Exercise 2: Chain saga steps with {@code andThen}.
     *
     * <pre>
     *   // Nudge:    andThen takes a function from the previous result to a new Saga.
     *   // Strategy: Saga.of(VTask.succeed("step1-result"), (String _) -&gt; {})
     *   //               .andThen(prev -&gt; Saga.of(VTask.succeed(prev + ":step2-result"),
     *   //                                         (String _) -&gt; {}))
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 2: Chain saga steps with andThen")
    void exercise2_chainSagaSteps() {
      // TODO: Build a Saga<String> by chaining two steps with andThen:
      //       Step 1: Saga.of(VTask.succeed("step1-result"), (String _) -> {})
      //       Step 2 (depends on prev): Saga.of(VTask.succeed(prev + ":step2-result"),
      //                                          (String _) -> {})
      Saga<String> saga = answerRequired();

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
     * Exercise 3: Compensation runs on failure.
     *
     * <pre>
     *   // Nudge:    Step 1 succeeds with a compensation that flips the flag; step 2 fails.
     *   // Strategy: Saga.of(VTask.succeed("step1"),
     *   //                   (String _) -&gt; step1Compensated.set(true))
     *   //               .andThen(_ -&gt; Saga.of(VTask.&lt;String&gt;fail(new RuntimeException("step2 failed")),
     *   //                                      (String _) -&gt; {}))
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 3: Verify compensation runs on failure")
    void exercise3_compensationOnFailure() {
      AtomicBoolean step1Compensated = new AtomicBoolean(false);

      // TODO: Build a Saga<String> where:
      //       Step 1 succeeds and its compensation sets step1Compensated to true
      //       Step 2 fails with VTask.<String>fail(new RuntimeException("step2 failed"))
      //       The failure of step 2 should cause step 1's compensation to run.
      Saga<String> saga = answerRequired();

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
     * Exercise 4: SagaBuilder fluent multi-step.
     *
     * <pre>
     *   // Nudge:    SagaBuilder.&lt;Unit&gt;start().step(...).step(...).build().
     *   // Strategy: SagaBuilder.&lt;Unit&gt;start()
     *   //               .step("charge", VTask.succeed("payment-456"), _ -&gt; {})
     *   //               .step("reserve", prev -&gt; VTask.succeed(prev + ":reserved"), _ -&gt; {})
     *   //               .build()
     *   // Spoiler:  exactly that.
     * </pre>
     */
    @Test
    @DisplayName("Exercise 4: Use SagaBuilder for multi-step saga")
    void exercise4_sagaBuilder() {
      // TODO: Build a Saga<String> using SagaBuilder.<Unit>start() with two named steps:
      //       .step("charge", VTask.succeed("payment-456"), _ -> {})
      //       .step("reserve", prev -> VTask.succeed(prev + ":reserved"), _ -> {})
      //       and finally .build()
      Saga<String> saga = answerRequired();

      String result = saga.run().run();
      assertThat(result).isEqualTo("payment-456:reserved");
    }

    /**
     * Exercise 5: {@code runSafe} returns Either&lt;SagaError, A&gt;.
     *
     * <pre>
     *   // Nudge:    runSafe gives a VTask; .run() unwraps the Either.
     *   // Strategy: failingSaga.runSafe().run()
     *   // Spoiler:  exactly that.
     * </pre>
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

      // TODO: Use failingSaga.runSafe().run() to obtain Either<SagaError, String>
      Either<SagaError, String> result = answerRequired();

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
