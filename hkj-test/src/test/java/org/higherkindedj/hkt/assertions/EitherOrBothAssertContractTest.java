// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link EitherOrBothAssert}. See {@link AssertContract}. */
@DisplayName("EitherOrBothAssert contract")
class EitherOrBothAssertContractTest
    extends AssertContract<EitherOrBoth<String, Integer>, EitherOrBothAssert<String, Integer>> {

  private static final EitherOrBoth<String, Integer> LEFT = EitherOrBoth.left("e");
  private static final EitherOrBoth<String, Integer> RIGHT = EitherOrBoth.right(5);
  private static final EitherOrBoth<String, Integer> BOTH = EitherOrBoth.both("w", 5);
  private static final EitherOrBoth<String, Integer> LEFT_X = EitherOrBoth.left("x");
  private static final EitherOrBoth<String, Integer> RIGHT_9 = EitherOrBoth.right(9);
  private static final EitherOrBoth<String, Integer> BOTH_9 = EitherOrBoth.both("w", 9);

  @Override
  protected Function<EitherOrBoth<String, Integer>, EitherOrBothAssert<String, Integer>> entry() {
    return EitherOrBothAssert::assertThatEitherOrBoth;
  }

  @Override
  protected Stream<Row<EitherOrBoth<String, Integer>, EitherOrBothAssert<String, Integer>>> rows() {
    return Stream.of(
        row("isLeft", LEFT, RIGHT, EitherOrBothAssert::isLeft),
        row("isRight", RIGHT, LEFT, EitherOrBothAssert::isRight),
        row("isBoth", BOTH, RIGHT, EitherOrBothAssert::isBoth),
        row("hasLeft matches", LEFT, RIGHT, a -> a.hasLeft("e")),
        row("hasLeft value mismatch", LEFT, LEFT_X, a -> a.hasLeft("e")),
        row("hasRight matches", RIGHT, LEFT, a -> a.hasRight(5)),
        row("hasRight value mismatch", RIGHT, RIGHT_9, a -> a.hasRight(5)),
        row("hasBoth matches", BOTH, RIGHT, a -> a.hasBoth("w", 5)),
        row("hasBoth value mismatch", BOTH, BOTH_9, a -> a.hasBoth("w", 5)),
        row("hasLeftSatisfying", LEFT, RIGHT, a -> a.hasLeftSatisfying(l -> {})),
        failOnly(
            "hasLeftSatisfying inner failure",
            LEFT,
            a ->
                a.hasLeftSatisfying(
                    l -> {
                      throw new AssertionError("inner");
                    })),
        row("hasRightSatisfying", RIGHT, LEFT, a -> a.hasRightSatisfying(r -> {})),
        failOnly(
            "hasRightSatisfying inner failure",
            RIGHT,
            a ->
                a.hasRightSatisfying(
                    r -> {
                      throw new AssertionError("inner");
                    })),
        row("hasBothSatisfying", BOTH, RIGHT, a -> a.hasBothSatisfying((l, r) -> {})),
        failOnly(
            "hasBothSatisfying inner failure",
            BOTH,
            a ->
                a.hasBothSatisfying(
                    (l, r) -> {
                      throw new AssertionError("inner");
                    })));
  }

  @Test
  void kind_entry_point_narrows_and_asserts() {
    EitherOrBothAssert.assertThatEitherOrBoth(EITHER_OR_BOTH.widen(BOTH)).isBoth().hasBoth("w", 5);
  }

  @Test
  void satisfies_propagates_inner_assertion_failure() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                EitherOrBothAssert.assertThatEitherOrBoth(RIGHT)
                    .hasRightSatisfying(
                        r -> {
                          throw new AssertionError("inner");
                        }));
  }
}
