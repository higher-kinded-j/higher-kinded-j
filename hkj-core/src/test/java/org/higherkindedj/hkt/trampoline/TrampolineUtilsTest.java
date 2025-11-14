// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
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
    @DisplayName("should traverse empty list")
    void shouldTraverseEmptyList() {
      final List<Integer> emptyList = List.of();

      final Kind<Id.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              emptyList, i -> Id.of("item-" + i), IdMonad.instance());

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).isEmpty();
    }

    @Test
    @DisplayName("should traverse single element list")
    void shouldTraverseSingleElementList() {
      final List<Integer> singleList = List.of(42);

      final Kind<Id.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              singleList, i -> Id.of("item-" + i), IdMonad.instance());

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly("item-42");
    }

    @Test
    @DisplayName("should traverse small list correctly")
    void shouldTraverseSmallList() {
      final List<Integer> smallList = List.of(1, 2, 3, 4, 5);

      final Kind<Id.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              smallList, i -> Id.of("item-" + i), IdMonad.instance());

      final List<String> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly("item-1", "item-2", "item-3", "item-4", "item-5");
    }

    @Test
    @DisplayName("should maintain order during traversal")
    void shouldMaintainOrder() {
      final List<Integer> list = IntStream.range(0, 100).boxed().collect(Collectors.toList());

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(list, i -> Id.of(i * 2), IdMonad.instance());

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

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(largeList, i -> Id.of(i * 2), IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(10_000);
      assertThat(unwrapped.get(0)).isEqualTo(0);
      assertThat(unwrapped.get(9_999)).isEqualTo(19_998);
    }

    @Test
    @DisplayName("should be stack-safe with very large lists (100,000 elements)")
    void shouldBeStackSafeWithVeryLargeLists() {
      final List<Integer> veryLargeList =
          IntStream.range(0, 100_000).boxed().collect(Collectors.toList());

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.traverseListStackSafe(
              veryLargeList, i -> Id.of(i + 1), IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(100_000);
      assertThat(unwrapped.get(0)).isEqualTo(1);
      assertThat(unwrapped.get(99_999)).isEqualTo(100_000);
    }

    @Test
    @DisplayName("should apply function to each element")
    void shouldApplyFunctionToEachElement() {
      final List<Integer> list = List.of(1, 2, 3, 4, 5);

      final Kind<Id.Witness, List<String>> result =
          TrampolineUtils.traverseListStackSafe(
              list, i -> Id.of("element-" + (i * 10)), IdMonad.instance());

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
      final List<Kind<Id.Witness, Integer>> emptyList = List.of();

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(emptyList, IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).isEmpty();
    }

    @Test
    @DisplayName("should sequence single element")
    void shouldSequenceSingleElement() {
      final List<Kind<Id.Witness, Integer>> singleList = List.of(Id.of(42));

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(singleList, IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly(42);
    }

    @Test
    @DisplayName("should sequence small list")
    void shouldSequenceSmallList() {
      final List<Kind<Id.Witness, Integer>> smallList =
          List.of(Id.of(1), Id.of(2), Id.of(3), Id.of(4), Id.of(5));

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(smallList, IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("should maintain order during sequencing")
    void shouldMaintainOrderDuringSequencing() {
      final List<Kind<Id.Witness, Integer>> list = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        list.add(Id.of(i));
      }

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(list, IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(100);
      for (int i = 0; i < 100; i++) {
        assertThat(unwrapped.get(i)).isEqualTo(i);
      }
    }

    @Test
    @DisplayName("should be stack-safe with large lists (50,000 elements)")
    void shouldBeStackSafeWithLargeLists() {
      final List<Kind<Id.Witness, Integer>> largeList = new ArrayList<>();
      for (int i = 0; i < 50_000; i++) {
        largeList.add(Id.of(i));
      }

      final Kind<Id.Witness, List<Integer>> result =
          TrampolineUtils.sequenceStackSafe(largeList, IdMonad.instance());

      final List<Integer> unwrapped = ID.narrow(result).value();
      assertThat(unwrapped).hasSize(50_000);
      assertThat(unwrapped.get(0)).isEqualTo(0);
      assertThat(unwrapped.get(49_999)).isEqualTo(49_999);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("traverseListStackSafe should throw NPE when function encounters null")
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
                      IdMonad.instance()))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
