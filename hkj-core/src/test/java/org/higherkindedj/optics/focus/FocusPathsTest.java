// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FocusPaths Utility Tests")
class FocusPathsTest {

  @Nested
  @DisplayName("List Optics")
  class ListOptics {

    @Test
    @DisplayName("listElements should traverse all elements")
    void listElementsShouldTraverseAllElements() {
      Traversal<List<String>, String> traversal = FocusPaths.listElements();

      List<String> list = List.of("a", "b", "c");

      assertThat(Traversals.getAll(traversal, list)).containsExactly("a", "b", "c");
      assertThat(Traversals.modify(traversal, String::toUpperCase, list))
          .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("listElements should handle empty list")
    void listElementsShouldHandleEmptyList() {
      Traversal<List<String>, String> traversal = FocusPaths.listElements();

      List<String> emptyList = List.of();

      assertThat(Traversals.getAll(traversal, emptyList)).isEmpty();
      assertThat(Traversals.modify(traversal, String::toUpperCase, emptyList)).isEmpty();
    }

    @Test
    @DisplayName("listAt should focus on element at index")
    void listAtShouldFocusOnElementAtIndex() {
      Affine<List<String>, String> affine = FocusPaths.listAt(1);

      List<String> list = List.of("a", "b", "c");

      assertThat(affine.getOptional(list)).contains("b");
      assertThat(affine.set("X", list)).containsExactly("a", "X", "c");
    }

    @Test
    @DisplayName("listAt should return empty for out of bounds index")
    void listAtShouldReturnEmptyForOutOfBounds() {
      Affine<List<String>, String> affine = FocusPaths.listAt(5);

      List<String> list = List.of("a", "b", "c");

      assertThat(affine.getOptional(list)).isEmpty();
      // Set on out of bounds returns original
      assertThat(affine.set("X", list)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("listAt should handle negative index")
    void listAtShouldHandleNegativeIndex() {
      Affine<List<String>, String> affine = FocusPaths.listAt(-1);

      List<String> list = List.of("a", "b", "c");

      assertThat(affine.getOptional(list)).isEmpty();
      // Setter with negative index should return original list unchanged
      assertThat(affine.set("X", list)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("listHead should focus on first element")
    void listHeadShouldFocusOnFirstElement() {
      Affine<List<String>, String> affine = FocusPaths.listHead();

      assertThat(affine.getOptional(List.of("a", "b", "c"))).contains("a");
      assertThat(affine.getOptional(List.of())).isEmpty();
    }

    @Test
    @DisplayName("listLast should focus on last element")
    void listLastShouldFocusOnLastElement() {
      Affine<List<String>, String> affine = FocusPaths.listLast();

      assertThat(affine.getOptional(List.of("a", "b", "c"))).contains("c");
      assertThat(affine.getOptional(List.of())).isEmpty();

      assertThat(affine.set("X", List.of("a", "b", "c"))).containsExactly("a", "b", "X");
    }

    @Test
    @DisplayName("listLast setter should return empty list unchanged")
    void listLastSetterShouldReturnEmptyListUnchanged() {
      Affine<List<String>, String> affine = FocusPaths.listLast();

      List<String> emptyList = List.of();

      // Setter on empty list should return the empty list unchanged
      assertThat(affine.set("X", emptyList)).isEmpty();
    }
  }

  @Nested
  @DisplayName("List Decomposition Optics")
  class ListDecompositionOptics {

    @Test
    @DisplayName("listCons should decompose list into head and tail")
    void listConsShouldDecomposeList() {
      Prism<List<String>, Pair<String, List<String>>> prism = FocusPaths.listCons();

      List<String> list = List.of("a", "b", "c");

      Optional<Pair<String, List<String>>> result = prism.getOptional(list);
      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("a");
      assertThat(result.get().second()).containsExactly("b", "c");
    }

    @Test
    @DisplayName("listCons should return empty for empty list")
    void listConsShouldReturnEmptyForEmptyList() {
      Prism<List<String>, Pair<String, List<String>>> prism = FocusPaths.listCons();

      assertThat(prism.getOptional(List.of())).isEmpty();
    }

    @Test
    @DisplayName("listCons should build list from head and tail")
    void listConsShouldBuildList() {
      Prism<List<String>, Pair<String, List<String>>> prism = FocusPaths.listCons();

      List<String> built = prism.build(Pair.of("x", List.of("y", "z")));
      assertThat(built).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("listHeadTail should be alias for listCons")
    void listHeadTailShouldBeAliasForListCons() {
      Prism<List<String>, Pair<String, List<String>>> prism = FocusPaths.listHeadTail();

      List<String> list = List.of("a", "b", "c");
      Optional<Pair<String, List<String>>> result = prism.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("a");
    }

    @Test
    @DisplayName("listSnoc should decompose list into init and last")
    void listSnocShouldDecomposeList() {
      Prism<List<String>, Pair<List<String>, String>> prism = FocusPaths.listSnoc();

      List<String> list = List.of("a", "b", "c");

      Optional<Pair<List<String>, String>> result = prism.getOptional(list);
      assertThat(result).isPresent();
      assertThat(result.get().first()).containsExactly("a", "b");
      assertThat(result.get().second()).isEqualTo("c");
    }

    @Test
    @DisplayName("listSnoc should return empty for empty list")
    void listSnocShouldReturnEmptyForEmptyList() {
      Prism<List<String>, Pair<List<String>, String>> prism = FocusPaths.listSnoc();

      assertThat(prism.getOptional(List.of())).isEmpty();
    }

    @Test
    @DisplayName("listSnoc should build list from init and last")
    void listSnocShouldBuildList() {
      Prism<List<String>, Pair<List<String>, String>> prism = FocusPaths.listSnoc();

      List<String> built = prism.build(Pair.of(List.of("x", "y"), "z"));
      assertThat(built).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("listInitLast should be alias for listSnoc")
    void listInitLastShouldBeAliasForListSnoc() {
      Prism<List<String>, Pair<List<String>, String>> prism = FocusPaths.listInitLast();

      List<String> list = List.of("a", "b", "c");
      Optional<Pair<List<String>, String>> result = prism.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().second()).isEqualTo("c");
    }

    @Test
    @DisplayName("listEmpty should match empty list")
    void listEmptyShouldMatchEmptyList() {
      Prism<List<String>, Unit> prism = FocusPaths.listEmpty();

      assertThat(prism.getOptional(List.of())).contains(Unit.INSTANCE);
      assertThat(prism.getOptional(List.of("a"))).isEmpty();
    }

    @Test
    @DisplayName("listEmpty should build empty list")
    void listEmptyShouldBuildEmptyList() {
      Prism<List<String>, Unit> prism = FocusPaths.listEmpty();

      List<String> built = prism.build(Unit.INSTANCE);
      assertThat(built).isEmpty();
    }

    @Test
    @DisplayName("listTail should focus on tail of list")
    void listTailShouldFocusOnTail() {
      Affine<List<String>, List<String>> affine = FocusPaths.listTail();

      List<String> list = List.of("a", "b", "c");

      assertThat(affine.getOptional(list)).contains(List.of("b", "c"));
    }

    @Test
    @DisplayName("listTail should return empty for empty list")
    void listTailShouldReturnEmptyForEmptyList() {
      Affine<List<String>, List<String>> affine = FocusPaths.listTail();

      assertThat(affine.getOptional(List.of())).isEmpty();
    }

    @Test
    @DisplayName("listTail should set new tail")
    void listTailShouldSetNewTail() {
      Affine<List<String>, List<String>> affine = FocusPaths.listTail();

      List<String> list = List.of("a", "b", "c");
      List<String> updated = affine.set(List.of("x", "y"), list);

      assertThat(updated).containsExactly("a", "x", "y");
    }

    @Test
    @DisplayName("listTail setter should return empty list unchanged")
    void listTailSetterShouldReturnEmptyListUnchanged() {
      Affine<List<String>, List<String>> affine = FocusPaths.listTail();

      List<String> result = affine.set(List.of("x"), List.of());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listInit should focus on init of list")
    void listInitShouldFocusOnInit() {
      Affine<List<String>, List<String>> affine = FocusPaths.listInit();

      List<String> list = List.of("a", "b", "c");

      assertThat(affine.getOptional(list)).contains(List.of("a", "b"));
    }

    @Test
    @DisplayName("listInit should return empty for empty list")
    void listInitShouldReturnEmptyForEmptyList() {
      Affine<List<String>, List<String>> affine = FocusPaths.listInit();

      assertThat(affine.getOptional(List.of())).isEmpty();
    }

    @Test
    @DisplayName("listInit should set new init")
    void listInitShouldSetNewInit() {
      Affine<List<String>, List<String>> affine = FocusPaths.listInit();

      List<String> list = List.of("a", "b", "c");
      List<String> updated = affine.set(List.of("x", "y"), list);

      assertThat(updated).containsExactly("x", "y", "c");
    }

    @Test
    @DisplayName("listInit setter should return empty list unchanged")
    void listInitSetterShouldReturnEmptyListUnchanged() {
      Affine<List<String>, List<String>> affine = FocusPaths.listInit();

      List<String> result = affine.set(List.of("x"), List.of());
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Map Optics")
  class MapOptics {

    @Test
    @DisplayName("mapValues should traverse all values")
    void mapValuesShouldTraverseAllValues() {
      Traversal<Map<String, Integer>, Integer> traversal = FocusPaths.mapValues();

      Map<String, Integer> map = new HashMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      assertThat(Traversals.getAll(traversal, map)).containsExactlyInAnyOrder(1, 2, 3);

      Map<String, Integer> modified = Traversals.modify(traversal, n -> n * 10, map);
      assertThat(modified.values()).containsExactlyInAnyOrder(10, 20, 30);
    }

    @Test
    @DisplayName("mapValues should handle empty map")
    void mapValuesShouldHandleEmptyMap() {
      Traversal<Map<String, Integer>, Integer> traversal = FocusPaths.mapValues();

      Map<String, Integer> emptyMap = Map.of();

      assertThat(Traversals.getAll(traversal, emptyMap)).isEmpty();
    }

    @Test
    @DisplayName("mapAt should focus on value at key")
    void mapAtShouldFocusOnValueAtKey() {
      Affine<Map<String, Integer>, Integer> affine = FocusPaths.mapAt("b");

      Map<String, Integer> map = new HashMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      assertThat(affine.getOptional(map)).contains(2);

      Map<String, Integer> updated = affine.set(20, map);
      assertThat(updated.get("b")).isEqualTo(20);
    }

    @Test
    @DisplayName("mapAt should return empty for missing key")
    void mapAtShouldReturnEmptyForMissingKey() {
      Affine<Map<String, Integer>, Integer> affine = FocusPaths.mapAt("missing");

      Map<String, Integer> map = Map.of("a", 1);

      assertThat(affine.getOptional(map)).isEmpty();
    }

    @Test
    @DisplayName("mapAt should add key when setting on missing key")
    void mapAtShouldAddKeyWhenSettingOnMissingKey() {
      Affine<Map<String, Integer>, Integer> affine = FocusPaths.mapAt("new");

      Map<String, Integer> map = new HashMap<>();
      map.put("a", 1);

      Map<String, Integer> updated = affine.set(99, map);
      assertThat(updated.get("new")).isEqualTo(99);
      assertThat(updated.get("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("mapAt should support remove operation")
    void mapAtShouldSupportRemove() {
      Affine<Map<String, Integer>, Integer> affine = FocusPaths.mapAt("b");

      Map<String, Integer> map = new HashMap<>();
      map.put("a", 1);
      map.put("b", 2);
      map.put("c", 3);

      Map<String, Integer> removed = affine.remove(map);
      assertThat(removed).containsOnlyKeys("a", "c");
    }
  }

  @Nested
  @DisplayName("Optional Optics")
  class OptionalOptics {

    @Test
    @DisplayName("optionalSome should unwrap present Optional")
    void optionalSomeShouldUnwrapPresent() {
      Affine<Optional<String>, String> affine = FocusPaths.optionalSome();

      assertThat(affine.getOptional(Optional.of("hello"))).contains("hello");
    }

    @Test
    @DisplayName("optionalSome should return empty for empty Optional")
    void optionalSomeShouldReturnEmptyForEmpty() {
      Affine<Optional<String>, String> affine = FocusPaths.optionalSome();

      assertThat(affine.getOptional(Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("optionalSome should wrap value when setting")
    void optionalSomeShouldWrapWhenSetting() {
      Affine<Optional<String>, String> affine = FocusPaths.optionalSome();

      assertThat(affine.set("world", Optional.of("hello"))).contains("world");
      assertThat(affine.set("new", Optional.empty())).contains("new");
    }

    @Test
    @DisplayName("optionalSome should support remove operation")
    void optionalSomeShouldSupportRemove() {
      Affine<Optional<String>, String> affine = FocusPaths.optionalSome();

      assertThat(affine.remove(Optional.of("hello"))).isEmpty();
      assertThat(affine.remove(Optional.empty())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Nullable Optics")
  class NullableOptics {

    @Test
    @DisplayName("nullable should return value when non-null")
    void nullableShouldReturnValueWhenNonNull() {
      Affine<String, String> affine = FocusPaths.nullable();

      assertThat(affine.getOptional("hello")).contains("hello");
    }

    @Test
    @DisplayName("nullable should return empty when null")
    void nullableShouldReturnEmptyWhenNull() {
      Affine<String, String> affine = FocusPaths.nullable();

      assertThat(affine.getOptional(null)).isEmpty();
    }

    @Test
    @DisplayName("nullable should set value directly")
    void nullableShouldSetValueDirectly() {
      Affine<String, String> affine = FocusPaths.nullable();

      assertThat(affine.set("world", "hello")).isEqualTo("world");
      assertThat(affine.set("new", null)).isEqualTo("new");
    }

    @Test
    @DisplayName("nullable should work with records containing nullable fields")
    void nullableShouldWorkWithRecords() {
      record UserWithNullable(String name, String nickname) {}

      // Create lens for nickname field
      Lens<UserWithNullable, String> nicknameLens =
          Lens.of(UserWithNullable::nickname, (u, n) -> new UserWithNullable(u.name(), n));

      // Create path with nullable
      AffinePath<UserWithNullable, String> nicknamePath = FocusPath.of(nicknameLens).nullable();

      UserWithNullable withNickname = new UserWithNullable("Alice", "Ally");
      UserWithNullable withoutNickname = new UserWithNullable("Bob", null);

      assertThat(nicknamePath.getOptional(withNickname)).contains("Ally");
      assertThat(nicknamePath.getOptional(withoutNickname)).isEmpty();

      UserWithNullable updated = nicknamePath.set("Bobby", withoutNickname);
      assertThat(updated.nickname()).isEqualTo("Bobby");
    }
  }

  @Nested
  @DisplayName("Array Optics")
  class ArrayOptics {

    @Test
    @DisplayName("arrayElements should traverse all elements")
    void arrayElementsShouldTraverseAllElements() {
      Traversal<String[], String> traversal = FocusPaths.arrayElements();

      String[] array = {"a", "b", "c"};

      assertThat(Traversals.getAll(traversal, array)).containsExactly("a", "b", "c");

      String[] modified = Traversals.modify(traversal, String::toUpperCase, array);
      assertThat(modified).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("arrayElements should handle empty array")
    void arrayElementsShouldHandleEmptyArray() {
      Traversal<String[], String> traversal = FocusPaths.arrayElements();

      String[] emptyArray = {};

      assertThat(Traversals.getAll(traversal, emptyArray)).isEmpty();
    }

    @Test
    @DisplayName("arrayAt should focus on element at index")
    void arrayAtShouldFocusOnElementAtIndex() {
      Affine<String[], String> affine = FocusPaths.arrayAt(1);

      String[] array = {"a", "b", "c"};

      assertThat(affine.getOptional(array)).contains("b");

      String[] updated = affine.set("X", array);
      assertThat(updated).containsExactly("a", "X", "c");
      // Original unchanged
      assertThat(array).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("arrayAt should return empty for out of bounds")
    void arrayAtShouldReturnEmptyForOutOfBounds() {
      Affine<String[], String> affine = FocusPaths.arrayAt(10);

      String[] array = {"a", "b", "c"};

      assertThat(affine.getOptional(array)).isEmpty();
      // Setter with out of bounds index should return original array unchanged
      assertThat(affine.set("X", array)).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("arrayAt should handle negative index")
    void arrayAtShouldHandleNegativeIndex() {
      Affine<String[], String> affine = FocusPaths.arrayAt(-1);

      String[] array = {"a", "b", "c"};

      assertThat(affine.getOptional(array)).isEmpty();
      // Setter with negative index should return original array unchanged
      assertThat(affine.set("X", array)).containsExactly("a", "b", "c");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("should support complex nested navigation")
    void shouldSupportComplexNestedNavigation() {
      record User(String name, List<Map<String, Optional<String>>> settings) {}

      // Lens to settings
      var settingsLens =
          Lens.<User, List<Map<String, Optional<String>>>>of(
              User::settings, (u, s) -> new User(u.name(), s));

      // Navigate: User -> List<Map> -> Map -> Optional -> String
      var path =
          FocusPath.of(settingsLens)
              .<Map<String, Optional<String>>>each()
              .<String, Optional<String>>atKey("theme")
              .<String>some();

      Map<String, Optional<String>> map1 = new HashMap<>();
      map1.put("theme", Optional.of("dark"));

      Map<String, Optional<String>> map2 = new HashMap<>();
      map2.put("theme", Optional.empty());

      Map<String, Optional<String>> map3 = new HashMap<>();
      map3.put("language", Optional.of("en"));

      User user = new User("Alice", List.of(map1, map2, map3));

      // Should find only the present theme value
      assertThat(path.getAll(user)).containsExactly("dark");
    }
  }
}
