// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.util.ErrorHandling.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroup;

/**
 * Monad instance for {@link Validated}. The error type {@code E} is fixed for this Monad instance.
 * Implements {@link MonadError} which transitively includes {@link org.higherkindedj.hkt.Monad
 * Monad} and {@link org.higherkindedj.hkt.Applicative Applicative}.
 *
 * <p><b>Important Note on Monad Laws:</b> With this implementation, the {@link #ap(Kind, Kind) ap}
 * method (from the {@code Applicative} superclass) accumulates errors, while {@link
 * #flatMap(Function, Kind) flatMap} will still fail fast on the first {@code Invalid} result. This
 * means that the monad law stating that {@code ap} should be equivalent to a {@code flatMap}
 * implementation (i.e., {@code ap(fab, fa)} should equal {@code fab.flatMap(f -> fa.map(f))}) will
 * not hold. This is a common and accepted trade-off when using {@code Validated} to accumulate
 * errors.
 *
 * @param <E> The type of the error value. For ValidatedMonad, this error type E is expected to be
 *     non-null.
 */
public final class ValidatedMonad<E> implements MonadError<ValidatedKind.Witness<E>, E> {

  private final Semigroup<E> semigroup;

  private ValidatedMonad(Semigroup<E> semigroup) {
    this.semigroup =
        Objects.requireNonNull(semigroup, "Semigroup for ValidatedMonad cannot be null");
  }

  /**
   * Provides an instance of {@code ValidatedMonad} for a given error type {@code E}, which requires
   * a {@link Semigroup} for error accumulation in {@code ap}.
   *
   * @param semigroup The semigroup for combining errors. Must not be null.
   * @param <E> The error type.
   * @return A new instance of {@code ValidatedMonad}.
   * @throws NullPointerException if {@code semigroup} is null.
   */
  public static <E> ValidatedMonad<E> instance(Semigroup<E> semigroup) {
    return new ValidatedMonad<>(semigroup);
  }

  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> map(
      Function<? super A, ? extends B> f, Kind<ValidatedKind.Witness<E>, A> fa) {
    requireNonNullFunction(f, "function f for map");
    requireNonNullKind(fa, "source Kind for map");

    Validated<E, A> validated = VALIDATED.narrow(fa);
    Validated<E, B> result = validated.map(f);
    return VALIDATED.widen(result);
  }

  /**
   * Lifts a pure value {@code A} into the {@code Validated} context, creating a {@code
   * Kind<ValidatedKind.Witness<E>, A>} that represents a {@code Valid(value)}. This method is part
   * of the {@link org.higherkindedj.hkt.Applicative Applicative} interface.
   *
   * @param value The value to lift. Must not be null.
   * @param <A> The type of the value.
   * @return A {@code Kind} instance representing {@code Validated.valid(value)}.
   * @throws NullPointerException if value is null.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> of(A value) {
    Objects.requireNonNull(value, "value for of cannot be null");
    Validated<E, A> validInstance = Validated.valid(value);
    return VALIDATED.widen(validInstance);
  }

  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> ap(
      Kind<ValidatedKind.Witness<E>, ? extends Function<A, B>> ff,
      Kind<ValidatedKind.Witness<E>, A> fa) {

    requireNonNullKind(ff, "function Kind for ap");
    requireNonNullKind(fa, "argument Kind for ap");

    Validated<E, ? extends Function<A, B>> fnValidated = VALIDATED.narrow(ff);
    Validated<E, A> valueValidated = VALIDATED.narrow(fa);
    // Ensure the function type matches what Validated.ap expects
    Validated<E, Function<? super A, ? extends B>> fnValidatedWithWildcards =
        fnValidated.map(f -> (Function<? super A, ? extends B>) f);

    Validated<E, B> result = valueValidated.ap(fnValidatedWithWildcards, semigroup);
    return VALIDATED.widen(result);
  }

  /**
   * Applies a function that returns a {@code Kind<ValidatedKind.Witness<E>, B>} to the value
   * contained in a {@code Kind<ValidatedKind.Witness<E>, A>}, effectively chaining operations.
   *
   * @param fn The function to apply. Must not be null and must not return a null {@code Kind}.
   * @param valueKind The {@code Kind} instance containing the value to transform. Must not be null.
   * @param <A> The type of the value in the input {@code Kind}.
   * @param <B> The type of the value in the output {@code Kind}.
   * @return A {@code Kind} instance representing the result of the flatMap operation.
   * @throws NullPointerException if {@code fn} is null, {@code valueKind} is null, or {@code fn}
   *     returns a null {@code Kind}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code valueKind} cannot be
   *     unwrapped to a valid {@code Validated} representation.
   */
  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> flatMap(
      Function<? super A, ? extends Kind<ValidatedKind.Witness<E>, B>> f,
      Kind<ValidatedKind.Witness<E>, A> ma) {
    requireNonNullFunction(f, "function f for flatMap");
    requireNonNullKind(ma, "source Kind for flatMap");

    Validated<E, A> validatedValue = VALIDATED.narrow(ma);
    Validated<E, B> result =
        validatedValue.flatMap(
            a -> {
              Kind<ValidatedKind.Witness<E>, B> kindResult = f.apply(a);
              Objects.requireNonNull(kindResult, "flatMap function returned null Kind");
              return VALIDATED.narrow(kindResult);
            });
    return VALIDATED.widen(result);
  }

  // --- MonadError Implementation ---

  /**
   * Lifts an error value {@code error} into the Validated context, creating an {@code
   * Invalid(error)}. For {@code Validated}, the error {@code E} must be non-null.
   *
   * @param error The non-null error value to lift.
   * @param <A> The phantom type parameter of the value (since this represents an error state).
   * @return The error wrapped as {@code Kind<ValidatedKind.Witness<E>, A>}.
   * @throws NullPointerException if error is null.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> raiseError(E error) {
    Objects.requireNonNull(error, "error for raiseError cannot be null");
    return VALIDATED.invalid(error);
  }

  /**
   * Handles an error within the Validated context. If {@code ma} represents a {@code Valid} value,
   * it's returned unchanged. If {@code ma} represents an {@code Invalid(e)}, the {@code handler}
   * function is applied to {@code e} to potentially recover with a new monadic value.
   *
   * @param ma The monadic value ({@code Kind<ValidatedKind.Witness<E>, A>}) potentially containing
   *     an error. Must not be null.
   * @param handler A function that takes an error {@code e} of type {@code E} and returns a new
   *     monadic value ({@code Kind<ValidatedKind.Witness<E>, A>}). Must not be null, and must not
   *     return null.
   * @param <A> The type of the value within the monad.
   * @return The original monadic value if it was {@code Valid}, or the result of the {@code
   *     handler} if it was {@code Invalid}. Guaranteed non-null.
   * @throws NullPointerException if ma, handler, or the result of the handler is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped
   *     to a valid {@code Validated} representation.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> handleErrorWith(
      Kind<ValidatedKind.Witness<E>, A> ma,
      Function<? super E, ? extends Kind<ValidatedKind.Witness<E>, A>> handler) {
    requireNonNullKind(ma, "Kind ma for handleErrorWith");
    requireNonNullFunction(handler, "handler function for handleErrorWith");

    Validated<E, A> validated = VALIDATED.narrow(ma);

    if (validated.isInvalid()) {
      E errorValue = validated.getError();
      Kind<ValidatedKind.Witness<E>, A> resultFromHandler = handler.apply(errorValue);
      Objects.requireNonNull(resultFromHandler, "handler function returned null Kind");
      return resultFromHandler;
    } else {
      return ma;
    }
  }
}
