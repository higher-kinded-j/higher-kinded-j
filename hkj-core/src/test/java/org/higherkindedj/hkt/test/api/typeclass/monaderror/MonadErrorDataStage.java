// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;

/**
 * Stage 3: Configure Monad operations (map, flatMap, ap).
 *
 * <p>Progressive disclosure: Shows Monad operation configuration. MonadError extends Monad, so we
 * need these operations first.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The mapped type
 */
public final class MonadErrorDataStage<F, E, A, B> {
  private final Class<?> contextClass;
  private final MonadError<F, E> monadError;
  private final Kind<F, A> validKind;

  MonadErrorDataStage(Class<?> contextClass, MonadError<F, E> monadError, Kind<F, A> validKind) {
    this.contextClass = contextClass;
    this.monadError = monadError;
    this.validKind = validKind;
  }

  /**
   * Provides Monad operation functions.
   *
   * <p>Progressive disclosure: Next step is {@code .withErrorHandling(...)}
   *
   * @param mapper The map function (A -> B)
   * @param flatMapper The flatMap function (A -> Kind&lt;F, B&gt;)
   * @param functionKind A Kind containing a function for ap testing
   * @return Next stage for configuring error handling
   */
  public MonadErrorOperationsStage<F, E, A, B> withMonadOperations(
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind) {

    return new MonadErrorOperationsStage<>(
        contextClass, monadError, validKind, mapper, flatMapper, functionKind);
  }
}
