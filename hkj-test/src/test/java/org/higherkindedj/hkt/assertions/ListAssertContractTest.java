// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link ListAssert}. See {@link AssertContract}. */
@DisplayName("ListAssert contract")
class ListAssertContractTest
    extends AssertContract<Kind<ListKind.Witness, Integer>, ListAssert<Integer>> {

  private static final Kind<ListKind.Witness, Integer> EMPTY = LIST.widen(List.of());
  private static final Kind<ListKind.Witness, Integer> ONE = LIST.widen(List.of(1));
  private static final Kind<ListKind.Witness, Integer> ONE_TWO_THREE = LIST.widen(List.of(1, 2, 3));
  private static final Kind<ListKind.Witness, Integer> ONE_TWO_FOUR = LIST.widen(List.of(1, 2, 4));
  private static final Kind<ListKind.Witness, Integer> NINE_EIGHT_SEVEN =
      LIST.widen(List.of(9, 8, 7));

  @Override
  protected Function<Kind<ListKind.Witness, Integer>, ListAssert<Integer>> entry() {
    return ListAssert::assertThatList;
  }

  @Override
  protected Stream<Row<Kind<ListKind.Witness, Integer>, ListAssert<Integer>>> rows() {
    return Stream.of(
        row("isEmpty", EMPTY, ONE, ListAssert::isEmpty),
        row("isNotEmpty", ONE, EMPTY, ListAssert::isNotEmpty),
        row("hasSize matches", ONE_TWO_THREE, ONE, a -> a.hasSize(3)),
        row("containsExactly matches", ONE_TWO_THREE, ONE, a -> a.containsExactly(1, 2, 3)),
        row(
            "containsExactly element mismatch",
            ONE_TWO_THREE,
            ONE_TWO_FOUR,
            a -> a.containsExactly(1, 2, 3)),
        row("contains single", ONE_TWO_THREE, EMPTY, a -> a.contains(2)),
        row(
            "contains varargs",
            ONE_TWO_THREE,
            EMPTY,
            a -> a.contains(Integer.valueOf(1), Integer.valueOf(3))),
        row("containsOnly matches", ONE_TWO_THREE, ONE_TWO_FOUR, a -> a.containsOnly(1, 2, 3)),
        failOnly(
            "containsOnly missing expected",
            ONE,
            a -> a.containsOnly(Integer.valueOf(1), Integer.valueOf(2))),
        row("doesNotContain", ONE, ONE_TWO_THREE, a -> a.doesNotContain(2)),
        row("allMatch", ONE_TWO_THREE, ONE_TWO_FOUR, a -> a.allMatch(n -> n < 4)),
        row("anyMatch", ONE_TWO_THREE, ONE, a -> a.anyMatch(n -> n > 2)),
        passOnly("satisfies", ONE_TWO_THREE, a -> a.satisfies(list -> {})),
        row("startsWith", ONE_TWO_THREE, NINE_EIGHT_SEVEN, a -> a.startsWith(1, 2)),
        failOnly("startsWith too short", ONE, a -> a.startsWith(1, 2, 3)),
        row("endsWith", ONE_TWO_THREE, NINE_EIGHT_SEVEN, a -> a.endsWith(2, 3)),
        failOnly("endsWith too short", ONE, a -> a.endsWith(1, 2, 3)));
  }

  @Test
  void satisfies_propagates_inner_assertion_failure() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(
            () ->
                ListAssert.assertThatList(ONE_TWO_THREE)
                    .satisfies(
                        list -> {
                          throw new AssertionError("inner");
                        }));
  }
}
