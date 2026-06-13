// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
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
public record Id<A>(@Nullable A value) implements IdKind<A> {

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
    Validation.function().require(fn, "fn", MAP);
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
    Validation.function().require(fn, "fn", FLAT_MAP);

    Id<? extends B> result = fn.apply(value());
    Validation.function().requireNonNullResult(result, "fn", FLAT_MAP);

    return covary(result);
  }

  /**
   * Reinterprets an {@code Id<? extends B>} as an {@code Id<B>}.
   *
   * <p>Safe: {@code Id} is an immutable record, so a value produced as {@code Id<? extends B>} can
   * be observed as {@code Id<B>} without risk — there is no operation through which the narrowed
   * element type could be written back.
   *
   * @param id the value to reinterpret; never null in practice (callers validate first)
   * @param <B> the target element type
   * @return {@code id} viewed as {@code Id<B>}
   */
  @SuppressWarnings("unchecked") // immutable record: covariant reinterpretation is unobservable
  private static <B> Id<B> covary(Id<? extends B> id) {
    return (Id<B>) id;
  }
}
