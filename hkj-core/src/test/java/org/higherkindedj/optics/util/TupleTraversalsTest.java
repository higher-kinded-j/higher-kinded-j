// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TupleTraversals - Tuple-specific Traversals")
class TupleTraversalsTest {

  @Nested
  @DisplayName("both() - Focus on both elements of Tuple2<A, A>")
  class BothTests {

    @Test
    @DisplayName("both() should modify both elements")
    void bothModifiesBothElements() {
      Traversal<Tuple2<Integer, Integer>, Integer> bothTraversal = TupleTraversals.both();

      Tuple2<Integer, Integer> pair = new Tuple2<>(10, 20);
      Tuple2<Integer, Integer> result = Traversals.modify(bothTraversal, x -> x * 2, pair);

      assertThat(result).isEqualTo(new Tuple2<>(20, 40));
    }

    @Test
    @DisplayName("both() getAll should return both elements")
    void bothGetAllReturnsBothElements() {
      Traversal<Tuple2<Integer, Integer>, Integer> bothTraversal = TupleTraversals.both();

      Tuple2<Integer, Integer> pair = new Tuple2<>(10, 20);
      List<Integer> result = Traversals.getAll(bothTraversal, pair);

      assertThat(result).containsExactly(10, 20);
    }

    @Test
    @DisplayName("both() with strings should apply transformation to both")
    void bothWithStrings() {
      Traversal<Tuple2<String, String>, String> bothTraversal = TupleTraversals.both();

      Tuple2<String, String> pair = new Tuple2<>("alice", "bob");
      Tuple2<String, String> result = Traversals.modify(bothTraversal, String::toUpperCase, pair);

      assertThat(result).isEqualTo(new Tuple2<>("ALICE", "BOB"));
    }

    @Test
    @DisplayName("both() should preserve order")
    void bothPreservesOrder() {
      Traversal<Tuple2<String, String>, String> bothTraversal = TupleTraversals.both();

      Tuple2<String, String> pair = new Tuple2<>("first", "second");
      List<String> result = Traversals.getAll(bothTraversal, pair);

      assertThat(result).containsExactly("first", "second");
    }

    @Test
    @DisplayName("both() with identity function should return unchanged tuple")
    void bothWithIdentity() {
      Traversal<Tuple2<Integer, Integer>, Integer> bothTraversal = TupleTraversals.both();

      Tuple2<Integer, Integer> pair = new Tuple2<>(42, 99);
      Tuple2<Integer, Integer> result = Traversals.modify(bothTraversal, x -> x, pair);

      assertThat(result).isEqualTo(pair);
    }

    @Test
    @DisplayName("both() should compose with list traversal")
    void bothComposesWithListTraversal() {
      Traversal<List<Tuple2<Integer, Integer>>, Integer> composedTraversal =
          Traversals.<Tuple2<Integer, Integer>>forList().andThen(TupleTraversals.both());

      List<Tuple2<Integer, Integer>> pairs =
          List.of(new Tuple2<>(1, 2), new Tuple2<>(3, 4), new Tuple2<>(5, 6));

      List<Integer> allInts = Traversals.getAll(composedTraversal, pairs);

      assertThat(allInts).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("both() should compose with list traversal for modification")
    void bothComposesWithListTraversalForModification() {
      Traversal<List<Tuple2<Integer, Integer>>, Integer> composedTraversal =
          Traversals.<Tuple2<Integer, Integer>>forList().andThen(TupleTraversals.both());

      List<Tuple2<Integer, Integer>> pairs = List.of(new Tuple2<>(1, 2), new Tuple2<>(3, 4));

      List<Tuple2<Integer, Integer>> result =
          Traversals.modify(composedTraversal, x -> x * 10, pairs);

      assertThat(result).containsExactly(new Tuple2<>(10, 20), new Tuple2<>(30, 40));
    }

    @Test
    @DisplayName("both() with same values should modify both to same new value")
    void bothWithSameValues() {
      Traversal<Tuple2<Integer, Integer>, Integer> bothTraversal = TupleTraversals.both();

      Tuple2<Integer, Integer> pair = new Tuple2<>(5, 5);
      Tuple2<Integer, Integer> result = Traversals.modify(bothTraversal, x -> x + 1, pair);

      assertThat(result).isEqualTo(new Tuple2<>(6, 6));
    }

    @Test
    @DisplayName("both() should handle complex transformations")
    void bothHandlesComplexTransformations() {
      Traversal<Tuple2<Integer, Integer>, Integer> bothTraversal = TupleTraversals.both();

      Tuple2<Integer, Integer> pair = new Tuple2<>(3, 5);
      Tuple2<Integer, Integer> result = Traversals.modify(bothTraversal, x -> x * x + 1, pair);

      assertThat(result).isEqualTo(new Tuple2<>(10, 26)); // 3^2+1=10, 5^2+1=26
    }

    @Test
    @DisplayName("both() with filtered should apply predicate to both")
    void bothComposesWithFiltered() {
      Traversal<Tuple2<Integer, Integer>, Integer> bothTraversal = TupleTraversals.both();
      Traversal<Tuple2<Integer, Integer>, Integer> evenOnly =
          bothTraversal.filtered(x -> x % 2 == 0);

      Tuple2<Integer, Integer> pair = new Tuple2<>(2, 3);
      Tuple2<Integer, Integer> result = Traversals.modify(evenOnly, x -> x * 10, pair);

      assertThat(result).isEqualTo(new Tuple2<>(20, 3)); // Only 2 is even, so only it gets modified
    }
  }
}
