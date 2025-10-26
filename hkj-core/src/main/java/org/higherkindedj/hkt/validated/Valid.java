// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.util.validation.Validation;

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
public record Valid<E, A>(A value) implements Validated<E, A>, ValidatedKind<E, A> {

  private static final Class<Valid> VALID_CLASS = Valid.class;

  /**
   * Compact constructor for {@code Valid}. Ensures the encapsulated value is non-null.
   *
   * @param value The value to encapsulate. Must be non-null.
   * @throws NullPointerException if {@code value} is null.
   */
  public Valid {
    Validation.coreType().requireValue(value, Valid.class, CONSTRUCTION);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean isInvalid() {
    return false;
  }

  @Override
  public A get() {
    return value;
  }

  @Override
  public E getError() throws NoSuchElementException {
    throw new NoSuchElementException("Cannot getError() from a Valid instance.");
  }

  @Override
  public A orElse(A other) {
    Objects.requireNonNull(
        other, "orElse 'other' parameter cannot be null, though it's unused for Valid.");
    return value;
  }

  @Override
  public A orElseGet(Supplier<? extends A> otherSupplier) {
    Validation.function().requireFunction(otherSupplier, "otherSupplier", VALID_CLASS, OR_ELSE_GET);
    return value;
  }

  @Override
  public <X extends Throwable> A orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
    Validation.function()
        .requireFunction(exceptionSupplier, "exceptionSupplier", VALID_CLASS, OR_ELSE_THROW);
    return value;
  }

  @Override
  public void ifValid(Consumer<? super A> consumer) {
    Validation.function().requireFunction(consumer, "consumer", VALID_CLASS, IF_VALID);
    consumer.accept(value);
  }

  @Override
  public void ifInvalid(Consumer<? super E> consumer) {
    Validation.function().requireFunction(consumer, "consumer", VALID_CLASS, IF_INVALID);
    // No action for Valid
  }

  @Override
  public <B> Validated<E, B> map(Function<? super A, ? extends B> fn) {
    Validation.function().requireMapper(fn, "fn", VALID_CLASS, MAP);
    B newValue = fn.apply(value);
    Validation.function().requireNonNullResult(newValue, "fn", VALID_CLASS, MAP);
    return new Valid<>(newValue);
  }

  @Override
  public <B> Validated<E, B> flatMap(Function<? super A, ? extends Validated<E, ? extends B>> fn) {
    Validation.function().requireFlatMapper(fn, "fn", VALID_CLASS, FLAT_MAP);
    Validated<E, ? extends B> result = fn.apply(value);
    Validation.function()
        .requireNonNullResult(result, "fn", VALID_CLASS, FLAT_MAP, Validated.class);

    @SuppressWarnings("unchecked")
    Validated<E, B> typedResult = (Validated<E, B>) result;
    return typedResult;
  }

  @Override
  public <B> Validated<E, B> ap(
      Validated<E, Function<? super A, ? extends B>> fnValidated, Semigroup<E> semigroup) {
    Validation.function().requireFunction(fnValidated, "fnValidated", VALID_CLASS, AP);
    Validation.coreType().requireValue(semigroup, "semigroup", VALID_CLASS, AP);

    return switch (fnValidated) {
      case Invalid<E, Function<? super A, ? extends B>>(var error) -> Validated.invalid(error);
      case Valid<E, Function<? super A, ? extends B>>(var function) -> this.map(function);
    };
  }

  @Override
  public String toString() {
    return "Valid(" + value + ")";
  }
}
