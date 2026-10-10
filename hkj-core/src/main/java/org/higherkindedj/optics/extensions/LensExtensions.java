// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Static utility methods for using {@link Lens} with hkj-core types (Maybe, Either, Validated,
 * Try).
 *
 * <p>This class provides ergonomic helpers for common patterns when working with lenses and
 * fallible or optional operations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.LensExtensions.*;
 *
 * Lens<User, String> emailLens = UserLenses.email();
 *
 * // Get as Maybe (null-safe)
 * Maybe<String> email = getMaybe(emailLens, user);
 *
 * // Get as Either with error message
 * Either<String, String> emailOrError = getEither(emailLens, "No email found", user);
 *
 * // Modify with validation
 * Either<String, User> result = modifyEither(
 *     emailLens,
 *     email -> email.contains("@")
 *         ? Either.right(email)
 *         : Either.left("Invalid email format"),
 *     user
 * );
 * }</pre>
 */
@NullMarked
public final class LensExtensions {
  /** Private constructor to prevent instantiation. */
  private LensExtensions() {}

  /**
   * Gets the value from a {@link Lens} wrapped in a {@link Maybe}.
   *
   * <p>This is null-safe; if the lens returns {@code null}, the result will be {@code
   * Maybe.nothing()}.
   *
   * @param lens The lens to get the value from
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Maybe.just(value)} if non-null, {@code Maybe.nothing()} otherwise
   */
  public static <S extends @Nullable Object, A extends @Nullable Object> Maybe<@NonNull A> getMaybe(
      Lens<S, A> lens, S source) {
    return Maybe.fromNullable(lens.get(source));
  }

  /**
   * Gets the value from a {@link Lens} wrapped in an {@link Either}.
   *
   * <p>If the lens returns {@code null}, the result will be {@code Either.left(errorValue)}.
   *
   * @param lens The lens to get the value from
   * @param errorValue The error value to use if the lens returns null; must not be null, even when
   *     the focus is present
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Either.right(value)} if non-null, {@code Either.left(errorValue)} otherwise
   * @throws NullPointerException if {@code errorValue} is null
   */
  public static <E, S extends @Nullable Object, A extends @Nullable Object>
      Either<E, @NonNull A> getEither(Lens<S, A> lens, E errorValue, S source) {
    Objects.requireNonNull(errorValue, "errorValue must not be null");
    A value = lens.get(source);
    return value != null ? Either.right(value) : Either.left(errorValue);
  }

  /**
   * Gets the value from a {@link Lens} wrapped in a {@link Validated}.
   *
   * <p>If the lens returns {@code null}, the result will be {@code Validated.invalid(errorValue)}.
   *
   * @param lens The lens to get the value from
   * @param errorValue The error value to use if the lens returns null; must not be null, even when
   *     the focus is present
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Validated.valid(value)} if non-null, {@code Validated.invalid(errorValue)}
   *     otherwise
   * @throws NullPointerException if {@code errorValue} is null
   */
  public static <E, S extends @Nullable Object, A extends @Nullable Object>
      Validated<E, @NonNull A> getValidated(Lens<S, A> lens, E errorValue, S source) {
    Objects.requireNonNull(errorValue, "errorValue must not be null");
    A value = lens.get(source);
    return value != null ? Validated.valid(value) : Validated.invalid(errorValue);
  }

  /**
   * Modifies the value with a function that returns {@link Maybe}.
   *
   * <p>If the function returns {@code Maybe.nothing()}, the entire operation results in {@code
   * Maybe.nothing()}, indicating that the modification failed. The original source is not preserved
   * in the result and must be handled by the caller if needed (e.g., via {@code orElse(source)}).
   *
   * <p>The function sees the focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param lens The lens to modify through
   * @param f The modification function returning {@code Maybe}
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Maybe.just(updatedSource)} if successful, {@code Maybe.nothing()} if the
   *     function returned nothing
   */
  public static <S, A extends @Nullable Object> Maybe<S> modifyMaybe(
      Lens<S, A> lens, Function<A, Maybe<@NonNull A>> f, S source) {
    return f.apply(lens.get(source)).map(newValue -> lens.set(newValue, source));
  }

  /**
   * Modifies the value with a function that returns {@link Either}.
   *
   * <p>If the function returns {@code Either.left(error)}, that error is returned. Otherwise, the
   * source is updated with the new value.
   *
   * <p>The function sees the focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param lens The lens to modify through
   * @param f The modification function returning {@code Either}
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Either.right(updatedSource)} if successful, {@code Either.left(error)} otherwise
   */
  public static <E, S, A extends @Nullable Object> Either<E, S> modifyEither(
      Lens<S, A> lens, Function<A, Either<E, @NonNull A>> f, S source) {
    return f.apply(lens.get(source))
        .fold(Either::left, newValue -> Either.right(lens.set(newValue, source)));
  }

  /**
   * Modifies the value with a function that returns {@link Validated}.
   *
   * <p>If the function returns {@code Validated.invalid(error)}, that error is returned. Otherwise,
   * the source is updated with the new value.
   *
   * <p>The function sees the focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param lens The lens to modify through
   * @param f The modification function returning {@code Validated}
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Validated.valid(updatedSource)} if successful, {@code Validated.invalid(error)}
   *     otherwise
   */
  public static <E, S, A extends @Nullable Object> Validated<E, S> modifyValidated(
      Lens<S, A> lens, Function<A, Validated<E, @NonNull A>> f, S source) {
    return f.apply(lens.get(source))
        .fold(Validated::invalid, newValue -> Validated.valid(lens.set(newValue, source)));
  }

  /**
   * Modifies the value with a function that may throw exceptions.
   *
   * <p>If the function throws an exception, it is caught and returned as {@code Try.failure}.
   * Otherwise, the source is updated with the new value.
   *
   * <p>The function sees the focus as it is, a null one included, and must give back a non-null
   * value to write.
   *
   * @param lens The lens to modify through
   * @param f The modification function that may throw
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Try.success(updatedSource)} if successful, {@code Try.failure(exception)} if an
   *     exception was thrown
   */
  public static <S, A extends @Nullable Object> Try<S> modifyTry(
      Lens<S, A> lens, Function<A, Try<@NonNull A>> f, S source) {
    return f.apply(lens.get(source))
        .foldFailureFirst(Try::failure, newValue -> Try.success(lens.set(newValue, source)));
  }

  /**
   * Sets a value if it passes a validation function.
   *
   * <p>This is useful for conditional updates based on business rules.
   *
   * <p>The validator sees the new value as it is, a null one included, and must give back a
   * non-null value to write.
   *
   * @param lens The lens to set through
   * @param validator Validation function that returns error message if invalid
   * @param newValue The new value to set
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Either.right(updatedSource)} if valid, {@code Either.left(errorMessage)}
   *     otherwise
   */
  public static <S, A extends @Nullable Object> Either<String, S> setIfValid(
      Lens<S, A> lens, Function<A, Either<String, @NonNull A>> validator, A newValue, S source) {
    return validator
        .apply(newValue)
        .fold(Either::left, validValue -> Either.right(lens.set(validValue, source)));
  }
}
