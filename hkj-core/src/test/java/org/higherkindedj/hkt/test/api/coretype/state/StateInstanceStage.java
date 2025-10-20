// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import org.higherkindedj.hkt.state.State;

/**
 * Stage 2: Configure initial state for State testing.
 *
 * <p>Progressive disclosure: Shows initial state configuration.
 *
 * @param <S> The state type
 * @param <A> The value type
 */
public final class StateInstanceStage<S, A> {
  private final Class<?> contextClass;
  private final State<S, A> stateInstance;

  StateInstanceStage(Class<?> contextClass, State<S, A> stateInstance) {
    this.contextClass = contextClass;
    this.stateInstance = stateInstance;
  }

  /**
   * Provides the initial state for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param initialState The initial state to provide
   * @return Next stage for configuring mappers
   */
  public StateOperationsStage<S, A> withInitialState(S initialState) {
    return new StateOperationsStage<>(contextClass, stateInstance, initialState);
  }
}
