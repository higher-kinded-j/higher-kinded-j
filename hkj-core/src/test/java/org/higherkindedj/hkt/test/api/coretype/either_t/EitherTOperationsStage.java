// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either_t.EitherT;

/**
 * Stage 3: Configure mapping functions for EitherT testing.
 *
 * <p>Progressive disclosure: Shows mapper configuration and execution options.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherTOperationsStage<F extends WitnessArity<TypeArity.Unary>, L, R> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final EitherT<F, L, R> leftInstance;
  private final EitherT<F, L, R> rightInstance;

  EitherTOperationsStage(
      Class<?> contextClass,
      Monad<F> outerMonad,
      EitherT<F, L, R> leftInstance,
      EitherT<F, L, R> rightInstance) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
    this.leftInstance = leftInstance;
    this.rightInstance = rightInstance;
  }

  /**
   * Provides mapping functions for testing map and flatMap operations.
   *
   * <p>Progressive disclosure: Next steps are test selection or execution.
   *
   * @param mapper The mapping function (R -> S)
   * @param <S> The mapped type
   * @return Configuration stage with execution options
   */
  public <S> EitherTTestConfigStage<F, L, R, S> withMappers(Function<R, S> mapper) {
    return new EitherTTestConfigStage<>(
        contextClass, outerMonad, leftInstance, rightInstance, mapper);
  }

  /**
   * Skip mapper configuration and proceed to testing.
   *
   * <p>This is useful when you only want to test operations that don't require mappers (like
   * factory methods, value accessor).
   *
   * @return Configuration stage without mappers
   */
  public EitherTTestConfigStage<F, L, R, String> withoutMappers() {
    return new EitherTTestConfigStage<>(
        contextClass, outerMonad, leftInstance, rightInstance, null);
  }
}
