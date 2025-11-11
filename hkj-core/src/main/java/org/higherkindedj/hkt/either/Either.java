// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Represents a value of one of two possible types: {@link Left} or {@link Right}. {@code Either} is
 * a sum type, also known as a disjoint union.
 *
 * <p>By convention, {@link Left} is used to represent an error, an alternative flow, or an invalid
 * state, while {@link Right} is used to represent a successful computation or the primary expected
 * value. This convention makes {@code Either} "right-biased" for monadic operations like {@link
 * #map(Function)} and {@link #flatMap(Function)}, which operate on the {@link Right} value and pass
 * {@link Left} values through unchanged.
 *
 * <p>It is a sealed interface, meaning its only direct implementations are {@link Left} and {@link
 * Right}, which are provided as nested records. This allows for exhaustive pattern matching using
 * `switch` expressions.
 *
 * <p>Example of use:
 *
 * <pre>{@code
 * public Either<String, Integer> parseInteger(String s) {
 *  try {
 *    return Either.right(Integer.parseInt(s));
 *  } catch (NumberFormatException e) {
 *    return Either.left("Invalid number format: " + s);
 *  }
 * }
 *
 * Either<String, Integer> result = parseInteger("123");
 * result.fold(
 *  error -> System.out.println("Error: " + error),
 *  value -> System.out.println("Parsed value: " + value)
 * );
 *
 * Either<String, Integer> length = result.map(v -> v * 2); // Only maps if result was Right
 * }</pre>
 *
 * @param <L> The type of the value if this is a {@link Left}. Conventionally the error type.
 * @param <R> The type of the value if this is a {@link Right}. Conventionally the success type.
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

  Class<Either> EITHER_CLASS = Either.class;

  /**
   * Checks if this {@code Either} instance is a {@link Left}.
   *
   * @return {@code true} if this is a {@link Left}, {@code false} otherwise.
   */
  boolean isLeft();

  /**
   * Checks if this {@code Either} instance is a {@link Right}.
   *
   * @return {@code true} if this is a {@link Right}, {@code false} otherwise.
   */
  boolean isRight();

  /**
   * Retrieves the value if this is a {@link Left}.
   *
   * <p><b>Caution:</b> This method should typically be called only after ensuring {@link #isLeft()}
   * returns {@code true}, or within a context where being a {@code Left} is guaranteed. Otherwise,
   * prefer using {@link #fold(Function, Function)} or pattern matching to safely access the value.
   *
   * @return The value of type {@code L} if this is a {@link Left}.
   * @throws NoSuchElementException if this is a {@link Right}.
   */
  @Nullable L getLeft() throws NoSuchElementException;

  /**
   * Retrieves the value if this is a {@link Right}.
   *
   * <p><b>Caution:</b> This method should typically be called only after ensuring {@link
   * #isRight()} returns {@code true}, or within a context where being a {@code Right} is
   * guaranteed. Otherwise, prefer using {@link #fold(Function, Function)}, {@link #map(Function)},
   * or pattern matching to safely access the value.
   *
   * @return The value of type {@code R} if this is a {@link Right}.
   * @throws NoSuchElementException if this is a {@link Left}.
   */
  R getRight() throws NoSuchElementException;

  /**
   * Applies one of two functions depending on whether this instance is a {@link Left} or a {@link
   * Right}. This is a universal way to extract the value from an {@code Either} by handling both
   * cases explicitly.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.right(10);
   * String result = either.fold(
   * error -> "Error: " + error,
   * value -> "Success: " + value.toString()
   * );
   * // result will be "Success: 10"
   * }</pre>
   *
   * @param leftMapper The non-null function to apply if this is a {@link Left}. It accepts the
   *     {@code L} value and returns a {@code T}.
   * @param rightMapper The non-null function to apply if this is a {@link Right}. It accepts the
   *     {@code R} value and returns a {@code T}.
   * @param <T> The target type to which both paths will be mapped.
   * @return The result of applying the appropriate mapping function. The result is guaranteed to be
   *     non-null if both mappers produce non-null results.
   * @throws NullPointerException if either {@code leftMapper} or {@code rightMapper} is null.
   */
  default <T> T fold(
      Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
    Validation.function().requireFunction(leftMapper, "leftMapper", EITHER_CLASS, FOLD);
    Validation.function().requireFunction(rightMapper, "rightMapper", EITHER_CLASS, FOLD);

    return switch (this) {
      case Left<L, R>(var leftValue) -> leftMapper.apply(leftValue);
      case Right<L, R>(var rightValue) -> rightMapper.apply(rightValue);
    };
  }

  /**
   * If this is a {@link Right}, applies the given mapping function to its value, returning a new
   * {@code Either.Right} containing the result. If this is a {@link Left}, it returns the original
   * {@link Left} instance unchanged.
   *
   * <p>This operation is "right-biased".
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> right = Either.right(5);
   * Either<String, String> mappedRight = right.map(i -> "Value: " + i); // Right("Value: 5")
   *
   * Either<String, Integer> left = Either.left("Error");
   * Either<String, String> mappedLeft = left.map(i -> "Value: " + i); // Left("Error")
   * }</pre>
   *
   * @param mapper The non-null function to apply to the {@link Right} value.
   * @param <R2> The type of the value in the resulting {@link Right} if this is a {@link Right}.
   * @return A new {@code Either<L, R2>} resulting from applying the mapper if this is a {@link
   *     Right}, or the original {@link Left} instance cast to {@code Either<L, R2>}. The returned
   *     {@code Either} will be non-null.
   * @throws NullPointerException if {@code mapper} is null.
   */
  @SuppressWarnings("unchecked")
  default <R2> Either<L, R2> map(Function<? super R, ? extends R2> mapper) {
    Validation.function().requireMapper(mapper, "mapper", EITHER_CLASS, MAP);
    return switch (this) {
      case Left<L, R> l -> (Either<L, R2>) l; // Return self, cast is safe.
      case Right<L, R>(var rValue) -> Either.right(mapper.apply(rValue)); // Create new Right
    };
  }

  /**
   * Transforms both the {@link Left} and {@link Right} values of this {@code Either} using the
   * provided mapping functions, producing a new {@code Either} with potentially different types for
   * both parameters.
   *
   * <p>This is the fundamental bifunctor operation for {@code Either}, allowing simultaneous
   * transformation of both the error channel (left) and success channel (right). Exactly one of the
   * two functions will be applied, depending on whether this is a {@link Left} or {@link Right}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> success = Either.right(42);
   * Either<Exception, String> result1 = success.bimap(
   *     Exception::new,           // Transform left (error) - not applied
   *     n -> "Value: " + n        // Transform right (success) - applied
   * );
   * // result1 = Right("Value: 42")
   *
   * Either<String, Integer> failure = Either.left("not found");
   * Either<Exception, String> result2 = failure.bimap(
   *     Exception::new,           // Transform left (error) - applied
   *     n -> "Value: " + n        // Transform right (success) - not applied
   * );
   * // result2 = Left(new Exception("not found"))
   * }</pre>
   *
   * @param leftMapper The non-null function to apply to the {@link Left} value if this is a {@link
   *     Left}.
   * @param rightMapper The non-null function to apply to the {@link Right} value if this is a
   *     {@link Right}.
   * @param <L2> The type of the {@link Left} value in the resulting {@code Either}.
   * @param <R2> The type of the {@link Right} value in the resulting {@code Either}.
   * @return A new {@code Either<L2, R2>} with one of its values transformed according to the
   *     appropriate mapper. The returned {@code Either} will be non-null.
   * @throws NullPointerException if either {@code leftMapper} or {@code rightMapper} is null.
   */
  default <L2, R2> Either<L2, R2> bimap(
      Function<? super L, ? extends L2> leftMapper,
      Function<? super R, ? extends R2> rightMapper) {
    Validation.function().requireMapper(leftMapper, "leftMapper", EITHER_CLASS, BIMAP);
    Validation.function().requireMapper(rightMapper, "rightMapper", EITHER_CLASS, BIMAP);

    return switch (this) {
      case Left<L, R>(var leftValue) -> Either.left(leftMapper.apply(leftValue));
      case Right<L, R>(var rightValue) -> Either.right(rightMapper.apply(rightValue));
    };
  }

  /**
   * Transforms only the {@link Left} value of this {@code Either}, leaving the {@link Right} value
   * unchanged if present.
   *
   * <p>This operation allows you to transform the error channel whilst preserving the success
   * channel. It is useful for converting error types, enriching error messages, or mapping between
   * different error representations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> failure = Either.left("invalid input");
   * Either<Exception, Integer> result1 = failure.mapLeft(Exception::new);
   * // result1 = Left(new Exception("invalid input"))
   *
   * Either<String, Integer> success = Either.right(42);
   * Either<Exception, Integer> result2 = success.mapLeft(Exception::new);
   * // result2 = Right(42) - right value unchanged
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@code bimap(leftMapper, Function.identity())}.
   *
   * @param leftMapper The non-null function to apply to the {@link Left} value if this is a {@link
   *     Left}.
   * @param <L2> The type of the {@link Left} value in the resulting {@code Either}.
   * @return A new {@code Either<L2, R>} with the left value transformed if this was a {@link Left},
   *     or the original {@link Right} value unchanged. The returned {@code Either} will be
   *     non-null.
   * @throws NullPointerException if {@code leftMapper} is null.
   */
  @SuppressWarnings("unchecked")
  default <L2> Either<L2, R> mapLeft(Function<? super L, ? extends L2> leftMapper) {
    Validation.function().requireMapper(leftMapper, "leftMapper", EITHER_CLASS, MAP_LEFT);

    return switch (this) {
      case Left<L, R>(var leftValue) -> Either.left(leftMapper.apply(leftValue));
      case Right<L, R> r -> (Either<L2, R>) r; // Right remains unchanged, cast is safe
    };
  }

  /**
   * Transforms only the {@link Right} value of this {@code Either}, leaving the {@link Left} value
   * unchanged if present.
   *
   * <p>This operation is functionally identical to {@link #map(Function)} but is provided for
   * symmetry with {@link #mapLeft(Function)} and to make bifunctor operations explicit. It
   * transforms the success channel whilst preserving the error channel.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> success = Either.right(42);
   * Either<String, String> result1 = success.mapRight(n -> "Value: " + n);
   * // result1 = Right("Value: 42")
   *
   * Either<String, Integer> failure = Either.left("error");
   * Either<String, String> result2 = failure.mapRight(n -> "Value: " + n);
   * // result2 = Left("error") - left value unchanged
   * }</pre>
   *
   * <p><b>Note:</b> This is equivalent to calling {@link #map(Function)} or {@code
   * bimap(Function.identity(), rightMapper)}.
   *
   * @param rightMapper The non-null function to apply to the {@link Right} value if this is a
   *     {@link Right}.
   * @param <R2> The type of the {@link Right} value in the resulting {@code Either}.
   * @return A new {@code Either<L, R2>} with the right value transformed if this was a {@link
   *     Right}, or the original {@link Left} value unchanged. The returned {@code Either} will be
   *     non-null.
   * @throws NullPointerException if {@code rightMapper} is null.
   * @see #map(Function)
   */
  @SuppressWarnings("unchecked")
  default <R2> Either<L, R2> mapRight(Function<? super R, ? extends R2> rightMapper) {
    Validation.function().requireMapper(rightMapper, "rightMapper", EITHER_CLASS, MAP_RIGHT);

    return switch (this) {
      case Left<L, R> l -> (Either<L, R2>) l; // Left remains unchanged, cast is safe
      case Right<L, R>(var rightValue) -> Either.right(rightMapper.apply(rightValue));
    };
  }

  /**
   * If this is a {@link Right}, applies the given {@code Either}-bearing function to its value and
   * returns the result. If this is a {@link Left}, it returns the original {@link Left} instance
   * unchanged.
   *
   * <p>This operation is "right-biased" and is fundamental for monadic sequencing. It allows
   * chaining operations that each return an {@code Either}, propagating {@link Left} values
   * automatically.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Function<Integer, Either<String, Double>> half =
   * val -> val % 2 == 0 ? Either.right(val / 2.0) : Either.left("Cannot half odd number");
   *
   * Either<String, Integer> r1 = Either.right(10);
   * Either<String, Double> result1 = r1.flatMap(half); // Right(5.0)
   *
   * Either<String, Integer> r2 = Either.right(5);
   * Either<String, Double> result2 = r2.flatMap(half); // Left("Cannot half odd number")
   *
   * Either<String, Integer> l = Either.left("Initial error");
   * Either<String, Double> result3 = l.flatMap(half); // Left("Initial error")
   * }</pre>
   *
   * @param mapper The non-null function to apply to the {@link Right} value. This function must
   *     return an {@code Either<L, ? extends R2>}.
   * @param <R2> The type of the {@link Right} value in the {@code Either} returned by the mapper.
   * @return The {@code Either<L, R2>} result from applying {@code mapper} if this is a {@link
   *     Right}, or the original {@link Left} instance cast to {@code Either<L, R2>}. The returned
   *     {@code Either} is guaranteed to be non-null if the mapper produces non-null Eithers.
   * @throws NullPointerException if {@code mapper} is null, or if {@code mapper} returns null when
   *     applied (the implementation in {@code Right#flatMap} checks this).
   */
  <R2> Either<L, R2> flatMap(Function<? super R, ? extends Either<L, ? extends R2>> mapper);

  /**
   * Performs the given action on the value if this is a {@link Left}. No action is performed if
   * this is a {@link Right}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either.left("Error details").ifLeft(System.err::println);
   * Either.right(42).ifLeft(error -> System.err.println("Unexpected error: " + error)); // No output
   * }</pre>
   *
   * @param action The non-null {@link Consumer} to execute with the {@link Left} value.
   * @throws NullPointerException if {@code action} is null (checked by implementations).
   */
  void ifLeft(Consumer<? super L> action);

  /**
   * Performs the given action on the value if this is a {@link Right}. No action is performed if
   * this is a {@link Left}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either.right(100).ifRight(value -> System.out.println("Got value: " + value));
   * Either.left("Error").ifRight(System.out::println); // No output
   * }</pre>
   *
   * @param action The non-null {@link Consumer} to execute with the {@link Right} value.
   * @throws NullPointerException if {@code action} is null (checked by implementations).
   */
  void ifRight(Consumer<? super R> action);

  // --- Static Factory Methods ---

  /**
   * Creates an {@code Either} instance representing the {@link Left} case. The value for {@code
   * Left} can be null, aligning with common conventions where a left value might represent an error
   * state that doesn't carry detailed information, or where {@code null} itself is a valid
   * representation for the left type.
   *
   * @param value The value for the {@link Left} case. Can be {@code null}.
   * @param <L> The type of the {@link Left} value.
   * @param <R> The type of the (absent) {@link Right} value.
   * @return A new non-null {@link Left} instance containing the given value.
   */
  static <L, R> Either<L, R> left(@Nullable L value) {
    return new Left<>(value);
  }

  /**
   * Creates an {@code Either} instance representing the {@link Right} case. The value for {@code
   * Right} can be {@code null} if {@code R} is a nullable type. If non-null values are strictly
   * desired for the {@code Right} case, callers should ensure this or use wrapper types like {@link
   * java.util.Optional} for {@code R}.
   *
   * @param value The value for the {@link Right} case. Can be {@code null}.
   * @param <L> The type of the (absent) {@link Left} value.
   * @param <R> The type of the {@link Right} value.
   * @return A new non-null {@link Right} instance containing the given value.
   */
  static <L, R> Either<L, R> right(@Nullable R value) {
    return new Right<>(value);
  }

  /**
   * Represents the {@link Left} case of an {@link Either}. By convention, this holds an error or
   * alternative value. This is a {@link Record} for conciseness and immutability.
   *
   * @param <L> The type of the value held.
   * @param <R> The type of the {@link Right} value (phantom type for {@code Left}).
   * @param value The value of type {@code L}. Can be {@code null}.
   */
  record Left<L, R>(@Nullable L value) implements Either<L, R> {

    @Override
    public boolean isLeft() {
      return true;
    }

    @Override
    public boolean isRight() {
      return false;
    }

    @Override
    public @Nullable L getLeft() {
      return value;
    }

    @Override
    public R getRight() {
      throw new NoSuchElementException("Cannot invoke getRight() on a Left instance.");
    }

    // flatMap is overridden from default for clarity, it does nothing on Left.
    @Override
    @SuppressWarnings("unchecked")
    public <R2> Either<L, R2> flatMap(
        Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
      Validation.function().requireFlatMapper(mapper, "mapper", Either.class, FLAT_MAP);
      return (Either<L, R2>) this; // Left remains Left, type L is unchanged.
    }

    @Override
    public void ifLeft(Consumer<? super L> action) {
      Validation.function().requireFunction(action, "action", Either.class, IF_LEFT);
      action.accept(value);
    }

    @Override
    public void ifRight(Consumer<? super R> action) {
      Validation.function().requireFunction(action, "action", EITHER_CLASS, IF_RIGHT);
    }

    /**
     * Returns a string representation of this {@link Left} instance. Example: {@code
     * Left(ErrorDetails)}
     *
     * @return A string representation.
     */
    @Override
    public String toString() {
      return "Left(" + value + ")";
    }
  }

  /**
   * Represents the {@link Right} case of an {@link Either}. By convention, this holds the
   * successful or primary value. This is a {@link Record} for conciseness and immutability.
   *
   * @param <L> The type of the {@link Left} value (phantom type for {@code Right}).
   * @param <R> The type of the value held.
   * @param value The value of type {@code R}. Can be {@code null}.
   */
  record Right<L, R>(@Nullable R value) implements Either<L, R> {
    @Override
    public boolean isLeft() {
      return false;
    }

    @Override
    public boolean isRight() {
      return true;
    }

    @Override
    public L getLeft() {
      throw new NoSuchElementException("Cannot invoke getLeft() on a Right instance.");
    }

    @Override
    public @Nullable R getRight() {
      return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R2> Either<L, R2> flatMap(
        Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
      Validation.function().requireFlatMapper(mapper, "mapper", Either.class, FLAT_MAP);
      // Apply the mapper, which itself returns an Either.
      Either<L, ? extends R2> result = mapper.apply(value);
      Validation.function()
          .requireNonNullResult(result, "mapper", EITHER_CLASS, FLAT_MAP, EITHER_CLASS);
      // Cast is safe because ? extends R2 is compatible with R2
      return (Either<L, R2>) result;
    }

    @Override
    public void ifLeft(Consumer<? super L> action) {
      Validation.function().requireFunction(action, "action", Either.class, IF_LEFT);
    }

    @Override
    public void ifRight(Consumer<? super R> action) {
      Validation.function().requireFunction(action, "action", Either.class, IF_RIGHT);
      action.accept(value);
    }

    /**
     * Returns a string representation of this {@link Right} instance. Example: {@code Right(42)}
     *
     * @return A string representation.
     */
    @Override
    public String toString() {
      return "Right(" + value + ")";
    }
  }
}
