// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} type class for {@link java.util.Optional}, using {@link
 * OptionalKind.Witness} as the higher-kinded type witness.
 *
 * <p>This class provides monadic operations for {@code Optional}, allowing it to be used in a
 * generic, functional, and composable manner within the Higher-Kinded-J framework. It treats {@link
 * Optional#empty()} as the error state, with {@link Unit} as the phantom error type, signifying
 * absence rather than a specific error value.
 *
 * <p>Key operations include:
 *
 * <ul>
 *   <li>{@link #of(Object)}: Lifts a potentially nullable value into an {@code OptionalKind}. A
 *       null value results in an empty {@code OptionalKind}.
 *   <li>{@link #map(Function, Kind)}: Applies a function to the value within an {@code
 *       OptionalKind} if it's present.
 *   <li>{@link #flatMap(Function, Kind)}: Applies a function that returns an {@code OptionalKind}
 *       to the value within an {@code OptionalKind} if present, and flattens the result.
 *   <li>{@link #ap(Kind, Kind)}: Applies an {@code OptionalKind} of a function to an {@code
 *       OptionalKind} of a value.
 *   <li>{@link #raiseError(Unit)}: Returns an empty {@code OptionalKind}.
 *   <li>{@link #handleErrorWith(Kind, Function)}: Allows recovery from an empty {@code
 *       OptionalKind}.
 *   <li>It treats {@link Optional#empty()} as both the error state and the "zero" element.
 * </ul>
 *
 * <p>This class extends {@link OptionalFunctor} and transitively implements {@link
 * org.higherkindedj.hkt.MonadZero}, {@link org.higherkindedj.hkt.Applicative}, and {@link
 * org.higherkindedj.hkt.Functor}.
 *
 * <p>This class is a final singleton, accessible via the static {@link #INSTANCE} field.
 *
 * @see Optional
 * @see OptionalKind
 * @see OptionalKindHelper
 * @see OptionalFunctor
 * @see MonadError
 * @see Kind
 * @see Unit
 */
public final class OptionalMonad extends OptionalFunctor
    implements MonadError<OptionalKind.Witness, Unit>, MonadZero<OptionalKind.Witness> {

  private static final Class<OptionalMonad> OPTIONAL_MONAD_CLASS = OptionalMonad.class;

  /** Singleton instance of {@code OptionalMonad}. */
  public static final OptionalMonad INSTANCE = new OptionalMonad();

  /** Private constructor to enforce the singleton pattern. */
  private OptionalMonad() {
    // Default constructor
  }

  /**
   * Lifts a value into the {@code OptionalKind} context. If the provided {@code value} is {@code
   * null}, this method returns an empty {@code OptionalKind}. Otherwise, it returns an {@code
   * OptionalKind} containing the value. This is equivalent to {@code Optional.ofNullable(value)}
   * wrapped in {@code OptionalKind}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A non-null {@code Kind<OptionalKind.Witness, A>} representing {@code
   *     Optional.ofNullable(value)}.
   */
  @Override
  public <A> Kind<OptionalKind.Witness, A> of(@Nullable A value) {
    return OPTIONAL.widen(Optional.ofNullable(value));
  }

  /**
   * Applies a function to the value within an {@code OptionalKind} if it is present, and flattens
   * the {@code OptionalKind} result. If the input {@code OptionalKind} ({@code ma}) is empty, or if
   * the function {@code f} applied to the present value results in an empty {@code OptionalKind},
   * an empty {@code OptionalKind} is returned.
   *
   * @param <A> The type of the value in the input {@code OptionalKind}.
   * @param <B> The type of the value in the {@code OptionalKind} returned by the function {@code
   *     f}.
   * @param f The non-null function to apply to the value if present. This function must return a
   *     {@code Kind<OptionalKind.Witness, B>}.
   * @param ma The non-null {@code Kind<OptionalKind.Witness, A>} to transform.
   * @return A non-null {@code Kind<OptionalKind.Witness, B>} representing the result of the flatMap
   *     operation.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} is not a valid {@code
   *     OptionalKind} representation.
   */
  @Override
  public <A, B> Kind<OptionalKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<OptionalKind.Witness, B>> f,
      Kind<OptionalKind.Witness, A> ma) {

    FunctionValidator.requireFlatMapper(f, "f", OPTIONAL_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(ma, OPTIONAL_MONAD_CLASS, FLAT_MAP);

    Optional<A> optA = OPTIONAL.narrow(ma);
    Optional<B> resultOpt =
        optA.flatMap(
            a -> {
              Kind<OptionalKind.Witness, B> kindB = f.apply(a);
              FunctionValidator.requireNonNullResult(kindB, "f", OPTIONAL_MONAD_CLASS, FLAT_MAP, Optional.class);
              return OPTIONAL.narrow(kindB);
            });
    return OPTIONAL.widen(resultOpt);
  }

  /**
   * Applies an {@code OptionalKind} containing a function to an {@code OptionalKind} containing a
   * value. If both the function and the value are present, the function is applied to the value,
   * and the result is wrapped in an {@code OptionalKind}. If either is empty, an empty {@code
   * OptionalKind} is returned.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The non-null {@code Kind<OptionalKind.Witness, Function<A, B>>} containing the
   *     function.
   * @param fa The non-null {@code Kind<OptionalKind.Witness, A>} containing the value.
   * @return A non-null {@code Kind<OptionalKind.Witness, B>} representing the result of the
   *     application.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} is not
   *     a valid {@code OptionalKind} representation.
   */
  @Override
  public <A, B> Kind<OptionalKind.Witness, B> ap(
      Kind<OptionalKind.Witness, ? extends Function<A, B>> ff, Kind<OptionalKind.Witness, A> fa) {

    KindValidator.requireNonNull(ff, OPTIONAL_MONAD_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, OPTIONAL_MONAD_CLASS, AP, "argument");

    Optional<? extends Function<A, B>> optF = OPTIONAL.narrow(ff);
    Optional<A> optA = OPTIONAL.narrow(fa);
    Optional<B> resultOpt = optF.flatMap(optA::map);
    return OPTIONAL.widen(resultOpt);
  }

  /**
   * Raises an error in the {@code OptionalKind} context, which corresponds to an empty {@code
   * Optional}. The error parameter (of type {@link Unit}) is typically {@link Unit#INSTANCE}.
   *
   * @param <A> The phantom type of the value for the resulting empty {@code OptionalKind}.
   * @param error The error value (typically {@link Unit#INSTANCE}). Must be non-null.
   * @return A non-null {@code Kind<OptionalKind.Witness, A>} representing {@code Optional.empty()}.
   */
  @Override
  public <A> Kind<OptionalKind.Witness, A> raiseError(Unit error) {
    // No need to validate the Unit parameter as it's a marker type
    return OPTIONAL.widen(Optional.empty());
  }

  /**
   * Handles an error (an empty {@code OptionalKind}) by applying a recovery function. If the input
   * {@code OptionalKind} ({@code ma}) is empty, the {@code handler} function is invoked (with
   * {@link Unit#INSTANCE} as the argument) to produce a new {@code OptionalKind}. If {@code ma} is
   * present, it is returned unchanged.
   *
   * @param <A> The type of the value.
   * @param ma The non-null {@code Kind<OptionalKind.Witness, A>} to handle.
   * @param handler The non-null function to apply if {@code ma} is empty. It takes {@link
   *     Unit#INSTANCE} and returns a new {@code Kind<OptionalKind.Witness, A>}.
   * @return A non-null {@code Kind<OptionalKind.Witness, A>}, either the original if present, or
   *     the result of the handler if empty.
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} is not a valid {@code
   *     OptionalKind} representation.
   */
  @Override
  public <A> Kind<OptionalKind.Witness, A> handleErrorWith(
      Kind<OptionalKind.Witness, A> ma,
      Function<? super Unit, ? extends Kind<OptionalKind.Witness, A>> handler) {

    KindValidator.requireNonNull(ma, OPTIONAL_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    FunctionValidator.requireFunction(handler, "handler", OPTIONAL_MONAD_CLASS, HANDLE_ERROR_WITH);

    Optional<A> optional = OPTIONAL.narrow(ma);
    if (optional.isEmpty()) {
      Kind<OptionalKind.Witness, A> recoveryKind = handler.apply(Unit.INSTANCE);
      FunctionValidator.requireNonNullResult(recoveryKind, "handler", OPTIONAL_MONAD_CLASS, HANDLE_ERROR_WITH, Optional.class);
      return recoveryKind;
    } else {
      return ma;
    }
  }

  /**
   * Returns the "zero" or "empty" value for this Monad, which is {@code Optional.empty()}.
   *
   * @param <T> The type parameter of the Kind.
   * @return A non-null {@code Kind<OptionalKind.Witness, T>} representing {@code Optional.empty()}.
   */
  @Override
  public <T> Kind<OptionalKind.Witness, T> zero() {
    return OPTIONAL.widen(Optional.empty());
  }
}
