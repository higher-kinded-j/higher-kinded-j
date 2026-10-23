// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import org.higherkindedj.hkt.Unit;

/**
 * A fluent builder for constructing complex {@link Saga} instances with multiple steps.
 *
 * <p>Each step has a name (for error reporting), an action, and a compensation. Steps execute
 * sequentially; each step's action can depend on the result of the previous step.
 *
 * <p>{@link #start()} gives a {@link Start}, which offers the step methods but no {@code build()}.
 * Each step method returns a {@code SagaBuilder}, so {@link #build()} always has a step to run and
 * never fails, and a saga with no steps does not compile. Both stages are {@link SagaSteps}, the
 * type to hold a builder in where the steps come from data and there may be none.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Saga<String> orderSaga = SagaBuilder.start()
 *     .step("charge-payment",
 *         VTask.of(() -> paymentService.charge(order)),
 *         paymentId -> paymentService.refund(paymentId))
 *     .step("reserve-inventory",
 *         paymentId -> VTask.of(() -> inventoryService.reserve(order)),
 *         reservationId -> inventoryService.release(reservationId))
 *     .step("schedule-shipping",
 *         reservationId -> VTask.of(() -> shippingService.schedule(order)),
 *         trackingId -> shippingService.cancel(trackingId))
 *     .build();
 *
 * Try<String> result = orderSaga.run().runSafe();
 * }</pre>
 *
 * @param <A> the type of the current step's result
 * @see Saga
 * @see SagaError
 */
public final class SagaBuilder<A> extends SagaSteps<A> {

  SagaBuilder(Saga<A> saga) {
    super(saga);
  }

  /**
   * Starts building a new saga. A function-taking first step receives {@link Unit#INSTANCE} as the
   * previous result.
   *
   * @return the builder before its first step, which has no {@code build()}
   */
  public static Start start() {
    return Start.INSTANCE;
  }

  /**
   * Builds the saga from the accumulated steps. It always holds at least one step, so this never
   * fails.
   *
   * @return a new Saga ready for execution
   */
  public Saga<A> build() {
    return saga();
  }

  /**
   * A saga builder before its first step. It offers the same step methods as {@link SagaBuilder},
   * and each one returns a {@code SagaBuilder} holding that step. It has no {@code build()}, so a
   * saga with no steps does not compile.
   *
   * <p>A function-taking step added here receives {@link Unit#INSTANCE} as the previous result.
   */
  public static final class Start extends SagaSteps<Unit> {

    private static final Start INSTANCE = new Start();

    private Start() {
      // Gives Unit and tracks no step, so there is nothing to compensate before the first step
      super(new Saga<>(_ -> Unit.INSTANCE));
    }
  }
}
