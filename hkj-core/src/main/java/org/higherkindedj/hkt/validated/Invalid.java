// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;

/**
 * Represents the erroneous (Invalid) case of a {@link Validated}. It holds a non-null error value
 * of type {@code E}. This class is an immutable record.
 *
 * <p>As part of the HKT pattern, this class also implements {@link ValidatedKind}, allowing it to
 * be used with typeclasses expecting {@code Kind<ValidatedKind.Witness<E>, A>}.
 *
 * @param <E> The type of the encapsulated error.
 * @param <A> The type of the potential valid value (unused in {@code Invalid}, but part of the
 *     {@link Validated} contract).
 * @param error The non-null error value held by this {@code Invalid} instance.
 */
public record Invalid<E, A>(E error) implements Validated<E, A>, ValidatedKind<E, A> {

  // --- Message Constants ---
  static final String CANNOT_GET_FROM_INVALID_INSTANCE_PREFIX_MSG =
      "Cannot get() from an Invalid instance. Error: ";
  static final String OR_ELSE_OTHER_CANNOT_BE_NULL_MSG =
      "orElse 'other' parameter cannot be null for Invalid.";
  static final String OR_ELSE_GET_SUPPLIER_CANNOT_BE_NULL_MSG =
      "orElseGet 'otherSupplier' parameter cannot be null for Invalid.";
  static final String OR_ELSE_GET_SUPPLIER_RETURNED_NULL_MSG =
      "orElseGet supplier returned null for Invalid.";
  static final String OR_ELSE_THROW_SUPPLIER_CANNOT_BE_NULL_MSG =
      "orElseThrow 'exceptionSupplier' parameter cannot be null for Invalid.";
  static final String OR_ELSE_THROW_SUPPLIER_PRODUCED_NULL_MSG =
      "orElseThrow 'exceptionSupplier' must not produce a null throwable for Invalid.";
  static final String IF_VALID_CONSUMER_CANNOT_BE_NULL_MSG =
      "ifValid 'consumer' parameter cannot be null for Invalid.";
  static final String IF_INVALID_CONSUMER_CANNOT_BE_NULL_MSG =
      "ifInvalid 'consumer' parameter cannot be null for Invalid.";
  static final String MAP_FN_CANNOT_BE_NULL_MSG = "Mapping function cannot be null for Invalid.map";
  static final String FLATMAP_FN_CANNOT_BE_NULL_MSG =
      "flatMap mapping function cannot be null for Invalid.flatMap";
  static final String AP_FN_VALIDATED_CANNOT_BE_NULL_MSG =
      "Validated function for ap cannot be null.";
  static String SEMIGROUP_FOR_FOR_AP_CANNOT_BE_NULL_MSG = "semigroup cannot be null.";

  /**
   * Compact constructor for {@code Invalid}. Ensures the encapsulated error is non-null.
   *
   * @param error The error to encapsulate. Must be non-null.
   * @throws NullPointerException if {@code error} is null.
   */
  public Invalid {
    Objects.requireNonNull(error, INVALID_ERROR_CANNOT_BE_NULL_MSG);
  }

  /**
   * Checks if this is a {@code Valid} instance.
   *
   * @return always {@code false} for {@code Invalid} instances.
   */
  @Override
  public boolean isValid() {
    return false;
  }

  /**
   * Checks if this is an {@code Invalid} instance.
   *
   * @return always {@code true} for {@code Invalid} instances.
   */
  @Override
  public boolean isInvalid() {
    return true;
  }

  /**
   * Throws {@link NoSuchElementException} because an {@code Invalid} instance does not contain a
   * valid value. The error message includes the string representation of the encapsulated error.
   *
   * @return never returns normally.
   * @throws NoSuchElementException always, as this is an {@code Invalid} instance.
   */
  @Override
  public A get() throws NoSuchElementException {
    throw new NoSuchElementException(CANNOT_GET_FROM_INVALID_INSTANCE_PREFIX_MSG + error);
  }

  /**
   * Gets the non-null error encapsulated by this {@code Invalid} instance.
   *
   * @return The non-null encapsulated error.
   */
  @Override
  public E getError() {
    return error;
  }

  /**
   * Returns the provided alternative {@code other} value, as this is an {@code Invalid} instance.
   *
   * @param other The non-null alternative value to return.
   * @return The provided {@code other} value.
   * @throws NullPointerException if {@code other} is null.
   */
  @Override
  public A orElse(A other) {
    Objects.requireNonNull(other, OR_ELSE_OTHER_CANNOT_BE_NULL_MSG);
    return other;
  }

  /**
   * Invokes the {@code otherSupplier} and returns its non-null result, as this is an {@code
   * Invalid} instance.
   *
   * @param otherSupplier The non-null supplier for the alternative value. Must not return null.
   * @return The non-null value supplied by {@code otherSupplier}.
   * @throws NullPointerException if {@code otherSupplier} is null or if {@code otherSupplier.get()}
   *     returns null.
   */
  @Override
  public A orElseGet(Supplier<? extends A> otherSupplier) {
    Objects.requireNonNull(otherSupplier, OR_ELSE_GET_SUPPLIER_CANNOT_BE_NULL_MSG);
    A suppliedValue = otherSupplier.get();
    Objects.requireNonNull(suppliedValue, OR_ELSE_GET_SUPPLIER_RETURNED_NULL_MSG);
    return suppliedValue;
  }

  /**
   * Always throws the exception produced by the {@code exceptionSupplier}, as this is an {@code
   * Invalid} instance. The type parameter {@code A} for the return type is effectively a phantom
   * type here, as an exception is always thrown.
   *
   * @param exceptionSupplier The non-null supplier for the exception to throw. Must not return null
   *     (unless X is NullPointerException itself).
   * @param <X> Type of the exception to be thrown.
   * @return never returns normally.
   * @throws X always, as produced by the {@code exceptionSupplier}.
   * @throws NullPointerException if {@code exceptionSupplier} is null or if {@code
   *     exceptionSupplier.get()} returns a null throwable.
   */
  @Override
  public <X extends Throwable> A orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
    Objects.requireNonNull(exceptionSupplier, OR_ELSE_THROW_SUPPLIER_CANNOT_BE_NULL_MSG);
    X throwable = exceptionSupplier.get();
    Objects.requireNonNull(throwable, OR_ELSE_THROW_SUPPLIER_PRODUCED_NULL_MSG);
    throw throwable;
  }

  /**
   * Performs no action, as this is an {@code Invalid} instance and thus does not contain a valid
   * value for the consumer. The provided consumer is checked for nullity but not invoked.
   *
   * @param consumer The action to perform if this were a {@code Valid} instance (ignored). Must not
   *     be null.
   * @throws NullPointerException if {@code consumer} is null.
   */
  @Override
  public void ifValid(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, IF_VALID_CONSUMER_CANNOT_BE_NULL_MSG);
  }

  /**
   * Performs the given action with the encapsulated non-null error.
   *
   * @param consumer The action to perform with the error. Must not be null.
   * @throws NullPointerException if {@code consumer} is null.
   */
  @Override
  public void ifInvalid(Consumer<? super E> consumer) {
    Objects.requireNonNull(consumer, IF_INVALID_CONSUMER_CANNOT_BE_NULL_MSG).accept(error);
  }

  /**
   * Returns this {@code Invalid} instance unchanged, cast to {@code Validated<E, B>}. The mapping
   * function {@code fn} is not applied because this is an {@code Invalid} instance.
   *
   * @param fn The mapping function (ignored). Must not be null.
   * @param <B> The new value type (phantom type for this {@code Invalid} instance).
   * @return this {@code Invalid} instance, cast to {@code Validated<E, B>}.
   * @throws NullPointerException if {@code fn} is null (due to eager null check).
   */
  @Override
  @SuppressWarnings("unchecked")
  public <B> Validated<E, B> map(Function<? super A, ? extends B> fn) {
    Objects.requireNonNull(fn, MAP_FN_CANNOT_BE_NULL_MSG);
    return (Validated<E, B>) this;
  }

  /**
   * Returns this {@code Invalid} instance unchanged, cast to {@code Validated<E, B>}. The function
   * {@code fn} is not applied because this is an {@code Invalid} instance.
   *
   * @param fn The function to apply (ignored). Must not be null.
   * @param <B> The new value type (phantom type for this {@code Invalid} instance).
   * @return this {@code Invalid} instance, cast to {@code Validated<E, B>}.
   * @throws NullPointerException if {@code fn} is null (due to eager null check).
   */
  @Override
  @SuppressWarnings("unchecked")
  public <B> Validated<E, B> flatMap(Function<? super A, ? extends Validated<E, ? extends B>> fn) {
    Objects.requireNonNull(fn, FLATMAP_FN_CANNOT_BE_NULL_MSG);
    return (Validated<E, B>) this;
  }

  /**
   * Determines the result of an applicative operation involving this {@code Invalid} instance,
   * accumulating errors.
   *
   * @param fnValidated A {@code Validated} instance expected to contain a function. Must not be
   *     null.
   * @param semigroup The {@link Semigroup} to use for combining errors.
   * @param <B> The value type of the resulting {@code Validated} instance.
   * @return an {@code Invalid<E, B>} instance. If {@code fnValidated} is also {@code Invalid}, its
   *     error is combined with this instance's error. Otherwise, this instance is returned.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <B> Validated<E, B> ap(
      Validated<E, Function<? super A, ? extends B>> fnValidated, Semigroup<E> semigroup) {
    Objects.requireNonNull(fnValidated, AP_FN_VALIDATED_CANNOT_BE_NULL_MSG);
    Objects.requireNonNull(semigroup, SEMIGROUP_FOR_FOR_AP_CANNOT_BE_NULL_MSG);
    if (fnValidated.isInvalid()) {
      E combinedError = semigroup.combine(fnValidated.getError(), this.error);
      return Validated.invalid(combinedError);
    }
    return (Validated<E, B>) this;
  }

  /**
   * Returns a string representation of this {@code Invalid} instance. The format is
   * "Invalid(error_string_representation)".
   *
   * @return a string representation of this {@code Invalid} instance.
   */
  @Override
  public String toString() {
    return "Invalid(" + error + ")";
  }
}
