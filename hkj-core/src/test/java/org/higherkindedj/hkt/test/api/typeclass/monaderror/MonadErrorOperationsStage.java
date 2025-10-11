// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.monaderror;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;

/**
 * Stage 4: Configure MonadError-specific error handling.
 *
 * <p>Progressive disclosure: Shows error handling configuration. Now that Monad operations are
 * configured, we can add error handling.
 *
 * @param <F> The MonadError witness type
 * @param <E> The error type
 * @param <A> The input type
 * @param <B> The mapped type
 */
public final class MonadErrorOperationsStage<F, E, A, B> {
  private final Class<?> contextClass;
  private final MonadError<F, E> monadError;
  private final Kind<F, A> validKind;
  private final Function<A, B> mapper;
  private final Function<A, Kind<F, B>> flatMapper;
  private final Kind<F, Function<A, B>> functionKind;

  MonadErrorOperationsStage(
      Class<?> contextClass,
      MonadError<F, E> monadError,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Function<A, Kind<F, B>> flatMapper,
      Kind<F, Function<A, B>> functionKind) {

    this.contextClass = contextClass;
    this.monadError = monadError;
    this.validKind = validKind;
    this.mapper = mapper;
    this.flatMapper = flatMapper;
    this.functionKind = functionKind;
  }

  /**
   * Provides error handling functions.
   *
   * <p>Progressive disclosure: Next steps are optional law configuration, validation configuration,
   * test selection, or immediate execution.
   *
   * @param handler The error handler function (E -> Kind&lt;F, A&gt;)
   * @param fallback A fallback Kind for recovery testing
   * @return Handler stage with execution options
   */
  public MonadErrorHandlerStage<F, E, A, B> withErrorHandling(
      Function<E, Kind<F, A>> handler, Kind<F, A> fallback) {

    return new MonadErrorHandlerStage<>(
        contextClass, monadError, validKind, mapper, flatMapper, functionKind, handler, fallback);
  }
}
