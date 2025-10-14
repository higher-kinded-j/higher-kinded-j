// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.jspecify.annotations.Nullable;

/**
 * The Identity monad, implemented as a Java record.
 *
 * <p>It's the simplest monad, which doesn't add any computational context beyond simply holding a
 * value. It's a wrapper around a value of type {@code A}. Records automatically provide a canonical
 * constructor, public accessors (e.g., {@code value()}), and implementations of {@code equals()},
 * {@code hashCode()}, and {@code toString()}.
 *
 * @param value The value being wrapped. Can be null.
 * @param <A> The type of the value.
 */
public record Id<A>(@Nullable A value) implements Kind<Id.Witness, A> {

  private static final Class<Id> ID_CLASS = Id.class;

  /** The HKT witness type for {@link Id}. */
  public static final class Witness {}

  /**
   * Static factory method to create an {@link Id} instance. This is idiomatic in functional
   * libraries and provides a consistent way to lift values into the Id context.
   *
   * @param value The value to wrap. Can be null.
   * @param <A> The type of the value.
   * @return An {@link Id} instance. Never null.
   */
  public static <A> Id<A> of(@Nullable A value) {
    return new Id<>(value);
  }

  /**
   * Applies a function to the wrapped value. This is equivalent to {@code
   * IdMonad.instance().map(fn, this)}.
   *
   * @param fn The function to apply. Must not be null.
   * @param <B> The type of the result of the function.
   * @return A new {@link Id} containing the result of applying the function. Never null.
   * @throws NullPointerException if {@code fn} is null.
   */
  public <B> Id<B> map(Function<? super A, ? extends B> fn) {
    FunctionValidator.requireMapper(fn, "fn", ID_CLASS, MAP);
    return new Id<>(fn.apply(value()));
  }

  /**
   * Applies a function that returns an {@link Id} to the wrapped value. This is equivalent to
   * {@code IdMonad.instance().flatMap(fn, this)}.
   *
   * @param fn The function to apply, which returns an {@link Id}. Must not be null.
   * @param <B> The type of the value within the {@link Id} returned by the function.
   * @return The {@link Id} instance returned by the function. Never null.
   * @throws NullPointerException if fn is null or if fn returns a null Id.
   */
  public <B> Id<B> flatMap(Function<? super A, ? extends Id<? extends B>> fn) {
    FunctionValidator.requireFlatMapper(fn, "fn", ID_CLASS, FLAT_MAP);

    Id<? extends B> result = fn.apply(value());
    FunctionValidator.requireNonNullResult(result, "fn", ID_CLASS, FLAT_MAP, ID_CLASS);

    // The cast is safe because fn returns Id<? extends B> which is covariant
    @SuppressWarnings("unchecked")
    Id<B> typedResult = (Id<B>) result;
    return typedResult;
  }
}
