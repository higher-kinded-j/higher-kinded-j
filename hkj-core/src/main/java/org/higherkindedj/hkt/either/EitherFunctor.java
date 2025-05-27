// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.unwrap;
import static org.higherkindedj.hkt.either.EitherKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link Functor} interface for the {@link Either} type, biased towards the "Right"
 * value. This allows mapping a function over the {@code R} value of an {@code Either<L, R>} if it's
 * a {@link Either.Right}, while leaving a {@link Either.Left} unchanged.
 *
 * @param <L> The fixed "Left" type (typically representing an error or alternative) for which this
 *     functor instance is defined.
 * @see Either
 * @see EitherKind
 * @see EitherKind.Witness
 * @see Functor
 * @see EitherKindHelper
 */
public class EitherFunctor<L> implements Functor<EitherKind.Witness<L>> {

  /**
   * Applies a function to the "Right" value if the provided {@link Kind} represents a {@link
   * Either.Right}. If it represents a {@link Either.Left}, the "Left" value is propagated
   * unchanged.
   *
   * @param f The non-null function to apply to the "Right" value.
   * @param ma The input {@code Kind<EitherKind.Witness<L>, A>}, representing an {@code Either<L,
   *     A>}. Must not be null.
   * @param <A> The type of the "Right" value in the input {@code Either}.
   * @param <B> The type of the "Right" value in the resulting {@code Either} after function
   *     application.
   * @return A new {@code Kind<EitherKind.Witness<L>, B>} representing the transformed {@code
   *     Either<L, B>}. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<EitherKind.Witness<L>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<EitherKind.Witness<L>, A> ma) {
    Either<L, A> eitherA = unwrap(ma);
    Either<L, B> resultEither = eitherA.map(f); // Delegates to Either's right-biased map
    return wrap(resultEither);
  }
}
