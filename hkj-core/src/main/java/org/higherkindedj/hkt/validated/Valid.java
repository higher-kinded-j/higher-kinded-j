// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

/**
 * Represents the correct (Valid) case of a {@link Validated}. It holds a non-null value of type
 * {@code A}. This class is an immutable record.
 *
 * <p>As part of the HKT pattern, this class also implements {@link ValidatedKind}, allowing it to
 * be used with typeclasses expecting {@code Kind<ValidatedKind.Witness<E>, A>}.
 *
 * @param <E> The type of the potential error (unused in {@code Valid}, but part of the {@link
 *     Validated} contract).
 * @param <A> The type of the encapsulated value.
 * @param value The non-null value held by this {@code Valid} instance.
 */
public record Valid<E, A>(@NonNull A value) implements Validated<E, A>, ValidatedKind<E, A> {

  static String CANNOT_GET_ERROR_FROM_VALID_INSTANCE_MSG =
      "Cannot getError() from a Valid instance.";
  static String VALUE_FOR_AP_CANNOT_BE_NULL_MSG = "Value for ap cannot be null.";
  static String OR_ELSE_CANNOT_BE_NULL_MSG =
      "orElse 'other' parameter cannot be null, though it's unused for Valid.";
  static String OR_ELSE_GET_CANNOT_BE_NULL_MSG =
      "orElseGet 'otherSupplier' parameter cannot be null, though it's unused for Valid.";
  static String OR_ELSE_THROW_CANNOT_BE_NULL_MSG =
      "orElseThrow 'exceptionSupplier' parameter cannot be null, though it's unused for Valid.";
  static String IF_VALID_CANNOT_BE_NULL_MSG = "ifValid 'consumer' parameter cannot be null.";
  static String IF_INVALID_CANNOT_BE_NULL_MSG = "ifInvalid 'consumer' parameter cannot be null.";
  static String MAP_FN_CANNOT_BE_NULL_MSG = "Mapping function cannot be null for Valid.map";
  static String MAP_FN_RETURNED_NULL_MSG = "Mapping function returned null in Valid.map";
  static String FLATMAP_FN_CANNOT_BE_NULL_MSG =
      "flatMap mapping function cannot be null for Valid.flatMap";
  static String FLATMAP_FN_RETURNED_NULL_MSG =
      "flatMap mapping function returned a null Validated instance";
  static String AP_FN_CANNOT_BE_NULL = "Validated function for ap cannot be null.";

  /**
   * Compact constructor for {@code Valid}. Ensures the encapsulated value is non-null.
   *
   * @param value The value to encapsulate. Must be non-null.
   * @throws NullPointerException if {@code value} is null.
   */
  public Valid {

    Objects.requireNonNull(value, VALID_VALUE_CANNOT_BE_NULL_MSG);
  }

  /**
   * Checks if this is a {@code Valid} instance.
   *
   * @return always {@code true} for {@code Valid} instances.
   */
  @Override
  public boolean isValid() {
    return true;
  }

  /**
   * Checks if this is an {@code Invalid} instance.
   *
   * @return always {@code false} for {@code Valid} instances.
   */
  @Override
  public boolean isInvalid() {
    return false;
  }

  /**
   * Gets the non-null value encapsulated by this {@code Valid} instance.
   *
   * @return The non-null encapsulated value.
   */
  @Override
  public @NonNull A get() {
    return value;
  }

  /**
   * Throws {@link NoSuchElementException} because a {@code Valid} instance does not contain an
   * error.
   *
   * @return never returns normally.
   * @throws NoSuchElementException always, as this is a {@code Valid} instance.
   */
  @Override
  public E getError() throws NoSuchElementException {
    throw new NoSuchElementException(CANNOT_GET_ERROR_FROM_VALID_INSTANCE_MSG);
  }

  /**
   * Returns the encapsulated value of this {@code Valid} instance. The provided alternative {@code
   * other} is ignored.
   *
   * @param other The alternative value, ignored by this implementation.
   * @return The non-null encapsulated value.
   */
  @Override
  public @NonNull A orElse(@NonNull A other) {
    Objects.requireNonNull(other, OR_ELSE_CANNOT_BE_NULL_MSG);
    return value;
  }

  /**
   * Returns the encapsulated value of this {@code Valid} instance. The provided {@code
   * otherSupplier} is not invoked.
   *
   * @param otherSupplier The supplier for an alternative value, ignored and not invoked by this
   *     implementation. Must not be null.
   * @return The non-null encapsulated value.
   * @throws NullPointerException if {@code otherSupplier} is null (due to eager null check).
   */
  @Override
  public @NonNull A orElseGet(@NonNull Supplier<? extends @NonNull A> otherSupplier) {
    Objects.requireNonNull(otherSupplier, OR_ELSE_GET_CANNOT_BE_NULL_MSG);
    return value;
  }

  /**
   * Returns the encapsulated value of this {@code Valid} instance. The provided {@code
   * exceptionSupplier} is not invoked.
   *
   * @param exceptionSupplier The supplier for an exception, ignored and not invoked by this
   *     implementation. Must not be null.
   * @param <X> Type of the exception that would be thrown (but is not by this implementation).
   * @return The non-null encapsulated value.
   * @throws X never thrown by this implementation.
   * @throws NullPointerException if {@code exceptionSupplier} is null (due to eager null check).
   */
  @Override
  public <X extends Throwable> @NonNull A orElseThrow(
      @NonNull Supplier<? extends X> exceptionSupplier) throws X {
    Objects.requireNonNull(exceptionSupplier, OR_ELSE_THROW_CANNOT_BE_NULL_MSG);
    return value;
  }

  /**
   * Performs the given action with the encapsulated non-null value.
   *
   * @param consumer The action to perform with the value. Must not be null.
   * @throws NullPointerException if {@code consumer} is null.
   */
  @Override
  public void ifValid(@NonNull Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, IF_VALID_CANNOT_BE_NULL_MSG).accept(value);
  }

  /**
   * Performs no action as this is a {@code Valid} instance and thus does not contain an error. The
   * provided consumer is checked for nullity but not invoked.
   *
   * @param consumer The action to perform if this were an {@code Invalid} instance (ignored). Must
   *     not be null.
   * @throws NullPointerException if {@code consumer} is null.
   */
  @Override
  public void ifInvalid(@NonNull Consumer<? super E> consumer) {
    Objects.requireNonNull(consumer, IF_INVALID_CANNOT_BE_NULL_MSG);
  }

  /**
   * Maps the encapsulated value of this {@code Valid} instance using the provided function. The
   * result is a new {@code Valid} instance containing the transformed value.
   *
   * @param fn The mapping function to apply to the value. Must not be null and must produce a
   *     non-null result.
   * @param <B> The type of the value returned by the mapping function.
   * @return a new {@code Valid<E, B>} instance containing the mapped value.
   * @throws NullPointerException if {@code fn} is null or if applying {@code fn} to the value
   *     results in a null value.
   */
  @Override
  public @NonNull <B> Validated<E, B> map(@NonNull Function<? super A, ? extends B> fn) {
    Objects.requireNonNull(fn, MAP_FN_CANNOT_BE_NULL_MSG);
    B newValue = fn.apply(value);
    Objects.requireNonNull(newValue, MAP_FN_RETURNED_NULL_MSG);
    return new Valid<>(newValue);
  }

  /**
   * Applies a function to the encapsulated value of this {@code Valid} instance. This is the
   * monadic bind operation. The function itself returns a {@link Validated} instance, which is then
   * returned by this method.
   *
   * @param fn The function to apply to the value. Must not be null and must return a non-null
   *     {@code Validated} instance.
   * @param <B> The value type of the {@code Validated} instance returned by {@code fn}.
   * @return the non-null {@code Validated<E, B>} instance produced by applying {@code fn} to the
   *     value.
   * @throws NullPointerException if {@code fn} is null or if {@code fn} returns a null {@code
   *     Validated} instance.
   */
  @Override
  public @NonNull <B> Validated<E, B> flatMap(
      @NonNull Function<? super A, ? extends @NonNull Validated<E, ? extends B>> fn) {
    Objects.requireNonNull(fn, FLATMAP_FN_CANNOT_BE_NULL_MSG);
    Validated<E, ? extends B> result = fn.apply(value);
    Objects.requireNonNull(result, FLATMAP_FN_RETURNED_NULL_MSG);
    // This cast is generally safe because B is determined by the result of fn.
    // If fn's signature is strictly followed, result will be Validated<E, B>.
    @SuppressWarnings("unchecked")
    Validated<E, B> typedResult = (Validated<E, B>) result;
    return typedResult;
  }

  /**
   * Applies a function contained in another {@code Validated} to the value of this {@code Valid}
   * instance.
   *
   * <ul>
   *   <li>If {@code fnValidated} is {@code Valid(f)}, this method behaves like {@code this.map(f)},
   *       returning a {@code Valid} of the applied function's result.
   *   <li>If {@code fnValidated} is {@code Invalid(e)}, this method returns an {@code Invalid(e)},
   *       propagating the error from {@code fnValidated}.
   * </ul>
   *
   * @param fnValidated A {@code Validated} instance expected to contain a function from {@code A}
   *     to {@code B}. Must not be null.
   * @param <B> The value type of the resulting {@code Validated} instance.
   * @return a new {@code Validated<E, B>} instance. If {@code fnValidated} is {@code Valid}, the
   *     result is the application of its function to this instance's value (wrapped in {@code
   *     Valid}). If {@code fnValidated} is {@code Invalid}, its error is propagated.
   * @throws NullPointerException if {@code fnValidated} is null. Also, if {@code fnValidated} is
   *     {@code Valid} and its function application via {@code map} results in a null value or the
   *     function itself is null (as per {@code map}'s contract).
   */
  @Override
  public @NonNull <B> Validated<E, B> ap(
      @NonNull Validated<E, Function<? super A, ? extends B>> fnValidated) {
    Objects.requireNonNull(fnValidated, AP_FN_CANNOT_BE_NULL);
    if (fnValidated.isValid()) {
      Function<? super A, ? extends B> f =
          fnValidated.get(); // Assuming get() on Valid FunctionValidated returns non-null function
      // map will handle if 'f' is null (though get() on Valid should not return null if fnValidated
      // was Valid(nonNullFunction))
      // and if f.apply(value) returns null.
      return this.map(f);
    } else {
      // Propagate the error from fnValidated
      return Validated.invalid(fnValidated.getError()); // getError() is safe here
    }
  }

  /**
   * Returns a string representation of this {@code Valid} instance. The format is
   * "Valid(value_string_representation)".
   *
   * @return a string representation of this {@code Valid} instance.
   */
  @Override
  public String toString() {
    return "Valid(" + value + ")";
  }
}
