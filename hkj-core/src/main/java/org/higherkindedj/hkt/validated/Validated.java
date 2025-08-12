// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.NonNull;

/**
 * Represents a value that is either Valid (correct) or Invalid (erroneous). This is a sealed
 * interface with two implementations: {@link Valid} and {@link Invalid}.
 *
 * <p>By convention, {@link Invalid} holds an error value of type {@code E}, and {@link Valid} holds
 * a success value of type {@code A}. Operations like {@link #map(Function)} and {@link
 * #flatMap(Function)} are right-biased, operating on the {@link Valid} value and passing {@link
 * Invalid} values through unchanged.
 *
 * @param <E> The type of the error in case of an Invalid value.
 * @param <A> The type of the value in case of a Valid value.
 */
public sealed interface Validated<E, A> permits Valid, Invalid {

  // --- Message Constants ---
  String VALID_VALUE_CANNOT_BE_NULL_MSG = "Valid value cannot be null";
  String INVALID_ERROR_CANNOT_BE_NULL_MSG = "Invalid error cannot be null";
  String FOLD_INVALID_MAPPER_CANNOT_BE_NULL_MSG = "invalidMapper cannot be null";
  String FOLD_VALID_MAPPER_CANNOT_BE_NULL_MSG = "validMapper cannot be null";

  /**
   * Checks if this is a {@code Valid} instance.
   *
   * @return true if this is {@code Valid}, false otherwise.
   */
  boolean isValid();

  /**
   * Checks if this is an {@code Invalid} instance.
   *
   * @return true if this is {@code Invalid}, false otherwise.
   */
  boolean isInvalid();

  /**
   * Gets the value if this is a {@code Valid} instance.
   *
   * @return The encapsulated value.
   * @throws NoSuchElementException if this is an {@code Invalid} instance.
   */
  A get() throws NoSuchElementException;

  /**
   * Gets the error if this is an {@code Invalid} instance.
   *
   * @return The encapsulated error.
   * @throws NoSuchElementException if this is a {@code Valid} instance.
   */
  E getError() throws NoSuchElementException;

  /**
   * Returns the value if {@code Valid}, otherwise returns {@code other}.
   *
   * @param other The value to return if this is {@code Invalid}.
   * @return The value if {@code Valid}, or {@code other}.
   */
  A orElse(@NonNull A other);

  /**
   * Returns the value if {@code Valid}, otherwise returns the result of {@code
   * otherSupplier.get()}.
   *
   * @param otherSupplier Supplier for the value to return if this is {@code Invalid}. Must not be
   *     null and must produce a non-null value.
   * @return The value if {@code Valid}, or the result of the supplier.
   */
  A orElseGet(@NonNull Supplier<? extends @NonNull A> otherSupplier);

  /**
   * Returns the value if {@code Valid}, otherwise throws the exception supplied by {@code
   * exceptionSupplier}.
   *
   * @param exceptionSupplier Supplier for the exception to throw. Must not be null.
   * @param <X> Type of the exception.
   * @return The value if {@code Valid}.
   * @throws X if this is {@code Invalid}.
   */
  <X extends Throwable> A orElseThrow(@NonNull Supplier<? extends X> exceptionSupplier) throws X;

  /**
   * Performs the given action with the value if it is {@code Valid}.
   *
   * @param consumer The action to perform. Must not be null.
   */
  void ifValid(@NonNull Consumer<? super A> consumer);

  /**
   * Performs the given action with the error if it is {@code Invalid}.
   *
   * @param consumer The action to perform. Must not be null.
   */
  void ifInvalid(@NonNull Consumer<? super E> consumer);

  /**
   * Maps the value {@code A} to {@code B} if this is {@code Valid}, otherwise returns the {@code
   * Invalid} instance.
   *
   * @param fn The mapping function. Must not be null.
   * @param <B> The new value type.
   * @return A {@code Validated<E, B>} instance.
   */
  @NonNull <B> Validated<E, B> map(@NonNull Function<? super A, ? extends B> fn);

  /**
   * Applies the function {@code fn} if this is {@code Valid}, otherwise returns the {@code Invalid}
   * instance. This is the monadic bind operation.
   *
   * @param fn The function to apply, which returns a {@code Validated<E, B>}. Must not be null and
   *     must not return a null Validated.
   * @param <B> The new value type of the resulting {@code Validated}.
   * @return A {@code Validated<E, B>} instance.
   */
  @NonNull <B> Validated<E, B> flatMap(
      @NonNull Function<? super A, ? extends @NonNull Validated<E, ? extends B>> fn);

  /**
   * Applies a function contained within a {@code Validated} to this {@code Validated}'s value.
   *
   * <p>If this instance is {@code Valid} and {@code fnValidated} is {@code Valid}, the result is
   * {@code Valid(f.apply(a))}.
   *
   * <p>If either this instance or {@code fnValidated} is {@code Invalid}, an {@code Invalid} is
   * returned. If both are {@code Invalid}, the errors are combined using the provided {@link
   * Semigroup}.
   *
   * @param fnValidated A {@code Validated} containing a function from {@code A} to {@code B}. Must
   *     not be null.
   * @param semigroup A {@link Semigroup} to combine errors if both this and {@code fnValidated} are
   *     {@code Invalid}.
   * @param <B> The new value type.
   * @return A {@code Validated<E, B>} instance.
   */
  @NonNull <B> Validated<E, B> ap(
      @NonNull Validated<E, Function<? super A, ? extends B>> fnValidated,
      @NonNull Semigroup<E> semigroup);

  /**
   * Applies one of two functions depending on whether this instance is {@link Invalid} or {@link
   * Valid}.
   *
   * @param invalidMapper The non-null function to apply if this is an {@link Invalid}.
   * @param validMapper The non-null function to apply if this is a {@link Valid}.
   * @param <T> The target type to which both paths will be mapped.
   * @return The result of applying the appropriate mapping function.
   */
  default <T> T fold(
      @NonNull Function<? super E, ? extends T> invalidMapper,
      @NonNull Function<? super A, ? extends T> validMapper) {
    Objects.requireNonNull(invalidMapper, FOLD_INVALID_MAPPER_CANNOT_BE_NULL_MSG);
    Objects.requireNonNull(validMapper, FOLD_VALID_MAPPER_CANNOT_BE_NULL_MSG);
    if (isInvalid()) {
      return invalidMapper.apply(getError());
    } else {
      return validMapper.apply(get());
    }
  }

  /**
   * Converts this {@code Validated} to an {@link Either}.
   *
   * <p>A {@link Valid} instance becomes an {@link Either.Right}, and an {@link Invalid} instance
   * becomes an {@link Either.Left}.
   *
   * @return An {@code Either<E, A>} representing the state of this {@code Validated}.
   */
  default @NonNull Either<E, A> toEither() {
    return fold(Either::left, Either::right);
  }

  /**
   * Creates a {@code Valid} instance containing the given value.
   *
   * @param value The value, must not be null.
   * @param <E> The error type (unused for Valid).
   * @param <A> The value type.
   * @return A {@code Valid<E, A>} instance.
   * @throws NullPointerException if value is null.
   */
  static <E, A> @NonNull Validated<E, A> valid(@NonNull A value) {
    Objects.requireNonNull(value, VALID_VALUE_CANNOT_BE_NULL_MSG);
    return new Valid<>(value);
  }

  /**
   * Creates an {@code Invalid} instance containing the given error.
   *
   * @param error The error, must not be null.
   * @param <E> The error type.
   * @param <A> The value type (unused for Invalid).
   * @return An {@code Invalid<E, A>} instance.
   * @throws NullPointerException if error is null.
   */
  static <E, A> @NonNull Validated<E, A> invalid(@NonNull E error) {
    Objects.requireNonNull(error, INVALID_ERROR_CANNOT_BE_NULL_MSG);
    return new Invalid<>(error);
  }
}
