// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import org.higherkindedj.hkt.state.State;

/**
 * Stage 1: Configure State test instances.
 *
 * <p>Entry point for State core type testing with progressive disclosure.
 *
 * @param <S> The state type
 * @param <A> The value type
 */
public final class StateCoreTestStage<S, A> {
  private final Class<?> contextClass;

  public StateCoreTestStage(Class<?> contextClass) {
    this.contextClass = contextClass;
  }

  /**
   * Provides a State instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withInitialState(...)}
   *
   * @param stateInstance A State instance
   * @return Next stage for configuring initial state
   */
  public StateInstanceStage<S, A> withState(State<S, A> stateInstance) {
    return new StateInstanceStage<>(contextClass, stateInstance);
  }
}
