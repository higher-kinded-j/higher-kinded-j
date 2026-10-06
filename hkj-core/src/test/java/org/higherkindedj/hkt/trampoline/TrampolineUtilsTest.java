// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.assertions.ListAssert.assertThatList;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TrampolineUtils}.
 *
 * <p>Verifies that the utility methods provide stack-safe operations for large collections and many
 * applicative operations.
 */
@DisplayName("TrampolineUtils")
class TrampolineUtilsTest {

  @Nested
  @DisplayName("traverseListStackSafe")
  class TraverseListStackSafeTests {

    @Test
    @DisplayName("should traverse empty list to an unmodifiable empty list")
    void shouldTraverseEmptyList() {
      final List<Integer> emptyList = List.of();

      final Kind<IdKind.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              emptyList, i -> Id.of("item-" + i), Instances.monad(id()));

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).isUnmodifiable().isEmpty();
    }

    @Test
    @DisplayName("should traverse single element list")
    void shouldTraverseSingleElementList() {
      final List<Integer> singleList = List.of(42);

      final Kind<IdKind.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              singleList, i -> Id.of("item-" + i), Instances.monad(id()));

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly("item-42");
    }

    @Test
    @DisplayName("should traverse small list in order into an unmodifiable list")
    void shouldTraverseSmallList() {
      final List<Integer> smallList = List.of(1, 2, 3, 4, 5);

      final Kind<IdKind.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              smallList, i -> Id.of("item-" + i), Instances.monad(id()));

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped)
          .isUnmodifiable()
          .containsExactly("item-1", "item-2", "item-3", "item-4", "item-5");
    }

    @Test
    @DisplayName("should give every combination under the List applicative")
    void shouldGiveEveryCombinationUnderList() {
      final Kind<ListKind.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              List.of("a", "b"),
              s -> LIST.widen(List.of(s + "1", s + "2")),
              Instances.monad(list()));

      assertThatList(result)
          .containsExactly(
              List.of("a1", "b1"), List.of("a1", "b2"), List.of("a2", "b1"), List.of("a2", "b2"));
    }

    @Test
    @DisplayName("should maintain order during traversal")
    void shouldMaintainOrder() {
      final List<Integer> list = IntStream.range(0, 100).boxed().collect(Collectors.toList());

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(list, i -> Id.of(i * 2), Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(100);
      for (int i = 0; i < 100; i++) {
        assertThat(unwrapped.get(i)).isEqualTo(i * 2);
      }
    }

    @Test
    @DisplayName("should handle moderately large lists (10,000 elements)")
    void shouldHandleModeratelyLargeLists() {
      final List<Integer> largeList =
          IntStream.range(0, 10_000).boxed().collect(Collectors.toList());

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              largeList, i -> Id.of(i * 2), Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(10_000);
      assertThat(unwrapped.getFirst()).isEqualTo(0);
      assertThat(unwrapped.get(9_999)).isEqualTo(19_998);
    }

    @Test
    @DisplayName("should be stack-safe with very large lists (100,000 elements)")
    void shouldBeStackSafeWithVeryLargeLists() {
      final List<Integer> veryLargeList =
          IntStream.range(0, 100_000).boxed().collect(Collectors.toList());

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              veryLargeList, i -> Id.of(i + 1), Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(100_000);
      assertThat(unwrapped.getFirst()).isEqualTo(1);
      assertThat(unwrapped.get(99_999)).isEqualTo(100_000);
    }

    @Test
    @DisplayName("should be stack-safe under IO with very large lists (100,000 elements)")
    void shouldBeStackSafeUnderIO() {
      final List<Integer> veryLargeList = IntStream.range(0, 100_000).boxed().toList();

      final Kind<IOKind.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              veryLargeList, i -> IO_OP.widen(IO.delay(() -> i + 1)), Instances.monad(io()));

      assertThatIO(result)
          .hasValueSatisfying(
              values -> {
                assertThat(values).hasSize(100_000);
                assertThat(values.getFirst()).isEqualTo(1);
                assertThat(values.getLast()).isEqualTo(100_000);
              });
    }

    @Test
    @DisplayName("should apply the function to the elements in order")
    void shouldApplyFunctionInElementOrder() {
      final List<Integer> applied = new ArrayList<>();

      TrampolineUtils.traverseListStackSafe(
          List.of(0, 1, 2, 3, 4),
          i -> {
            applied.add(i);
            return Id.of(i);
          },
          Instances.monad(id()));

      assertThat(applied).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("should run the effects in element order")
    void shouldRunEffectsInElementOrder() {
      final List<Integer> ran = new ArrayList<>();

      final Kind<IOKind.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              List.of(0, 1, 2, 3, 4),
              i ->
                  IO_OP.widen(
                      IO.delay(
                          () -> {
                            ran.add(i);
                            return i;
                          })),
              Instances.monad(io()));

      assertThatIO(result).hasValue(List.of(0, 1, 2, 3, 4));
      assertThat(ran).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("should report the earliest failure under Either")
    void shouldReportEarliestFailureUnderEither() {
      final Kind<EitherKind.Witness<String>, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              List.of(1, -2, 3, -4, 5),
              i -> EITHER.widen(i > 0 ? Either.right(i) : Either.left("rejected " + i)),
              Instances.monadError(either()));

      assertThatEither(result).hasLeft("rejected -2");
    }

    @Test
    @DisplayName("should accumulate Validated errors in element order")
    void shouldAccumulateValidatedErrorsInElementOrder() {
      final Kind<ValidatedKind.Witness<List<String>>, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              List.of(1, -2, 3, -4, -5),
              i ->
                  VALIDATED.widen(
                      i > 0 ? Validated.valid(i) : Validated.invalid(List.of("rejected " + i))),
              Instances.validated(Semigroups.<String>list()));

      assertThatValidated(result).hasError(List.of("rejected -2", "rejected -4", "rejected -5"));
    }

    @Test
    @DisplayName("should apply function to each element")
    void shouldApplyFunctionToEachElement() {
      final List<Integer> list = List.of(1, 2, 3, 4, 5);

      final Kind<IdKind.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              list, i -> Id.of("element-" + (i * 10)), Instances.monad(id()));

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped)
          .containsExactly("element-10", "element-20", "element-30", "element-40", "element-50");
    }
  }

  @Nested
  @DisplayName("sequenceStackSafe")
  class SequenceStackSafeTests {

    @Test
    @DisplayName("should sequence empty list")
    void shouldSequenceEmptyList() {
      final List<Kind<IdKind.Witness, Integer>> emptyList = List.of();

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(emptyList, Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).isEmpty();
    }

    @Test
    @DisplayName("should sequence single element")
    void shouldSequenceSingleElement() {
      final List<Kind<IdKind.Witness, Integer>> singleList = List.of(Id.of(42));

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(singleList, Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly(42);
    }

    @Test
    @DisplayName("should sequence small list")
    void shouldSequenceSmallList() {
      final List<Kind<IdKind.Witness, Integer>> smallList =
          List.of(Id.of(1), Id.of(2), Id.of(3), Id.of(4), Id.of(5));

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(smallList, Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("should maintain order during sequencing")
    void shouldMaintainOrderDuringSequencing() {
      final List<Kind<IdKind.Witness, Integer>> list = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        list.add(Id.of(i));
      }

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(list, Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(100);
      for (int i = 0; i < 100; i++) {
        assertThat(unwrapped.get(i)).isEqualTo(i);
      }
    }

    @Test
    @DisplayName("should be stack-safe with large lists (50,000 elements)")
    void shouldBeStackSafeWithLargeLists() {
      final List<Kind<IdKind.Witness, Integer>> largeList = new ArrayList<>();
      for (int i = 0; i < 50_000; i++) {
        largeList.add(Id.of(i));
      }

      final Kind<IdKind.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(largeList, Instances.monad(id()));

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(50_000);
      assertThat(unwrapped.getFirst()).isEqualTo(0);
      assertThat(unwrapped.get(49_999)).isEqualTo(49_999);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("traverseListStackSafe should throw NPE when function encounters null")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void traverseShouldThrowNPEWhenFunctionEncountersNull() {
      final List<Integer> listWithNulls = new ArrayList<>();
      listWithNulls.add(1);
      listWithNulls.add(null);
      listWithNulls.add(3);

      // Function doesn't handle null, so will throw NPE when applied to null element
      assertThatThrownBy(
              () ->
                  TrampolineUtils.traverseListStackSafe(
                      listWithNulls,
                      i -> Id.of(i.toString()), // This will throw NPE when i is null
                      Instances.monad(id())))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
