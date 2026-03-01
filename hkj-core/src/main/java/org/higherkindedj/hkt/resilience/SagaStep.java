// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Internal record representing a single step in a {@link Saga}, pairing an action with
 * its compensation logic.
 *
 * @param action the forward action to execute
 * @param compensate a function that produces a compensation task given the action's result
 * @param name a descriptive name for this step (used in error reporting)
 * @param <A> the type of the value produced by the action
 * @see Saga
 * @see SagaBuilder
 */
record SagaStep<A>(
    VTask<A> action,
    Function<A, VTask<Unit>> compensate,
    String name
) {

  /**
   * Creates a SagaStep with a synchronous (Consumer-based) compensation action.
   *
   * @param action the forward action
   * @param compensate the compensation action (receives the result of the forward action)
   * @param name the step name
   * @param <A> the result type
   * @return a new SagaStep
   */
  static <A> SagaStep<A> of(VTask<A> action, Consumer<A> compensate, String name) {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");
    Objects.requireNonNull(name, "name must not be null");
    return new SagaStep<>(
        action,
        a -> VTask.exec(() -> compensate.accept(a)),
        name
    );
  }

  /**
   * Creates a SagaStep with an asynchronous (VTask-based) compensation action.
   *
   * @param action the forward action
   * @param compensate a function that produces a compensation VTask
   * @param name the step name
   * @param <A> the result type
   * @return a new SagaStep
   */
  static <A> SagaStep<A> ofAsync(
      VTask<A> action, Function<A, VTask<Unit>> compensate, String name) {
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(compensate, "compensate must not be null");
    Objects.requireNonNull(name, "name must not be null");
    return new SagaStep<>(action, compensate, name);
  }
}
