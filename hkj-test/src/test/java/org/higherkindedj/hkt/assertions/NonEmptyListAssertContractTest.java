// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link NonEmptyListAssert}. See {@link AssertContract}. */
@DisplayName("NonEmptyListAssert contract")
class NonEmptyListAssertContractTest
    extends AssertContract<NonEmptyList<Integer>, NonEmptyListAssert<Integer>> {

  private static final NonEmptyList<Integer> ONE = NonEmptyList.single(1);
  private static final NonEmptyList<Integer> ONE_TWO_THREE = NonEmptyList.of(1, 2, 3);
  private static final NonEmptyList<Integer> ONE_TWO_FOUR = NonEmptyList.of(1, 2, 4);
  private static final NonEmptyList<Integer> NINE_EIGHT_SEVEN = NonEmptyList.of(9, 8, 7);

  @Override
  protected Function<NonEmptyList<Integer>, NonEmptyListAssert<Integer>> entry() {
    return NonEmptyListAssert::assertThatNonEmptyList;
  }

  @Override
  protected Stream<Row<NonEmptyList<Integer>, NonEmptyListAssert<Integer>>> rows() {
    return Stream.of(
        passOnly("isNotEmpty (invariant)", ONE_TWO_THREE, NonEmptyListAssert::isNotEmpty),
        row("hasHead matches", ONE_TWO_THREE, NINE_EIGHT_SEVEN, a -> a.hasHead(1)),
        row("hasLast matches", ONE_TWO_THREE, ONE_TWO_FOUR, a -> a.hasLast(3)),
        row("hasSize matches", ONE_TWO_THREE, ONE, a -> a.hasSize(3)),
        row("containsExactly matches", ONE_TWO_THREE, ONE, a -> a.containsExactly(1, 2, 3)),
        row(
            "containsExactly element mismatch",
            ONE_TWO_THREE,
            ONE_TWO_FOUR,
            a -> a.containsExactly(1, 2, 3)),
        row("contains single", ONE_TWO_THREE, NINE_EIGHT_SEVEN, a -> a.contains(2)),
        row(
            "contains varargs",
            ONE_TWO_THREE,
            NINE_EIGHT_SEVEN,
            a -> a.contains(Integer.valueOf(1), Integer.valueOf(3))),
        row("containsOnly matches", ONE_TWO_THREE, ONE_TWO_FOUR, a -> a.containsOnly(1, 2, 3)),
        failOnly(
            "containsOnly missing expected",
            ONE,
            a -> a.containsOnly(Integer.valueOf(1), Integer.valueOf(2))),
        row("doesNotContain", ONE, ONE_TWO_THREE, a -> a.doesNotContain(2)),
        row("allMatch", ONE_TWO_THREE, ONE_TWO_FOUR, a -> a.allMatch(n -> n < 4)),
        row("anyMatch", ONE_TWO_THREE, ONE, a -> a.anyMatch(n -> n > 2)),
        passOnly("satisfies", ONE_TWO_THREE, a -> a.satisfies(nel -> {})));
  }

  @Test
  void kind_entry_point_narrows_and_asserts() {
    NonEmptyListAssert.assertThatNonEmptyList(NON_EMPTY_LIST.widen(ONE_TWO_THREE))
        .hasSize(3)
        .hasHead(1)
        .hasLast(3);
  }

  @Test
  void satisfies_propagates_inner_assertion_failure() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                NonEmptyListAssert.assertThatNonEmptyList(ONE_TWO_THREE)
                    .satisfies(
                        nel -> {
                          throw new AssertionError("inner");
                        }));
  }
}
