// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a Monad that can explicitly handle errors of type E. Extends {@code Monad<F>}.
 *
 * @param <F> The witness type for the Monad (e.g., {@code EitherKind<E, ?>}).
 * @param <E> The type of the error value. Nullability depends on context. For example, {@link
 *     Throwable} is typically non-null. If representing simple absence (like in Optional or Maybe),
 *     {@code org.higherkindedj.hkt.Unit} can be used as a non-nullable error type, with its single
 *     instance {@code Unit.INSTANCE} passed to {@link #raiseError(Object)}.
 */
@NullMarked
public interface MonadError<F extends WitnessArity<TypeArity.Unary>, E> extends Monad<F> {

  /**
   * Lifts an error value 'e' into the monadic context F. For {@code Either<E, A>}, this would be
   * Kind(Left(e)). For {@code Maybe<A>} or {@code Optional<A>} (where E is {@code
   * org.higherkindedj.hkt.Unit}), this would be Kind(Nothing) or Kind(Optional.empty()), typically
   * invoked with {@code Unit.INSTANCE}.
   *
   * @param error The error value to lift. If E is {@code org.higherkindedj.hkt.Unit}, this must be
   *     {@code Unit.INSTANCE}. Otherwise, nullability depends on the specific E type.
   * @param <A> The phantom type parameter of the value (since this represents an error state).
   * @return The error wrapped in the context F. Guaranteed non-null.
   */
  <A> Kind<F, A> raiseError(@Nullable final E error);

  /**
   * Handles an error within the monadic context. If 'ma' represents a success value, it's returned
   * unchanged. If 'ma' represents an error 'e', the 'handler' function is applied to 'e' to
   * potentially recover with a new monadic value.
   *
   * @param ma The monadic value potentially containing an error. Assumed non-null.
   * @param handler A function that takes an error 'e' and returns a new monadic value, potentially
   *     recovering from the error. Assumed non-null.
   * @param <A> The type of the value within the monad.
   * @return The original monadic value if it was successful, or the result of the handler if it
   *     contained an error. Guaranteed non-null.
   */
  <A> Kind<F, A> handleErrorWith(
      final Kind<F, A> ma, final Function<? super E, ? extends Kind<F, A>> handler);

  /**
   * A simpler version of handleErrorWith where the handler returns a pure value 'a' which is then
   * lifted into the context using 'of'. Recovers from any error with a default value 'a'.
   *
   * @param ma The monadic value potentially containing an error. Assumed non-null.
   * @param handler A function that takes an error 'e' and returns a pure value 'a'. Assumed
   *     non-null. If E is {@code org.higherkindedj.hkt.Unit}, the handler will receive {@code
   *     Unit.INSTANCE}.
   * @param <A> The type of the value within the monad.
   * @return The original monadic value if successful, or the result of the handler lifted into the
   *     monad if it contained an error. Guaranteed non-null.
   */
  default <A> Kind<F, A> handleError(
      final Kind<F, A> ma, final Function<? super E, ? extends A> handler) {
    return handleErrorWith(ma, error -> of(handler.apply(error)));
  }

  /**
   * Recovers from an error with a default monadic value 'fallback'. If 'ma' contains an error,
   * 'fallback' is returned, otherwise 'ma' is returned.
   *
   * @param ma The monadic value potentially containing an error. Assumed non-null.
   * @param fallback The default monadic value to use in case of error. Assumed non-null.
   * @param <A> The type of the value within the monad.
   * @return 'ma' if successful, 'fallback' otherwise. Guaranteed non-null.
   */
  default <A> Kind<F, A> recoverWith(final Kind<F, A> ma, final Kind<F, A> fallback) {
    return handleErrorWith(ma, error -> fallback);
  }

  /**
   * Recovers from an error with a default pure value 'a'. If 'ma' contains an error, 'of(a)' is
   * returned, otherwise 'ma' is returned.
   *
   * @param ma The monadic value potentially containing an error. Assumed non-null.
   * @param value The default pure value to use in case of error. Nullability depends on `of`.
   * @param <A> The type of the value within the monad.
   * @return 'ma' if successful, 'of(value)' otherwise. Guaranteed non-null.
   */
  default <A> @NonNull Kind<F, A> recover(final Kind<F, A> ma, @Nullable A value) {
    return handleError(ma, _ -> value);
  }
}
