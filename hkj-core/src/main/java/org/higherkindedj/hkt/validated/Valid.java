// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.util.validation.FunctionValidator;

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

    /**
     * Compact constructor for {@code Valid}. Ensures the encapsulated value is non-null.
     *
     * @param value The value to encapsulate. Must be non-null.
     * @throws NullPointerException if {@code value} is null.
     */
    public Valid {
        Objects.requireNonNull(value, VALID_VALUE_CANNOT_BE_NULL_MSG);
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
        Objects.requireNonNull(other, "orElse 'other' parameter cannot be null, though it's unused for Valid.");
        return value;
    }

    @Override
    public A orElseGet(Supplier<? extends A> otherSupplier) {
        FunctionValidator.requireFunction(otherSupplier, "otherSupplier", "Valid.orElseGet");
        return value;
    }

    @Override
    public <X extends Throwable> A orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        FunctionValidator.requireFunction(exceptionSupplier, "exceptionSupplier", "Valid.orElseThrow");
        return value;
    }

    @Override
    public void ifValid(Consumer<? super A> consumer) {
        FunctionValidator.requireFunction(consumer, "consumer", "Valid.ifValid");
        consumer.accept(value);
    }

    @Override
    public void ifInvalid(Consumer<? super E> consumer) {
        FunctionValidator.requireFunction(consumer, "consumer", "Valid.ifInvalid");
        // No action for Valid
    }

    @Override
    public <B> Validated<E, B> map(Function<? super A, ? extends B> fn) {
        FunctionValidator.requireMapper(fn, "Valid.map");
        B newValue = fn.apply(value);
        Objects.requireNonNull(newValue, "Mapping function returned null in Valid.map");
        return new Valid<>(newValue);
    }

    @Override
    public <B> Validated<E, B> flatMap(Function<? super A, ? extends Validated<E, ? extends B>> fn) {
        FunctionValidator.requireFlatMapper(fn, "Valid.flatMap");
        Validated<E, ? extends B> result = fn.apply(value);
        Objects.requireNonNull(result, "flatMap mapping function returned a null Validated instance");

        @SuppressWarnings("unchecked")
        Validated<E, B> typedResult = (Validated<E, B>) result;
        return typedResult;
    }

    @Override
    public <B> Validated<E, B> ap(
            Validated<E, Function<? super A, ? extends B>> fnValidated, Semigroup<E> semigroup) {

        Objects.requireNonNull(fnValidated, "Validated function for ap cannot be null.");
        Objects.requireNonNull(semigroup, "semigroup cannot be null.");

        return fnValidated.fold(
                Validated::invalid, // Propagate the error from the function
                this::map // Apply the function to this value
        );
    }

    @Override
    public String toString() {
        return "Valid(" + value + ")";
    }
}