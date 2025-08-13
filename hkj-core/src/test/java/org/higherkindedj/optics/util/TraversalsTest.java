// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Lenses;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Traversals Utility Class Tests")
class TraversalsTest {

  @Nested
  @DisplayName("modify Tests")
  class ModifyTests {
    @Test
    @DisplayName("should apply a pure function to all parts of a Traversal")
    void modify_shouldApplyFunctionToAllParts() {
      // Given
      final List<String> sourceList = List.of("one", "two", "three");
      final Traversal<List<String>, String> listTraversal = Traversals.forList();

      // When
      final List<String> resultList =
          Traversals.modify(listTraversal, String::toUpperCase, sourceList);

      // Then
      assertThat(resultList).containsExactly("ONE", "TWO", "THREE");
      assertThat(sourceList).containsExactly("one", "two", "three"); // Original is unchanged
    }
  }

  @Nested
  @DisplayName("getAll Tests")
  class GetAllTests {
    @Test
    @DisplayName("should extract all elements from a simple traversal")
    void getAll_simpleTraversal() {
      final List<String> source = List.of("a", "b", "c");
      final Traversal<List<String>, String> traversal = Traversals.forList();
      assertThat(Traversals.getAll(traversal, source)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("should return an empty list for a traversal with no targets")
    void getAll_noTargets() {
      final List<String> source = Collections.emptyList();
      final Traversal<List<String>, String> traversal = Traversals.forList();
      assertThat(Traversals.getAll(traversal, source)).isEmpty();
    }

    @Test
    @DisplayName("should extract all elements from a deep, composed traversal")
    void getAll_deepTraversal() {
      // Use Tuple2 and the generated Tuple2Lenses, which are available in the test classpath
      final Traversal<List<Tuple2<Integer, String>>, String> deepTraversal =
          Traversals.<Tuple2<Integer, String>>forList()
              .andThen(Tuple2Lenses.<Integer, String>_2().asTraversal());

      final List<Tuple2<Integer, String>> source =
          List.of(new Tuple2<>(1, "Alice"), new Tuple2<>(2, "Bob"));

      assertThat(Traversals.getAll(deepTraversal, source)).containsExactly("Alice", "Bob");
    }
  }

  @Nested
  @DisplayName("forList Tests")
  class ForListTests {
    @Test
    @DisplayName("should create a traversal that targets every element in a list")
    void forList_targetsEveryElement() {
      final Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();
      final List<Integer> source = List.of(1, 2, 3);
      final List<Integer> modified = Traversals.modify(listTraversal, i -> i + 1, source);
      assertThat(modified).containsExactly(2, 3, 4);
    }
  }

  @Nested
  @DisplayName("forMap Tests")
  class ForMapTests {
    @Test
    @DisplayName("should create a traversal that targets an existing map value")
    void forMap_targetsExistingValue() {
      final Traversal<Map<String, Integer>, Integer> mapTraversal = Traversals.forMap("key2");
      final Map<String, Integer> source = Map.of("key1", 10, "key2", 20);
      final Map<String, Integer> modified = Traversals.modify(mapTraversal, i -> i * 2, source);
      assertThat(modified).containsEntry("key1", 10).containsEntry("key2", 40);
    }

    @Test
    @DisplayName("should do nothing if the key does not exist")
    void forMap_doesNothingForMissingKey() {
      final Traversal<Map<String, Integer>, Integer> mapTraversal = Traversals.forMap("key3");
      final Map<String, Integer> source = Map.of("key1", 10, "key2", 20);
      final Map<String, Integer> modified = Traversals.modify(mapTraversal, i -> i * 2, source);
      assertThat(modified).isEqualTo(source); // The map is unchanged
    }
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
}
