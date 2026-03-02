// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A fluent builder for constructing complex {@link Saga} instances with multiple steps.
 *
 * <p>Each step has a name (for error reporting), an action, and a compensation. Steps execute
 * sequentially; each step's action can depend on the result of the previous step.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Saga<String> orderSaga = SagaBuilder.<Unit>start()
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
public final class SagaBuilder<A> {

  private final Saga<A> saga;
  private final int stepCount;

  private SagaBuilder(Saga<A> saga, int stepCount) {
    this.saga = saga;
    this.stepCount = stepCount;
  }

  /**
   * Starts building a new saga. The initial result is {@link Unit#INSTANCE}.
   *
   * @return a new SagaBuilder at the starting position
   */
  public static SagaBuilder<Unit> start() {
    // Initial saga that produces Unit without tracking any step
    Saga<Unit> initial = new Saga<>(_ -> Unit.INSTANCE);
    return new SagaBuilder<>(initial, 0);
  }

  /**
   * Adds a step with a standalone action (not depending on the previous result) and synchronous
   * compensation.
   *
   * @param name the step name
   * @param action the forward action
   * @param compensate compensation to run on failure (receives the action's result)
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public <B> SagaBuilder<B> step(String name, VTask<B> action, Consumer<B> compensate) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");

    Saga<B> newSaga = saga.andThen(_ -> Saga.namedStepSync(name, action, compensate));
    return new SagaBuilder<>(newSaga, stepCount + 1);
  }

  /**
   * Adds a step whose action depends on the previous step's result, with synchronous compensation.
   *
   * @param name the step name
   * @param action function producing the forward action from the previous result
   * @param compensate compensation to run on failure
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public <B> SagaBuilder<B> step(
      String name, Function<A, VTask<B>> action, Consumer<B> compensate) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");

    Saga<B> newSaga =
        saga.andThen(prevResult -> Saga.namedStepSync(name, action.apply(prevResult), compensate));
    return new SagaBuilder<>(newSaga, stepCount + 1);
  }

  /**
   * Adds a step with asynchronous (VTask-based) compensation.
   *
   * @param name the step name
   * @param action function producing the forward action from the previous result
   * @param compensate function producing a compensation VTask
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public <B> SagaBuilder<B> stepAsync(
      String name, Function<A, VTask<B>> action, Function<B, VTask<Unit>> compensate) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");

    Saga<B> newSaga =
        saga.andThen(prevResult -> Saga.namedStep(name, action.apply(prevResult), compensate));
    return new SagaBuilder<>(newSaga, stepCount + 1);
  }

  /**
   * Adds a step with no compensation. Use for final steps or idempotent operations.
   *
   * @param name the step name
   * @param action function producing the forward action from the previous result
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public <B> SagaBuilder<B> stepNoCompensation(String name, Function<A, VTask<B>> action) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");

    Saga<B> newSaga =
        saga.andThen(
            prevResult ->
                Saga.namedStep(name, action.apply(prevResult), _ -> VTask.succeed(Unit.INSTANCE)));
    return new SagaBuilder<>(newSaga, stepCount + 1);
  }

  /**
   * Builds the saga from the accumulated steps.
   *
   * @return a new Saga ready for execution
   * @throws IllegalStateException if no steps have been added
   */
  public Saga<A> build() {
    if (stepCount == 0) {
      throw new IllegalStateException("Saga must have at least one step");
    }
    return saga;
  }
}
