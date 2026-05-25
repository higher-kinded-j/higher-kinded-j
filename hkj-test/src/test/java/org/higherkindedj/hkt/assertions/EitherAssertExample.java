// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.EitherAssert}. */
@DisplayName("EitherAssert showcase")
class EitherAssertExample {

  @Test
  @DisplayName("Right value is asserted with isRight() and hasRight()")
  void rightValue() {
    Either<String, Integer> result = Either.right(42);

    assertThatEither(result).isRight().hasRight(42);
  }

  @Test
  @DisplayName("Left value is asserted with isLeft() and hasLeft()")
  void leftValue() {
    Either<String, Integer> result = Either.left("not found");

    assertThatEither(result).isLeft().hasLeft("not found");
  }

  @Test
  @DisplayName("Use hasRightSatisfying() for richer checks on the success value")
  void rightSatisfying() {
    Either<String, String> result = Either.right("hello@example.com");

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            email -> {
              // Standard AssertJ inside the consumer.
              Assertions.assertThat(email).contains("@");
            });
  }

  @Test
  @DisplayName("hasLeftSatisfying() checks the error without unwrapping by hand")
  void leftSatisfying() {
    Either<RuntimeException, Integer> result = Either.left(new IllegalStateException("boom"));

    assertThatEither(result)
        .isLeft()
        .hasLeftSatisfying(
            error ->
                Assertions.assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom"));
  }

  @Test
  @DisplayName("Accepts Kind<EitherKind.Witness<L>, R> directly without manual narrowing")
  void acceptsKindDirectly() {
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(99));

    assertThatEither(kind).isRight().hasRight(99);
  }
}
