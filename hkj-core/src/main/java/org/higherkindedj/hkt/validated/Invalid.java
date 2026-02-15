// Copyright (c) 2025 - 2026 Magnus Smith
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
 * Represents the erroneous (Invalid) case of a {@link Validated}. It holds a non-null error value
 * of type {@code E}. This class is an immutable record.
 *
 * <p>As part of the HKT pattern, this class implements both {@link ValidatedKind} and {@link
 * ValidatedKind2}, allowing it to be used with typeclasses expecting {@code
 * Kind<ValidatedKind.Witness<E>, A>} (for functors/applicatives) or {@code
 * Kind2<ValidatedKind2.Witness, E, A>} (for bifunctors).
 *
 * @param <E> The type of the encapsulated error.
 * @param <A> The type of the potential valid value (unused in {@code Invalid}, but part of the
 *     {@link Validated} contract).
 * @param error The non-null error value held by this {@code Invalid} instance.
 */
public record Invalid<E, A>(E error)
    implements Validated<E, A>, ValidatedKind<E, A>, ValidatedKind2<E, A> {

  /**
   * Compact constructor for {@code Invalid}. Ensures the encapsulated error is non-null.
   *
   * @param error The error to encapsulate. Must be non-null.
   * @throws NullPointerException if {@code error} is null.
   */
  public Invalid {
    Validation.coreType().requireError(error, Invalid.class, INVALID);
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean isInvalid() {
    return true;
  }

  @Override
  public A get() throws NoSuchElementException {
    throw new NoSuchElementException("Cannot get() from an Invalid instance. Error: " + error);
  }

  @Override
  public E getError() {
    return error;
  }

  @Override
  public A orElse(A other) {
    Objects.requireNonNull(other, "orElse 'other' parameter cannot be null for Invalid.");
    return other;
  }

  @Override
  public A orElseGet(Supplier<? extends A> otherSupplier) {
    Validation.function()
        .requireFunction(otherSupplier, "otherSupplier", Invalid.class, OR_ELSE_GET);
    A suppliedValue = otherSupplier.get();
    Objects.requireNonNull(suppliedValue, "orElseGet supplier returned null for Invalid.");
    return suppliedValue;
  }

  @Override
  public <X extends Throwable> A orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
    Validation.function()
        .requireFunction(exceptionSupplier, "exceptionSupplier", Invalid.class, OR_ELSE_THROW);
    X throwable = exceptionSupplier.get();
    Objects.requireNonNull(
        throwable,
        "orElseThrow 'exceptionSupplier' must not produce a null throwable for Invalid.");
    throw throwable;
  }

  @Override
  public void ifValid(Consumer<? super A> consumer) {
    Validation.function().requireFunction(consumer, "consumer", Invalid.class, IF_VALID);
    // No action for Invalid
  }

  @Override
  public void ifInvalid(Consumer<? super E> consumer) {
    Validation.function().requireFunction(consumer, "consumer", Invalid.class, IF_INVALID);
    consumer.accept(error);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> Validated<E, B> map(Function<? super A, ? extends B> fn) {
    Validation.function().requireMapper(fn, "fn", Invalid.class, MAP);
    return (Validated<E, B>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> Validated<E, B> flatMap(Function<? super A, ? extends Validated<E, ? extends B>> fn) {
    Validation.function().requireFlatMapper(fn, "fn", Invalid.class, FLAT_MAP);
    return (Validated<E, B>) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> Validated<E, B> ap(
      Validated<E, Function<? super A, ? extends B>> fnValidated, Semigroup<E> semigroup) {
    Validation.function().requireNonNullResult(fnValidated, "fnValidated", VALIDATED_CLASS, AP);
    Validation.coreType().requireValue(semigroup, "semigroup", Invalid.class, AP);

    return switch (fnValidated) {
      case Invalid<E, Function<? super A, ? extends B>>(var otherError) -> {
        E combinedError = semigroup.combine(otherError, this.error);
        yield Validated.invalid(combinedError);
      }
      case Valid<E, Function<? super A, ? extends B>> ignored -> (Validated<E, B>) this;
    };
  }

  @Override
  public String toString() {
    return "Invalid(" + error + ")";
  }
}
