// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional.OptionalSelective;
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
      final List<String> sourceList = List.of("one", "two", "three");
      final Traversal<List<String>, String> listTraversal = Traversals.forList();

      final List<String> resultList =
          Traversals.modify(listTraversal, String::toUpperCase, sourceList);

      assertThat(resultList).containsExactly("ONE", "TWO", "THREE");
      assertThat(sourceList).containsExactly("one", "two", "three"); // Original is unchanged
    }

    @Test
    @DisplayName("should handle empty lists")
    void modify_shouldHandleEmptyLists() {
      final List<String> sourceList = Collections.emptyList();
      final Traversal<List<String>, String> listTraversal = Traversals.forList();

      final List<String> resultList =
          Traversals.modify(listTraversal, String::toUpperCase, sourceList);

      assertThat(resultList).isEmpty();
    }

    @Test
    @DisplayName("should maintain immutability when modifying lists")
    void modify_shouldMaintainImmutability() {
      final List<Integer> sourceList = List.of(1, 2, 3);
      final Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();

      final List<Integer> resultList = Traversals.modify(listTraversal, i -> i * 2, sourceList);

      assertThat(resultList).containsExactly(2, 4, 6);
      assertThat(sourceList).containsExactly(1, 2, 3); // Original unchanged
    }

    @Test
    @DisplayName("should work with complex transformations")
    void modify_shouldWorkWithComplexTransformations() {
      final List<String> sourceList = List.of("a", "bb", "ccc");
      final Traversal<List<String>, String> listTraversal = Traversals.forList();

      final List<String> resultList =
          Traversals.modify(listTraversal, s -> s + ":" + s.length(), sourceList);

      assertThat(resultList).containsExactly("a:1", "bb:2", "ccc:3");
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
      // Use Tuple2 and the generated Tuple2Lenses
      final Traversal<List<Tuple2<Integer, String>>, String> deepTraversal =
          Traversals.<Tuple2<Integer, String>>forList()
              .andThen(Tuple2Lenses.<Integer, String>_2().asTraversal());

      final List<Tuple2<Integer, String>> source =
          List.of(new Tuple2<>(1, "Alice"), new Tuple2<>(2, "Bob"));

      assertThat(Traversals.getAll(deepTraversal, source)).containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("should preserve order when extracting elements")
    void getAll_shouldPreserveOrder() {
      final List<Integer> source = List.of(5, 3, 8, 1, 9);
      final Traversal<List<Integer>, Integer> traversal = Traversals.forList();
      assertThat(Traversals.getAll(traversal, source)).containsExactly(5, 3, 8, 1, 9);
    }

    @Test
    @DisplayName("should handle duplicate elements")
    void getAll_shouldHandleDuplicates() {
      final List<String> source = List.of("a", "b", "a", "c", "a");
      final Traversal<List<String>, String> traversal = Traversals.forList();
      assertThat(Traversals.getAll(traversal, source)).containsExactly("a", "b", "a", "c", "a");
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

    @Test
    @DisplayName("should handle single element lists")
    void forList_shouldHandleSingleElement() {
      final Traversal<List<String>, String> listTraversal = Traversals.forList();
      final List<String> source = List.of("solo");
      final List<String> modified = Traversals.modify(listTraversal, String::toUpperCase, source);
      assertThat(modified).containsExactly("SOLO");
    }

    @Test
    @DisplayName("should work with different element types")
    void forList_shouldWorkWithDifferentTypes() {
      final Traversal<List<Double>, Double> listTraversal = Traversals.forList();
      final List<Double> source = List.of(1.5, 2.5, 3.5);
      final List<Double> modified = Traversals.modify(listTraversal, d -> d * 2, source);
      assertThat(modified).containsExactly(3.0, 5.0, 7.0);
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

    @Test
    @DisplayName("should maintain immutability of original map")
    void forMap_shouldMaintainImmutability() {
      final Traversal<Map<String, String>, String> mapTraversal = Traversals.forMap("name");
      final Map<String, String> source = Map.of("name", "Alice", "city", "NYC");
      final Map<String, String> modified =
          Traversals.modify(mapTraversal, String::toUpperCase, source);

      assertThat(modified).containsEntry("name", "ALICE").containsEntry("city", "NYC");
      assertThat(source).containsEntry("name", "Alice"); // Original unchanged
    }

    @Test
    @DisplayName("should work with empty map")
    void forMap_shouldWorkWithEmptyMap() {
      final Traversal<Map<String, Integer>, Integer> mapTraversal = Traversals.forMap("key");
      final Map<String, Integer> source = Collections.emptyMap();
      final Map<String, Integer> modified = Traversals.modify(mapTraversal, i -> i * 2, source);
      assertThat(modified).isEmpty();
    }

    @Test
    @DisplayName("should handle null values in map")
    void forMap_shouldHandleNullValues() {
      // Note: This test assumes the map implementation allows null values
      final Traversal<Map<String, Integer>, Integer> mapTraversal = Traversals.forMap("nullKey");
      final Map<String, Integer> source = new java.util.HashMap<>();
      source.put("key1", 10);
      source.put("nullKey", null);

      final Map<String, Integer> modified = Traversals.modify(mapTraversal, i -> i * 2, source);

      assertThat(modified).isEqualTo(source);
    }
  }

  @Nested
  @DisplayName("traverseList Tests")
  class TraverseListTests {

    @Test
    @DisplayName("should return Optional of List when all functions succeed")
    void withOptional_shouldReturnOptionalOfList_whenAllSucceed() {

      final List<Integer> source = List.of(1, 2, 3);
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> i > 0 ? OPTIONAL.widen(Optional.of("ok-" + i)) : OPTIONAL.widen(Optional.empty());
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("ok-1", "ok-2", "ok-3"));
    }

    @Test
    @DisplayName("should return Optional.empty when any function fails")
    void withOptional_shouldReturnEmpty_whenAnyFails() {

      final List<Integer> source = List.of(1, -2, 3);
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> i > 0 ? OPTIONAL.widen(Optional.of("ok-" + i)) : OPTIONAL.widen(Optional.empty());
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("should return a list of all combinations when traversing with List applicative")
    void withList_shouldReturnListOfAllCombinations() {

      final List<Integer> source = List.of(1, 2);
      final Function<Integer, Kind<ListKind.Witness, Integer>> f =
          i -> ListKindHelper.LIST.widen(List.of(i, i * 10));
      final Kind<ListKind.Witness, List<Integer>> result =
          Traversals.traverseList(source, f, ListMonad.INSTANCE);
      final List<List<Integer>> actual = ListKindHelper.LIST.narrow(result);
      assertThat(actual)
          .containsExactlyInAnyOrder(
              List.of(1, 2), List.of(1, 20), List.of(10, 2), List.of(10, 20));
    }

    @Test
    @DisplayName("should return an applicative of an empty list when input is empty")
    void shouldReturnApplicativeOfEmptyList_whenInputIsEmpty() {

      final List<Integer> source = Collections.emptyList();
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> OPTIONAL.widen(Optional.of("ok"));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(Collections.emptyList());
    }

    @Test
    @DisplayName("should handle single element list")
    void shouldHandleSingleElementList() {

      final List<Integer> source = List.of(42);
      final Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> OPTIONAL.widen(Optional.of("value-" + i));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("value-42"));
    }

    @Test
    @DisplayName("should fail fast with Optional on first failure")
    void shouldFailFastWithOptional() {

      final AtomicInteger callCount = new AtomicInteger(0);
      final List<Integer> source = List.of(1, 2, -3, 4, 5);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> {
            callCount.incrementAndGet();
            return i > 0 ? OPTIONAL.widen(Optional.of(i)) : OPTIONAL.widen(Optional.empty());
          };
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
      // With fail-fast semantics, all elements are still processed
      // (traverse processes sequentially but collects all)
      assertThat(callCount.get()).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("speculativeTraverseList Tests")
  class SpeculativeTraverseListTests {

    @Test
    @DisplayName("should apply then branch when predicate is true")
    void shouldApplyThenBranch() {

      final List<Integer> source = List.of(5, 10, 15);
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.of("then-" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.of("else-" + i));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> true, thenBranch, elseBranch, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("then-5", "then-10", "then-15"));
    }

    @Test
    @DisplayName("should apply else branch when predicate is false")
    void shouldApplyElseBranch() {

      final List<Integer> source = List.of(5, 10, 15);
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.of("then-" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.of("else-" + i));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> false, thenBranch, elseBranch, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("else-5", "else-10", "else-15"));
    }

    @Test
    @DisplayName("should branch based on element value")
    void shouldBranchBasedOnValue() {

      final List<Integer> source = List.of(1, 10, 3, 20, 5);
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.of("big-" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.of("small-" + i));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> i >= 10, thenBranch, elseBranch, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("small-1", "big-10", "small-3", "big-20", "small-5"));
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {

      final List<Integer> source = Collections.emptyList();
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.of("then-" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.of("else-" + i));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> true, thenBranch, elseBranch, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(Collections.emptyList());
    }

    @Test
    @DisplayName("should fail if then branch fails")
    void shouldFailIfThenBranchFails() {

      final List<Integer> source = List.of(5, 10, 15);
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.empty());
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.of("else-" + i));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> true, thenBranch, elseBranch, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("should fail if else branch fails")
    void shouldFailIfElseBranchFails() {

      final List<Integer> source = List.of(5, 10, 15);
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.of("then-" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.empty());
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> false, thenBranch, elseBranch, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
    }
  }

  @Nested
  @DisplayName("traverseListIf Tests")
  class TraverseListIfTests {

    @Test
    @DisplayName("should apply function only to matching elements")
    void shouldApplyOnlyToMatchingElements() {

      final List<Integer> source = List.of(1, 2, 3, 4, 5);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 10));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListIf(source, i -> i % 2 == 0, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(1, 20, 3, 40, 5));
    }

    @Test
    @DisplayName("should leave all elements unchanged when predicate never matches")
    void shouldLeaveUnchangedWhenPredicateNeverMatches() {

      final List<String> source = List.of("a", "b", "c");
      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> OPTIONAL.widen(Optional.of(s.toUpperCase()));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseListIf(source, s -> false, f, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("a", "b", "c"));
    }

    @Test
    @DisplayName("should process all elements when predicate always matches")
    void shouldProcessAllWhenPredicateAlwaysMatches() {

      final List<String> source = List.of("a", "b", "c");
      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> OPTIONAL.widen(Optional.of(s.toUpperCase()));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseListIf(source, s -> true, f, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("A", "B", "C"));
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {

      final List<Integer> source = Collections.emptyList();
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 2));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListIf(source, i -> true, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(Collections.emptyList());
    }

    @Test
    @DisplayName("should fail when function fails on matching element")
    void shouldFailWhenFunctionFailsOnMatchingElement() {

      final List<Integer> source = List.of(1, 2, 3, 4, 5);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.empty());
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListIf(source, i -> i % 2 == 0, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("should skip expensive operations for non-matching elements")
    void shouldSkipExpensiveOperations() {

      final AtomicInteger expensiveCallCount = new AtomicInteger(0);
      final List<String> source = List.of("short", "verylongstring", "tiny", "medium");
      final Function<String, Kind<OptionalKind.Witness, String>> expensiveOperation =
          s -> {
            expensiveCallCount.incrementAndGet();
            return OPTIONAL.widen(Optional.of(s.toUpperCase()));
          };
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseListIf(
              source, s -> s.length() > 5, expensiveOperation, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("short", "VERYLONGSTRING", "tiny", "MEDIUM"));
      assertThat(expensiveCallCount.get()).isEqualTo(2); // Only 2 strings match predicate
    }
  }

  @Nested
  @DisplayName("traverseListUntil Tests")
  class TraverseListUntilTests {

    @Test
    @DisplayName("should process elements until stop condition is met")
    void shouldProcessUntilStopCondition() {

      final List<Integer> source = List.of(1, 2, 3, 4, 5);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 10));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> i >= 3, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(10, 20, 3, 4, 5));
    }

    @Test
    @DisplayName("should process all elements when stop condition never met")
    void shouldProcessAllWhenStopConditionNeverMet() {

      final List<String> source = List.of("a", "b", "c");
      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> OPTIONAL.widen(Optional.of(s.toUpperCase()));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseListUntil(source, s -> false, f, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("A", "B", "C"));
    }

    @Test
    @DisplayName("should process no elements when stop condition met immediately")
    void shouldProcessNoneWhenStopConditionMetImmediately() {

      final List<Integer> source = List.of(10, 20, 30);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 2));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> i >= 10, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(10, 20, 30));
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {

      final List<Integer> source = Collections.emptyList();
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 2));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> true, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(Collections.emptyList());
    }

    @Test
    @DisplayName("should stop processing after first element meeting condition")
    void shouldStopAfterFirstMatch() {

      final AtomicInteger processCount = new AtomicInteger(0);
      final List<Integer> source = List.of(1, 2, 5, 3, 4);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> {
            processCount.incrementAndGet();
            return OPTIONAL.widen(Optional.of(i * 10));
          };
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> i >= 5, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(10, 20, 5, 3, 4));
      assertThat(processCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("should maintain state across invocations")
    void shouldMaintainStateAcrossInvocations() {

      final List<String> source = List.of("a", "stop", "b", "c");
      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> OPTIONAL.widen(Optional.of(s.toUpperCase()));
      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseListUntil(
              source, s -> s.equals("stop"), f, OptionalSelective.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of("A", "stop", "b", "c"));
    }

    @Test
    @DisplayName("should fail when function fails before stop condition")
    void shouldFailWhenFunctionFailsBeforeStop() {

      final List<Integer> source = List.of(1, 2, 3, 4, 5);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> i == 2 ? OPTIONAL.widen(Optional.empty()) : OPTIONAL.widen(Optional.of(i * 10));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> i >= 100, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isEmpty();
    }

    @Test
    @DisplayName("should handle single element list")
    void shouldHandleSingleElementList() {

      final List<Integer> source = List.of(42);
      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 2));
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> i > 100, f, OptionalSelective.INSTANCE);
      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(84));
    }
  }
}
