// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A container object which may or may not contain a non-null value. If a value is present, {@link
 * #isJust()} returns {@code true} and {@link #isNothing()} returns {@code false}. If no value is
 * present, {@link #isNothing()} returns {@code true} and {@link #isJust()} returns {@code false}.
 * This state is referred to as {@code Nothing}.
 *
 * <p>This type is conceptually similar to {@link java.util.Optional}, but it is implemented
 * independently and is a {@code sealed interface} with specific implementing classes {@link Just}
 * and {@link Nothing}. Crucially, {@code Maybe} does not permit {@code null} values to be held
 * within a {@link Just} instance; the {@code Nothing} state should be used to represent the absence
 * of a value.
 *
 * @param <T> the type of the value held by this {@code Maybe} instance. This type parameter itself
 *     can be nullable if {@code T} represents a type that can be null, but a {@link Just} instance
 *     will always wrap a non-null value.
 */
public sealed interface Maybe<T> permits Just, Nothing {

  /**
   * Returns a {@code Maybe} describing the given non-null value.
   *
   * @param value the value to describe, which must be non-null.
   * @param <T> the type of the value.
   * @return a {@code Maybe} instance with the specified non-null value present. Will not be {@code
   *     null}.
   * @throws NullPointerException if {@code value} is {@code null}.
   */
  static <T> @NonNull Maybe<T> just(@NonNull T value) {
    Objects.requireNonNull(value, "Value for Just cannot be null");
    return new Just<>(value);
  }

  /**
   * Returns an empty {@code Maybe} instance, representing the absence of a value (Nothing).
   *
   * @param <T> The type of the non-existent value. This is a phantom type parameter.
   * @return an empty {@code Maybe} instance. Will not be {@code null}.
   */
  static <T> @NonNull Maybe<T> nothing() {
    return Nothing.instance();
  }

  /**
   * Returns a {@code Maybe} describing the given value if it is non-null, otherwise returns an
   * empty {@code Maybe} (Nothing).
   *
   * @param value the possibly-null value to describe.
   * @param <T> the type of the value.
   * @return a {@code Maybe} with the value present if the specified {@code value} is non-null,
   *     otherwise an empty {@code Maybe} (Nothing). Will not be {@code null}.
   */
  static <T> @NonNull Maybe<T> fromNullable(@Nullable T value) {
    return value == null ? nothing() : just(value);
  }

  /**
   * Returns {@code true} if a value is present in this {@code Maybe}, {@code false} otherwise. If
   * this method returns {@code true}, {@link #isNothing()} will return {@code false}.
   *
   * @return {@code true} if a value is present, otherwise {@code false}.
   */
  boolean isJust();

  /**
   * Returns {@code true} if no value is present in this {@code Maybe} (i.e., it is Nothing), {@code
   * false} otherwise. If this method returns {@code true}, {@link #isJust()} will return {@code
   * false}.
   *
   * @return {@code true} if no value is present, otherwise {@code false}.
   */
  boolean isNothing();

  /**
   * If a value is present (i.e., this is a {@link Just}), returns the value. The returned value is
   * guaranteed to be non-null.
   *
   * @return the non-null value held by this {@code Maybe}.
   * @throws NoSuchElementException if there is no value present (i.e., this is {@link Nothing}).
   */
  @NonNull T get() throws NoSuchElementException;

  /**
   * Returns the value if present (i.e., this is a {@link Just}), otherwise returns {@code other}.
   * The {@code other} parameter must not be {@code null}.
   *
   * @param other the non-null value to be returned if there is no value present.
   * @return the value, if present, otherwise {@code other}. The result is guaranteed to be
   *     non-null.
   */
  @NonNull T orElse(@NonNull T other);

  /**
   * Returns the value if present (i.e., this is a {@link Just}), otherwise invokes {@code
   * otherSupplier} and returns the result of that invocation. The supplier must not be {@code null}
   * and must produce a non-null value.
   *
   * @param otherSupplier a {@link Supplier} whose result is returned if no value is present. Must
   *     not be {@code null}. The supplier itself is {@code @NonNull}, and it's expected to produce
   *     a {@code @NonNull T}.
   * @return the value if present, otherwise the non-null result of {@code otherSupplier.get()}.
   * @throws NullPointerException if no value is present and {@code otherSupplier} is {@code null}.
   */
  @NonNull T orElseGet(@NonNull Supplier<? extends @NonNull T> otherSupplier);

  /**
   * If a value is present (i.e., this is a {@link Just}), returns a {@code Maybe} describing the
   * result of applying the given mapping function to the value. Otherwise, returns an empty {@code
   * Maybe} (Nothing).
   *
   * <p>If the mapping function produces a {@code null} result, this method returns {@code Nothing},
   * effectively behaving like {@code Maybe.fromNullable(mapper.apply(value))}.
   *
   * @param <U> The type of the result of the mapping function.
   * @param mapper the mapping function to apply to a value, if present. Must not be {@code null}.
   *     The function takes the non-null value of type {@code T} and can return a {@code @Nullable
   *     U}.
   * @return a {@code Maybe} describing the result of applying a mapping function to the value of
   *     this {@code Maybe}, if a value is present; otherwise, an empty {@code Maybe} (Nothing). The
   *     returned {@code Maybe} itself will not be {@code null}.
   */
  @NonNull <U> Maybe<U> map(@NonNull Function<? super T, ? extends @Nullable U> mapper);

  /**
   * If a value is present (i.e., this is a {@link Just}), returns the result of applying the given
   * {@code Maybe}-bearing mapping function to the value. Otherwise, returns an empty {@code Maybe}
   * (Nothing).
   *
   * <p>This method is similar to {@link #map(Function)}, but the mapping function is one whose
   * result is already a {@code Maybe}. If invoked, {@code flatMap} does not wrap this result in an
   * additional {@code Maybe}. The mapper function must not be {@code null} and must not return a
   * {@code null} {@code Maybe}.
   *
   * @param <U> The type parameter of the {@code Maybe} returned by the mapping function.
   * @param mapper the mapping function to apply to a value, if present. Must not be {@code null}.
   *     The function takes the non-null value of type {@code T} and must return a {@code @NonNull
   *     Maybe<? extends U>}.
   * @return the result of applying a {@code Maybe}-bearing mapping function to the value of this
   *     {@code Maybe}, if a value is present; otherwise, an empty {@code Maybe} (Nothing). The
   *     returned {@code Maybe} itself will not be {@code null}.
   * @throws NullPointerException if {@code mapper} is {@code null}, or if a value is present and
   *     {@code mapper} returns a {@code null} {@code Maybe} instance.
   */
  @NonNull <U> Maybe<U> flatMap(
      @NonNull Function<? super T, ? extends @NonNull Maybe<? extends U>> mapper);
}
