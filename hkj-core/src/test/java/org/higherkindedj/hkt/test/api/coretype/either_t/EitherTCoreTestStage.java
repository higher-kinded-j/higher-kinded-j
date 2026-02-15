// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either_t.EitherT;

/**
 * Stage 1: Configure EitherT test instances.
 *
 * <p>Entry point for EitherT core type testing with progressive disclosure.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherTCoreTestStage<F extends WitnessArity<TypeArity.Unary>, L, R> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;

  public EitherTCoreTestStage(Class<?> contextClass, Monad<F> outerMonad) {
    this.contextClass = contextClass;
    this.outerMonad = outerMonad;
  }

  /**
   * Provides a Left instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withRight(...)}
   *
   * @param leftInstance A Left instance (can have null value)
   * @return Next stage for configuring Right instance
   */
  public EitherTInstanceStage<F, L, R> withLeft(EitherT<F, L, R> leftInstance) {
    return new EitherTInstanceStage<>(contextClass, outerMonad, leftInstance, null);
  }

  /**
   * Provides a Right instance for testing.
   *
   * <p>Progressive disclosure: Next step is {@code .withLeft(...)}
   *
   * @param rightInstance A Right instance (can have null value)
   * @return Next stage for configuring Left instance
   */
  public EitherTInstanceStage<F, L, R> withRight(EitherT<F, L, R> rightInstance) {
    return new EitherTInstanceStage<>(contextClass, outerMonad, null, rightInstance);
  }
}
