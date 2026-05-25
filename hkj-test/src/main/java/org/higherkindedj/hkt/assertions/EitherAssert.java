// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;

/**
 * Custom AssertJ assertions for {@link Either} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods for testing {@code Either} values in their {@code Kind}
 * representation, narrowing internally so callers don't have to. A bare {@code Either} can also be
 * passed via the {@link #assertThatEither(Either)} overload.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
 *
 * Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(42));
 * assertThatEither(kind).isRight().hasRight(42);
 *
 * Either<String, Integer> left = Either.left("error");
 * assertThatEither(left).isLeft().hasLeft("error");
 * }</pre>
 *
 * @param <L> The type of the Left value
 * @param <R> The type of the Right value
 */
public class EitherAssert<L, R>
    extends AbstractAssert<EitherAssert<L, R>, Kind<EitherKind.Witness<L>, R>> {

  /** Entry point accepting a {@code Kind<EitherKind.Witness<L>, R>}. */
  public static <L, R> EitherAssert<L, R> assertThatEither(Kind<EitherKind.Witness<L>, R> actual) {
    return new EitherAssert<>(actual);
  }

  /**
   * Entry point accepting a bare {@code Either<L, R>}. Most-specific overload — preserves source
   * compatibility for callers passing an unwrapped {@code Either}.
   */
  public static <L, R> EitherAssert<L, R> assertThatEither(Either<L, R> actual) {
    return new EitherAssert<>(EITHER.widen(actual));
  }

  protected EitherAssert(Kind<EitherKind.Witness<L>, R> actual) {
    super(actual, EitherAssert.class);
  }

  /** Verifies the actual {@code Either} is a Right. */
  public EitherAssert<L, R> isRight() {
    isNotNull();
    Either<L, R> e = EITHER.narrow(actual);
    Assertions.assertThat(e.isRight())
        .withFailMessage(
            () -> "Expected Either to be Right but was Left with value: <" + e.getLeft() + ">")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code Either} is a Left. */
  public EitherAssert<L, R> isLeft() {
    isNotNull();
    Either<L, R> e = EITHER.narrow(actual);
    Assertions.assertThat(e.isLeft())
        .withFailMessage(
            () -> "Expected Either to be Left but was Right with value: <" + e.getRight() + ">")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code Either} is a Right and contains {@code expected}. */
  public EitherAssert<L, R> hasRight(R expected) {
    isRight();
    R actualRight = EITHER.narrow(actual).getRight();
    Assertions.assertThat(actualRight)
        .withFailMessage(
            "Expected Either.Right to contain <%s> but contained <%s>", expected, actualRight)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies the actual {@code Either} is a Left and contains {@code expected}. */
  public EitherAssert<L, R> hasLeft(L expected) {
    isLeft();
    L actualLeft = EITHER.narrow(actual).getLeft();
    Assertions.assertThat(actualLeft)
        .withFailMessage(
            "Expected Either.Left to contain <%s> but contained <%s>", expected, actualLeft)
        .isEqualTo(expected);
    return this;
  }

  /**
   * Verifies the actual {@code Either} is a Right and the contained value satisfies {@code
   * requirements}.
   */
  public EitherAssert<L, R> hasRightSatisfying(Consumer<? super R> requirements) {
    isRight();
    requirements.accept(EITHER.narrow(actual).getRight());
    return this;
  }

  /**
   * Verifies the actual {@code Either} is a Left and the contained value satisfies {@code
   * requirements}.
   */
  public EitherAssert<L, R> hasLeftSatisfying(Consumer<? super L> requirements) {
    isLeft();
    requirements.accept(EITHER.narrow(actual).getLeft());
    return this;
  }

  /** Verifies the actual {@code Either} is a Right containing null. */
  public EitherAssert<L, R> hasRightNull() {
    isRight();
    Assertions.assertThat(EITHER.narrow(actual).getRight()).as("Either.Right value").isNull();
    return this;
  }

  /** Verifies the actual {@code Either} is a Left containing null. */
  public EitherAssert<L, R> hasLeftNull() {
    isLeft();
    Assertions.assertThat(EITHER.narrow(actual).getLeft()).as("Either.Left value").isNull();
    return this;
  }

  /** Verifies the actual {@code Either} is a Right containing a non-null value. */
  public EitherAssert<L, R> hasRightNonNull() {
    isRight();
    Assertions.assertThat(EITHER.narrow(actual).getRight()).as("Either.Right value").isNotNull();
    return this;
  }

  /** Verifies the actual {@code Either} is a Left containing a non-null value. */
  public EitherAssert<L, R> hasLeftNonNull() {
    isLeft();
    Assertions.assertThat(EITHER.narrow(actual).getLeft()).as("Either.Left value").isNotNull();
    return this;
  }
}
