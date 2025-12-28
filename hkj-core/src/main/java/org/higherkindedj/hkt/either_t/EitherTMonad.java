// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} interface for the {@link EitherT} monad transformer. The HKT
 * witness used is {@code EitherTKind.Witness<F, L>}.
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F}. The 'left' type
 * {@code L} of the inner {@link Either} serves as the error type {@code E} for this {@code
 * MonadError} instance.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}).
 * @param <L> The type of the 'left' (error) value in the inner {@link Either}. This is fixed for a
 *     given instance of {@code EitherTMonad}.
 * @see EitherT
 * @see EitherTKind
 * @see EitherTKind.Witness
 * @see MonadError
 * @see EitherTKindHelper
 */
public class EitherTMonad<F extends WitnessArity<TypeArity.Unary>, L>
    implements MonadError<EitherTKind.Witness<F, L>, L> {

  private static final Class<EitherTMonad> EITHER_T_MONAD_CLASS = EitherTMonad.class;
  private final Monad<F> outerMonad;

  /**
   * Constructs an {@code EitherTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public EitherTMonad(Monad<F> outerMonad) {
    this.outerMonad =
        Validation.transformer().requireOuterMonad(outerMonad, EITHER_T_MONAD_CLASS, CONSTRUCTION);
  }

  /**
   * Lifts a 'right' value {@code r} into the {@code Kind<EitherTKind.Witness<F, L>, R>} context.
   * This results in an {@code EitherT} wrapping {@code F<Right(r)>}.
   *
   * @param <R> The type of the 'right' value.
   * @param r The 'right' value to lift. Can be null if {@code R} is nullable.
   * @return A {@code Kind<EitherTKind.Witness<F, L>, R>} representing the lifted 'right' value.
   *     Never null.
   */
  @Override
  public <R> Kind<EitherTKind.Witness<F, L>, R> of(@Nullable R r) {
    EitherT<F, L, R> concreteEitherT = EitherT.right(outerMonad, r);
    return EITHER_T.widen(concreteEitherT);
  }

  /**
   * Maps a function {@code f} over the 'right' value within a {@code Kind<EitherTKind.Witness<F,
   * L>, R_IN>}. The transformation occurs within the context of the outer monad {@code F} and the
   * inner {@link Either}.
   *
   * @param <R_IN> The original type of the 'right' value.
   * @param <R_OUT> The new type of the 'right' value after applying the function.
   * @param f The function to apply to the 'right' value. Must not be null.
   * @param fa The {@code Kind<EitherTKind.Witness<F, L>, R_IN>} to map over. Must not be null.
   * @return A new {@code Kind<EitherTKind.Witness<F, L>, R_OUT>} with the function applied. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <R_IN, R_OUT> Kind<EitherTKind.Witness<F, L>, R_OUT> map(
      Function<? super R_IN, ? extends R_OUT> f, Kind<EitherTKind.Witness<F, L>, R_IN> fa) {

    Validation.function().requireMapper(f, "f", EITHER_T_MONAD_CLASS, MAP);
    Validation.kind().requireNonNull(fa, EITHER_T_MONAD_CLASS, MAP);

    EitherT<F, L, R_IN> eitherT = EITHER_T.narrow(fa);
    Kind<F, Either<L, R_OUT>> newValue = outerMonad.map(either -> either.map(f), eitherT.value());
    return EITHER_T.widen(EitherT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<EitherTKind.Witness<F, L>, Function<R_IN, R_OUT>>} to
   * a 'right' value wrapped in {@code Kind<EitherTKind.Witness<F, L>, R_IN>}. This operation
   * respects both the outer monad {@code F} and the inner {@link Either}'s error handling.
   *
   * @param <R_IN> The type of the input 'right' value.
   * @param <R_OUT> The type of the result 'right' value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped 'right' value. Must not be null.
   * @return A new {@code Kind<EitherTKind.Witness<F, L>, R_OUT>} representing the application.
   *     Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <R_IN, R_OUT> Kind<EitherTKind.Witness<F, L>, R_OUT> ap(
      Kind<EitherTKind.Witness<F, L>, ? extends Function<R_IN, R_OUT>> ff,
      Kind<EitherTKind.Witness<F, L>, R_IN> fa) {

    Validation.kind().requireNonNull(ff, EITHER_T_MONAD_CLASS, AP, "function");
    Validation.kind().requireNonNull(fa, EITHER_T_MONAD_CLASS, AP, "argument");

    EitherT<F, L, ? extends Function<R_IN, R_OUT>> funcT = EITHER_T.narrow(ff);
    EitherT<F, L, R_IN> valT = EITHER_T.narrow(fa);

    Kind<F, Either<L, R_OUT>> resultValue =
        outerMonad.flatMap(
            eitherF ->
                outerMonad.map(
                    eitherA ->
                        eitherF.flatMap(eitherA::map), // Applies func if both Eithers are Right
                    valT.value()),
            funcT.value());

    return EITHER_T.widen(EitherT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<EitherTKind.Witness<F, L>, R_OUT>} to
   * the 'right' value within a {@code Kind<EitherTKind.Witness<F, L>, R_IN>}, and flattens the
   * result. This is the core monadic bind operation, sequencing computations within {@code
   * EitherT}.
   *
   * @param <R_IN> The original type of the 'right' value.
   * @param <R_OUT> The type of the 'right' value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<EitherTKind.Witness<F, L>, R_IN>} to transform. Must not be null.
   * @return A new {@code Kind<EitherTKind.Witness<F, L>, R_OUT>}. Never null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code f} cannot be unwrapped.
   */
  @Override
  public <R_IN, R_OUT> Kind<EitherTKind.Witness<F, L>, R_OUT> flatMap(
      Function<? super R_IN, ? extends Kind<EitherTKind.Witness<F, L>, R_OUT>> f,
      Kind<EitherTKind.Witness<F, L>, R_IN> ma) {

    Validation.function().requireFlatMapper(f, "f", EITHER_T_MONAD_CLASS, FLAT_MAP);
    Validation.kind().requireNonNull(ma, EITHER_T_MONAD_CLASS, FLAT_MAP);

    EitherT<F, L, R_IN> eitherT_ma = EITHER_T.narrow(ma);

    Kind<F, Either<L, R_OUT>> newUnderlyingValue =
        outerMonad.flatMap(
            (Either<L, R_IN> innerEither) -> {
              if (innerEither.isRight()) {
                R_IN r_in = innerEither.getRight();
                Kind<EitherTKind.Witness<F, L>, R_OUT> resultKindT = f.apply(r_in);
                Validation.function()
                    .requireNonNullResult(resultKindT, "f", EITHER_T_MONAD_CLASS, FLAT_MAP);
                EitherT<F, L, R_OUT> resultT = EITHER_T.narrow(resultKindT);
                return resultT.value(); // This is Kind<F, Either<L, R_OUT>>
              } else {
                // Propagate the Left by lifting it into F
                return outerMonad.of(Either.left(innerEither.getLeft()));
              }
            },
            eitherT_ma.value() // This is Kind<F, Either<L, R_IN>>
            );
    return EITHER_T.widen(EitherT.fromKind(newUnderlyingValue));
  }

  // --- MonadError Methods ---

  /**
   * Raises an error in the {@code Kind<EitherTKind.Witness<F, L>, R>} context. For {@code EitherT},
   * an error is represented by a 'left' value of type {@code L}. This method returns an {@code
   * EitherT} wrapping {@code F<Left(error)>}.
   *
   * @param <R> The type parameter for the 'right' side (will be absent).
   * @param error The 'left' (error) value. Can be null if {@code L} is nullable.
   * @return A {@code Kind<EitherTKind.Witness<F, L>, R>} representing {@code F<Left(error)>}. Never
   *     null.
   */
  @Override
  public <R> Kind<EitherTKind.Witness<F, L>, R> raiseError(@Nullable L error) {
    // Either allows null error values - no validation needed
    EitherT<F, L, R> concreteEitherT = EitherT.left(outerMonad, error);
    return EITHER_T.widen(concreteEitherT);
  }

  /**
   * Handles an error (a 'left' value) in the {@code Kind<EitherTKind.Witness<F, L>, R>}. If the
   * input {@code ma} represents {@code F<Right(r)>}, it's returned unchanged. If it represents
   * {@code F<Left(l)>}, the {@code handler} function is applied to {@code l}, and its result
   * (another {@code Kind<EitherTKind.Witness<F, L>, R>}) is returned. This operation is performed
   * within the context of the outer monad {@code F}.
   *
   * @param <R> The type of the 'right' value.
   * @param ma The {@code Kind<EitherTKind.Witness<F, L>, R>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Left(l)>}. Must not be
   *     null.
   * @return A {@code Kind<EitherTKind.Witness<F, L>, R>}, either the original or the result of the
   *     handler. Never null.
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code handler} cannot be unwrapped.
   */
  @Override
  public <R> Kind<EitherTKind.Witness<F, L>, R> handleErrorWith(
      Kind<EitherTKind.Witness<F, L>, R> ma,
      Function<? super L, ? extends Kind<EitherTKind.Witness<F, L>, R>> handler) {

    Validation.kind().requireNonNull(ma, EITHER_T_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    Validation.function()
        .requireFunction(handler, "handler", EITHER_T_MONAD_CLASS, HANDLE_ERROR_WITH);

    EitherT<F, L, R> eitherT_ma = EITHER_T.narrow(ma);

    Kind<F, Either<L, R>> newUnderlyingValue =
        outerMonad.flatMap(
            (Either<L, R> innerEither) -> {
              if (innerEither.isRight()) {
                return outerMonad.of(innerEither); // It's Right, re-wrap in F
              } else {
                L leftVal = innerEither.getLeft();
                Kind<EitherTKind.Witness<F, L>, R> resultKindT = handler.apply(leftVal);
                Validation.function()
                    .requireNonNullResult(
                        resultKindT, "handler", EitherT.class, HANDLE_ERROR_WITH, Kind.class);
                EitherT<F, L, R> resultT = EITHER_T.narrow(resultKindT);
                return resultT.value(); // This is Kind<F, Either<L, R>>
              }
            },
            eitherT_ma.value() // This is Kind<F, Either<L, R>>
            );
    return EITHER_T.widen(EitherT.fromKind(newUnderlyingValue));
  }
}
