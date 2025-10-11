// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import java.util.function.Function;
import org.higherkindedj.hkt.state.State;

/**
 * Stage 3: Configure mapping functions for State testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <S> The state type
 * @param <A> The value type
 */
public final class StateOperationsStage<S, A> {
  private final Class<?> contextClass;
  private final State<S, A> stateInstance;
  private final S initialState;

  StateOperationsStage(Class<?> contextClass, State<S, A> stateInstance, S initialState) {
    this.contextClass = contextClass;
    this.stateInstance = stateInstance;
    this.initialState = initialState;
  }

  /**
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (A -> B)
   * @param <B> The mapped type
   * @return Configuration stage with execution options
   */
  public <B> StateTestConfigStage<S, A, B> withMappers(Function<A, B> mapper) {
    return new StateTestConfigStage<>(contextClass, stateInstance, initialState, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like run,
   * pure, get, set, modify, inspect).
   *
   * @return Configuration stage without mappers
   */
  public StateTestConfigStage<S, A, String> withoutMappers() {
    return new StateTestConfigStage<>(contextClass, stateInstance, initialState, null);
  }
}
