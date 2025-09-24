// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
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

  private static final EitherMonad<?> INSTANCE = new EitherMonad<>();

  private static final Class<EitherMonad> EITHER_MONAD_CLASS = EitherMonad.class;

  private EitherMonad() {
    // Private constructor
  }

  @SuppressWarnings("unchecked")
  public static <L> EitherMonad<L> instance() {
    return (EitherMonad<L>) INSTANCE;
  }

  /**
   * Lifts a value into the "Right" side of an {@link Either}. This is equivalent to {@code
   * Either.right(value)}.
   *
   * @param value The value to lift into the {@link Either.Right}. Can be {@code null}.
   * @param <R> The type of the "Right" value.
   * @return A {@code Kind<EitherKind.Witness<L>, R>} representing {@code Right(value)}. Never null.
   */
  @Override
  public <R> Kind<EitherKind.Witness<L>, R> of(@Nullable R value) {
    return EITHER.widen(Either.right(value));
  }

  /**
   * Sequentially composes two {@link Either} actions, operating on the "Right" value. If {@code ma}
   * is {@code Right(a)}, applies {@code f} to {@code a} to get a new {@link Kind}. If {@code ma} is
   * {@code Left(l)}, propagates the {@code Left(l)} unchanged.
   *
   * @param f The function to apply to the "Right" value, returning a new {@code
   *     Kind<EitherKind.Witness<L>, B>}. Must not be null.
   * @param ma The input {@code Kind<EitherKind.Witness<L>, A>}. Must not be null.
   * @param <A> The type of the "Right" value in the input {@code Kind}.
   * @param <B> The type of the "Right" value in the resulting {@code Kind}.
   * @return The resulting {@code Kind<EitherKind.Witness<L>, B>} after applying {@code f}, or the
   *     original "Left" propagated. Never null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped
   *     to a valid {@code Either} representation.
   */
  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> flatMap(
      Function<? super A, ? extends Kind<EitherKind.Witness<L>, B>> f,
      Kind<EitherKind.Witness<L>, A> ma) {
    Function<? super A, ? extends Kind<EitherKind.Witness<L>, B>> validatedF =
        FunctionValidator.requireFlatMapper(f, EITHER_MONAD_CLASS, FLAT_MAP);
    Kind<EitherKind.Witness<L>, A> validatedMa =
        KindValidator.requireNonNull(ma, EITHER_MONAD_CLASS, FLAT_MAP);

    Either<L, A> eitherA = EITHER.narrow(validatedMa);
    Either<L, B> resultEither =
        eitherA.flatMap(
            a -> {
              Kind<EitherKind.Witness<L>, B> kindB = validatedF.apply(a);
              FunctionValidator.requireNonNullResult(kindB, FLAT_MAP, Either.class);
              return EITHER.narrow(kindB);
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
   * @throws NullPointerException if {@code ffKind} or {@code faKind} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ffKind} or {@code faKind}
   *     cannot be unwrapped to valid {@code Either} representations.
   */
  @Override
  public <A, B> Kind<EitherKind.Witness<L>, B> ap(
      Kind<EitherKind.Witness<L>, ? extends Function<A, B>> ffKind,
      Kind<EitherKind.Witness<L>, A> faKind) {

    // Enhanced validation with descriptive parameters
    Kind<EitherKind.Witness<L>, ? extends Function<A, B>> validatedFfKind =
        KindValidator.requireNonNull(ffKind, EITHER_MONAD_CLASS, AP, "function");
    Kind<EitherKind.Witness<L>, A> validatedFaKind =
        KindValidator.requireNonNull(faKind, EITHER_MONAD_CLASS, AP, "argument");

    Either<L, ? extends Function<A, B>> eitherF = EITHER.narrow(validatedFfKind);
    Either<L, A> eitherA = EITHER.narrow(validatedFaKind);

    Either<L, B> resultEither = eitherF.flatMap(eitherA::map);
    return EITHER.widen(resultEither);
  }

  // map is inherited from EitherFunctor and is correct.

  @Override
  public <A, B, C, R_TYPE> Kind<EitherKind.Witness<L>, R_TYPE> map3(
      Kind<EitherKind.Witness<L>, A> faKind,
      Kind<EitherKind.Witness<L>, B> fbKind,
      Kind<EitherKind.Witness<L>, C> fcKind,
      Function3<? super A, ? super B, ? super C, ? extends R_TYPE> f) {
    Kind<EitherKind.Witness<L>, A> validatedFaKind =
        KindValidator.requireNonNull(faKind, EITHER_MONAD_CLASS, MAP_3, "first");
    Kind<EitherKind.Witness<L>, B> validatedFbKind =
        KindValidator.requireNonNull(fbKind, EITHER_MONAD_CLASS, MAP_3, "second");
    Kind<EitherKind.Witness<L>, C> validatedFcKind =
        KindValidator.requireNonNull(fcKind, EITHER_MONAD_CLASS, MAP_3, "third");
    Function3<? super A, ? super B, ? super C, ? extends R_TYPE> validatedF =
        FunctionValidator.requireFunction(f, "combining function", EITHER_MONAD_CLASS, MAP_3);

    return this.flatMap(
        a ->
            this.flatMap(
                b -> this.map(c -> validatedF.apply(a, b, c), validatedFcKind), validatedFbKind),
        validatedFaKind);
  }

  @Override
  public <A, B, C, D, R_TYPE> Kind<EitherKind.Witness<L>, R_TYPE> map4(
      Kind<EitherKind.Witness<L>, A> faKind,
      Kind<EitherKind.Witness<L>, B> fbKind,
      Kind<EitherKind.Witness<L>, C> fcKind,
      Kind<EitherKind.Witness<L>, D> fdKind,
      Function4<? super A, ? super B, ? super C, ? super D, ? extends R_TYPE> f) {

    Kind<EitherKind.Witness<L>, A> validatedFaKind =
        KindValidator.requireNonNull(faKind, EITHER_MONAD_CLASS, MAP_4, "first");
    Kind<EitherKind.Witness<L>, B> validatedFbKind =
        KindValidator.requireNonNull(fbKind, EITHER_MONAD_CLASS, MAP_4, "second");
    Kind<EitherKind.Witness<L>, C> validatedFcKind =
        KindValidator.requireNonNull(fcKind, EITHER_MONAD_CLASS, MAP_4, "third");
    Kind<EitherKind.Witness<L>, D> validatedFdKind =
        KindValidator.requireNonNull(fdKind, EITHER_MONAD_CLASS, MAP_4, "fourth");
    Function4<? super A, ? super B, ? super C, ? super D, ? extends R_TYPE> validatedF =
        FunctionValidator.requireFunction(f, "combining function", EITHER_MONAD_CLASS, MAP_4);

    return this.flatMap(
        a ->
            this.flatMap(
                b ->
                    this.flatMap(
                        c -> this.map(d -> validatedF.apply(a, b, c, d), validatedFdKind),
                        validatedFcKind),
                validatedFbKind),
        validatedFaKind);
  }

  /**
   * Raises an error in the {@code Kind<EitherKind.Witness<L>, R>} context by creating a "Left"
   * value.
   *
   * @param error The error value of type {@code L}. Can be null if {@code L} is nullable.
   * @return A {@code Kind<EitherKind.Witness<L>, R>} representing {@code Left(error)}.
   */
  @Override
  public <A> Kind<EitherKind.Witness<L>, A> raiseError(@Nullable L error) {
    // Either allows null error values - no validation needed
    return EITHER.widen(Either.left(error));
  }

  /**
   * Handles an error (a "Left" value) using the provided handler function.
   *
   * @param ma The {@code Kind<EitherKind.Witness<L>, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents a "Left" value. Must not be null.
   * @param <A> The type of the "Right" value.
   * @return A {@code Kind<EitherKind.Witness<L>, A>}, either the original or the result of the
   *     handler.
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped.
   */
  @Override
  public <A> Kind<EitherKind.Witness<L>, A> handleErrorWith(
      Kind<EitherKind.Witness<L>, A> ma,
      Function<? super L, ? extends Kind<EitherKind.Witness<L>, A>> handler) {

    Kind<EitherKind.Witness<L>, A> validatedMa =
        KindValidator.requireNonNull(ma, EITHER_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    Function<? super L, ? extends Kind<EitherKind.Witness<L>, A>> validatedHandler =
        FunctionValidator.requireFunction(
            handler, "handler", EITHER_MONAD_CLASS, HANDLE_ERROR_WITH);

    Either<L, A> either = EITHER.narrow(validatedMa);
    return either.fold(validatedHandler, _ -> validatedMa);
  }

  @Override
  public <A> Kind<EitherKind.Witness<L>, A> recoverWith(
      final Kind<EitherKind.Witness<L>, A> ma, final Kind<EitherKind.Witness<L>, A> fallback) {

    KindValidator.requireNonNull(ma, EITHER_MONAD_CLASS, RECOVER_WITH, "source");
    KindValidator.requireNonNull(fallback, EITHER_MONAD_CLASS, RECOVER_WITH, "fallback");

    return handleErrorWith(ma, error -> fallback);
  }

  @Override
  public <A> Kind<EitherKind.Witness<L>, A> recover(
      final Kind<EitherKind.Witness<L>, A> ma, @Nullable A value) {

    KindValidator.requireNonNull(ma, EITHER_MONAD_CLASS, RECOVER, "source");

    return handleError(ma, _ -> value);
  }
}
