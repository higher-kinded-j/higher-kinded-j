// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every optic that writes a map hands back one in the source's iteration order.
 *
 * <p>The source is built in the order {@code c, b, a}, which is the reverse of the order a {@code
 * HashMap} iterates those keys in, so each test fails if its optic rebuilds the map as a {@code
 * HashMap}.
 */
@DisplayName("A map written through an optic keeps its source's iteration order")
class MapIterationOrderTest {

  private static Map<String, Integer> source() {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put("c", 3);
    map.put("b", 2);
    map.put("a", 1);
    return map;
  }

  @Test
  @DisplayName("Traversals.forMapValues() keeps the order")
  void forMapValues() {
    Map<String, Integer> result =
        Traversals.modify(Traversals.<String, Integer>forMapValues(), v -> v * 10, source());

    assertThat(result).containsExactly(entry("c", 30), entry("b", 20), entry("a", 10));
  }

  @Test
  @DisplayName("Traversals.forMapValuesCollecting(LinkedHashMap::new) keeps the order")
  void forMapValuesCollecting() {
    Traversal<LinkedHashMap<String, Integer>, Integer> values =
        Traversals.forMapValuesCollecting(LinkedHashMap::new);

    LinkedHashMap<String, Integer> result =
        Traversals.modify(values, v -> v * 10, new LinkedHashMap<>(source()));

    assertThat(result).containsExactly(entry("c", 30), entry("b", 20), entry("a", 10));
  }

  @Test
  @DisplayName("Traversals.forMap(key) keeps the order")
  void forMapKey() {
    Map<String, Integer> result =
        Traversals.modify(Traversals.<String, Integer>forMap("b"), v -> v * 10, source());

    assertThat(result).containsExactly(entry("c", 3), entry("b", 20), entry("a", 1));
  }

  @Test
  @DisplayName("IndexedTraversals.forMap() keeps the order")
  void indexedForMap() {
    Map<String, Integer> result =
        IndexedTraversals.imodify(
            IndexedTraversals.<String, Integer>forMap(),
            (key, v) -> key.equals("a") ? v * 10 : v,
            source());

    assertThat(result).containsExactly(entry("c", 3), entry("b", 2), entry("a", 10));
  }

  @Test
  @DisplayName("AtInstances.mapAt() keeps an updated key in place and appends a new one")
  void mapAt() {
    At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();

    assertThat(at.at("b").set(Optional.of(20), source()))
        .containsExactly(entry("c", 3), entry("b", 20), entry("a", 1));
    assertThat(at.at("d").set(Optional.of(4), source()))
        .containsExactly(entry("c", 3), entry("b", 2), entry("a", 1), entry("d", 4));
    assertThat(at.at("b").set(Optional.empty(), source()))
        .containsExactly(entry("c", 3), entry("a", 1));
  }

  @Test
  @DisplayName("FocusPaths.mapAt(key) and mapValues() keep the order")
  void focusPaths() {
    Affine<Map<String, Integer>, Integer> b = FocusPaths.mapAt("b");

    assertThat(b.set(20, source())).containsExactly(entry("c", 3), entry("b", 20), entry("a", 1));
    assertThat(b.remove(source())).containsExactly(entry("c", 3), entry("a", 1));
    assertThat(Traversals.modify(FocusPaths.<String, Integer>mapValues(), v -> v * 10, source()))
        .containsExactly(entry("c", 30), entry("b", 20), entry("a", 10));
  }
}
