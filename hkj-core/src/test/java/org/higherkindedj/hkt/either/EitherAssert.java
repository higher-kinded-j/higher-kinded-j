// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link Either} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Either} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
 *
 * Either<String, Integer> right = Either.right(42);
 * assertThat(right)
 *     .isRight()
 *     .hasRight(42);
 *
 * Either<String, Integer> left = Either.left("error");
 * assertThat(left)
 *     .isLeft()
 *     .hasLeft("error");
 *
 * // Chaining with null-safe assertions
 * assertThat(right)
 *     .isRight()
 *     .hasRightSatisfying(value -> {
 *         assertThat(value).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <L> The type of the Left value
 * @param <R> The type of the Right value
 */
public class EitherAssert<L, R> extends AbstractAssert<EitherAssert<L, R>, Either<L, R>> {

  /**
   * Creates a new {@code EitherAssert} instance.
   *
   * <p>This is the entry point for all Either assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
   * }</pre>
   *
   * @param <L> The type of the Left value
   * @param <R> The type of the Right value
   * @param actual The Either instance to make assertions on
   * @return A new EitherAssert instance
   */
  public static <L, R> EitherAssert<L, R> assertThatEither(Either<L, R> actual) {
    return new EitherAssert<>(actual);
  }

  protected EitherAssert(Either<L, R> actual) {
    super(actual, EitherAssert.class);
  }

  /**
   * Verifies that the actual {@code Either} is a {@link Either.Right} instance.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.right(42);
   * assertThat(either).isRight();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Either is null or is a Left
   */
  public EitherAssert<L, R> isRight() {
    isNotNull();
    if (!actual.isRight()) {
      failWithMessage(
          "Expected Either to be Right but was Left with value: <%s>", actual.getLeft());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a {@link Either.Left} instance.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.left("error");
   * assertThat(either).isLeft();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Either is null or is a Right
   */
  public EitherAssert<L, R> isLeft() {
    isNotNull();
    if (!actual.isLeft()) {
      failWithMessage(
          "Expected Either to be Left but was Right with value: <%s>", actual.getRight());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Right and contains the expected value.
   *
   * <p>This method first verifies that the Either is a Right, then checks the contained value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.right(42);
   * assertThat(either).hasRight(42);
   * }</pre>
   *
   * @param expected The expected Right value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Left or the Right value doesn't match
   */
  public EitherAssert<L, R> hasRight(R expected) {
    isRight();
    R actualRight = actual.getRight();
    if (!Objects.equals(actualRight, expected)) {
      failWithMessage(
          "Expected Either.Right to contain <%s> but contained <%s>", expected, actualRight);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Left and contains the expected value.
   *
   * <p>This method first verifies that the Either is a Left, then checks the contained value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.left("error");
   * assertThat(either).hasLeft("error");
   * }</pre>
   *
   * @param expected The expected Left value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Right or the Left value doesn't match
   */
  public EitherAssert<L, R> hasLeft(L expected) {
    isLeft();
    L actualLeft = actual.getLeft();
    if (!Objects.equals(actualLeft, expected)) {
      failWithMessage(
          "Expected Either.Left to contain <%s> but contained <%s>", expected, actualLeft);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Right and the contained value satisfies the given
   * requirements.
   *
   * <p>This is useful for complex assertions on the Right value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.right(42);
   * assertThat(either).hasRightSatisfying(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the Right value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Left or the requirements are not satisfied
   */
  public EitherAssert<L, R> hasRightSatisfying(Consumer<? super R> requirements) {
    isRight();
    requirements.accept(actual.getRight());
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Left and the contained value satisfies the given
   * requirements.
   *
   * <p>This is useful for complex assertions on the Left value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.left("error message");
   * assertThat(either).hasLeftSatisfying(error -> {
   *     assertThat(error).startsWith("error");
   *     assertThat(error).contains("message");
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the Left value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Right or the requirements are not satisfied
   */
  public EitherAssert<L, R> hasLeftSatisfying(Consumer<? super L> requirements) {
    isLeft();
    requirements.accept(actual.getLeft());
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Right containing null.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.right(null);
   * assertThat(either).hasRightNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Left or the Right value is not null
   */
  public EitherAssert<L, R> hasRightNull() {
    isRight();
    if (actual.getRight() != null) {
      failWithMessage(
          "Expected Either.Right to contain null but contained <%s>", actual.getRight());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Left containing null.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.left(null);
   * assertThat(either).hasLeftNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Right or the Left value is not null
   */
  public EitherAssert<L, R> hasLeftNull() {
    isLeft();
    if (actual.getLeft() != null) {
      failWithMessage("Expected Either.Left to contain null but contained <%s>", actual.getLeft());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Right containing a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.right(42);
   * assertThat(either).hasRightNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Left or the Right value is null
   */
  public EitherAssert<L, R> hasRightNonNull() {
    isRight();
    if (actual.getRight() == null) {
      failWithMessage("Expected Either.Right to contain non-null value but was null");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Either} is a Left containing a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Either<String, Integer> either = Either.left("error");
   * assertThat(either).hasLeftNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Either is Right or the Left value is null
   */
  public EitherAssert<L, R> hasLeftNonNull() {
    isLeft();
    if (actual.getLeft() == null) {
      failWithMessage("Expected Either.Left to contain non-null value but was null");
    }
    return this;
  }
}
