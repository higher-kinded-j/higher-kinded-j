// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IndexedTraversals Utility Tests")
class IndexedTraversalsTest {

  @Nested
  @DisplayName("filteredByIndex() - Conditional indexed access")
  class FilteredByIndexTests {

    @Test
    @DisplayName("should focus when predicate matches")
    void focusWhenPredicateMatches() {
      IndexedTraversal<String, Pair<String, Integer>, Integer> filtered =
          IndexedTraversals.filteredByIndex((String index) -> index.equals("target"));

      Pair<String, Integer> source = new Pair<>("target", 42);
      Pair<String, Integer> result =
          IndexedTraversals.imodify(filtered, (idx, val) -> val * 2, source);

      assertThat(result.first()).isEqualTo("target");
      assertThat(result.second()).isEqualTo(84);
    }

    @Test
    @DisplayName("should not focus when predicate does not match")
    void doNotFocusWhenPredicateDoesNotMatch() {
      IndexedTraversal<String, Pair<String, Integer>, Integer> filtered =
          IndexedTraversals.filteredByIndex((String index) -> index.equals("target"));

      Pair<String, Integer> source = new Pair<>("other", 42);
      Pair<String, Integer> result =
          IndexedTraversals.imodify(filtered, (idx, val) -> val * 2, source);

      assertThat(result.first()).isEqualTo("other");
      assertThat(result.second()).isEqualTo(42); // Unchanged
    }

    @Test
    @DisplayName("should work with numeric indices")
    void workWithNumericIndices() {
      IndexedTraversal<Integer, Pair<Integer, String>, String> evenOnly =
          IndexedTraversals.filteredByIndex((Integer i) -> i % 2 == 0);

      Pair<Integer, String> even = new Pair<>(2, "even");
      Pair<Integer, String> odd = new Pair<>(3, "odd");

      Pair<Integer, String> evenResult =
          IndexedTraversals.imodify(evenOnly, (i, s) -> s.toUpperCase(), even);
      Pair<Integer, String> oddResult =
          IndexedTraversals.imodify(evenOnly, (i, s) -> s.toUpperCase(), odd);

      assertThat(evenResult.second()).isEqualTo("EVEN");
      assertThat(oddResult.second()).isEqualTo("odd"); // Unchanged
    }

    @Test
    @DisplayName("should extract indexed values only when predicate matches")
    void extractOnlyWhenMatches() {
      IndexedTraversal<String, Pair<String, Integer>, Integer> filtered =
          IndexedTraversals.filteredByIndex((String index) -> index.startsWith("a"));

      Pair<String, Integer> matching = new Pair<>("abc", 10);
      Pair<String, Integer> notMatching = new Pair<>("xyz", 20);

      List<Pair<String, Integer>> matchingList =
          IndexedTraversals.toIndexedList(filtered, matching);
      List<Pair<String, Integer>> notMatchingList =
          IndexedTraversals.toIndexedList(filtered, notMatching);

      assertThat(matchingList).hasSize(1);
      assertThat(matchingList.get(0).first()).isEqualTo("abc");
      assertThat(matchingList.get(0).second()).isEqualTo(10);

      assertThat(notMatchingList).isEmpty();
    }
  }

  @Nested
  @DisplayName("Pair/Tuple2 conversion methods")
  class ConversionTests {

    @Test
    @DisplayName("pairToTuple2() should convert correctly")
    void pairToTuple2() {
      Pair<String, Integer> pair = new Pair<>("hello", 42);
      Tuple2<String, Integer> tuple = IndexedTraversals.pairToTuple2(pair);

      assertThat(tuple._1()).isEqualTo("hello");
      assertThat(tuple._2()).isEqualTo(42);
    }

    @Test
    @DisplayName("tuple2ToPair() should convert correctly")
    void tuple2ToPair() {
      Tuple2<String, Integer> tuple = new Tuple2<>("world", 99);
      Pair<String, Integer> pair = IndexedTraversals.tuple2ToPair(tuple);

      assertThat(pair.first()).isEqualTo("world");
      assertThat(pair.second()).isEqualTo(99);
    }

    @Test
    @DisplayName("pairToTuple2() and tuple2ToPair() should be inverses")
    void roundTripConversion() {
      Pair<String, Integer> original = new Pair<>("test", 123);
      Pair<String, Integer> roundTrip =
          IndexedTraversals.tuple2ToPair(IndexedTraversals.pairToTuple2(original));

      assertThat(roundTrip).isEqualTo(original);
    }

    @Test
    @DisplayName("pairsToTuple2s() should convert list of pairs")
    void pairsToTuple2s() {
      List<Pair<String, Integer>> pairs =
          List.of(new Pair<>("a", 1), new Pair<>("b", 2), new Pair<>("c", 3));

      List<Tuple2<String, Integer>> tuples = IndexedTraversals.pairsToTuple2s(pairs);

      assertThat(tuples).hasSize(3);
      assertThat(tuples.get(0)._1()).isEqualTo("a");
      assertThat(tuples.get(0)._2()).isEqualTo(1);
      assertThat(tuples.get(1)._1()).isEqualTo("b");
      assertThat(tuples.get(1)._2()).isEqualTo(2);
      assertThat(tuples.get(2)._1()).isEqualTo("c");
      assertThat(tuples.get(2)._2()).isEqualTo(3);
    }

    @Test
    @DisplayName("tuple2sToPairs() should convert list of tuples")
    void tuple2sToPairs() {
      List<Tuple2<String, Integer>> tuples =
          List.of(new Tuple2<>("x", 10), new Tuple2<>("y", 20), new Tuple2<>("z", 30));

      List<Pair<String, Integer>> pairs = IndexedTraversals.tuple2sToPairs(tuples);

      assertThat(pairs).hasSize(3);
      assertThat(pairs.get(0).first()).isEqualTo("x");
      assertThat(pairs.get(0).second()).isEqualTo(10);
      assertThat(pairs.get(1).first()).isEqualTo("y");
      assertThat(pairs.get(1).second()).isEqualTo(20);
      assertThat(pairs.get(2).first()).isEqualTo("z");
      assertThat(pairs.get(2).second()).isEqualTo(30);
    }

    @Test
    @DisplayName("should handle empty lists")
    void handleEmptyLists() {
      List<Pair<String, Integer>> emptyPairs = List.of();
      List<Tuple2<String, Integer>> emptyTuples = List.of();

      assertThat(IndexedTraversals.pairsToTuple2s(emptyPairs)).isEmpty();
      assertThat(IndexedTraversals.tuple2sToPairs(emptyTuples)).isEmpty();
    }
  }

  @Nested
  @DisplayName("foldList() and foldMap() - IndexedFold factory methods")
  class FoldFactoryTests {

    @Test
    @DisplayName("foldList() should create read-only indexed fold for lists")
    void foldListCreation() {
      IndexedFold<Integer, List<String>, String> fold = IndexedTraversals.foldList();
      List<String> source = List.of("a", "b", "c");

      List<Pair<Integer, String>> indexed = fold.toIndexedList(source);

      assertThat(indexed)
          .containsExactly(new Pair<>(0, "a"), new Pair<>(1, "b"), new Pair<>(2, "c"));
    }

    @Test
    @DisplayName("foldList() should handle empty lists")
    void foldListEmpty() {
      IndexedFold<Integer, List<String>, String> fold = IndexedTraversals.foldList();
      List<String> source = List.of();

      List<Pair<Integer, String>> indexed = fold.toIndexedList(source);

      assertThat(indexed).isEmpty();
    }

    @Test
    @DisplayName("foldMap() should create read-only indexed fold for maps")
    void foldMapCreation() {
      IndexedFold<String, Map<String, Integer>, Integer> fold = IndexedTraversals.foldMap();
      Map<String, Integer> source = Map.of("a", 1, "b", 2);

      List<Pair<String, Integer>> indexed = fold.toIndexedList(source);

      assertThat(indexed).hasSize(2);
      assertThat(indexed).anyMatch(p -> p.first().equals("a") && p.second().equals(1));
      assertThat(indexed).anyMatch(p -> p.first().equals("b") && p.second().equals(2));
    }

    @Test
    @DisplayName("foldMap() should handle empty maps")
    void foldMapEmpty() {
      IndexedFold<String, Map<String, Integer>, Integer> fold = IndexedTraversals.foldMap();
      Map<String, Integer> source = Map.of();

      List<Pair<String, Integer>> indexed = fold.toIndexedList(source);

      assertThat(indexed).isEmpty();
    }

    @Test
    @DisplayName("foldList() should support all IndexedFold operations")
    void foldListOperations() {
      IndexedFold<Integer, List<Integer>, Integer> fold = IndexedTraversals.foldList();
      List<Integer> source = List.of(10, 20, 30, 40);

      // Test exists
      assertThat(fold.exists(v -> v > 25, source)).isTrue();
      assertThat(fold.exists(v -> v > 100, source)).isFalse();

      // Test all
      assertThat(fold.all(v -> v > 0, source)).isTrue();
      assertThat(fold.all(v -> v > 25, source)).isFalse();

      // Test length
      assertThat(fold.length(source)).isEqualTo(4);

      // Test isEmpty
      assertThat(fold.isEmpty(source)).isFalse();
      assertThat(fold.isEmpty(List.of())).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge cases and special scenarios")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle null values in pairs")
    void handleNullInPairs() {
      Pair<String, Integer> pairWithNull = new Pair<>(null, 42);
      Tuple2<String, Integer> tuple = IndexedTraversals.pairToTuple2(pairWithNull);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isEqualTo(42);
    }

    @Test
    @DisplayName("filteredByIndex should work with complex predicates")
    void complexPredicates() {
      IndexedTraversal<Integer, Pair<Integer, String>, String> multipleOfThree =
          IndexedTraversals.filteredByIndex((Integer i) -> i % 3 == 0 && i > 0);

      Pair<Integer, String> match = new Pair<>(6, "six");
      Pair<Integer, String> noMatch1 = new Pair<>(0, "zero"); // 0 is not > 0
      Pair<Integer, String> noMatch2 = new Pair<>(5, "five"); // 5 is not multiple of 3

      assertThat(IndexedTraversals.toIndexedList(multipleOfThree, match)).hasSize(1);
      assertThat(IndexedTraversals.toIndexedList(multipleOfThree, noMatch1)).isEmpty();
      assertThat(IndexedTraversals.toIndexedList(multipleOfThree, noMatch2)).isEmpty();
    }

    @Test
    @DisplayName("conversion methods should preserve types correctly")
    void typePreservation() {
      // Test with different types
      Pair<Long, Double> pair = new Pair<>(123L, 45.67);
      Tuple2<Long, Double> tuple = IndexedTraversals.pairToTuple2(pair);
      Pair<Long, Double> backToPair = IndexedTraversals.tuple2ToPair(tuple);

      assertThat(backToPair.first()).isInstanceOf(Long.class).isEqualTo(123L);
      assertThat(backToPair.second()).isInstanceOf(Double.class).isEqualTo(45.67);
    }
  }
}
