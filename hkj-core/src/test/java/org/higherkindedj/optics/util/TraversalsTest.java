// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Traversals Utility Class Tests")
class TraversalsTest {

  @Test
  @DisplayName("modify should apply a pure function to all parts of a Traversal")
  void modify_shouldApplyFunctionToAllParts() {
    // Given
    final List<String> sourceList = List.of("one", "two", "three");
    final Traversal<List<String>, String> listTraversal = listElements();

    // When
    final List<String> resultList =
        Traversals.modify(listTraversal, String::toUpperCase, sourceList);

    // Then
    assertThat(resultList).containsExactly("ONE", "TWO", "THREE");
    assertThat(sourceList).containsExactly("one", "two", "three");
  }

  @Nested
  @DisplayName("traverseList Tests")
  class TraverseListTests {

    @Test
    @DisplayName("should return Optional of List when all functions succeed")
    void withOptional_shouldReturnOptionalOfList_whenAllSucceed() {
      // Given
      final List<Integer> source = List.of(1, 2, 3);
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i ->
              i > 0
                  ? OptionalKindHelper.OPTIONAL.widen(Optional.of("ok-" + i))
                  : OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      // When
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);

      // Then
      final Optional<List<String>> actual = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("ok-1", "ok-2", "ok-3"));
    }

    @Test
    @DisplayName("should return Optional.empty when any function fails")
    void withOptional_shouldReturnEmpty_whenAnyFails() {
      // Given
      final List<Integer> source = List.of(1, -2, 3);
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i ->
              i > 0
                  ? OptionalKindHelper.OPTIONAL.widen(Optional.of("ok-" + i))
                  : OptionalKindHelper.OPTIONAL.widen(Optional.empty());

      // When
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);

      // Then
      final Optional<List<String>> actual = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("should return a list of all combinations when traversing with List applicative")
    void withList_shouldReturnListOfAllCombinations() {
      // Given
      final List<Integer> source = List.of(1, 2);
      final Function<Integer, Kind<ListKind.Witness, Integer>> f =
          i -> ListKindHelper.LIST.widen(List.of(i, i * 10));

      // When
      // The type here is Kind<List, List<Integer>> because traversing a List with a List effect
      // produces the cartesian product of all possible lists.
      final Kind<ListKind.Witness, List<Integer>> result =
          Traversals.traverseList(source, f, ListMonad.INSTANCE);

      // Then
      final List<List<Integer>> actual = ListKindHelper.LIST.narrow(result);
      assertThat(actual)
          .containsExactlyInAnyOrder(
              List.of(1, 2), List.of(1, 20), List.of(10, 2), List.of(10, 20));
    }

    @Test
    @DisplayName("should return an applicative of an empty list when input is empty")
    void shouldReturnApplicativeOfEmptyList_whenInputIsEmpty() {
      // Given
      final List<Integer> source = Collections.emptyList();
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> OptionalKindHelper.OPTIONAL.widen(Optional.of("ok"));

      // When
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);

      // Then
      final Optional<List<String>> actual = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(actual).contains(Collections.emptyList());
    }
  }

  // Helper to create a Traversal for a list
  private <T> Traversal<List<T>, T> listElements() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<T>> modifyF(
          Function<T, Kind<F, T>> f, List<T> source, Applicative<F> applicative) {

        Kind<F, Kind<ListKind.Witness, T>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, ListKindHelper.LIST.widen(source), f);

        return applicative.map(ListKindHelper.LIST::narrow, traversed);
      }
    };
  }
}
