// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError; // Import MonadError
import org.jspecify.annotations.NonNull;

/**
 * Monad instance for {@link Validated}. The error type {@code E} is fixed for this Monad instance.
 * Implements {@link MonadError} which transitively includes {@link org.higherkindedj.hkt.Monad
 * Monad} and {@link org.higherkindedj.hkt.Applicative Applicative}.
 *
 * @param <E> The type of the error value. For ValidatedMonad, this error type E is expected to be
 *     non-null.
 */
public final class ValidatedMonad<E> implements MonadError<ValidatedKind.Witness<E>, E> {

  // --- Message Constants ---
  static final String MAP_FN_NULL_MSG =
      "Mapping function (fn) passed to ValidatedMonad.map must not be null";
  static final String MAP_VALUE_KIND_NULL_MSG =
      "Value Kind (valueKind) passed to ValidatedMonad.map must not be null";
  static final String OF_VALUE_NULL_MSG = "Value for of cannot be null";
  static final String AP_FN_KIND_NULL_MSG =
      "Function Kind (fnKind) passed to ValidatedMonad.ap must not be null";
  static final String AP_VALUE_KIND_NULL_MSG =
      "Value Kind (valueKind) passed to ValidatedMonad.ap must not be null";
  static final String FLATMAP_FN_NULL_MSG = "Function provided to flatMap cannot be null.";
  static final String FLATMAP_VALUE_KIND_NULL_MSG = "Input Kind to flatMap cannot be null.";
  static final String FLATMAP_FN_RETURNED_NULL_KIND_MSG =
      "Function provided to flatMap returned a null Kind.";
  static final String RAISE_ERROR_ERROR_NULL_MSG =
      "Error passed to raiseError for ValidatedMonad cannot be null.";
  static final String HANDLE_ERROR_WITH_MA_NULL_MSG =
      "Input Kind (ma) to handleErrorWith cannot be null.";
  static final String HANDLE_ERROR_WITH_HANDLER_NULL_MSG =
      "Handler function to handleErrorWith cannot be null.";
  static final String HANDLE_ERROR_WITH_HANDLER_RETURNED_NULL_KIND_MSG =
      "Handler function in handleErrorWith must not return a null Kind.";

  private static final ValidatedMonad<?> INSTANCE = new ValidatedMonad<>();

  private ValidatedMonad() {}

  /**
   * Provides a singleton instance of {@code ValidatedMonad} for a given error type {@code E}.
   *
   * @param <E> The error type.
   * @return The singleton instance.
   */
  @SuppressWarnings("unchecked")
  public static <E> @NonNull ValidatedMonad<E> instance() {
    return (ValidatedMonad<E>) INSTANCE;
  }

  @Override
  public <A, B> @NonNull Kind<ValidatedKind.Witness<E>, B> map(
      @NonNull Function<A, B> fn, @NonNull Kind<ValidatedKind.Witness<E>, A> valueKind) {
    requireNonNull(fn, MAP_FN_NULL_MSG);
    requireNonNull(valueKind, MAP_VALUE_KIND_NULL_MSG);

    Validated<E, A> validated = ValidatedKindHelper.narrow(valueKind);
    Validated<E, B> result = validated.map(fn);
    return ValidatedKindHelper.widen(result);
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
  public <A> @NonNull Kind<ValidatedKind.Witness<E>, A> of(@NonNull A value) {
    requireNonNull(value, OF_VALUE_NULL_MSG);
    Validated<E, A> validInstance = Validated.valid(value);
    return ValidatedKindHelper.widen(validInstance);
  }

  @Override
  public <A, B> @NonNull Kind<ValidatedKind.Witness<E>, B> ap(
      @NonNull Kind<ValidatedKind.Witness<E>, Function<A, B>> fnKind,
      @NonNull Kind<ValidatedKind.Witness<E>, A> valueKind) {

    requireNonNull(fnKind, AP_FN_KIND_NULL_MSG);
    requireNonNull(valueKind, AP_VALUE_KIND_NULL_MSG);

    Validated<E, Function<A, B>> fnValidated = ValidatedKindHelper.narrow(fnKind);
    Validated<E, A> valueValidated = ValidatedKindHelper.narrow(valueKind);
    // Ensure the function type matches what Validated.ap expects
    Validated<E, Function<? super A, ? extends B>> fnValidatedWithWildcards =
        fnValidated.map(f -> (Function<? super A, ? extends B>) f);

    Validated<E, B> result = valueValidated.<B>ap(fnValidatedWithWildcards);
    return ValidatedKindHelper.widen(result);
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
   */
  @Override
  public <A, B> @NonNull Kind<ValidatedKind.Witness<E>, B> flatMap(
      @NonNull Function<A, Kind<ValidatedKind.Witness<E>, B>> fn,
      @NonNull Kind<ValidatedKind.Witness<E>, A> valueKind) {
    requireNonNull(fn, FLATMAP_FN_NULL_MSG);
    requireNonNull(valueKind, FLATMAP_VALUE_KIND_NULL_MSG);

    Validated<E, A> validatedValue = ValidatedKindHelper.narrow(valueKind);
    Validated<E, B> result =
        validatedValue.flatMap(
            a -> {
              Kind<ValidatedKind.Witness<E>, B> kindResult = fn.apply(a);
              requireNonNull(kindResult, FLATMAP_FN_RETURNED_NULL_KIND_MSG);
              return ValidatedKindHelper.narrow(kindResult);
            });
    return ValidatedKindHelper.widen(result);
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
  public <A> @NonNull Kind<ValidatedKind.Witness<E>, A> raiseError(@NonNull E error) {
    requireNonNull(error, RAISE_ERROR_ERROR_NULL_MSG);
    return ValidatedKindHelper.invalid(error);
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
   */
  @Override
  public <A> @NonNull Kind<ValidatedKind.Witness<E>, A> handleErrorWith(
      @NonNull Kind<ValidatedKind.Witness<E>, A> ma,
      @NonNull Function<E, Kind<ValidatedKind.Witness<E>, A>> handler) {
    requireNonNull(ma, HANDLE_ERROR_WITH_MA_NULL_MSG);
    requireNonNull(handler, HANDLE_ERROR_WITH_HANDLER_NULL_MSG);

    Validated<E, A> validated = ValidatedKindHelper.narrow(ma);

    if (validated.isInvalid()) {
      E errorValue = validated.getError();
      Kind<ValidatedKind.Witness<E>, A> resultFromHandler = handler.apply(errorValue);
      requireNonNull(resultFromHandler, HANDLE_ERROR_WITH_HANDLER_RETURNED_NULL_KIND_MSG);
      return resultFromHandler;
    } else {
      return ma;
    }
  }
}
