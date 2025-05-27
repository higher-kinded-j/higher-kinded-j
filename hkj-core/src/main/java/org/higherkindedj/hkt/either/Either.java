// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable; // Assuming Right can hold null, Left typically can.

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
 * try {
 * return Either.right(Integer.parseInt(s));
 * } catch (NumberFormatException e) {
 * return Either.left("Invalid number format: " + s);
 * }
 * }
 *
 * Either<String, Integer> result = parseInteger("123");
 * result.fold(
 * error -> System.out.println("Error: " + error),
 * value -> System.out.println("Parsed value: " + value)
 * );
 *
 * Either<String, Integer> length = result.map(v -> v * 2); // Only maps if result was Right
 * }</pre>
 *
 * @param <L> The type of the value if this is a {@link Left}. Conventionally the error type.
 * @param <R> The type of the value if this is a {@link Right}. Conventionally the success type.
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

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
  L getLeft() throws NoSuchElementException;

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
      @NonNull Function<? super L, ? extends T> leftMapper,
      @NonNull Function<? super R, ? extends T> rightMapper) {
    Objects.requireNonNull(leftMapper, "leftMapper cannot be null");
    Objects.requireNonNull(rightMapper, "rightMapper cannot be null");

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
  default <R2> @NonNull Either<L, R2> map(@NonNull Function<? super R, ? extends R2> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    return switch (this) {
      case Left<L, R> l -> (Either<L, R2>) l; // Return self, cast is safe.
      case Right<L, R>(var rValue) -> Either.right(mapper.apply(rValue)); // Create new Right
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
  default <R2> Either<L, R2> flatMap(
      @NonNull Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    // This default implementation is only for the interface.
    // The actual logic is in Left.flatMap (which does nothing) and Right.flatMap.
    // For Left, this cast is safe as L does not change and R becomes R2.
    @SuppressWarnings("unchecked")
    Either<L, R2> self = (Either<L, R2>) this;
    return self; // Default for Left; Right overrides this.
  }

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
  void ifLeft(@NonNull Consumer<? super L> action);

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
  void ifRight(@NonNull Consumer<? super R> action);

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
  static <L, R> @NonNull Either<L, R> left(@Nullable L value) {
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
  static <L, R> @NonNull Either<L, R> right(@Nullable R value) {
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
    public <R2> @NonNull Either<L, R2> flatMap(
        @NonNull Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
      Objects.requireNonNull(mapper, "mapper function cannot be null");
      return (Either<L, R2>) this; // Left remains Left, type L is unchanged.
    }

    @Override
    public void ifLeft(@NonNull Consumer<? super L> action) {
      Objects.requireNonNull(action, "action cannot be null");
      action.accept(value);
    }

    @Override
    public void ifRight(@NonNull Consumer<? super R> action) {
      Objects.requireNonNull(action, "action cannot be null");
    }

    /**
     * Returns a string representation of this {@link Left} instance. Example: {@code
     * Left(ErrorDetails)}
     *
     * @return A string representation.
     */
    @Override
    public @NonNull String toString() {
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
    public <R2> @NonNull Either<L, R2> flatMap(
        @NonNull Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
      Objects.requireNonNull(mapper, "mapper function cannot be null");
      // Apply the mapper, which itself returns an Either.
      Either<L, ? extends R2> result = mapper.apply(value);
      Objects.requireNonNull(
          result, "flatMap mapper returned a null Either instance, which is not allowed.");
      // The cast is necessary because mapper returns <? extends R2>
      // but the method signature requires <R2>.
      @SuppressWarnings("unchecked")
      Either<L, R2> typedResult = (Either<L, R2>) result;
      return typedResult;
    }

    @Override
    public void ifLeft(@NonNull Consumer<? super L> action) {
      Objects.requireNonNull(action, "action cannot be null");
    }

    @Override
    public void ifRight(@NonNull Consumer<? super R> action) {
      Objects.requireNonNull(action, "action cannot be null");
      action.accept(value);
    }

    /**
     * Returns a string representation of this {@link Right} instance. Example: {@code Right(42)}
     *
     * @return A string representation.
     */
    @Override
    public @NonNull String toString() {
      return "Right(" + value + ")";
    }
  }
}
