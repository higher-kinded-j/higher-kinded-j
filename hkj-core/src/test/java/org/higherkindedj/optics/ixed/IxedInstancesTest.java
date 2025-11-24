// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.ixed;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.function.Function;
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IxedInstances Tests")
class IxedInstancesTest {

  @Nested
  @DisplayName("Map Ixed Instance")
  class MapIxTests {

    private final Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

    @Test
    @DisplayName("ix() should return traversal focusing on zero or one element")
    void ixReturnsTraversal() {
      Traversal<Map<String, Integer>, Integer> traversal = mapIx.ix("key");
      assertThat(traversal).isNotNull();
    }

    @Test
    @DisplayName("get() should return empty for missing key")
    void getMissingKey() {
      Map<String, Integer> map = new HashMap<>();
      Optional<Integer> result = IxedInstances.get(mapIx, "missing", map);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("get() should return present value for existing key")
    void getExistingKey() {
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);
      Optional<Integer> result = IxedInstances.get(mapIx, "alice", map);
      assertThat(result).hasValue(100);
    }

    @Test
    @DisplayName("get() should treat null values as empty (Java Optional limitation)")
    void getNullValue() {
      Map<String, Integer> map = new HashMap<>();
      map.put("nullKey", null);

      Optional<Integer> nullResult = IxedInstances.get(mapIx, "nullKey", map);
      Optional<Integer> absentResult = IxedInstances.get(mapIx, "absent", map);

      // Java's Optional cannot hold null values
      assertThat(nullResult).isEmpty();
      assertThat(absentResult).isEmpty();
    }

    @Test
    @DisplayName("update() should modify existing entry")
    void updateExistingEntry() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = IxedInstances.update(mapIx, "alice", 150, original);

      assertThat(updated).containsEntry("alice", 150);
      assertThat(original).containsEntry("alice", 100); // Original unchanged
    }

    @Test
    @DisplayName("update() should be no-op for missing key (cannot insert)")
    void updateMissingKey() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = IxedInstances.update(mapIx, "bob", 85, original);

      // Unlike At.insertOrUpdate, Ixed.update does NOT insert new keys
      assertThat(updated).containsEntry("alice", 100).doesNotContainKey("bob").hasSize(1);
    }

    @Test
    @DisplayName("modify() should update existing value with function")
    void modifyExistingValue() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = IxedInstances.modify(mapIx, "alice", x -> x + 10, original);

      assertThat(updated).containsEntry("alice", 110);
    }

    @Test
    @DisplayName("modify() should be no-op for missing key")
    void modifyMissingKey() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = IxedInstances.modify(mapIx, "missing", x -> x + 10, original);

      assertThat(updated).containsExactlyEntriesOf(original);
    }

    @Test
    @DisplayName("contains() should return true for existing key")
    void containsExistingKey() {
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);

      assertThat(IxedInstances.contains(mapIx, "alice", map)).isTrue();
    }

    @Test
    @DisplayName("contains() should return false for missing key")
    void containsMissingKey() {
      Map<String, Integer> map = new HashMap<>();

      assertThat(IxedInstances.contains(mapIx, "alice", map)).isFalse();
    }

    @Test
    @DisplayName("Traversal should focus on zero elements for missing key")
    void traversalFocusesZeroElements() {
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);

      List<Integer> results = Traversals.getAll(mapIx.ix("missing"), map);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Traversal should focus on one element for existing key")
    void traversalFocusesOneElement() {
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);
      map.put("bob", 85);

      List<Integer> results = Traversals.getAll(mapIx.ix("alice"), map);

      assertThat(results).containsExactly(100);
    }

    @Test
    @DisplayName("Traversal composition should work with map ix")
    void traversalComposition() {
      record Container(Map<String, Integer> scores) {}
      Lens<Container, Map<String, Integer>> scoresLens =
          Lens.of(Container::scores, (c, s) -> new Container(s));

      Container original = new Container(Map.of("alice", 100, "bob", 85));

      // Compose to get traversal focusing on alice's score
      Traversal<Container, Integer> aliceScoreTraversal =
          scoresLens.asTraversal().andThen(mapIx.ix("alice"));

      List<Integer> scores = Traversals.getAll(aliceScoreTraversal, original);
      assertThat(scores).containsExactly(100);

      Container updated = Traversals.modify(aliceScoreTraversal, x -> x + 50, original);
      assertThat(updated.scores()).containsEntry("alice", 150).containsEntry("bob", 85);
    }

    @Test
    @DisplayName("Multiple Ixed operations should chain correctly")
    void chainingOperations() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);
      original.put("bob", 85);
      original.put("charlie", 90);

      Map<String, Integer> result =
          IxedInstances.modify(
              mapIx,
              "alice",
              x -> x + 10,
              IxedInstances.modify(mapIx, "bob", x -> x + 5, original));

      assertThat(result)
          .containsEntry("alice", 110)
          .containsEntry("bob", 90)
          .containsEntry("charlie", 90);
    }
  }

  @Nested
  @DisplayName("List Ixed Instance")
  class ListIxTests {

    private final Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();

    @Test
    @DisplayName("get() should return empty for out of bounds index")
    void getOutOfBounds() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(IxedInstances.get(listIx, -1, list)).isEmpty();
      assertThat(IxedInstances.get(listIx, 3, list)).isEmpty();
      assertThat(IxedInstances.get(listIx, 100, list)).isEmpty();
    }

    @Test
    @DisplayName("get() should return element at valid index")
    void getValidIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(IxedInstances.get(listIx, 0, list)).hasValue("a");
      assertThat(IxedInstances.get(listIx, 1, list)).hasValue("b");
      assertThat(IxedInstances.get(listIx, 2, list)).hasValue("c");
    }

    @Test
    @DisplayName("update() should modify element at valid index")
    void updateValidIndex() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = IxedInstances.update(listIx, 1, "B", original);

      assertThat(updated).containsExactly("a", "B", "c");
      assertThat(original).containsExactly("a", "b", "c"); // Original unchanged
    }

    @Test
    @DisplayName("update() should be no-op for out of bounds index (cannot insert)")
    void updateOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      // Unlike At.insertOrUpdate, Ixed.update does NOT insert at out-of-bounds indices
      List<String> updatedHigh = IxedInstances.update(listIx, 5, "x", original);
      List<String> updatedNegative = IxedInstances.update(listIx, -1, "x", original);

      assertThat(updatedHigh).containsExactly("a", "b", "c");
      assertThat(updatedNegative).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("modify() should update element with function")
    void modifyValidIndex() {
      List<String> original = new ArrayList<>(List.of("hello", "world"));

      List<String> updated = IxedInstances.modify(listIx, 0, String::toUpperCase, original);

      assertThat(updated).containsExactly("HELLO", "world");
    }

    @Test
    @DisplayName("modify() should be no-op for out of bounds index")
    void modifyOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("hello", "world"));

      List<String> updated = IxedInstances.modify(listIx, 10, String::toUpperCase, original);

      assertThat(updated).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("contains() should check index validity")
    void containsChecksIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(IxedInstances.contains(listIx, 0, list)).isTrue();
      assertThat(IxedInstances.contains(listIx, 2, list)).isTrue();
      assertThat(IxedInstances.contains(listIx, 3, list)).isFalse();
      assertThat(IxedInstances.contains(listIx, -1, list)).isFalse();
    }

    @Test
    @DisplayName("Traversal should focus on zero elements for out of bounds")
    void traversalFocusesZeroElements() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      List<String> results = Traversals.getAll(listIx.ix(10), list);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Traversal should focus on one element for valid index")
    void traversalFocusesOneElement() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      List<String> results = Traversals.getAll(listIx.ix(1), list);

      assertThat(results).containsExactly("b");
    }

    @Test
    @DisplayName("Traversal composition should work with list ix")
    void traversalComposition() {
      record Container(List<String> items) {}
      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));

      Container original = new Container(new ArrayList<>(List.of("alice", "bob", "charlie")));

      // Compose to get traversal focusing on first item
      Traversal<Container, String> firstItemTraversal =
          itemsLens.asTraversal().andThen(listIx.ix(0));

      List<String> items = Traversals.getAll(firstItemTraversal, original);
      assertThat(items).containsExactly("alice");

      Container updated = Traversals.modify(firstItemTraversal, String::toUpperCase, original);
      assertThat(updated.items()).containsExactly("ALICE", "bob", "charlie");
    }

    @Test
    @DisplayName("Multiple indices can be modified independently")
    void multipleIndicesModified() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c", "d"));

      List<String> result =
          IxedInstances.modify(
              listIx,
              0,
              String::toUpperCase,
              IxedInstances.modify(listIx, 2, String::toUpperCase, original));

      assertThat(result).containsExactly("A", "b", "C", "d");
    }
  }

  @Nested
  @DisplayName("Generic Ixed Factory Methods")
  class GenericFactoryTests {

    @Test
    @DisplayName("fromAt() should create Ixed from any At instance")
    void fromAtCreatesIxed() {
      var mapAt = AtInstances.<String, Integer>mapAt();
      Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.fromAt(mapAt);

      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);

      assertThat(IxedInstances.get(mapIx, "alice", map)).hasValue(100);
      assertThat(IxedInstances.get(mapIx, "missing", map)).isEmpty();

      Map<String, Integer> updated = IxedInstances.update(mapIx, "alice", 200, map);
      assertThat(updated).containsEntry("alice", 200);
    }

    @Test
    @DisplayName("listIxFrom() should create Ixed from custom At instance")
    void listIxFromCreatesIxed() {
      var paddingAt = AtInstances.<String>listAtWithPadding("X");
      Ixed<List<String>, Integer, String> listIx = IxedInstances.listIxFrom(paddingAt);

      List<String> list = new ArrayList<>(List.of("a", "b"));

      // Even though underlying At supports padding, Ixed.update won't insert
      List<String> updated = IxedInstances.update(listIx, 5, "z", list);

      // Update at non-existent index is no-op
      assertThat(updated).containsExactly("a", "b");

      // But update at existing index works
      List<String> modified = IxedInstances.update(listIx, 0, "A", list);
      assertThat(modified).containsExactly("A", "b");
    }
  }

  @Nested
  @DisplayName("Ixed vs At Semantic Differences")
  class SemanticDifferenceTests {

    @Test
    @DisplayName("At can insert new entries, Ixed cannot")
    void insertionDifference() {
      var mapAt = AtInstances.<String, Integer>mapAt();
      var mapIx = IxedInstances.<String, Integer>mapIx();

      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      // At can insert
      Map<String, Integer> afterAtInsert = mapAt.insertOrUpdate("bob", 85, original);
      assertThat(afterAtInsert).containsKey("bob");

      // Ixed cannot insert
      Map<String, Integer> afterIxUpdate = IxedInstances.update(mapIx, "charlie", 90, original);
      assertThat(afterIxUpdate).doesNotContainKey("charlie");
    }

    @Test
    @DisplayName("At can delete entries, Ixed cannot")
    void deletionDifference() {
      var mapAt = AtInstances.<String, Integer>mapAt();
      var mapIx = IxedInstances.<String, Integer>mapIx();

      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);
      original.put("bob", 85);

      // At can delete
      Map<String, Integer> afterAtRemove = mapAt.remove("alice", original);
      assertThat(afterAtRemove).doesNotContainKey("alice");

      // Ixed has no remove method - can only update existing values
      // This is by design: Ixed focuses on existing elements only
      assertThat(mapIx).isNotNull();
    }

    @Test
    @DisplayName("Ixed preserves structure, At can change it")
    void structurePreservation() {
      var listAt = AtInstances.<String>listAt();
      var listIx = IxedInstances.<String>listIx();

      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      // At remove changes structure (list size)
      List<String> afterAtRemove = listAt.remove(1, original);
      assertThat(afterAtRemove).hasSize(2);

      // Ixed update preserves structure (list size)
      List<String> afterIxUpdate = IxedInstances.update(listIx, 1, "B", original);
      assertThat(afterIxUpdate).hasSize(3);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Empty map operations should be safe")
    void emptyMapOperations() {
      var mapIx = IxedInstances.<String, Integer>mapIx();
      Map<String, Integer> emptyMap = new HashMap<>();

      // Get from empty map
      assertThat(IxedInstances.get(mapIx, "any", emptyMap)).isEmpty();

      // Update on empty map (no-op)
      Map<String, Integer> afterUpdate = IxedInstances.update(mapIx, "any", 100, emptyMap);
      assertThat(afterUpdate).isEmpty();

      // Modify on empty map (no-op)
      Map<String, Integer> afterModify = IxedInstances.modify(mapIx, "any", x -> x + 1, emptyMap);
      assertThat(afterModify).isEmpty();

      // Contains on empty map
      assertThat(IxedInstances.contains(mapIx, "any", emptyMap)).isFalse();
    }

    @Test
    @DisplayName("Empty list operations should be safe")
    void emptyListOperations() {
      var listIx = IxedInstances.<String>listIx();
      List<String> emptyList = new ArrayList<>();

      // Get from empty list
      assertThat(IxedInstances.get(listIx, 0, emptyList)).isEmpty();

      // Update on empty list (no-op)
      List<String> afterUpdate = IxedInstances.update(listIx, 0, "x", emptyList);
      assertThat(afterUpdate).isEmpty();

      // Modify on empty list (no-op)
      List<String> afterModify = IxedInstances.modify(listIx, 0, String::toUpperCase, emptyList);
      assertThat(afterModify).isEmpty();

      // Contains on empty list
      assertThat(IxedInstances.contains(listIx, 0, emptyList)).isFalse();
    }

    @Test
    @DisplayName("Null elements in list should be handled")
    void nullElementsInList() {
      var listIx = IxedInstances.<String>listIx();
      List<String> list = new ArrayList<>();
      list.add("a");
      list.add(null);
      list.add("c");

      // Null elements appear as empty (Java Optional limitation)
      Optional<String> result = IxedInstances.get(listIx, 1, list);
      assertThat(result).isEmpty();

      // Contains reports false for null element
      assertThat(IxedInstances.contains(listIx, 1, list)).isFalse();

      // Update of null element position is a no-op. Since the element at index 1 is null,
      // the underlying At treats it as absent (Optional.empty()), and Ixed does not perform
      // insertions or deletions. The list remains unchanged.
      List<String> updated = IxedInstances.update(listIx, 1, "b", list);
      assertThat(updated).containsExactly("a", null, "c");
    }

    @Test
    @DisplayName("Large index values should be handled gracefully")
    void largeIndexValues() {
      var listIx = IxedInstances.<String>listIx();
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      // Very large index
      assertThat(IxedInstances.get(listIx, Integer.MAX_VALUE, list)).isEmpty();
      assertThat(IxedInstances.contains(listIx, Integer.MAX_VALUE, list)).isFalse();

      List<String> updated = IxedInstances.update(listIx, Integer.MAX_VALUE, "x", list);
      assertThat(updated).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Negative index values should be handled gracefully")
    void negativeIndexValues() {
      var listIx = IxedInstances.<String>listIx();
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      // Negative index
      assertThat(IxedInstances.get(listIx, Integer.MIN_VALUE, list)).isEmpty();
      assertThat(IxedInstances.contains(listIx, Integer.MIN_VALUE, list)).isFalse();

      List<String> updated = IxedInstances.update(listIx, Integer.MIN_VALUE, "x", list);
      assertThat(updated).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Identity function in modify should preserve value")
    void identityModify() {
      var mapIx = IxedInstances.<String, Integer>mapIx();
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> result = IxedInstances.modify(mapIx, "alice", x -> x, original);

      assertThat(result).containsEntry("alice", 100);
    }

    @Test
    @DisplayName("Immutability should be preserved across operations")
    void immutabilityPreserved() {
      var listIx = IxedInstances.<String>listIx();
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated1 = IxedInstances.update(listIx, 0, "A", original);
      List<String> updated2 = IxedInstances.update(listIx, 1, "B", original);
      List<String> updated3 = IxedInstances.modify(listIx, 2, String::toUpperCase, original);

      // All operations should return different list instances
      assertThat(original).containsExactly("a", "b", "c");
      assertThat(updated1).containsExactly("A", "b", "c");
      assertThat(updated2).containsExactly("a", "B", "c");
      assertThat(updated3).containsExactly("a", "b", "C");

      // All should be distinct objects
      assertThat(original).isNotSameAs(updated1);
      assertThat(original).isNotSameAs(updated2);
      assertThat(original).isNotSameAs(updated3);
    }
  }

  @Nested
  @DisplayName("Traversal Properties")
  class TraversalPropertyTests {

    @Test
    @DisplayName("Ixed traversal should satisfy identity law")
    void identityLaw() {
      var mapIx = IxedInstances.<String, Integer>mapIx();
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);

      // Modifying with identity should return equivalent map
      Map<String, Integer> result = Traversals.modify(mapIx.ix("alice"), x -> x, map);

      assertThat(result).containsExactlyEntriesOf(map);
    }

    @Test
    @DisplayName("Ixed traversal should satisfy composition law")
    void compositionLaw() {
      var mapIx = IxedInstances.<String, Integer>mapIx();
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);

      // f . g should equal doing g then f
      Function<Integer, Integer> f = x -> x + 10;
      Function<Integer, Integer> g = x -> x * 2;

      Map<String, Integer> composed = Traversals.modify(mapIx.ix("alice"), f.compose(g), map);
      Map<String, Integer> sequential =
          Traversals.modify(mapIx.ix("alice"), f, Traversals.modify(mapIx.ix("alice"), g, map));

      assertThat(composed).containsExactlyEntriesOf(sequential);
    }
  }
}
