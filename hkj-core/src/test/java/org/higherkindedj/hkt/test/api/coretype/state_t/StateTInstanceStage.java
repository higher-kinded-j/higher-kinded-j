// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state_t.StateT;

/**
 * Stage 2: Complete StateT instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 */
public final class StateTInstanceStage<S, F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final StateT<S, F, A> firstInstance;
  private final StateT<S, F, A> secondInstance;

  StateTInstanceStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      StateT<S, F, A> firstInstance,
      StateT<S, F, A> secondInstance) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.firstInstance = firstInstance;
    this.secondInstance = secondInstance;
  }

  /**
   * Provides the second instance (if first was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param secondInstance A StateT instance
   * @return Next stage for configuring mappers
   */
  public StateTOperationsStage<S, F, A> withAnotherInstance(StateT<S, F, A> secondInstance) {
    if (this.firstInstance == null) {
      throw new IllegalStateException("Cannot set second instance twice");
    }
    return new StateTOperationsStage<>(contextClass, outerMonad, firstInstance, secondInstance);
  }

  /**
   * Provides the first instance (if second was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param firstInstance A StateT instance
   * @return Next stage for configuring mappers
   */
  public StateTOperationsStage<S, F, A> withInstance(StateT<S, F, A> firstInstance) {
    if (this.secondInstance == null) {
      throw new IllegalStateException("Cannot set first instance twice");
    }
    return new StateTOperationsStage<>(contextClass, outerMonad, firstInstance, secondInstance);
  }
}
