// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;

/**
 * Stage 2: Configure the base Kind for testing.
 *
 * <p>Progressive disclosure: Shows only Kind configuration.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The value type
 */
public final class MonadErrorInstanceStage<F, E, A> {
  private final Class<?> contextClass;
  private final MonadError<F, E> monadError;

  MonadErrorInstanceStage(Class<?> contextClass, MonadError<F, E> monadError) {
    this.contextClass = contextClass;
    this.monadError = monadError;
  }

  /**
   * Provides the test Kind instance.
   *
   * <p>Progressive disclosure: Next step is {@code .withMonadOperations(...)}
   *
   * @param validKind A valid Kind instance for testing
   * @param <B> The mapped type
   * @return Next stage for configuring Monad operations
   */
  public <B> MonadErrorDataStage<F, E, A, B> withKind(Kind<F, A> validKind) {
    return new MonadErrorDataStage<>(contextClass, monadError, validKind);
  }
}
