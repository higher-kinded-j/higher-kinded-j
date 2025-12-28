// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state_t.StateT;

/**
 * Stage 1: Configure StateT test instances.
 *
 * <p>Entry point for StateT core type testing with progressive disclosure.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 */
public final class StateTCoreTestStage<S, F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;

  public StateTCoreTestStage(Class<?> contextClass, Monad<F> outerMonad) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
  }

  /**
   * Provides a StateT instance with a simple state transformation for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withAnotherInstance(...)}
   *
   * @param stateInstance A StateT instance (can have null value)
   * @return Next stage for configuring another instance
   */
  public StateTInstanceStage<S, F, A> withInstance(StateT<S, F, A> stateInstance) {
    return new StateTInstanceStage<>(contextClass, outerMonad, stateInstance, null);
  }

  /**
   * Provides a second StateT instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withInstance(...)}
   *
   * @param stateInstance A StateT instance (can have null value)
   * @return Next stage for configuring first instance
   */
  public StateTInstanceStage<S, F, A> withAnotherInstance(StateT<S, F, A> stateInstance) {
    return new StateTInstanceStage<>(contextClass, outerMonad, null, stateInstance);
  }
}
