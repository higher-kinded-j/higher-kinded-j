// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements {@link MonadError} for {@link Either}, with a fixed "Left" type {@code L} serving as
 * the error type {@code E}. All operations are right-biased, meaning they operate on the {@link
 * Either.Right} value and pass {@link Either.Left} values through.
 *
 * @param <L> The fixed type for the "Left" value, representing the error type for {@link
 *     MonadError}.
 * @see Either
 * @see EitherKind
 * @see EitherKind.Witness
 * @see MonadError
 * @see EitherFunctor
 * @see EitherKindHelper
 */
public class EitherMonad<L> extends EitherFunctor<L>
    implements MonadError<EitherKind.Witness<L>, L> {

  /**
   * Lifts a value into the "Right" side of an {@link Either}. This is equivalent to {@code
   * Either.right(value)}.
   *
   * @param value The value to lift into the {@link Either.Right}. Can be {@code null}.
   * @param <R> The type of the "Right" value.
   * @return A {@code Kind<EitherKind.Witness<L>, R>} representing {@code Right(value)}. Never null.
   */
  @Override
  public <R> @NonNull Kind<EitherKind.Witness<L>, R> of(@Nullable R value) {
    return EITHER.widen(Either.right(value));
  }

  /**
   * Sequentially composes two {@link Either} actions, operating on the "Right" value. If {@code ma}
   * is {@code Right(a)}, applies {@code f} to {@code a} to get a new {@link Kind}. If {@code ma} is
   * {@code Left(l)}, propagates the {@code Left(l)} unchanged.
   *
   * @param f The non-null function to apply to the "Right" value, returning a new {@code
   *     Kind<EitherKind.Witness<L>, B>}.
   * @param ma The input {@code Kind<EitherKind.Witness<L>, A>}. Must not be null.
   * @param <A> The type of the "Right" value in the input {@code Kind}.
   * @param <B> The type of the "Right" value in the resulting {@code Kind}.
   * @return The resulting {@code Kind<EitherKind.Witness<L>, B>} after applying {@code f}, or the
   *     original "Left" propagated. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<EitherKind.Witness<L>, B> flatMap(
      @NonNull Function<A, Kind<EitherKind.Witness<L>, B>> f,
      @NonNull Kind<EitherKind.Witness<L>, A> ma) {
    Either<L, A> eitherA = EITHER.narrow(ma);
    Either<L, B> resultEither =
        eitherA.flatMap(
            a -> {
              Kind<EitherKind.Witness<L>, B> kindB = f.apply(a);
              return EITHER.narrow(kindB); // Unwrap inner Kind to Either for Either.flatMap
            });
    return EITHER.widen(resultEither);
  }

  /**
   * Applies a function wrapped in an {@code Either} to a value wrapped in an {@code Either}. If
   * both {@code ffKind} (the function container) and {@code faKind} (the argument container) are
   * {@link Either.Right}, the function is applied to the value. If either is {@link Either.Left},
   * the first encountered "Left" is propagated.
   *
   * @param ffKind The {@code Kind<EitherKind.Witness<L>, Function<A, B>>} containing the function.
   *     Must not be null.
   * @param faKind The {@code Kind<EitherKind.Witness<L>, A>} containing the argument. Must not be
   *     null.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<EitherKind.Witness<L>, B>} representing the result. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<EitherKind.Witness<L>, B> ap(
      @NonNull Kind<EitherKind.Witness<L>, Function<A, B>> ffKind,
      @NonNull Kind<EitherKind.Witness<L>, A> faKind) {
    Either<L, Function<A, B>> eitherF = EITHER.narrow(ffKind);
    Either<L, A> eitherA = EITHER.narrow(faKind);

    // This uses the Either.flatMap and Either.map methods directly, which is efficient.
    // funcValue is the Function<A,B> from eitherF if Right
    // argValue is the A from eitherA if Right
    Either<L, B> resultEither = eitherF.flatMap(eitherA::map);
    return EITHER.widen(resultEither);
  }

  // map is inherited from EitherFunctor and is correct.

  @Override
  public <A, B, C, R_TYPE> @NonNull Kind<EitherKind.Witness<L>, R_TYPE> map3(
      @NonNull Kind<EitherKind.Witness<L>, A> faKind,
      @NonNull Kind<EitherKind.Witness<L>, B> fbKind,
      @NonNull Kind<EitherKind.Witness<L>, C> fcKind,
      @NonNull Function3<A, B, C, R_TYPE> f) {
    // Monad.flatMap(Function<T, Kind<F, U>> func, Kind<F, T> kind)
    // Monad.map(Function<T, U> func, Kind<F, T> kind)
    return this.flatMap(
        a -> this.flatMap(b -> this.map(c -> f.apply(a, b, c), fcKind), fbKind), faKind);
  }

  @Override
  public <A, B, C, D, R_TYPE> @NonNull Kind<EitherKind.Witness<L>, R_TYPE> map4(
      @NonNull Kind<EitherKind.Witness<L>, A> faKind,
      @NonNull Kind<EitherKind.Witness<L>, B> fbKind,
      @NonNull Kind<EitherKind.Witness<L>, C> fcKind,
      @NonNull Kind<EitherKind.Witness<L>, D> fdKind,
      @NonNull Function4<A, B, C, D, R_TYPE> f) {
    return this.flatMap(
        a ->
            this.flatMap(
                b -> this.flatMap(c -> this.map(d -> f.apply(a, b, c, d), fdKind), fcKind), fbKind),
        faKind);
  }

  @Override
  public <A> @NonNull Kind<EitherKind.Witness<L>, A> raiseError(@Nullable L error) {
    return EITHER.widen(Either.left(error));
  }

  @Override
  public <A> @NonNull Kind<EitherKind.Witness<L>, A> handleErrorWith(
      @NonNull Kind<EitherKind.Witness<L>, A> ma,
      @NonNull Function<L, Kind<EitherKind.Witness<L>, A>> handler) {
    Either<L, A> either = EITHER.narrow(ma);
    return either.fold(handler, rightValue -> ma);
  }
}
