// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static java.util.Objects.requireNonNull;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.util.validation.Validation;

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

  Class<Validated> VALIDATED_CLASS = Validated.class;

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
  A orElse(A other);

  /**
   * Returns the value if {@code Valid}, otherwise returns the result of {@code
   * otherSupplier.get()}.
   *
   * @param otherSupplier Supplier for the value to return if this is {@code Invalid}. Must not be
   *     null and must produce a non-null value.
   * @return The value if {@code Valid}, or the result of the supplier.
   */
  A orElseGet(Supplier<? extends A> otherSupplier);

  /**
   * Returns the value if {@code Valid}, otherwise throws the exception supplied by {@code
   * exceptionSupplier}.
   *
   * @param exceptionSupplier Supplier for the exception to throw. Must not be null.
   * @param <X> Type of the exception.
   * @return The value if {@code Valid}.
   * @throws X if this is {@code Invalid}.
   */
  <X extends Throwable> A orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

  /**
   * Performs the given action with the value if it is {@code Valid}.
   *
   * @param consumer The action to perform. Must not be null.
   */
  void ifValid(Consumer<? super A> consumer);

  /**
   * Performs the given action with the error if it is {@code Invalid}.
   *
   * @param consumer The action to perform. Must not be null.
   */
  void ifInvalid(Consumer<? super E> consumer);

  /**
   * Maps the value {@code A} to {@code B} if this is {@code Valid}, otherwise returns the {@code
   * Invalid} instance.
   *
   * @param fn The mapping function. Must not be null.
   * @param <B> The new value type.
   * @return A {@code Validated<E, B>} instance.
   */
  <B> Validated<E, B> map(Function<? super A, ? extends B> fn);

  /**
   * Transforms both the error and value of this {@code Validated} using the provided mapping
   * functions, producing a new {@code Validated} with potentially different types for both
   * parameters.
   *
   * <p>This is the fundamental bifunctor operation for {@code Validated}, allowing simultaneous
   * transformation of both the error channel (invalid) and success channel (valid). Exactly one of
   * the two functions will be applied, depending on whether this is {@link Invalid} or {@link
   * Valid}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Validated<String, Integer> success = Validated.valid(42);
   * Validated<Exception, String> result1 = success.bimap(
   *     Exception::new,           // Transform error - not applied
   *     n -> "Value: " + n        // Transform value - applied
   * );
   * // result1 = Valid("Value: 42")
   *
   * Validated<String, Integer> failure = Validated.invalid("not found");
   * Validated<Exception, String> result2 = failure.bimap(
   *     Exception::new,           // Transform error - applied
   *     n -> "Value: " + n        // Transform value - not applied
   * );
   * // result2 = Invalid(new Exception("not found"))
   * }</pre>
   *
   * @param errorMapper The non-null function to apply to the error if this is {@link Invalid}.
   * @param valueMapper The non-null function to apply to the value if this is {@link Valid}.
   * @param <E2> The type of the error in the resulting {@code Validated}.
   * @param <B> The type of the value in the resulting {@code Validated}.
   * @return A new {@code Validated<E2, B>} with one value transformed according to the appropriate
   *     mapper. The returned {@code Validated} will be non-null.
   * @throws NullPointerException if either {@code errorMapper} or {@code valueMapper} is null.
   */
  default <E2, B> Validated<E2, B> bimap(
      Function<? super E, ? extends E2> errorMapper, Function<? super A, ? extends B> valueMapper) {
    Validation.function().requireMapper(errorMapper, "errorMapper", VALIDATED_CLASS, BIMAP);
    Validation.function().requireMapper(valueMapper, "valueMapper", VALIDATED_CLASS, BIMAP);

    return switch (this) {
      case Invalid<E, A>(var error) -> Validated.invalid(errorMapper.apply(error));
      case Valid<E, A>(var value) -> Validated.valid(valueMapper.apply(value));
    };
  }

  /**
   * Transforms only the error of this {@code Validated}, leaving the value unchanged if present.
   *
   * <p>This operation allows you to transform the error channel whilst preserving the success
   * channel. It is useful for converting error types, enriching error messages, or mapping between
   * different error representations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Validated<String, Integer> failure = Validated.invalid("invalid input");
   * Validated<Exception, Integer> result1 = failure.mapError(Exception::new);
   * // result1 = Invalid(new Exception("invalid input"))
   *
   * Validated<String, Integer> success = Validated.valid(42);
   * Validated<Exception, Integer> result2 = success.mapError(Exception::new);
   * // result2 = Valid(42) - value unchanged
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@code bimap(errorMapper, Function.identity())}.
   *
   * @param errorMapper The non-null function to apply to the error if this is {@link Invalid}.
   * @param <E2> The type of the error in the resulting {@code Validated}.
   * @return A new {@code Validated<E2, A>} with the error transformed if this was {@link Invalid},
   *     or the original value unchanged. The returned {@code Validated} will be non-null.
   * @throws NullPointerException if {@code errorMapper} is null.
   */
  @SuppressWarnings("unchecked")
  default <E2> Validated<E2, A> mapError(Function<? super E, ? extends E2> errorMapper) {
    Validation.function().requireMapper(errorMapper, "errorMapper", VALIDATED_CLASS, MAP_ERROR);

    return switch (this) {
      case Invalid<E, A>(var error) -> Validated.invalid(errorMapper.apply(error));
      case Valid<E, A> v -> (Validated<E2, A>) v; // Valid remains unchanged, cast is safe
    };
  }

  /**
   * Applies the function {@code fn} if this is {@code Valid}, otherwise returns the {@code Invalid}
   * instance. This is the monadic bind operation.
   *
   * @param fn The function to apply, which returns a {@code Validated<E, B>}. Must not be null and
   *     must not return a null Validated.
   * @param <B> The new value type of the resulting {@code Validated}.
   * @return A {@code Validated<E, B>} instance.
   */
  <B> Validated<E, B> flatMap(Function<? super A, ? extends Validated<E, ? extends B>> fn);

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
  <B> Validated<E, B> ap(
      Validated<E, Function<? super A, ? extends B>> fnValidated, Semigroup<E> semigroup);

  /**
   * Applies one of two functions depending on whether this instance is {@link Invalid} or {@link
   * Valid}, using modern switch expression pattern matching.
   *
   * @param invalidMapper The non-null function to apply if this is an {@link Invalid}.
   * @param validMapper The non-null function to apply if this is a {@link Valid}.
   * @param <T> The target type to which both paths will be mapped.
   * @return The result of applying the appropriate mapping function.
   * @throws NullPointerException if either {@code invalidMapper} or {@code validMapper} is null.
   */
  default <T> T fold(
      Function<? super E, ? extends T> invalidMapper,
      Function<? super A, ? extends T> validMapper) {
    Validation.function().requireFunction(invalidMapper, "invalidMapper", VALIDATED_CLASS, FOLD);
    Validation.function().requireFunction(validMapper, "validMapper", VALIDATED_CLASS, FOLD);

    return switch (this) {
      case Invalid<E, A>(var error) -> invalidMapper.apply(error);
      case Valid<E, A>(var value) -> validMapper.apply(value);
    };
  }

  /**
   * Converts this {@code Validated} to an {@link Either}.
   *
   * <p>A {@link Valid} instance becomes an {@link Either.Right}, and an {@link Invalid} instance
   * becomes an {@link Either.Left}.
   *
   * @return An {@code Either<E, A>} representing the state of this {@code Validated}.
   */
  default Either<E, A> toEither() {
    return fold(Either::left, Either::right);
  }

  /**
   * Validates a condition, returning Valid(Unit) if true, Invalid(error) if false.
   *
   * <p>This is useful for validation logic that doesn't produce a value, only checks a condition.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Validated<String, Unit> ageCheck =
   *     Validated.validateThat(age >= 18, "Must be 18 or older");
   *
   * Validated<String, Unit> nameCheck =
   *     Validated.validateThat(!name.isEmpty(), "Name required");
   *
   * // Combine both checks
   * ValidatedApplicative<String> app = ValidatedApplicative.instance(Semigroups.string(", "));
   * Kind<ValidatedKind.Witness<String>, Unit> allValid =
   *     app.map2(
   *         VALIDATED.widen(ageCheck),
   *         VALIDATED.widen(nameCheck),
   *         (u1, u2) -> Unit.INSTANCE
   *     );
   * }</pre>
   *
   * @param condition The condition to check
   * @param error The error to return if condition is false, must not be null
   * @param <E> The error type
   * @return Valid(Unit.INSTANCE) if condition is true, Invalid(error) otherwise
   * @throws NullPointerException if error is null
   */
  static <E> Validated<E, Unit> validateThat(boolean condition, E error) {
    requireNonNull(error, "error cannot be null");
    return condition ? Validated.valid(Unit.INSTANCE) : Validated.invalid(error);
  }

  /**
   * Validates a condition with a lazy error supplier. Only evaluates the error if the condition
   * fails.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Validated<String, Unit> check = Validated.validateThat(
   *     expensiveCondition(),
   *     () -> expensiveErrorMessage()
   * );
   * }</pre>
   *
   * @param condition The condition to check
   * @param errorSupplier Supplies the error if needed, must not be null
   * @param <E> The error type
   * @return Valid(Unit.INSTANCE) if true, Invalid(errorSupplier.get()) if false
   * @throws NullPointerException if errorSupplier is null
   */
  static <E> Validated<E, Unit> validateThat(boolean condition, Supplier<E> errorSupplier) {

    requireNonNull(errorSupplier, "errorSupplier cannot be null");
    return condition ? Validated.valid(Unit.INSTANCE) : Validated.invalid(errorSupplier.get());
  }

  /**
   * Discards the value of a Valid, replacing it with Unit. If Invalid, returns the same Invalid.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Validated<Error, User> userValidation = validateUser(data);
   * Validated<Error, Unit> justCheckValidity = userValidation.asUnit();
   * }</pre>
   *
   * @return A {@code Validated<E, Unit>} with the same validity but Unit as value
   */
  default Validated<E, Unit> asUnit() {
    return map(a -> Unit.INSTANCE);
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
  static <E, A> Validated<E, A> valid(A value) {
    Validation.coreType().requireValue(value, VALIDATED_CLASS, CONSTRUCTION);
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
  static <E, A> Validated<E, A> invalid(E error) {
    Validation.coreType().requireError(error, VALIDATED_CLASS, INVALID);
    return new Invalid<>(error);
  }
}
