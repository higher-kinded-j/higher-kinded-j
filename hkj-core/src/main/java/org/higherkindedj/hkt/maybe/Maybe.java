// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.util.validation.Operation.JUST;
import static org.higherkindedj.hkt.util.validation.Operation.TO_EITHER;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.util.validation.Validation;
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
 * <p>As part of the Higher-Kinded-J HKT simulation, both {@link Just} and {@link Nothing} directly
 * implement {@link MaybeKind}, allowing them to participate in the HKT framework without requiring
 * wrapper types. This means that widen/narrow operations via {@link MaybeKindHelper} have zero
 * runtime overhead (simple type-safe casts rather than object allocation).
 *
 * @param <T> the type of the value held by this {@code Maybe} instance. This type parameter itself
 *     can be nullable if {@code T} represents a type that can be null, but a {@link Just} instance
 *     will always wrap a non-null value.
 */
public sealed interface Maybe<T> permits Just, Nothing {

  Class<Maybe> MAYBE_CLASS = Maybe.class;

  /**
   * Returns a {@code Maybe} describing the given non-null value.
   *
   * @param value the value to describe, which must be non-null.
   * @param <T> the type of the value.
   * @return a {@code Maybe} instance with the specified non-null value present. Will not be {@code
   *     null}.
   * @throws NullPointerException if {@code value} is {@code null}.
   */
  static <T> Maybe<T> just(T value) {
    Validation.coreType().requireValue(value, MAYBE_CLASS, JUST);
    return new Just<>(value);
  }

  /**
   * Returns an empty {@code Maybe} instance, representing the absence of a value (Nothing).
   *
   * @param <T> The type of the non-existent value. This is a phantom type parameter.
   * @return an empty {@code Maybe} instance. Will not be {@code null}.
   */
  static <T> Maybe<T> nothing() {
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
  static <T> Maybe<T> fromNullable(@Nullable T value) {
    return value == null ? nothing() : just(value);
  }

  /**
   * Converts a {@link java.util.Optional} to a {@code Maybe}.
   *
   * <p>If the {@code Optional} is present, returns a {@code Maybe} with the value. If the {@code
   * Optional} is empty, returns {@code Nothing}.
   *
   * @param optional the {@code Optional} to convert, which must be non-null.
   * @param <T> the type of the value.
   * @return a {@code Maybe} with the value present if the {@code Optional} is present, otherwise
   *     {@code Nothing}. Will not be {@code null}.
   * @throws NullPointerException if {@code optional} is {@code null}.
   */
  static <T> Maybe<T> fromOptional(Optional<T> optional) {
    return optional.isPresent() ? just(optional.get()) : nothing();
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
  T get() throws NoSuchElementException;

  /**
   * Returns the value if present (i.e., this is a {@link Just}), otherwise returns {@code other}.
   * The {@code other} parameter must not be {@code null}.
   *
   * @param other the non-null value to be returned if there is no value present.
   * @return the value, if present, otherwise {@code other}. The result is guaranteed to be
   *     non-null.
   */
  T orElse(T other);

  /**
   * Returns the value if present (i.e., this is a {@link Just}), otherwise invokes {@code
   * otherSupplier} and returns the result of that invocation. The supplier must not be {@code null}
   * and must produce a non-null value.
   *
   * @param otherSupplier a {@link Supplier} whose result is returned if no value is present. Must
   *     not be {@code null}. The supplier itself is {@code }, and it's expected to produce a {@code
   *     T}.
   * @return the value if present, otherwise the non-null result of {@code otherSupplier.get()}.
   * @throws NullPointerException if no value is present and {@code otherSupplier} is {@code null}.
   */
  T orElseGet(Supplier<? extends T> otherSupplier);

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
  <U> Maybe<U> map(Function<? super T, ? extends @Nullable U> mapper);

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
   *     The function takes the non-null value of type {@code T} and must return a {@code Maybe<?
   *     extends U>}.
   * @return the result of applying a {@code Maybe}-bearing mapping function to the value of this
   *     {@code Maybe}, if a value is present; otherwise, an empty {@code Maybe} (Nothing). The
   *     returned {@code Maybe} itself will not be {@code null}.
   * @throws NullPointerException if {@code mapper} is {@code null}, or if a value is present and
   *     {@code mapper} returns a {@code null} {@code Maybe} instance.
   */
  <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper);

  /**
   * Converts this {@code Maybe} to an {@code Either}, using the provided value for the {@link
   * Either.Left Left} case if this is {@link Nothing}.
   *
   * <p>This method bridges between the {@code Maybe} and {@code Either} types, which both represent
   * computations that may not produce a value. The key difference is that {@code Either} can carry
   * information about why no value was produced (in its {@code Left} case), while {@code Nothing}
   * carries no such information.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> justValue = Maybe.just(42);
   * Either<String, Integer> right = justValue.toEither("No value");
   * // right = Right(42)
   *
   * Maybe<Integer> nothing = Maybe.nothing();
   * Either<String, Integer> left = nothing.toEither("No value");
   * // left = Left("No value")
   * }</pre>
   *
   * @param <L> The type of the {@link Either.Left Left} value.
   * @param leftValue The value to use for the {@link Either.Left Left} case if this is {@link
   *     Nothing}. May be {@code null} if the left type permits null values.
   * @return An {@code Either} that is {@link Either.Right Right} containing this {@code Maybe}'s
   *     value if this is {@link Just}, or {@link Either.Left Left} containing {@code leftValue} if
   *     this is {@link Nothing}. The returned {@code Either} will not be {@code null}.
   */
  default <L> Either<L, T> toEither(L leftValue) {
    return switch (this) {
      case Just<T>(var value) -> Either.right(value);
      case Nothing<T> n -> Either.left(leftValue);
    };
  }

  /**
   * Converts this {@code Maybe} to an {@code Either}, using the provided supplier to generate the
   * {@link Either.Left Left} value if this is {@link Nothing}.
   *
   * <p>This variant is useful when computing the left value is expensive and should only be
   * performed when necessary (i.e., when this is {@link Nothing}).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> justValue = Maybe.just(42);
   * Either<String, Integer> right = justValue.toEither(() -> "No value found");
   * // right = Right(42), supplier never called
   *
   * Maybe<Integer> nothing = Maybe.nothing();
   * Either<String, Integer> left = nothing.toEither(() -> "No value found");
   * // left = Left("No value found")
   * }</pre>
   *
   * @param <L> The type of the {@link Either.Left Left} value.
   * @param leftSupplier The supplier to generate the {@link Either.Left Left} value if this is
   *     {@link Nothing}. Must not be {@code null} when this is {@link Nothing}.
   * @return An {@code Either} that is {@link Either.Right Right} containing this {@code Maybe}'s
   *     value if this is {@link Just}, or {@link Either.Left Left} containing the supplied value if
   *     this is {@link Nothing}. The returned {@code Either} will not be {@code null}.
   * @throws NullPointerException if this is {@link Nothing} and {@code leftSupplier} is {@code
   *     null}.
   */
  default <L> Either<L, T> toEither(Supplier<? extends L> leftSupplier) {
    return switch (this) {
      case Just<T>(var value) -> Either.right(value);
      case Nothing<T> n -> {
        Validation.function().requireFunction(leftSupplier, "leftSupplier", MAYBE_CLASS, TO_EITHER);
        yield Either.left(leftSupplier.get());
      }
    };
  }
}
