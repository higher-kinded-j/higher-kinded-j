// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.at;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AtInstances Tests")
class AtInstancesTest {

  @Nested
  @DisplayName("Map At Instance")
  class MapAtTests {

    private final At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

    @Test
    @DisplayName("at() should return lens focusing on optional value")
    void atReturnsLens() {
      Lens<Map<String, Integer>, Optional<Integer>> lens = mapAt.at("key");
      assertThat(lens).isNotNull();
    }

    @Test
    @DisplayName("get() should return empty for missing key")
    void getMissingKey() {
      Map<String, Integer> map = new HashMap<>();
      Optional<Integer> result = mapAt.get("missing", map);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("get() should return present value for existing key")
    void getExistingKey() {
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);
      Optional<Integer> result = mapAt.get("alice", map);
      assertThat(result).hasValue(100);
    }

    @Test
    @DisplayName("get() should treat null values as empty (Java Optional limitation)")
    void getNullValue() {
      Map<String, Integer> map = new HashMap<>();
      map.put("nullKey", null);

      Optional<Integer> nullResult = mapAt.get("nullKey", map);
      Optional<Integer> absentResult = mapAt.get("absent", map);

      // Note: Java's Optional cannot hold null values.
      // Optional.ofNullable(null) returns Optional.empty()
      // So null map values are indistinguishable from absent keys
      assertThat(nullResult).isEmpty();

      // Absent key
      assertThat(absentResult).isEmpty();
    }

    @Test
    @DisplayName("insertOrUpdate() should add new entry")
    void insertNewEntry() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = mapAt.insertOrUpdate("bob", 85, original);

      assertThat(updated).containsEntry("alice", 100).containsEntry("bob", 85);
      assertThat(original).doesNotContainKey("bob"); // Original unchanged
    }

    @Test
    @DisplayName("insertOrUpdate() should update existing entry")
    void updateExistingEntry() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = mapAt.insertOrUpdate("alice", 150, original);

      assertThat(updated).containsEntry("alice", 150);
      assertThat(original).containsEntry("alice", 100); // Original unchanged
    }

    @Test
    @DisplayName("remove() should delete existing entry")
    void removeExistingEntry() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);
      original.put("bob", 85);

      Map<String, Integer> updated = mapAt.remove("alice", original);

      assertThat(updated).doesNotContainKey("alice").containsEntry("bob", 85);
      assertThat(original).containsKey("alice"); // Original unchanged
    }

    @Test
    @DisplayName("remove() should be no-op for missing key")
    void removeMissingKey() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = mapAt.remove("missing", original);

      assertThat(updated).containsEntry("alice", 100).hasSize(1);
    }

    @Test
    @DisplayName("modify() should update existing value with function")
    void modifyExistingValue() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = mapAt.modify("alice", x -> x + 10, original);

      assertThat(updated).containsEntry("alice", 110);
    }

    @Test
    @DisplayName("modify() should be no-op for missing key")
    void modifyMissingKey() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = mapAt.modify("missing", x -> x + 10, original);

      assertThat(updated).containsExactlyEntriesOf(original);
    }

    @Test
    @DisplayName("contains() should return true for existing key")
    void containsExistingKey() {
      Map<String, Integer> map = new HashMap<>();
      map.put("alice", 100);

      assertThat(mapAt.contains("alice", map)).isTrue();
    }

    @Test
    @DisplayName("contains() should return false for missing key")
    void containsMissingKey() {
      Map<String, Integer> map = new HashMap<>();

      assertThat(mapAt.contains("alice", map)).isFalse();
    }

    @Test
    @DisplayName("set() with Optional.empty() should remove key")
    void setEmptyRemovesKey() {
      Map<String, Integer> original = new HashMap<>();
      original.put("alice", 100);

      Map<String, Integer> updated = mapAt.set("alice", Optional.empty(), original);

      assertThat(updated).doesNotContainKey("alice");
    }

    @Test
    @DisplayName("set() with Optional.of() should insert/update key")
    void setPresentInsertsOrUpdates() {
      Map<String, Integer> original = new HashMap<>();

      Map<String, Integer> updated = mapAt.set("alice", Optional.of(100), original);

      assertThat(updated).containsEntry("alice", 100);
    }

    @Test
    @DisplayName("Lens composition should work with map at")
    void lensComposition() {
      record Container(Map<String, Integer> scores) {}
      Lens<Container, Map<String, Integer>> scoresLens =
          Lens.of(Container::scores, (c, s) -> new Container(s));

      Container original = new Container(Map.of("alice", 100));

      // Compose lenses
      Lens<Container, Optional<Integer>> aliceScoreLens = scoresLens.andThen(mapAt.at("alice"));

      Optional<Integer> score = aliceScoreLens.get(original);
      assertThat(score).hasValue(100);

      Container updated = aliceScoreLens.set(Optional.of(150), original);
      assertThat(updated.scores()).containsEntry("alice", 150);
    }
  }

  @Nested
  @DisplayName("List At Instance")
  class ListAtTests {

    private final At<List<String>, Integer, String> listAt = AtInstances.listAt();

    @Test
    @DisplayName("get() should return empty for out of bounds index")
    void getOutOfBounds() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(listAt.get(-1, list)).isEmpty();
      assertThat(listAt.get(3, list)).isEmpty();
      assertThat(listAt.get(100, list)).isEmpty();
    }

    @Test
    @DisplayName("get() should return element at valid index")
    void getValidIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(listAt.get(0, list)).hasValue("a");
      assertThat(listAt.get(1, list)).hasValue("b");
      assertThat(listAt.get(2, list)).hasValue("c");
    }

    @Test
    @DisplayName("insertOrUpdate() should update element at valid index")
    void updateValidIndex() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = listAt.insertOrUpdate(1, "B", original);

      assertThat(updated).containsExactly("a", "B", "c");
      assertThat(original).containsExactly("a", "b", "c"); // Original unchanged
    }

    @Test
    @DisplayName("insertOrUpdate() should throw for out of bounds index")
    void updateOutOfBounds() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThatThrownBy(() -> listAt.insertOrUpdate(5, "x", list))
          .isInstanceOf(IndexOutOfBoundsException.class);

      assertThatThrownBy(() -> listAt.insertOrUpdate(-1, "x", list))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("remove() should delete element and shift indices")
    void removeShiftsIndices() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c", "d"));

      List<String> updated = listAt.remove(1, original);

      assertThat(updated).containsExactly("a", "c", "d");
      assertThat(original).containsExactly("a", "b", "c", "d"); // Original unchanged
    }

    @Test
    @DisplayName("remove() should be no-op for out of bounds index")
    void removeOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = listAt.remove(10, original);

      assertThat(updated).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("modify() should update element with function")
    void modifyValidIndex() {
      List<String> original = new ArrayList<>(List.of("hello", "world"));

      List<String> updated = listAt.modify(0, String::toUpperCase, original);

      assertThat(updated).containsExactly("HELLO", "world");
    }

    @Test
    @DisplayName("modify() should be no-op for out of bounds index")
    void modifyOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("hello", "world"));

      List<String> updated = listAt.modify(10, String::toUpperCase, original);

      assertThat(updated).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("contains() should check index validity")
    void containsChecksIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(listAt.contains(0, list)).isTrue();
      assertThat(listAt.contains(2, list)).isTrue();
      assertThat(listAt.contains(3, list)).isFalse();
      assertThat(listAt.contains(-1, list)).isFalse();
    }

    @Test
    @DisplayName("Lens from at() should be composable")
    void lensComposition() {
      record Container(List<String> items) {}
      Lens<Container, List<String>> itemsLens =
          Lens.of(Container::items, (c, i) -> new Container(i));

      Container original = new Container(new ArrayList<>(List.of("a", "b", "c")));

      Lens<Container, Optional<String>> firstItemLens = itemsLens.andThen(listAt.at(0));

      assertThat(firstItemLens.get(original)).hasValue("a");

      Container updated = firstItemLens.set(Optional.of("A"), original);
      assertThat(updated.items()).containsExactly("A", "b", "c");
    }
  }

  @Nested
  @DisplayName("List At With Padding Instance")
  class ListAtWithPaddingTests {

    private final At<List<String>, Integer, String> listAt = AtInstances.listAtWithPadding(null);

    @Test
    @DisplayName("insertOrUpdate() should pad list for out of bounds index")
    void padForOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("a", "b"));

      List<String> updated = listAt.insertOrUpdate(5, "f", original);

      assertThat(updated).containsExactly("a", "b", null, null, null, "f");
      assertThat(original).containsExactly("a", "b"); // Original unchanged
    }

    @Test
    @DisplayName("insertOrUpdate() should throw for negative index")
    void throwForNegativeIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b"));

      assertThatThrownBy(() -> listAt.insertOrUpdate(-1, "x", list))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("custom default value should be used for padding")
    void customDefaultValue() {
      At<List<String>, Integer, String> listAtWithDefault = AtInstances.listAtWithPadding("EMPTY");

      List<String> original = new ArrayList<>(List.of("a"));

      List<String> updated = listAtWithDefault.insertOrUpdate(3, "d", original);

      assertThat(updated).containsExactly("a", "EMPTY", "EMPTY", "d");
    }

    @Test
    @DisplayName("get() should return empty for out of bounds index")
    void getOutOfBounds() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(listAt.get(-1, list)).isEmpty();
      assertThat(listAt.get(3, list)).isEmpty();
      assertThat(listAt.get(100, list)).isEmpty();
    }

    @Test
    @DisplayName("get() should return element at valid index")
    void getValidIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(listAt.get(0, list)).hasValue("a");
      assertThat(listAt.get(1, list)).hasValue("b");
      assertThat(listAt.get(2, list)).hasValue("c");
    }

    @Test
    @DisplayName("remove() should delete element and shift indices")
    void removeShiftsIndices() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c", "d"));

      List<String> updated = listAt.remove(1, original);

      assertThat(updated).containsExactly("a", "c", "d");
      assertThat(original).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("remove() should be no-op for out of bounds index")
    void removeOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updatedHigh = listAt.remove(10, original);
      List<String> updatedNegative = listAt.remove(-1, original);

      assertThat(updatedHigh).containsExactly("a", "b", "c");
      assertThat(updatedNegative).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("modify() should update element with function")
    void modifyValidIndex() {
      List<String> original = new ArrayList<>(List.of("hello", "world"));

      List<String> updated = listAt.modify(0, String::toUpperCase, original);

      assertThat(updated).containsExactly("HELLO", "world");
    }

    @Test
    @DisplayName("modify() should be no-op for out of bounds index")
    void modifyOutOfBounds() {
      List<String> original = new ArrayList<>(List.of("hello", "world"));

      List<String> updated = listAt.modify(10, String::toUpperCase, original);

      assertThat(updated).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("contains() should check index validity")
    void containsChecksIndex() {
      List<String> list = new ArrayList<>(List.of("a", "b", "c"));

      assertThat(listAt.contains(0, list)).isTrue();
      assertThat(listAt.contains(2, list)).isTrue();
      assertThat(listAt.contains(3, list)).isFalse();
      assertThat(listAt.contains(-1, list)).isFalse();
    }

    @Test
    @DisplayName("set() with Optional.empty() should remove element")
    void setEmptyRemovesElement() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = listAt.set(1, Optional.empty(), original);

      assertThat(updated).containsExactly("a", "c");
    }

    @Test
    @DisplayName("set() with Optional.of() should update element")
    void setPresentUpdatesElement() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = listAt.set(1, Optional.of("B"), original);

      assertThat(updated).containsExactly("a", "B", "c");
    }

    @Test
    @DisplayName("insertOrUpdate() should update existing element without padding")
    void updateExistingWithoutPadding() {
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = listAt.insertOrUpdate(1, "B", original);

      assertThat(updated).containsExactly("a", "B", "c");
    }
  }

  @Nested
  @DisplayName("List At Edge Cases")
  class ListAtEdgeCaseTests {

    @Test
    @DisplayName("listAt() should handle null elements in list")
    void handleNullElements() {
      At<List<String>, Integer, String> listAt = AtInstances.listAt();
      List<String> list = new ArrayList<>();
      list.add("a");
      list.add(null);
      list.add("c");

      // Null elements are returned as Optional.empty() due to Java's Optional semantics
      Optional<String> result = listAt.get(1, list);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listAt() remove with negative index should be no-op")
    void removeNegativeIndex() {
      At<List<String>, Integer, String> listAt = AtInstances.listAt();
      List<String> original = new ArrayList<>(List.of("a", "b", "c"));

      List<String> updated = listAt.remove(-5, original);

      assertThat(updated).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("mapAt() should handle empty map operations")
    void emptyMapOperations() {
      At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
      Map<String, Integer> emptyMap = new HashMap<>();

      // Get from empty map
      assertThat(mapAt.get("any", emptyMap)).isEmpty();

      // Remove from empty map
      Map<String, Integer> afterRemove = mapAt.remove("any", emptyMap);
      assertThat(afterRemove).isEmpty();

      // Modify on empty map
      Map<String, Integer> afterModify = mapAt.modify("any", x -> x + 1, emptyMap);
      assertThat(afterModify).isEmpty();
    }

    @Test
    @DisplayName("listAt() should handle empty list operations")
    void emptyListOperations() {
      At<List<String>, Integer, String> listAt = AtInstances.listAt();
      List<String> emptyList = new ArrayList<>();

      // Get from empty list
      assertThat(listAt.get(0, emptyList)).isEmpty();

      // Remove from empty list
      List<String> afterRemove = listAt.remove(0, emptyList);
      assertThat(afterRemove).isEmpty();

      // Modify on empty list
      List<String> afterModify = listAt.modify(0, String::toUpperCase, emptyList);
      assertThat(afterModify).isEmpty();
    }

    @Test
    @DisplayName("listAtWithPadding() should handle exact boundary padding")
    void exactBoundaryPadding() {
      At<List<String>, Integer, String> listAt = AtInstances.listAtWithPadding("X");
      List<String> original = new ArrayList<>(List.of("a", "b"));

      // Insert at exact next position (no padding needed, just appending concept)
      List<String> updated = listAt.insertOrUpdate(2, "c", original);

      assertThat(updated).containsExactly("a", "b", "c");
    }
  }
}
