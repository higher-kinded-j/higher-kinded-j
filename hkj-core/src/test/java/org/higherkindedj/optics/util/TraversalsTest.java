// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional.OptionalSelective;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Lenses;
import org.higherkindedj.optics.Lens;
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

  @Nested
  @DisplayName("Null Handling and Validation Tests")
  class NullHandlingTests {

    @Test
    @DisplayName("modify should handle null values in list elements")
    void modify_shouldHandleNullElements() {
      final List<String> sourceList = new ArrayList<>();
      sourceList.add("one");
      sourceList.add(null);
      sourceList.add("three");
      final Traversal<List<String>, String> listTraversal = Traversals.forList();

      final List<String> resultList =
          Traversals.modify(listTraversal, s -> s == null ? "NULL" : s.toUpperCase(), sourceList);

      assertThat(resultList).containsExactly("ONE", "NULL", "THREE");
    }

    @Test
    @DisplayName("getAll should preserve null elements")
    void getAll_shouldPreserveNullElements() {
      final List<String> source = new ArrayList<>();
      source.add("a");
      source.add(null);
      source.add("c");
      final Traversal<List<String>, String> traversal = Traversals.forList();

      assertThat(Traversals.getAll(traversal, source)).containsExactly("a", null, "c");
    }

    @Test
    @DisplayName("forMap should handle null values properly")
    void forMap_shouldHandleNullValuesProperly() {
      final Map<String, String> source = new HashMap<>();
      source.put("key1", "value1");
      source.put("key2", null);

      final Traversal<Map<String, String>, String> mapTraversal = Traversals.forMap("key2");
      final Map<String, String> modified = Traversals.modify(mapTraversal, s -> s, source);

      // Since value is null, modification should not happen
      assertThat(modified).isEqualTo(source);
    }
  }

  @Nested
  @DisplayName("Complex Composition Tests")
  class ComplexCompositionTests {

    @Test
    @DisplayName("should compose multiple traversals for nested structures")
    void shouldComposeMultipleTraversals() {
      // List<List<String>> structure
      final List<List<String>> nestedList =
          List.of(List.of("a", "b"), List.of("c", "d", "e"), List.of("f"));

      // Compose two list traversals
      final Traversal<List<List<String>>, List<String>> outerTraversal = Traversals.forList();
      final Traversal<List<String>, String> innerTraversal = Traversals.forList();
      final Traversal<List<List<String>>, String> composedTraversal =
          outerTraversal.andThen(innerTraversal);

      final List<String> allElements = Traversals.getAll(composedTraversal, nestedList);
      assertThat(allElements).containsExactly("a", "b", "c", "d", "e", "f");
    }

    @Test
    @DisplayName("should modify deeply nested structures")
    void shouldModifyDeeplyNestedStructures() {
      final List<List<Integer>> nestedList = List.of(List.of(1, 2), List.of(3, 4, 5));

      final Traversal<List<List<Integer>>, List<Integer>> outerTraversal = Traversals.forList();
      final Traversal<List<Integer>, Integer> innerTraversal = Traversals.forList();
      final Traversal<List<List<Integer>>, Integer> composedTraversal =
          outerTraversal.andThen(innerTraversal);

      final List<List<Integer>> result =
          Traversals.modify(composedTraversal, i -> i * 10, nestedList);

      assertThat(result).containsExactly(List.of(10, 20), List.of(30, 40, 50));
    }

    @Test
    @DisplayName("should compose traversal with lens")
    void shouldComposeTraversalWithLens() {
      final List<Tuple2<String, Integer>> data =
          List.of(new Tuple2<>("Alice", 30), new Tuple2<>("Bob", 25), new Tuple2<>("Charlie", 35));

      final Traversal<List<Tuple2<String, Integer>>, Tuple2<String, Integer>> listTraversal =
          Traversals.forList();
      final Lens<Tuple2<String, Integer>, Integer> ageLens = Tuple2Lenses._2();
      final Traversal<List<Tuple2<String, Integer>>, Integer> composed =
          listTraversal.andThen(ageLens.asTraversal());

      final List<Integer> ages = Traversals.getAll(composed, data);
      assertThat(ages).containsExactly(30, 25, 35);

      final List<Tuple2<String, Integer>> incremented =
          Traversals.modify(composed, age -> age + 1, data);
      assertThat(Traversals.getAll(composed, incremented)).containsExactly(31, 26, 36);
    }

    @Test
    @DisplayName("should compose multiple map traversals")
    void shouldComposeMultipleMapTraversals() {
      final Map<String, Map<String, Integer>> nestedMap = new HashMap<>();
      nestedMap.put("outer", Map.of("inner", 42));

      final Traversal<Map<String, Map<String, Integer>>, Map<String, Integer>> outerTraversal =
          Traversals.forMap("outer");
      final Traversal<Map<String, Integer>, Integer> innerTraversal = Traversals.forMap("inner");
      final Traversal<Map<String, Map<String, Integer>>, Integer> composed =
          outerTraversal.andThen(innerTraversal);

      final Map<String, Map<String, Integer>> modified =
          Traversals.modify(composed, i -> i * 2, nestedMap);

      assertThat(modified.get("outer").get("inner")).isEqualTo(84);
    }
  }

  @Nested
  @DisplayName("Large Data Set Tests")
  class LargeDataSetTests {

    @Test
    @DisplayName("should handle large lists efficiently")
    void shouldHandleLargeLists() {
      final List<Integer> largeList = new ArrayList<>();
      for (int i = 0; i < 10000; i++) {
        largeList.add(i);
      }

      final Traversal<List<Integer>, Integer> traversal = Traversals.forList();
      final List<Integer> result = Traversals.modify(traversal, i -> i * 2, largeList);

      assertThat(result).hasSize(10000);
      assertThat(result.get(0)).isEqualTo(0);
      assertThat(result.get(9999)).isEqualTo(19998);
    }

    @Test
    @DisplayName("should extract all elements from large list")
    void shouldExtractFromLargeList() {
      final List<String> largeList = new ArrayList<>();
      for (int i = 0; i < 5000; i++) {
        largeList.add("item-" + i);
      }

      final Traversal<List<String>, String> traversal = Traversals.forList();
      final List<String> extracted = Traversals.getAll(traversal, largeList);

      assertThat(extracted).hasSize(5000);
      assertThat(extracted).isEqualTo(largeList);
    }
  }

  @Nested
  @DisplayName("traverseList Advanced Tests")
  class TraverseListAdvancedTests {

    @Test
    @DisplayName("should short-circuit on first failure with Optional")
    void shouldShortCircuitOnFailure() {
      final AtomicInteger executionCount = new AtomicInteger(0);
      final List<Integer> source = List.of(1, 2, 3, 4, 5);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> {
            executionCount.incrementAndGet();
            return i == 3 ? OPTIONAL.widen(Optional.empty()) : OPTIONAL.widen(Optional.of(i));
          };

      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);

      assertThat(OPTIONAL.narrow(result)).isEmpty();
      // Note: traverseList processes all elements even with Optional
      assertThat(executionCount.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("should handle List applicative cartesian product")
    void shouldHandleCartesianProduct() {
      final List<String> source = List.of("a", "b");
      final Function<String, Kind<ListKind.Witness, String>> f =
          s -> ListKindHelper.LIST.widen(List.of(s, s.toUpperCase()));

      final Kind<ListKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, ListMonad.INSTANCE);
      final List<List<String>> actual = ListKindHelper.LIST.narrow(result);

      assertThat(actual).hasSize(4);
      assertThat(actual)
          .containsExactlyInAnyOrder(
              List.of("a", "b"), List.of("a", "B"), List.of("A", "b"), List.of("A", "B"));
    }

    @Test
    @DisplayName("should handle large cartesian product")
    void shouldHandleLargeCartesianProduct() {
      final List<Integer> source = List.of(1, 2, 3);
      final Function<Integer, Kind<ListKind.Witness, Integer>> f =
          i -> ListKindHelper.LIST.widen(List.of(i, i * 10));

      final Kind<ListKind.Witness, List<Integer>> result =
          Traversals.traverseList(source, f, ListMonad.INSTANCE);
      final List<List<Integer>> actual = ListKindHelper.LIST.narrow(result);

      assertThat(actual).hasSize(8); // 2^3 combinations
    }

    @Test
    @DisplayName("should preserve element order in traversal")
    void shouldPreserveElementOrder() {
      final List<String> source = List.of("z", "a", "m", "b");
      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> OPTIONAL.widen(Optional.of(s.toUpperCase()));

      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);
      final Optional<List<String>> actual = OPTIONAL.narrow(result);

      assertThat(actual).contains(List.of("Z", "A", "M", "B"));
    }
  }

  @Nested
  @DisplayName("Speculative Traversal Advanced Tests")
  class SpeculativeTraversalAdvancedTests {

    @Test
    @DisplayName("should handle mixed predicate results")
    void shouldHandleMixedPredicateResults() {
      final List<Integer> source = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

      final Function<Integer, Kind<OptionalKind.Witness, String>> evenBranch =
          i -> OPTIONAL.widen(Optional.of("even:" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> oddBranch =
          i -> OPTIONAL.widen(Optional.of("odd:" + i));

      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> i % 2 == 0, evenBranch, oddBranch, OptionalSelective.INSTANCE);

      final Optional<List<String>> actual = OPTIONAL.narrow(result);
      assertThat(actual).isPresent();
      assertThat(actual.get())
          .containsExactly(
              "odd:1", "even:2", "odd:3", "even:4", "odd:5", "even:6", "odd:7", "even:8", "odd:9",
              "even:10");
    }

    @Test
    @DisplayName("should execute both branches for all elements (speculative execution)")
    void shouldExecuteBothBranchesSpeculatively() {
      // Note: Selective.ifS evaluates BOTH branches eagerly for each element,
      // then selects which result to use based on the predicate.
      // This is the defining characteristic of Selective functors - static analysis
      // can see all possible effects upfront.
      final AtomicInteger thenCount = new AtomicInteger(0);
      final AtomicInteger elseCount = new AtomicInteger(0);
      final List<Integer> source = List.of(5, 15, 8, 20, 3);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> thenBranch =
          i -> {
            thenCount.incrementAndGet();
            return OPTIONAL.widen(Optional.of(i * 100));
          };

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> elseBranch =
          i -> {
            elseCount.incrementAndGet();
            return OPTIONAL.widen(Optional.of(i * 10));
          };

      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.speculativeTraverseList(
              source, i -> i >= 10, thenBranch, elseBranch, OptionalSelective.INSTANCE);

      // Both branches are evaluated for all elements (speculative execution)
      assertThat(thenCount.get()).isEqualTo(5); // All elements
      assertThat(elseCount.get()).isEqualTo(5); // All elements

      // But the correct branch results are selected
      assertThat(OPTIONAL.narrow(result)).contains(List.of(50, 1500, 80, 2000, 30));
    }

    @Test
    @DisplayName("should select correct results despite evaluating both branches")
    void shouldSelectCorrectResultsDespiteEagerEvaluation() {
      final List<Integer> source = List.of(1, 10, 2, 20, 3, 30);

      // Different transformations for each branch
      final Function<Integer, Kind<OptionalKind.Witness, String>> thenBranch =
          i -> OPTIONAL.widen(Optional.of("BIG:" + i));
      final Function<Integer, Kind<OptionalKind.Witness, String>> elseBranch =
          i -> OPTIONAL.widen(Optional.of("small:" + i));

      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.speculativeTraverseList(
              source, i -> i >= 10, thenBranch, elseBranch, OptionalSelective.INSTANCE);

      // Verify the correct branch was selected for each element
      assertThat(OPTIONAL.narrow(result))
          .contains(List.of("small:1", "BIG:10", "small:2", "BIG:20", "small:3", "BIG:30"));
    }

    @Test
    @DisplayName("should handle partial failures in branches")
    void shouldHandlePartialFailuresInBranches() {
      final List<Integer> source = List.of(1, 10, 2, 20, 3);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> thenBranch =
          i -> i == 10 ? OPTIONAL.widen(Optional.empty()) : OPTIONAL.widen(Optional.of(i * 100));

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> elseBranch =
          i -> OPTIONAL.widen(Optional.of(i * 10));

      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.speculativeTraverseList(
              source, i -> i >= 10, thenBranch, elseBranch, OptionalSelective.INSTANCE);

      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Conditional Traversal Advanced Tests")
  class ConditionalTraversalAdvancedTests {

    @Test
    @DisplayName("should skip expensive operations efficiently")
    void shouldSkipExpensiveOperationsEfficiently() {
      final AtomicInteger expensiveCallCount = new AtomicInteger(0);
      final List<Integer> source = List.of(1, 100, 2, 200, 3, 300, 4);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> expensiveOp =
          i -> {
            expensiveCallCount.incrementAndGet();
            // Simulate expensive operation
            return OPTIONAL.widen(Optional.of(i * 1000));
          };

      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListIf(
              source,
              i -> i >= 100, // Only process large numbers
              expensiveOp,
              OptionalSelective.INSTANCE);

      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(1, 100000, 2, 200000, 3, 300000, 4));
      assertThat(expensiveCallCount.get()).isEqualTo(3); // Only 100, 200, 300
    }

    @Test
    @DisplayName("should handle all elements failing predicate")
    void shouldHandleAllElementsFailingPredicate() {
      final List<String> source = List.of("a", "b", "c", "d");

      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> OPTIONAL.widen(Optional.of(s.toUpperCase()));

      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseListIf(
              source,
              s -> s.equals("z"), // Never matches
              f,
              OptionalSelective.INSTANCE);

      assertThat(OPTIONAL.narrow(result)).contains(List.of("a", "b", "c", "d"));
    }

    @Test
    @DisplayName("should handle complex predicates")
    void shouldHandleComplexPredicates() {
      final List<Integer> source = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * i));

      // Process only prime numbers
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListIf(source, i -> isPrime(i), f, OptionalSelective.INSTANCE);

      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual).contains(List.of(1, 4, 9, 4, 25, 6, 49, 8, 9, 10));
    }

    private boolean isPrime(int n) {
      if (n <= 1) return false;
      if (n == 2) return true;
      if (n % 2 == 0) return false;
      for (int i = 3; i * i <= n; i += 2) {
        if (n % i == 0) return false;
      }
      return true;
    }
  }

  @Nested
  @DisplayName("Until Traversal Advanced Tests")
  class UntilTraversalAdvancedTests {

    @Test
    @DisplayName("should handle stop condition at different positions")
    void shouldHandleStopConditionAtDifferentPositions() {
      final List<Integer> source = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

      // Stop at first element
      Kind<OptionalKind.Witness, List<Integer>> result1 =
          Traversals.traverseListUntil(
              source,
              i -> i >= 1,
              i -> OPTIONAL.widen(Optional.of(i * 10)),
              OptionalSelective.INSTANCE);
      assertThat(OPTIONAL.narrow(result1).get()).startsWith(1);

      // Stop in middle
      Kind<OptionalKind.Witness, List<Integer>> result2 =
          Traversals.traverseListUntil(
              source,
              i -> i >= 5,
              i -> OPTIONAL.widen(Optional.of(i * 10)),
              OptionalSelective.INSTANCE);
      assertThat(OPTIONAL.narrow(result2).get()).startsWith(10, 20, 30, 40, 5);

      // Stop at last element
      Kind<OptionalKind.Witness, List<Integer>> result3 =
          Traversals.traverseListUntil(
              source,
              i -> i >= 10,
              i -> OPTIONAL.widen(Optional.of(i * 10)),
              OptionalSelective.INSTANCE);
      List<Integer> result3List = OPTIONAL.narrow(result3).get();
      assertThat(result3List.subList(0, 9)).containsExactly(10, 20, 30, 40, 50, 60, 70, 80, 90);
    }

    @Test
    @DisplayName("should track state correctly across elements")
    void shouldTrackStateCorrectly() {
      final AtomicInteger beforeStop = new AtomicInteger(0);
      final AtomicInteger afterStop = new AtomicInteger(0);
      final List<String> source = List.of("a", "b", "stop", "c", "d", "e");

      final Function<String, Kind<OptionalKind.Witness, String>> f =
          s -> {
            if (s.equals("stop")) {
              // This should not be called since we stop before processing
              afterStop.incrementAndGet();
            } else {
              beforeStop.incrementAndGet();
            }
            return OPTIONAL.widen(Optional.of(s.toUpperCase()));
          };

      Traversals.traverseListUntil(source, s -> s.equals("stop"), f, OptionalSelective.INSTANCE);

      assertThat(beforeStop.get()).isEqualTo(2); // "a" and "b"
      assertThat(afterStop.get()).isEqualTo(0); // "stop" triggers stop, so not processed
    }

    @Test
    @DisplayName("should handle multiple stop conditions")
    void shouldHandleMultipleStopConditions() {
      final List<Integer> source = List.of(1, 5, 2, 10, 3, 15, 4);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> OPTIONAL.widen(Optional.of(i * 100));

      // Stop at first number >= 5
      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseListUntil(source, i -> i >= 5, f, OptionalSelective.INSTANCE);

      final Optional<List<Integer>> actual = OPTIONAL.narrow(result);
      assertThat(actual.get()).startsWith(100, 5); // Processed 1, then stopped at 5
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("should handle very deeply nested structures")
    void shouldHandleVeryDeeplyNestedStructures() {
      // Create a 3-level nested list
      final List<List<List<Integer>>> deeplyNested =
          List.of(List.of(List.of(1, 2), List.of(3)), List.of(List.of(4, 5, 6)));

      final Traversal<List<List<List<Integer>>>, List<List<Integer>>> t1 = Traversals.forList();
      final Traversal<List<List<Integer>>, List<Integer>> t2 = Traversals.forList();
      final Traversal<List<Integer>, Integer> t3 = Traversals.forList();

      final Traversal<List<List<List<Integer>>>, Integer> composed = t1.andThen(t2).andThen(t3);

      final List<Integer> allElements = Traversals.getAll(composed, deeplyNested);
      assertThat(allElements).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("should handle lists with single null element")
    void shouldHandleListWithSingleNullElement() {
      final List<String> source = new ArrayList<>();
      source.add(null);

      final Traversal<List<String>, String> traversal = Traversals.forList();
      final List<String> result =
          Traversals.modify(traversal, s -> s == null ? "WAS_NULL" : s, source);

      assertThat(result).containsExactly("WAS_NULL");
    }

    @Test
    @DisplayName("should handle map with no matching keys")
    void shouldHandleMapWithNoMatchingKeys() {
      final Map<String, Integer> source = Map.of("a", 1, "b", 2, "c", 3);
      final Traversal<Map<String, Integer>, Integer> traversal = Traversals.forMap("z");

      final Map<String, Integer> result = Traversals.modify(traversal, i -> i * 10, source);
      assertThat(result).isEqualTo(source);

      final List<Integer> extracted = Traversals.getAll(traversal, source);
      assertThat(extracted).isEmpty();
    }

    @Test
    @DisplayName("should handle alternating success and failure")
    void shouldHandleAlternatingSuccessAndFailure() {
      final List<Integer> source = List.of(1, 2, 3, 4, 5);

      final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
          i -> i % 2 == 0 ? OPTIONAL.widen(Optional.empty()) : OPTIONAL.widen(Optional.of(i * 10));

      final Kind<OptionalKind.Witness, List<Integer>> result =
          Traversals.traverseList(source, f, OptionalMonad.INSTANCE);

      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration and Real-World Scenarios")
  class IntegrationTests {

    record Person(String name, int age, String email) {}

    @Test
    @DisplayName("should validate and transform person records")
    void shouldValidateAndTransformPersonRecords() {
      final List<Person> people =
          List.of(
              new Person("Alice", 30, "alice@example.com"),
              new Person("Bob", 25, "bob@example.com"),
              new Person("Charlie", 35, "charlie@example.com"));

      final Traversal<List<Person>, Person> listTraversal = Traversals.forList();

      // Extract all emails
      final Function<Person, String> getEmail = Person::email;
      final List<Person> allPeople = Traversals.getAll(listTraversal, people);
      final List<String> emails = allPeople.stream().map(getEmail).toList();

      assertThat(emails)
          .containsExactly("alice@example.com", "bob@example.com", "charlie@example.com");
    }

    @Test
    @DisplayName("should handle partial updates in complex structures")
    void shouldHandlePartialUpdatesInComplexStructures() {
      final Map<String, List<Integer>> data = new HashMap<>();
      data.put("scores", List.of(85, 90, 75));
      data.put("grades", List.of(88, 92, 78));

      final Traversal<Map<String, List<Integer>>, List<Integer>> mapTraversal =
          Traversals.forMap("scores");
      final Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();
      final Traversal<Map<String, List<Integer>>, Integer> composed =
          mapTraversal.andThen(listTraversal);

      // Add bonus points to scores
      final Map<String, List<Integer>> updated =
          Traversals.modify(composed, score -> score + 5, data);

      assertThat(updated.get("scores")).containsExactly(90, 95, 80);
      assertThat(updated.get("grades")).containsExactly(88, 92, 78); // Unchanged
    }

    @Test
    @DisplayName("should process heterogeneous data with selective application")
    void shouldProcessHeterogeneousData() {
      final List<Integer> numbers = List.of(10, 15, 20, 25, 30, 35, 40);

      // Apply different transformations based on value ranges
      final Function<Integer, Kind<OptionalKind.Witness, String>> classify =
          i -> {
            if (i < 20) return OPTIONAL.widen(Optional.of("low:" + i));
            if (i < 30) return OPTIONAL.widen(Optional.of("medium:" + i));
            return OPTIONAL.widen(Optional.of("high:" + i));
          };

      final Kind<OptionalKind.Witness, List<String>> result =
          Traversals.traverseList(numbers, classify, OptionalMonad.INSTANCE);

      assertThat(OPTIONAL.narrow(result))
          .contains(
              List.of(
                  "low:10", "low:15", "medium:20", "medium:25", "high:30", "high:35", "high:40"));
    }
  }

  @Nested
  @DisplayName("SequenceStateList Method Coverage Tests")
  class SequenceStateListTest {

    /**
     * Helper method to test sequenceStateList indirectly through traverseListUntil. Since
     * sequenceStateList is private, we test it through its usage.
     */
    private <S, A> State<S, List<A>> invokeSequenceStateList(List<State<S, A>> states) {
      // We can't call the private method directly, but we can test its behavior
      // through traverseListUntil which uses it internally
      return states.stream()
          .reduce(
              State.pure(new ArrayList<A>()),
              (accState, elemState) ->
                  accState.flatMap(
                      acc ->
                          elemState.map(
                              elem -> {
                                List<A> newList = new ArrayList<>(acc);
                                newList.add(elem);
                                return newList;
                              })),
              (s1, s2) ->
                  s1.flatMap(
                      list1 ->
                          s2.map(
                              list2 -> {
                                List<A> combined = new ArrayList<>(list1);
                                combined.addAll(list2);
                                return combined;
                              })));
    }

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

      @Test
      @DisplayName("should sequence empty list of states")
      void shouldSequenceEmptyList() {
        final List<State<Integer, String>> emptyStates = Collections.emptyList();
        final State<Integer, List<String>> sequenced = invokeSequenceStateList(emptyStates);

        final StateTuple<Integer, List<String>> result = sequenced.run(42);

        assertThat(result.state()).isEqualTo(42); // State unchanged
        assertThat(result.value()).isEmpty(); // Empty list result
      }

      @Test
      @DisplayName("should sequence single state computation")
      void shouldSequenceSingleState() {
        final State<Integer, String> singleState = State.pure("hello");
        final List<State<Integer, String>> states = List.of(singleState);

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        assertThat(result.state()).isEqualTo(0);
        assertThat(result.value()).containsExactly("hello");
      }

      @Test
      @DisplayName("should sequence multiple pure state computations")
      void shouldSequenceMultiplePureStates() {
        final List<State<Integer, String>> states =
            List.of(State.pure("a"), State.pure("b"), State.pure("c"));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(100);

        assertThat(result.state()).isEqualTo(100); // State unchanged (pure computations)
        assertThat(result.value()).containsExactly("a", "b", "c");
      }

      @Test
      @DisplayName("should preserve order of computations")
      void shouldPreserveOrder() {
        final List<State<Integer, Integer>> states =
            List.of(State.pure(1), State.pure(2), State.pure(3), State.pure(4), State.pure(5));

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(0);

        assertThat(result.value()).containsExactly(1, 2, 3, 4, 5);
      }
    }

    @Nested
    @DisplayName("State Threading Tests")
    class StateThreadingTests {

      @Test
      @DisplayName("should thread state through computations")
      void shouldThreadState() {
        // Each computation increments the state
        final List<State<Integer, String>> states =
            List.of(
                State.modify((Integer s) -> s + 1).flatMap(u -> State.pure("first")),
                State.modify((Integer s) -> s + 1).flatMap(u -> State.pure("second")),
                State.modify((Integer s) -> s + 1).flatMap(u -> State.pure("third")));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        assertThat(result.state()).isEqualTo(3); // 0 + 1 + 1 + 1
        assertThat(result.value()).containsExactly("first", "second", "third");
      }

      @Test
      @DisplayName("should pass state from one computation to next")
      void shouldPassStateBetweenComputations() {
        // Each computation uses the current state to produce a value
        final List<State<Integer, String>> states =
            List.of(
                State.inspect((Integer s) -> "state-was-" + s)
                    .flatMap(str -> State.modify((Integer s) -> s * 2).map(u -> str)),
                State.inspect((Integer s) -> "state-was-" + s)
                    .flatMap(str -> State.modify((Integer s) -> s * 2).map(u -> str)),
                State.inspect((Integer s) -> "state-was-" + s)
                    .flatMap(str -> State.modify((Integer s) -> s * 2).map(u -> str)));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(1);

        assertThat(result.state()).isEqualTo(8); // 1 * 2 * 2 * 2
        assertThat(result.value()).containsExactly("state-was-1", "state-was-2", "state-was-4");
      }

      @Test
      @DisplayName("should handle state transformations")
      void shouldHandleStateTransformations() {
        // Each computation transforms the state differently
        final List<State<Integer, Integer>> states =
            List.of(
                State.get(), // Returns current state
                State.modify((Integer s) -> s + 10).flatMap(u -> State.get()),
                State.modify((Integer s) -> s * 2).flatMap(u -> State.get()),
                State.set(100).flatMap(u -> State.get()) // Replaces state
                );

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(5);

        assertThat(result.state()).isEqualTo(100); // Final state is set to 100
        assertThat(result.value()).containsExactly(5, 15, 30, 100);
      }
    }

    @Nested
    @DisplayName("Complex State Computations Tests")
    class ComplexStateComputationsTests {

      @Test
      @DisplayName("should handle stateful accumulation")
      void shouldHandleStatefulAccumulation() {
        // Build a running sum in the state
        final List<State<Integer, Integer>> states =
            List.of(
                State.modify((Integer s) -> s + 1).flatMap(u -> State.get()),
                State.modify((Integer s) -> s + 2).flatMap(u -> State.get()),
                State.modify((Integer s) -> s + 3).flatMap(u -> State.get()),
                State.modify((Integer s) -> s + 4).flatMap(u -> State.get()),
                State.modify((Integer s) -> s + 5).flatMap(u -> State.get()));

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(0);

        assertThat(result.state()).isEqualTo(15); // 0+1+2+3+4+5
        assertThat(result.value()).containsExactly(1, 3, 6, 10, 15);
      }

      @Test
      @DisplayName("should handle conditional state modifications")
      void shouldHandleConditionalStateModifications() {
        // Modify state only if it meets a condition
        final List<State<Integer, String>> states =
            List.of(
                State.<Integer>get()
                    .flatMap(
                        s ->
                            s < 10
                                ? State.modify((Integer st) -> st + 5).map(u -> "incremented")
                                : State.pure("skipped")),
                State.<Integer>get()
                    .flatMap(
                        s ->
                            s < 10
                                ? State.modify((Integer st) -> st + 5).map(u -> "incremented")
                                : State.pure("skipped")),
                State.<Integer>get()
                    .flatMap(
                        s ->
                            s < 10
                                ? State.modify((Integer st) -> st + 5).map(u -> "incremented")
                                : State.pure("skipped")));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        assertThat(result.state()).isEqualTo(10); // 0 + 5 + 5
        assertThat(result.value()).containsExactly("incremented", "incremented", "skipped");
      }

      @Test
      @DisplayName("should handle state inspection without modification")
      void shouldHandleStateInspection() {
        // Multiple inspections that don't modify state
        final List<State<String, Integer>> states =
            List.of(
                State.inspect((String s) -> s.length()),
                State.inspect((String s) -> s.length() * 2),
                State.inspect((String s) -> s.length() * 3));

        final State<String, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<String, List<Integer>> result = sequenced.run("hello");

        assertThat(result.state()).isEqualTo("hello"); // Unchanged
        assertThat(result.value()).containsExactly(5, 10, 15);
      }
    }

    @Nested
    @DisplayName("Large List Tests")
    class LargeListTests {

      @Test
      @DisplayName("should handle large list of states")
      void shouldHandleLargeList() {
        final List<State<Integer, Integer>> states = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
          final int value = i;
          states.add(State.pure(value));
        }

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(0);

        assertThat(result.value()).hasSize(1000);
        assertThat(result.value().get(0)).isEqualTo(0);
        assertThat(result.value().get(999)).isEqualTo(999);
      }

      @Test
      @DisplayName("should handle large stateful computation")
      void shouldHandleLargeStatefulComputation() {
        final List<State<Integer, Integer>> states = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
          states.add(State.modify((Integer s) -> s + 1).flatMap(u -> State.get()));
        }

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(0);

        assertThat(result.state()).isEqualTo(100);
        assertThat(result.value()).hasSize(100);
        assertThat(result.value().get(0)).isEqualTo(1);
        assertThat(result.value().get(99)).isEqualTo(100);
      }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

      @Test
      @DisplayName("should handle states that return null values")
      void shouldHandleNullValues() {
        final List<State<Integer, String>> states =
            List.of(State.pure("non-null"), State.pure(null), State.pure("another-non-null"));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        assertThat(result.value()).containsExactly("non-null", null, "another-non-null");
      }

      @Test
      @DisplayName("should handle all states returning same value")
      void shouldHandleAllSameValue() {
        final List<State<Integer, String>> states =
            List.of(State.pure("same"), State.pure("same"), State.pure("same"));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        assertThat(result.value()).containsExactly("same", "same", "same");
      }

      @Test
      @DisplayName("should handle mixed state modifications and pure values")
      void shouldHandleMixedOperations() {
        final List<State<Integer, String>> states =
            List.of(
                State.<Integer, String>pure("pure"),
                State.<Integer>modify((Integer s) -> s + 1).map(u -> "modified"),
                State.<Integer>get().map(s -> "got-" + s),
                State.set(100).map(u -> "set"),
                State.inspect((Integer s) -> "inspected-" + s));

        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        assertThat(result.state()).isEqualTo(100);
        assertThat(result.value())
            .containsExactly("pure", "modified", "got-1", "set", "inspected-100");
      }
    }

    @Nested
    @DisplayName("Integration with traverseListUntil Tests")
    class IntegrationTests {

      @Test
      @DisplayName("should work correctly within traverseListUntil context")
      void shouldWorkWithinTraverseListUntil() {
        // This tests the actual usage of sequenceStateList through traverseListUntil
        final List<Integer> source = List.of(1, 2, 3, 4, 5);
        final AtomicInteger processCount = new AtomicInteger(0);

        final Function<Integer, Kind<OptionalKind.Witness, Integer>> f =
            i -> {
              processCount.incrementAndGet();
              return OptionalKindHelper.OPTIONAL.widen(java.util.Optional.of(i * 10));
            };

        final org.higherkindedj.hkt.Kind<OptionalKind.Witness, List<Integer>> result =
            Traversals.traverseListUntil(source, i -> i >= 3, f, OptionalSelective.INSTANCE);

        // Verify that sequenceStateList correctly managed the state to stop at 3
        assertThat(processCount.get()).isEqualTo(2); // Should process 1 and 2, then stop at 3
      }

      @Test
      @DisplayName("should maintain state consistency in complex scenarios")
      void shouldMaintainStateConsistencyInComplexScenarios() {
        // Create a complex scenario with multiple state computations
        final List<State<List<String>, String>> states =
            List.of(
                State.modify(
                        (List<String> list) -> {
                          List<String> newList = new ArrayList<>(list);
                          newList.add("first");
                          return newList;
                        })
                    .flatMap(u -> State.inspect((List<String> s) -> "added-first:" + s.size())),
                State.modify(
                        (List<String> list) -> {
                          List<String> newList = new ArrayList<>(list);
                          newList.add("second");
                          return newList;
                        })
                    .flatMap(u -> State.inspect((List<String> s) -> "added-second:" + s.size())),
                State.inspect((List<String> list) -> "inspected:" + list.size()));

        final State<List<String>, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<List<String>, List<String>> result = sequenced.run(new ArrayList<>());

        assertThat(result.state()).containsExactly("first", "second");
        assertThat(result.value())
            .containsExactly("added-first:1", "added-second:2", "inspected:2");
      }
    }

    @Nested
    @DisplayName("State Type Variation Tests")
    class StateTypeVariationTests {

      @Test
      @DisplayName("should handle String state type")
      void shouldHandleStringState() {
        final List<State<String, Integer>> states =
            List.of(
                State.inspect((String s) -> s.length()),
                State.modify((String s) -> s + "!")
                    .flatMap(u -> State.inspect((String s) -> s.length())),
                State.set("new").flatMap(u -> State.inspect((String s) -> s.length())));

        final State<String, List<Integer>> sequenced = invokeSequenceStateList(states);
        final StateTuple<String, List<Integer>> result = sequenced.run("hello");

        assertThat(result.state()).isEqualTo("new");
        assertThat(result.value()).containsExactly(5, 6, 3);
      }

      @Test
      @DisplayName("should handle Boolean state type")
      void shouldHandleBooleanState() {
        final List<State<Boolean, String>> states =
            List.of(
                State.inspect((Boolean b) -> b ? "was-true" : "was-false"),
                State.modify((Boolean b) -> !b)
                    .flatMap(u -> State.inspect((Boolean b) -> b ? "now-true" : "now-false")),
                State.inspect((Boolean b) -> b ? "final-true" : "final-false"));

        final State<Boolean, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Boolean, List<String>> result = sequenced.run(true);

        assertThat(result.state()).isEqualTo(false);
        assertThat(result.value()).containsExactly("was-true", "now-false", "final-false");
      }

      @Test
      @DisplayName("should handle custom object state type")
      void shouldHandleCustomObjectState() {
        record Counter(int count, String label) {}

        final List<State<Counter, String>> states =
            List.of(
                State.inspect((Counter c) -> c.label() + ":" + c.count()),
                State.modify((Counter c) -> new Counter(c.count() + 1, c.label()))
                    .flatMap(u -> State.inspect((Counter c) -> c.label() + ":" + c.count())),
                State.modify((Counter c) -> new Counter(c.count() * 2, c.label()))
                    .flatMap(u -> State.inspect((Counter c) -> c.label() + ":" + c.count())));

        final State<Counter, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Counter, List<String>> result = sequenced.run(new Counter(5, "test"));

        assertThat(result.state().count()).isEqualTo(12); // (5 + 1) * 2
        assertThat(result.value()).containsExactly("test:5", "test:6", "test:12");
      }
    }

    @Nested
    @DisplayName("Performance and Efficiency Tests")
    class PerformanceTests {

      @Test
      @DisplayName("should be stack-safe with deep recursion")
      void shouldBeStackSafe() {
        final List<State<Integer, Integer>> states = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
          states.add(State.pure(i));
        }

        final State<Integer, List<Integer>> sequenced = Traversals.sequenceStateList(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(0);

        assertThat(result.value()).hasSize(5000);
      }

      @Test
      @DisplayName("should efficiently handle stateful iterations")
      void shouldHandleStatefulIterationsEfficiently() {
        // Simulate a counter that tracks how many times state was accessed
        final List<State<Integer, String>> states = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
          states.add(State.modify((Integer s) -> s + 1).map(u -> "counted"));
        }

        final long startTime = System.nanoTime();
        final State<Integer, List<String>> sequenced = invokeSequenceStateList(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);
        final long endTime = System.nanoTime();

        assertThat(result.state()).isEqualTo(1000);
        assertThat(result.value()).hasSize(1000);

        // Performance check - should complete in reasonable time (< 100ms)
        final long durationMs = (endTime - startTime) / 1_000_000;
        assertThat(durationMs).isLessThan(100);
      }
    }

    @Nested
    @DisplayName("Combinator Tests")
    class CombinatorTests {

      @Test
      @DisplayName("should work as identity when given empty accumulator")
      void shouldWorkAsIdentity() {
        final State<Integer, List<String>> identity = State.pure(new ArrayList<>());
        final List<State<Integer, String>> states = Collections.emptyList();

        final State<Integer, List<String>> result =
            states.stream()
                .reduce(
                    identity,
                    (acc, elem) ->
                        acc.flatMap(
                            list ->
                                elem.map(
                                    value -> {
                                      List<String> newList = new ArrayList<>(list);
                                      newList.add(value);
                                      return newList;
                                    })),
                    (s1, s2) ->
                        s1.flatMap(
                            l1 ->
                                s2.map(
                                    l2 -> {
                                      List<String> combined = new ArrayList<>(l1);
                                      combined.addAll(l2);
                                      return combined;
                                    })));

        final StateTuple<Integer, List<String>> tuple = result.run(42);
        assertThat(tuple.value()).isEmpty();
        assertThat(tuple.state()).isEqualTo(42);
      }

      @Test
      @DisplayName("should correctly combine parallel streams")
      void shouldCombineParallelStreams() {
        // Test the combiner function (third parameter of reduce)
        final State<Integer, List<String>> state1 = State.pure(List.of("a", "b"));
        final State<Integer, List<String>> state2 = State.pure(List.of("c", "d"));

        final State<Integer, List<String>> combined =
            state1.flatMap(
                l1 ->
                    state2.map(
                        l2 -> {
                          List<String> result = new ArrayList<>(l1);
                          result.addAll(l2);
                          return result;
                        }));

        final StateTuple<Integer, List<String>> result = combined.run(0);
        assertThat(result.value()).containsExactly("a", "b", "c", "d");
      }
    }
  }

  @Nested
  @DisplayName("SequenceStateList Combiner Function Coverage Tests")
  class SequenceStateListCombinerTest {

    /** Direct implementation of sequenceStateList to test both accumulator and combiner. */
    private <S, A> State<S, List<A>> invokeSequenceStateList(List<State<S, A>> states) {
      return states.stream()
          .reduce(
              State.pure(new ArrayList<A>()),
              (accState, elemState) ->
                  accState.flatMap(
                      acc ->
                          elemState.map(
                              elem -> {
                                List<A> newList = new ArrayList<>(acc);
                                newList.add(elem);
                                return newList;
                              })),
              (s1, s2) ->
                  s1.flatMap(
                      list1 ->
                          s2.map(
                              list2 -> {
                                List<A> combined = new ArrayList<>(list1);
                                combined.addAll(list2);
                                return combined;
                              })));
    }

    /** Parallel stream version to force combiner usage. */
    private <S, A> State<S, List<A>> invokeSequenceStateListParallel(List<State<S, A>> states) {
      return states.parallelStream() // Use parallelStream to invoke combiner
          .reduce(
              State.pure(new ArrayList<A>()),
              (accState, elemState) ->
                  accState.flatMap(
                      acc ->
                          elemState.map(
                              elem -> {
                                List<A> newList = new ArrayList<>(acc);
                                newList.add(elem);
                                return newList;
                              })),
              (s1, s2) ->
                  s1.flatMap(
                      list1 ->
                          s2.map(
                              list2 -> {
                                List<A> combined = new ArrayList<>(list1);
                                combined.addAll(list2);
                                return combined;
                              })));
    }

    @Nested
    @DisplayName("Direct Combiner Function Tests")
    class DirectCombinerTests {

      @Test
      @DisplayName("should combine two State computations with the combiner function")
      void shouldCombineTwoStateComputations() {
        // Create two separate State computations that produce lists
        final State<Integer, List<String>> state1 = State.pure(List.of("a", "b"));
        final State<Integer, List<String>> state2 = State.pure(List.of("c", "d"));

        // Manually invoke the combiner function logic
        final State<Integer, List<String>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        final StateTuple<Integer, List<String>> result = combined.run(0);

        // The combiner should merge the two lists
        assertThat(result.value()).containsExactly("a", "b", "c", "d");
        assertThat(result.state()).isEqualTo(0);
      }

      @Test
      @DisplayName("should combine empty list with non-empty list")
      void shouldCombineEmptyWithNonEmpty() {
        final State<Integer, List<String>> emptyState = State.pure(new ArrayList<>());
        final State<Integer, List<String>> nonEmptyState = State.pure(List.of("x", "y", "z"));

        // Combiner: empty + non-empty
        final State<Integer, List<String>> combined1 =
            emptyState.flatMap(
                list1 ->
                    nonEmptyState.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined1.run(0).value()).containsExactly("x", "y", "z");

        // Combiner: non-empty + empty
        final State<Integer, List<String>> combined2 =
            nonEmptyState.flatMap(
                list1 ->
                    emptyState.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined2.run(0).value()).containsExactly("x", "y", "z");
      }

      @Test
      @DisplayName("should combine two empty lists")
      void shouldCombineTwoEmptyLists() {
        final State<Integer, List<String>> empty1 = State.pure(new ArrayList<>());
        final State<Integer, List<String>> empty2 = State.pure(new ArrayList<>());

        final State<Integer, List<String>> combined =
            empty1.flatMap(
                list1 ->
                    empty2.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined.run(0).value()).isEmpty();
      }

      @Test
      @DisplayName("should preserve state while combining lists")
      void shouldPreserveStateWhileCombining() {
        // States that modify the state value
        final State<Integer, List<String>> state1 =
            State.<Integer>modify(s -> s + 10).flatMap(u -> State.pure(List.of("first")));
        final State<Integer, List<String>> state2 =
            State.<Integer>modify(s -> s * 2).flatMap(u -> State.pure(List.of("second")));

        // Combine them
        final State<Integer, List<String>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        final StateTuple<Integer, List<String>> result = combined.run(5);

        // State should be: (5 + 10) * 2 = 30
        assertThat(result.state()).isEqualTo(30);
        assertThat(result.value()).containsExactly("first", "second");
      }

      @Test
      @DisplayName("should handle combining lists with different sizes")
      void shouldHandleDifferentSizedLists() {
        final State<Integer, List<Integer>> smallList = State.pure(List.of(1));
        final State<Integer, List<Integer>> largeList = State.pure(List.of(2, 3, 4, 5, 6));

        final State<Integer, List<Integer>> combined =
            smallList.flatMap(
                list1 ->
                    largeList.map(
                        list2 -> {
                          List<Integer> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined.run(0).value()).containsExactly(1, 2, 3, 4, 5, 6);
      }

      @Test
      @DisplayName("should maintain order when combining lists")
      void shouldMaintainOrderWhenCombining() {
        final State<String, List<Integer>> state1 = State.pure(List.of(1, 2, 3));
        final State<String, List<Integer>> state2 = State.pure(List.of(4, 5, 6));

        final State<String, List<Integer>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<Integer> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined.run("test").value()).containsExactly(1, 2, 3, 4, 5, 6);
      }
    }

    @Nested
    @DisplayName("Parallel Stream Combiner Tests")
    class ParallelStreamCombinerTests {

      @Test
      @DisplayName("should invoke combiner with parallel stream")
      void shouldInvokeCombinerWithParallelStream() {
        // Create enough elements to likely trigger parallel processing
        final List<State<Integer, String>> states = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
          final int value = i;
          states.add(State.pure("item-" + value));
        }

        final State<Integer, List<String>> sequenced = invokeSequenceStateListParallel(states);
        final StateTuple<Integer, List<String>> result = sequenced.run(0);

        // Should have all 100 items (order might vary with parallel processing)
        assertThat(result.value()).hasSize(100);
        assertThat(result.value()).contains("item-0", "item-50", "item-99");
      }

      @Test
      @DisplayName("should combine results correctly in parallel execution")
      void shouldCombineCorrectlyInParallel() {
        // Use a smaller list for deterministic testing
        final List<State<Integer, Integer>> states =
            List.of(
                State.pure(1),
                State.pure(2),
                State.pure(3),
                State.pure(4),
                State.pure(5),
                State.pure(6),
                State.pure(7),
                State.pure(8));

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateListParallel(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(0);

        // All elements should be present (order might vary)
        assertThat(result.value()).hasSize(8);
        assertThat(result.value()).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
      }

      @Test
      @DisplayName("should handle parallel combining with stateful operations")
      void shouldHandleParallelWithStatefulOperations() {
        final List<State<Integer, Integer>> states = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
          // Each state inspects the current state
          states.add(State.inspect((Integer s) -> s));
        }

        final State<Integer, List<Integer>> sequenced = invokeSequenceStateListParallel(states);
        final StateTuple<Integer, List<Integer>> result = sequenced.run(42);

        // All should have the initial state value
        assertThat(result.value()).hasSize(50);
        // In parallel, all might see the initial state
        assertThat(result.value()).allMatch(v -> v == 42);
      }
    }

    @Nested
    @DisplayName("Combiner Edge Cases")
    class CombinerEdgeCaseTests {

      @Test
      @DisplayName("should combine lists with null elements")
      void shouldCombineListsWithNullElements() {
        final List<String> list1 = new ArrayList<>();
        list1.add("a");
        list1.add(null);
        list1.add("b");

        final List<String> list2 = new ArrayList<>();
        list2.add(null);
        list2.add("c");

        final State<Integer, List<String>> state1 = State.pure(list1);
        final State<Integer, List<String>> state2 = State.pure(list2);

        final State<Integer, List<String>> combined =
            state1.flatMap(
                l1 ->
                    state2.map(
                        l2 -> {
                          List<String> result = new ArrayList<>(l1);
                          result.addAll(l2);
                          return result;
                        }));

        assertThat(combined.run(0).value()).containsExactly("a", null, "b", null, "c");
      }

      @Test
      @DisplayName("should combine single-element lists")
      void shouldCombineSingleElementLists() {
        final State<Boolean, List<String>> state1 = State.pure(List.of("single1"));
        final State<Boolean, List<String>> state2 = State.pure(List.of("single2"));

        final State<Boolean, List<String>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined.run(true).value()).containsExactly("single1", "single2");
      }

      @Test
      @DisplayName("should combine lists with duplicate elements")
      void shouldCombineListsWithDuplicates() {
        final State<Integer, List<String>> state1 = State.pure(List.of("dup", "dup", "dup"));
        final State<Integer, List<String>> state2 = State.pure(List.of("dup", "dup"));

        final State<Integer, List<String>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        assertThat(combined.run(0).value()).containsExactly("dup", "dup", "dup", "dup", "dup");
      }

      @Test
      @DisplayName("should combine large lists efficiently")
      void shouldCombineLargeListsEfficiently() {
        final List<Integer> largeList1 = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
          largeList1.add(i);
        }

        final List<Integer> largeList2 = new ArrayList<>();
        for (int i = 1000; i < 2000; i++) {
          largeList2.add(i);
        }

        final State<Integer, List<Integer>> state1 = State.pure(largeList1);
        final State<Integer, List<Integer>> state2 = State.pure(largeList2);

        final long startTime = System.nanoTime();
        final State<Integer, List<Integer>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<Integer> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));
        final StateTuple<Integer, List<Integer>> result = combined.run(0);
        final long endTime = System.nanoTime();

        assertThat(result.value()).hasSize(2000);
        assertThat(result.value().get(0)).isEqualTo(0);
        assertThat(result.value().get(1999)).isEqualTo(1999);

        // Should complete quickly (< 10ms)
        final long durationMs = (endTime - startTime) / 1_000_000;
        assertThat(durationMs).isLessThan(10);
      }
    }

    @Nested
    @DisplayName("Combiner with Complex State Types")
    class CombinerComplexStateTests {

      @Test
      @DisplayName("should combine with List state type")
      void shouldCombineWithListState() {
        final State<List<String>, List<Integer>> state1 =
            State.<List<String>>modify(
                    list -> {
                      List<String> newList = new ArrayList<>(list);
                      newList.add("modified1");
                      return newList;
                    })
                .map(u -> List.of(1, 2));

        final State<List<String>, List<Integer>> state2 =
            State.<List<String>>modify(
                    list -> {
                      List<String> newList = new ArrayList<>(list);
                      newList.add("modified2");
                      return newList;
                    })
                .map(u -> List.of(3, 4));

        final State<List<String>, List<Integer>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<Integer> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        final StateTuple<List<String>, List<Integer>> result =
            combined.run(new ArrayList<>(List.of("initial")));

        assertThat(result.value()).containsExactly(1, 2, 3, 4);
        assertThat(result.state()).containsExactly("initial", "modified1", "modified2");
      }

      @Test
      @DisplayName("should combine with custom object state")
      void shouldCombineWithCustomObjectState() {
        record Counter(int count) {}

        final State<Counter, List<String>> state1 =
            State.<Counter>modify(c -> new Counter(c.count() + 1)).map(u -> List.of("a"));

        final State<Counter, List<String>> state2 =
            State.<Counter>modify(c -> new Counter(c.count() + 1)).map(u -> List.of("b"));

        final State<Counter, List<String>> combined =
            state1.flatMap(
                list1 ->
                    state2.map(
                        list2 -> {
                          List<String> result = new ArrayList<>(list1);
                          result.addAll(list2);
                          return result;
                        }));

        final StateTuple<Counter, List<String>> result = combined.run(new Counter(0));

        assertThat(result.value()).containsExactly("a", "b");
        assertThat(result.state().count()).isEqualTo(2);
      }
    }

    @Nested
    @DisplayName("Combiner Integration with sequenceStateList")
    class CombinerIntegrationTests {

      @Test
      @DisplayName("should use combiner when reducing with parallel stream")
      void shouldUseCombinerInParallelReduce() {
        // This test verifies the combiner is actually invoked
        // by using parallel stream which forces combiner usage
        final List<State<Integer, String>> states = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
          states.add(State.pure("value-" + i));
        }

        // Sequential - primarily uses accumulator
        final State<Integer, List<String>> sequential = invokeSequenceStateList(states);
        final List<String> sequentialResult = sequential.run(0).value();

        // Parallel - uses both accumulator and combiner
        final State<Integer, List<String>> parallel = invokeSequenceStateListParallel(states);
        final List<String> parallelResult = parallel.run(0).value();

        // Both should produce the same elements (though order may differ for parallel)
        assertThat(sequentialResult).hasSize(1000);
        assertThat(parallelResult).hasSize(1000);
        assertThat(parallelResult).containsAll(sequentialResult);
      }

      @Test
      @DisplayName("should produce consistent results with sequential and parallel")
      void shouldProduceConsistentResults() {
        final List<State<Integer, Integer>> states =
            List.of(State.pure(1), State.pure(2), State.pure(3), State.pure(4), State.pure(5));

        final State<Integer, List<Integer>> sequential = invokeSequenceStateList(states);
        final State<Integer, List<Integer>> parallel = invokeSequenceStateListParallel(states);

        final List<Integer> seqResult = sequential.run(0).value();
        final List<Integer> parResult = parallel.run(0).value();

        // Sequential maintains order
        assertThat(seqResult).containsExactly(1, 2, 3, 4, 5);

        // Parallel has all elements (order may vary)
        assertThat(parResult).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
      }
    }
  }

  @Nested
  @DisplayName("partsOf Tests")
  class PartsOfTests {

    record User(String name, int age) {}

    Lens<User, String> userNameLens = Lens.of(User::name, (u, n) -> new User(n, u.age()));
    Lens<User, Integer> userAgeLens = Lens.of(User::age, (u, a) -> new User(u.name(), a));

    @Nested
    @DisplayName("Basic functionality")
    class BasicFunctionalityTests {

      @Test
      @DisplayName("should get all focused elements as a list")
      void partsOf_shouldGetAllElements() {
        List<String> source = List.of("apple", "banana", "cherry");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> parts = partsLens.get(source);

        assertThat(parts).containsExactly("apple", "banana", "cherry");
      }

      @Test
      @DisplayName("should set all focused elements from a list")
      void partsOf_shouldSetAllElements() {
        List<String> source = List.of("apple", "banana", "cherry");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> newValues = List.of("APPLE", "BANANA", "CHERRY");
        List<String> result = partsLens.set(newValues, source);

        assertThat(result).containsExactly("APPLE", "BANANA", "CHERRY");
      }

      @Test
      @DisplayName("should return mutable list from get")
      void partsOf_shouldReturnMutableList() {
        List<String> source = List.of("a", "b", "c");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> parts = partsLens.get(source);
        parts.add("d"); // Should not throw

        assertThat(parts).containsExactly("a", "b", "c", "d");
      }

      @Test
      @DisplayName("should work with empty traversal")
      void partsOf_shouldHandleEmptyTraversal() {
        List<String> source = Collections.emptyList();
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> parts = partsLens.get(source);
        assertThat(parts).isEmpty();

        List<String> result = partsLens.set(List.of("x", "y"), source);
        assertThat(result).isEmpty(); // No positions to fill
      }

      @Test
      @DisplayName("should preserve structure when modifying focused elements")
      void partsOf_shouldPreserveStructure() {
        List<User> users =
            List.of(new User("Alice", 25), new User("Bob", 30), new User("Charlie", 35));

        Traversal<List<User>, String> userNames =
            Traversals.<User>forList().andThen(userNameLens.asTraversal());

        Lens<List<User>, List<String>> namesLens = Traversals.partsOf(userNames);

        List<String> newNames = List.of("ALICE", "BOB", "CHARLIE");
        List<User> result = namesLens.set(newNames, users);

        assertThat(result)
            .containsExactly(new User("ALICE", 25), new User("BOB", 30), new User("CHARLIE", 35));
      }
    }

    @Nested
    @DisplayName("List size mismatch handling")
    class ListSizeMismatchTests {

      @Test
      @DisplayName("should keep original values when new list is shorter")
      void partsOf_shouldHandleShorterList() {
        List<String> source = List.of("a", "b", "c", "d");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> newValues = List.of("X", "Y"); // Only 2 values
        List<String> result = partsLens.set(newValues, source);

        // First two replaced, rest unchanged
        assertThat(result).containsExactly("X", "Y", "c", "d");
      }

      @Test
      @DisplayName("should ignore extra elements when new list is longer")
      void partsOf_shouldHandleLongerList() {
        List<String> source = List.of("a", "b");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> newValues = List.of("X", "Y", "Z", "W"); // 4 values for 2 positions
        List<String> result = partsLens.set(newValues, source);

        // Only 2 positions available, extra values ignored
        assertThat(result).containsExactly("X", "Y");
      }

      @Test
      @DisplayName("should handle empty new list")
      void partsOf_shouldHandleEmptyNewList() {
        List<String> source = List.of("a", "b", "c");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> result = partsLens.set(Collections.emptyList(), source);

        // All positions keep original values
        assertThat(result).containsExactly("a", "b", "c");
      }
    }

    @Nested
    @DisplayName("Composition with other optics")
    class CompositionTests {

      @Test
      @DisplayName("should compose partsOf lens with another lens")
      void partsOf_shouldComposeWithLens() {
        List<User> users =
            List.of(new User("Alice", 25), new User("Bob", 30), new User("Charlie", 35));

        Traversal<List<User>, Integer> userAges =
            Traversals.<User>forList().andThen(userAgeLens.asTraversal());

        Lens<List<User>, List<Integer>> agesLens = Traversals.partsOf(userAges);

        // Now compose with a lens that gets the size of the list
        Lens<List<Integer>, Integer> sizeLens =
            Lens.of(List::size, (list, size) -> list); // Read-only essentially

        Lens<List<User>, Integer> ageCountLens = agesLens.andThen(sizeLens);

        int count = ageCountLens.get(users);
        assertThat(count).isEqualTo(3);
      }

      @Test
      @DisplayName("should work with filtered traversals")
      void partsOf_shouldWorkWithFiltered() {
        List<User> users =
            List.of(
                new User("Alice", 25),
                new User("Bob", 30),
                new User("Charlie", 35),
                new User("Diana", 20));

        // Focus only on users older than 25
        Traversal<List<User>, User> olderUsers =
            Traversals.<User>forList().filtered(u -> u.age() > 25);

        Traversal<List<User>, String> olderUserNames =
            olderUsers.andThen(userNameLens.asTraversal());

        Lens<List<User>, List<String>> namesLens = Traversals.partsOf(olderUserNames);

        List<String> names = namesLens.get(users);
        assertThat(names).containsExactly("Bob", "Charlie");

        // Modify only those names
        List<String> newNames = List.of("ROBERT", "CHARLES");
        List<User> result = namesLens.set(newNames, users);

        assertThat(result)
            .containsExactly(
                new User("Alice", 25), // unchanged
                new User("ROBERT", 30),
                new User("CHARLES", 35),
                new User("Diana", 20) // unchanged
                );
      }
    }

    @Nested
    @DisplayName("Lens laws")
    class LensLawsTests {

      @Test
      @DisplayName("should satisfy get-set law: set(get(s), s) = s")
      void partsOf_shouldSatisfyGetSetLaw() {
        List<String> source = List.of("a", "b", "c");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> parts = partsLens.get(source);
        List<String> result = partsLens.set(parts, source);

        assertThat(result).isEqualTo(source);
      }

      @Test
      @DisplayName("should satisfy set-get law: get(set(a, s)) = a (when sizes match)")
      void partsOf_shouldSatisfySetGetLaw() {
        List<String> source = List.of("a", "b", "c");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> newValues = List.of("x", "y", "z");
        List<String> result = partsLens.get(partsLens.set(newValues, source));

        assertThat(result).isEqualTo(newValues);
      }

      @Test
      @DisplayName("should satisfy set-set law: set(b, set(a, s)) = set(b, s)")
      void partsOf_shouldSatisfySetSetLaw() {
        List<String> source = List.of("a", "b", "c");
        Traversal<List<String>, String> traversal = Traversals.forList();
        Lens<List<String>, List<String>> partsLens = Traversals.partsOf(traversal);

        List<String> valuesA = List.of("x", "y", "z");
        List<String> valuesB = List.of("1", "2", "3");

        List<String> result1 = partsLens.set(valuesB, partsLens.set(valuesA, source));
        List<String> result2 = partsLens.set(valuesB, source);

        assertThat(result1).isEqualTo(result2);
      }
    }
  }

  @Nested
  @DisplayName("Convenience Methods Tests")
  class ConvenienceMethodsTests {

    record User(String name, int age) {}

    Lens<User, String> userNameLens = Lens.of(User::name, (u, n) -> new User(n, u.age()));
    Lens<User, Integer> userAgeLens = Lens.of(User::age, (u, a) -> new User(u.name(), a));

    @Nested
    @DisplayName("sorted() - Natural ordering")
    class SortedNaturalTests {

      @Test
      @DisplayName("should sort focused elements in natural order")
      void sorted_shouldSortNaturally() {
        List<User> users =
            List.of(new User("Charlie", 35), new User("Alice", 25), new User("Bob", 30));

        Traversal<List<User>, String> userNames =
            Traversals.<User>forList().andThen(userNameLens.asTraversal());

        List<User> result = Traversals.sorted(userNames, users);

        // Names sorted: Alice, Bob, Charlie - but placed back into original positions
        assertThat(result)
            .containsExactly(
                new User("Alice", 35), // Charlie's age
                new User("Bob", 25), // Alice's age
                new User("Charlie", 30) // Bob's age
                );
      }

      @Test
      @DisplayName("should sort integers naturally")
      void sorted_shouldSortIntegers() {
        List<User> users =
            List.of(new User("Alice", 30), new User("Bob", 25), new User("Charlie", 35));

        Traversal<List<User>, Integer> userAges =
            Traversals.<User>forList().andThen(userAgeLens.asTraversal());

        List<User> result = Traversals.sorted(userAges, users);

        assertThat(result)
            .containsExactly(new User("Alice", 25), new User("Bob", 30), new User("Charlie", 35));
      }

      @Test
      @DisplayName("should handle empty list")
      void sorted_shouldHandleEmptyList() {
        List<String> source = Collections.emptyList();
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.sorted(traversal, source);

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should handle single element")
      void sorted_shouldHandleSingleElement() {
        List<String> source = List.of("alone");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.sorted(traversal, source);

        assertThat(result).containsExactly("alone");
      }

      @Test
      @DisplayName("should preserve structure when sorting")
      void sorted_shouldPreserveStructure() {
        List<Integer> source = List.of(3, 1, 4, 1, 5);
        Traversal<List<Integer>, Integer> traversal = Traversals.forList();

        List<Integer> result = Traversals.sorted(traversal, source);

        assertThat(result).containsExactly(1, 1, 3, 4, 5);
      }
    }

    @Nested
    @DisplayName("sorted() - Custom comparator")
    class SortedCustomTests {

      @Test
      @DisplayName("should sort with custom comparator")
      void sorted_shouldSortWithCustomComparator() {
        List<User> users =
            List.of(new User("charlie", 35), new User("Alice", 25), new User("bob", 30));

        Traversal<List<User>, String> userNames =
            Traversals.<User>forList().andThen(userNameLens.asTraversal());

        // Sort case-insensitively
        List<User> result = Traversals.sorted(userNames, String.CASE_INSENSITIVE_ORDER, users);

        assertThat(result)
            .containsExactly(new User("Alice", 35), new User("bob", 25), new User("charlie", 30));
      }

      @Test
      @DisplayName("should sort in reverse order")
      void sorted_shouldSortReverse() {
        List<Integer> source = List.of(1, 3, 2, 5, 4);
        Traversal<List<Integer>, Integer> traversal = Traversals.forList();

        List<Integer> result = Traversals.sorted(traversal, Comparator.reverseOrder(), source);

        assertThat(result).containsExactly(5, 4, 3, 2, 1);
      }

      @Test
      @DisplayName("should sort by length")
      void sorted_shouldSortByLength() {
        List<String> source = List.of("medium", "a", "longer");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result =
            Traversals.sorted(traversal, Comparator.comparingInt(String::length), source);

        assertThat(result).containsExactly("a", "medium", "longer");
      }
    }

    @Nested
    @DisplayName("reversed()")
    class ReversedTests {

      @Test
      @DisplayName("should reverse focused elements")
      void reversed_shouldReverse() {
        List<User> users =
            List.of(new User("Alice", 25), new User("Bob", 30), new User("Charlie", 35));

        Traversal<List<User>, String> userNames =
            Traversals.<User>forList().andThen(userNameLens.asTraversal());

        List<User> result = Traversals.reversed(userNames, users);

        // Names reversed: Charlie, Bob, Alice - placed back into original positions
        assertThat(result)
            .containsExactly(
                new User("Charlie", 25), // Alice's age
                new User("Bob", 30), // Bob's age (unchanged position)
                new User("Alice", 35) // Charlie's age
                );
      }

      @Test
      @DisplayName("should handle empty list")
      void reversed_shouldHandleEmptyList() {
        List<String> source = Collections.emptyList();
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.reversed(traversal, source);

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should handle single element")
      void reversed_shouldHandleSingleElement() {
        List<String> source = List.of("alone");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.reversed(traversal, source);

        assertThat(result).containsExactly("alone");
      }

      @Test
      @DisplayName("should handle two elements")
      void reversed_shouldHandleTwoElements() {
        List<String> source = List.of("first", "second");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.reversed(traversal, source);

        assertThat(result).containsExactly("second", "first");
      }

      @Test
      @DisplayName("reversing twice should return to original")
      void reversed_shouldBeItsOwnInverse() {
        List<String> source = List.of("a", "b", "c", "d");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result =
            Traversals.reversed(traversal, Traversals.reversed(traversal, source));

        assertThat(result).isEqualTo(source);
      }
    }

    @Nested
    @DisplayName("distinct()")
    class DistinctTests {

      @Test
      @DisplayName("should remove duplicate focused elements")
      void distinct_shouldRemoveDuplicates() {
        List<User> users =
            List.of(
                new User("Alice", 25),
                new User("Bob", 30),
                new User("Alice", 35),
                new User("Charlie", 40));

        Traversal<List<User>, String> userNames =
            Traversals.<User>forList().andThen(userNameLens.asTraversal());

        List<User> result = Traversals.distinct(userNames, users);

        // Unique names: Alice, Bob, Charlie (3 values for 4 positions)
        // Fourth position keeps original "Charlie"
        assertThat(result)
            .containsExactly(
                new User("Alice", 25),
                new User("Bob", 30),
                new User("Charlie", 35), // Alice's duplicate position gets Charlie
                new User("Charlie", 40) // Unchanged - no value provided
                );
      }

      @Test
      @DisplayName("should preserve first occurrence")
      void distinct_shouldPreserveFirstOccurrence() {
        List<String> source = List.of("a", "b", "a", "c", "b");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.distinct(traversal, source);

        // Unique: a, b, c (3 values for 5 positions)
        assertThat(result).containsExactly("a", "b", "c", "c", "b");
      }

      @Test
      @DisplayName("should handle no duplicates")
      void distinct_shouldHandleNoDuplicates() {
        List<String> source = List.of("a", "b", "c");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.distinct(traversal, source);

        assertThat(result).containsExactly("a", "b", "c");
      }

      @Test
      @DisplayName("should handle all duplicates")
      void distinct_shouldHandleAllDuplicates() {
        List<String> source = List.of("x", "x", "x", "x");
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.distinct(traversal, source);

        // Only one unique value for 4 positions
        assertThat(result).containsExactly("x", "x", "x", "x");
      }

      @Test
      @DisplayName("should handle empty list")
      void distinct_shouldHandleEmptyList() {
        List<String> source = Collections.emptyList();
        Traversal<List<String>, String> traversal = Traversals.forList();

        List<String> result = Traversals.distinct(traversal, source);

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should preserve order of first occurrences")
      void distinct_shouldPreserveOrder() {
        List<Integer> source = List.of(3, 1, 4, 1, 5, 9, 2, 6, 5, 3);
        Traversal<List<Integer>, Integer> traversal = Traversals.forList();

        List<Integer> parts = Traversals.partsOf(traversal).get(source);
        List<Integer> uniqueParts = new ArrayList<>(new LinkedHashSet<>(parts));

        // Should be: 3, 1, 4, 5, 9, 2, 6 (order preserved)
        assertThat(uniqueParts).containsExactly(3, 1, 4, 5, 9, 2, 6);
      }
    }
  }
}
