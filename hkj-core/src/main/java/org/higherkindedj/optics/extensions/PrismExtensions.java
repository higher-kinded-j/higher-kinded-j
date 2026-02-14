// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.jspecify.annotations.NullMarked;

/**
 * Static utility methods for using {@link Prism} with hkj-core types (Maybe, Either, Validated).
 *
 * <p>This class provides ergonomic helpers for common patterns when working with prisms and
 * optional or fallible operations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.PrismExtensions.*;
 *
 * Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();
 *
 * // Get as Maybe instead of Optional
 * Maybe<JsonString> maybeString = getMaybe(stringPrism, jsonValue);
 *
 * // Get as Either with error message
 * Either<String, JsonString> stringOrError =
 *     getEither(stringPrism, "Not a string value", jsonValue);
 *
 * // Modify with validation
 * Either<String, JsonValue> result = modifyEither(
 *     stringPrism,
 *     str -> str.value().length() > 0
 *         ? Either.right(str)
 *         : Either.left("String cannot be empty"),
 *     jsonValue
 * );
 * }</pre>
 */
@NullMarked
public final class PrismExtensions {
  /** Private constructor to prevent instantiation. */
  private PrismExtensions() {}

  /**
   * Gets the value from a {@link Prism} wrapped in a {@link Maybe}.
   *
   * <p>This converts the standard {@link Optional} result to {@link Maybe}.
   *
   * @param prism The prism to get the value from
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Maybe.just(value)} if the prism matches, {@code Maybe.nothing()} otherwise
   */
  public static <S, A> Maybe<A> getMaybe(Prism<S, A> prism, S source) {
    return prism.getOptional(source).map(Maybe::just).orElse(Maybe.nothing());
  }

  /**
   * Gets the value from a {@link Prism} wrapped in an {@link Either}.
   *
   * <p>If the prism doesn't match, the result will be {@code Either.left(errorValue)}.
   *
   * @param prism The prism to get the value from
   * @param errorValue The error value to use if the prism doesn't match
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Either.right(value)} if the prism matches, {@code Either.left(errorValue)}
   *     otherwise
   */
  public static <E, S, A> Either<E, A> getEither(Prism<S, A> prism, E errorValue, S source) {
    return prism.getOptional(source).map(Either::<E, A>right).orElse(Either.left(errorValue));
  }

  /**
   * Gets the value from a {@link Prism} wrapped in a {@link Validated}.
   *
   * <p>If the prism doesn't match, the result will be {@code Validated.invalid(errorValue)}.
   *
   * @param prism The prism to get the value from
   * @param errorValue The error value to use if the prism doesn't match
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Validated.valid(value)} if the prism matches, {@code
   *     Validated.invalid(errorValue)} otherwise
   */
  public static <E, S, A> Validated<E, A> getValidated(Prism<S, A> prism, E errorValue, S source) {
    return prism
        .getOptional(source)
        .map(Validated::<E, A>valid)
        .orElse(Validated.invalid(errorValue));
  }

  /**
   * Modifies the value with a function that returns {@link Maybe}.
   *
   * <p>If the prism doesn't match or the function returns {@code Maybe.nothing()}, returns {@code
   * Maybe.nothing()}.
   *
   * @param prism The prism to modify through
   * @param f The modification function returning {@code Maybe}
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Maybe.just(updatedSource)} if successful, {@code Maybe.nothing()} otherwise
   */
  public static <S, A> Maybe<S> modifyMaybe(Prism<S, A> prism, Function<A, Maybe<A>> f, S source) {
    return prism
        .getOptional(source)
        .map(Maybe::just)
        .orElse(Maybe.nothing())
        .flatMap(f)
        .map(prism::build);
  }

  /**
   * Modifies the value with a function that returns {@link Either}.
   *
   * <p>If the prism doesn't match, returns {@code Either.left(noMatchError)}. If the function
   * returns an error, that error is returned.
   *
   * @param prism The prism to modify through
   * @param noMatchError Error to return if the prism doesn't match
   * @param f The modification function returning {@code Either}
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Either.right(updatedSource)} if successful, {@code Either.left(error)} otherwise
   */
  public static <E, S, A> Either<E, S> modifyEither(
      Prism<S, A> prism, E noMatchError, Function<A, Either<E, A>> f, S source) {
    return prism
        .getOptional(source)
        .map(Either::<E, A>right)
        .orElse(Either.left(noMatchError))
        .flatMap(f)
        .map(prism::build);
  }

  /**
   * Modifies the value with a function that returns {@link Validated}.
   *
   * <p>If the prism doesn't match, returns {@code Validated.invalid(noMatchError)}.
   *
   * @param prism The prism to modify through
   * @param noMatchError Error to return if the prism doesn't match
   * @param f The modification function returning {@code Validated}
   * @param source The source structure
   * @param <E> The type of the error value
   * @param <S> The type of the source structure
   * @param <A> The type of the focused part
   * @return {@code Validated.valid(updatedSource)} if successful, {@code Validated.invalid(error)}
   *     otherwise
   */
  public static <E, S, A> Validated<E, S> modifyValidated(
      Prism<S, A> prism, E noMatchError, Function<A, Validated<E, A>> f, S source) {
    return prism
        .getOptional(source)
        .map(Validated::<E, A>valid)
        .orElse(Validated.invalid(noMatchError))
        .flatMap(f)
        .map(prism::build);
  }
}
