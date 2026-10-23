// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The step methods both stages of a saga builder share: {@link SagaBuilder.Start}, before the first
 * step, and {@link SagaBuilder}, after it.
 *
 * <p>Every step method returns a {@link SagaBuilder}, which holds at least one step and is the only
 * stage with {@code build()}. So a saga with no steps does not compile.
 *
 * <p>The two stages are closed by design: a builder either has no steps yet or has some. So a
 * {@code switch} over them needs no default.
 *
 * <p>Hold a builder as a {@code SagaSteps} where the steps come from data and there may be none.
 * Switching on it to build covers both stages, so the case with no steps must be handled. Each step
 * the loop adds gives {@link Unit}, so its compensation takes what it undoes from the loop
 * variable:
 *
 * <pre>{@code
 * SagaSteps<Unit> steps = SagaBuilder.start();
 * for (Item item : items) {
 *   steps = steps.step("reserve-" + item.id(),
 *       VTask.exec(() -> inventory.reserve(item)),
 *       _ -> inventory.release(item));
 * }
 * Maybe<Saga<Unit>> reserveAll = switch (steps) {
 *   case SagaBuilder<Unit> builder -> Maybe.just(builder.build());
 *   case SagaBuilder.Start _ -> Maybe.nothing(); // no items, so no saga to run
 * };
 * }</pre>
 *
 * @param <A> the result of the steps so far, {@link Unit} before the first step
 * @see SagaBuilder
 * @see SagaBuilder.Start
 */
public abstract sealed class SagaSteps<A> permits SagaBuilder, SagaBuilder.Start {

  /** The steps so far: a saga that gives {@link Unit} and tracks nothing before the first step. */
  private final Saga<A> saga;

  SagaSteps(Saga<A> saga) {
    this.saga = saga;
  }

  /** The steps so far, which {@link SagaBuilder#build()} hands out once there is at least one. */
  final Saga<A> saga() {
    return saga;
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
  public final <B> SagaBuilder<B> step(String name, VTask<B> action, Consumer<B> compensate) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");

    Saga<B> newSaga = saga.andThen(_ -> Saga.namedStepSync(name, action, compensate));
    return new SagaBuilder<>(newSaga);
  }

  /**
   * Adds a step whose action depends on the previous step's result ({@link Unit#INSTANCE} before
   * the first step), with synchronous compensation.
   *
   * @param name the step name
   * @param action function producing the forward action from the previous result
   * @param compensate compensation to run on failure
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public final <B> SagaBuilder<B> step(
      String name, Function<A, VTask<B>> action, Consumer<B> compensate) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");

    Saga<B> newSaga =
        saga.andThen(prevResult -> Saga.namedStepSync(name, action.apply(prevResult), compensate));
    return new SagaBuilder<>(newSaga);
  }

  /**
   * Adds a step with asynchronous (VTask-based) compensation.
   *
   * @param name the step name
   * @param action function producing the forward action from the previous result ({@link
   *     Unit#INSTANCE} before the first step)
   * @param compensate function producing a compensation VTask
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public final <B> SagaBuilder<B> stepAsync(
      String name, Function<A, VTask<B>> action, Function<B, VTask<Unit>> compensate) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");

    Saga<B> newSaga =
        saga.andThen(prevResult -> Saga.namedStep(name, action.apply(prevResult), compensate));
    return new SagaBuilder<>(newSaga);
  }

  /**
   * Adds a step with no compensation. Use for final steps or idempotent operations.
   *
   * @param name the step name
   * @param action function producing the forward action from the previous result ({@link
   *     Unit#INSTANCE} before the first step)
   * @param <B> the result type of this step
   * @return a new SagaBuilder at the new step
   */
  public final <B> SagaBuilder<B> stepNoCompensation(String name, Function<A, VTask<B>> action) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(action, "action must not be null");

    Saga<B> newSaga =
        saga.andThen(
            prevResult ->
                Saga.namedStep(name, action.apply(prevResult), _ -> VTask.succeed(Unit.INSTANCE)));
    return new SagaBuilder<>(newSaga);
  }
}
