// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state_t.StateT;

/**
 * Stage 3: Configure mapping functions for StateT testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <S> The state type
 * @param <F> The outer monad witness type
 * @param <A> The value type
 */
public final class StateTOperationsStage<S, F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final StateT<S, F, A> firstInstance;
  private final StateT<S, F, A> secondInstance;

  StateTOperationsStage(
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
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (A -> B)
   * @param <B> The mapped type
   * @return Configuration stage with execution options
   */
  public <B> StateTTestConfigStage<S, F, A, B> withMappers(Function<A, B> mapper) {
    return new StateTTestConfigStage<>(
        contextClass, outerMonad, firstInstance, secondInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like
   * factory methods, runner methods).
   *
   * @return Configuration stage without mappers
   */
  public StateTTestConfigStage<S, F, A, String> withoutMappers() {
    return new StateTTestConfigStage<>(
        contextClass, outerMonad, firstInstance, secondInstance, null);
  }
}
