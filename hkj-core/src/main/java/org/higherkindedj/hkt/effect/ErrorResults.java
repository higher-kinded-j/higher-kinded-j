// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;

/**
 * Internal helper that holds an error built from an exception to the non-null contract every error
 * channel keeps.
 *
 * <p>A caller's function that turns an exception into a typed error must not return null. When it
 * does, the {@link NullPointerException} names the function and keeps the exception as its cause,
 * so the failure that was being typed is not lost.
 */
final class ErrorResults {

  private ErrorResults() {}

  /**
   * Applies {@code mapper} to {@code exception}, refusing a null result.
   *
   * @param mapper the caller's function that types the exception
   * @param exception the exception being typed
   * @param mapperName the parameter name the refusal names
   * @param <X> the exception type
   * @param <E> the error type
   * @return the typed error
   * @throws NullPointerException if {@code mapper} returns null, with {@code exception} as its
   *     cause
   */
  static <X extends Throwable, E> E fromException(
      Function<? super X, ? extends E> mapper, X exception, String mapperName) {
    E error = mapper.apply(exception);
    if (error == null) {
      NullPointerException refused = new NullPointerException(mapperName + " must not return null");
      refused.initCause(exception);
      throw refused;
    }
    return error;
  }

  /**
   * Refuses an {@link Either} that is missing, or that holds a null error, before it enters a path.
   *
   * @param either the Either to admit
   * @param name the parameter name the refusal names
   * @param <E> the error type
   * @param <A> the success type
   * @return {@code either}
   * @throws NullPointerException if {@code either} is null or is a {@code Left(null)}
   */
  static <E, A> Either<E, A> admitted(Either<E, A> either, String name) {
    Objects.requireNonNull(either, name + " must not be null");
    if (either.isLeft() && either.getLeft() == null) {
      throw new NullPointerException(name + " must not hold a null error");
    }
    return either;
  }
}
