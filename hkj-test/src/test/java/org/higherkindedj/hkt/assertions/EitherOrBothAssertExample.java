// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.EitherOrBothAssert}. */
@DisplayName("EitherOrBothAssert showcase")
class EitherOrBothAssertExample {

  @Test
  @DisplayName("Left value is asserted with isLeft() and hasLeft()")
  void leftValue() {
    EitherOrBoth<String, Integer> result = EitherOrBoth.left("not found");

    assertThatEitherOrBoth(result).isLeft().hasLeft("not found");
  }

  @Test
  @DisplayName("Right value is asserted with isRight() and hasRight()")
  void rightValue() {
    EitherOrBoth<String, Integer> result = EitherOrBoth.right(42);

    assertThatEitherOrBoth(result).isRight().hasRight(42);
  }

  @Test
  @DisplayName("Both is asserted with isBoth() and hasBoth(), the shape Either cannot express")
  void bothValue() {
    EitherOrBoth<String, Integer> result = EitherOrBoth.both("deprecated key", 42);

    assertThatEitherOrBoth(result).isBoth().hasBoth("deprecated key", 42);
  }

  @Test
  @DisplayName("hasLeftSatisfying() checks the error without unwrapping by hand")
  void leftSatisfying() {
    EitherOrBoth<String, Integer> result = EitherOrBoth.left("missing field");

    assertThatEitherOrBoth(result)
        .isLeft()
        .hasLeftSatisfying(error -> Assertions.assertThat(error).contains("field"));
  }

  @Test
  @DisplayName("Use hasRightSatisfying() for richer checks on the success value")
  void rightSatisfying() {
    EitherOrBoth<String, String> result = EitherOrBoth.right("hello@example.com");

    assertThatEitherOrBoth(result)
        .isRight()
        .hasRightSatisfying(
            email -> {
              // Standard AssertJ inside the consumer.
              Assertions.assertThat(email).contains("@");
            });
  }

  @Test
  @DisplayName("hasBothSatisfying() checks the warning and the success value together")
  void bothSatisfying() {
    record Order(String id) {}

    EitherOrBoth<String, Order> warned = EitherOrBoth.both("deprecated key", new Order("o1"));

    assertThatEitherOrBoth(warned)
        .hasBothSatisfying(
            (warning, order) -> {
              Assertions.assertThat(warning).contains("deprecated");
              Assertions.assertThat(order.id()).isEqualTo("o1");
            });
  }

  @Test
  @DisplayName("Accepts Kind<EitherOrBothKind.Witness<L>, R> directly without manual narrowing")
  void acceptsKindDirectly() {
    Kind<EitherOrBothKind.Witness<String>, Integer> kind =
        EITHER_OR_BOTH.widen(EitherOrBoth.both("warn", 99));

    assertThatEitherOrBoth(kind).isBoth().hasBoth("warn", 99);
  }
}
