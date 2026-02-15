// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.either_t;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either_t.EitherT;

/**
 * Stage 2: Complete EitherT instance configuration.
 *
 * <p>Progressive disclosure: Shows completion of instance setup.
 *
 * @param <F> The outer monad witness type
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherTInstanceStage<F extends WitnessArity<TypeArity.Unary>, L, R> {
  private final Class<?> contextClass;
  private final Monad<F> outerMonad;
  private final EitherT<F, L, R> leftInstance;
  private final EitherT<F, L, R> rightInstance;

  EitherTInstanceStage(
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
   * Provides the Right instance (if Left was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param rightInstance A Right instance
   * @return Next stage for configuring mappers
   */
  public EitherTOperationsStage<F, L, R> withRight(EitherT<F, L, R> rightInstance) {
    if (this.leftInstance == null) {
      throw new IllegalStateException("Cannot set Right twice");
    }
    return new EitherTOperationsStage<>(contextClass, outerMonad, leftInstance, rightInstance);
  }

  /**
   * Provides the Left instance (if Right was configured first).
   *
   * <p>Progressive disclosure: Next step is {@code .withMappers(...)}
   *
   * @param leftInstance A Left instance
   * @return Next stage for configuring mappers
   */
  public EitherTOperationsStage<F, L, R> withLeft(EitherT<F, L, R> leftInstance) {
    if (this.rightInstance == null) {
      throw new IllegalStateException("Cannot set Left twice");
    }
    return new EitherTOperationsStage<>(contextClass, outerMonad, leftInstance, rightInstance);
  }
}
