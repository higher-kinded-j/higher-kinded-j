// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MapEntries: the one rule by which a map optic builds a new map")
class MapEntriesTest {

  /** A key that equals by name alone, so a map ordered by rank holds two that equals merges. */
  private record Tag(String name, int rank) {
    @Override
    public boolean equals(Object other) {
      return other instanceof Tag tag && tag.name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return name + "#" + rank;
    }
  }

  /** An identity map that refuses every write, so any write to it shows it was not copied. */
  @SuppressWarnings("serial") // never serialised
  private static final class FrozenIdentityMap<K, V> extends IdentityHashMap<K, V> {
    private boolean frozen;

    @Override
    public V put(K key, V value) {
      if (frozen) {
        throw new UnsupportedOperationException("frozen");
      }
      return super.put(key, value);
    }
  }

  /** Six keys that are equal but not the same, in an identity map at its default capacity. */
  private static IdentityHashMap<String, Integer> sixEqualKeys() {
    IdentityHashMap<String, Integer> map = new IdentityHashMap<>();
    for (int i = 0; i < 6; i++) {
      map.put(new String("k"), i);
    }
    return map;
  }

  private static Map<Tag, Integer> mergingSource() {
    Map<Tag, Integer> map = new TreeMap<>(Comparator.comparingInt(Tag::rank));
    map.put(new Tag("k", 1), 1);
    map.put(new Tag("k", 2), 2);
    return map;
  }

  private static final String REFUSAL =
      "A map optic cannot rebuild this java.util.TreeMap without losing an entry: its keys k#1"
          + " and k#2 are equal by equals(), and the rebuilt map looks keys up by equals(). If"
          + " this is a view of an IdentityHashMap, pass the map itself; otherwise traverse an"
          + " IdentityHashMap copy of it, with"
          + " Traversals.forMapValuesCollecting(IdentityHashMap::new, rebuild). A key changed"
          + " after it went into the map also causes this.";

  /** The result holds the source's own keys, the same objects, in the source's order. */
  private static void assertSameKeysInOrder(Map<String, ?> result, Map<String, ?> source) {
    assertThat(result).isExactlyInstanceOf(IdentityHashMap.class).hasSameSizeAs(source);
    Iterator<String> resultKeys = result.keySet().iterator();
    for (String key : source.keySet()) {
      assertThat(resultKeys.next()).isSameAs(key);
    }
  }

  @Nested
  @DisplayName("of() and rebuild()")
  class OfTests {

    @Test
    @DisplayName("read the keys and values in the source's iteration order, unmodifiable")
    void readsInOrder() {
      Map<String, Integer> source = new LinkedHashMap<>();
      source.put("c", 3);
      source.put("a", 1);

      MapEntries<String, Integer> entries = MapEntries.of(source);

      assertThat(entries.keys()).containsExactly("c", "a");
      assertThat(entries.values()).containsExactly(3, 1);
      assertThatThrownBy(() -> entries.keys().add("b"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> entries.values().add(2))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("build a LinkedHashMap in the source's order for a map that compares by equals")
    void equalsSource() {
      Map<String, Integer> source = new LinkedHashMap<>();
      source.put("c", 3);
      source.put("a", 1);

      Map<String, String> rebuilt = MapEntries.of(source).rebuild(List.of("three", "one"));

      assertThat(rebuilt)
          .isInstanceOf(LinkedHashMap.class)
          .containsExactly(entry("c", "three"), entry("a", "one"));
    }

    @Test
    @DisplayName("build an IdentityHashMap with every key of an identity source, in its order")
    void identitySourceKeepsEveryKey() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      MapEntries<String, Integer> entries = MapEntries.of(source);

      Map<String, Integer> rebuilt =
          entries.rebuild(entries.values().stream().map(n -> n * 10).toList());

      assertSameKeysInOrder(rebuilt, source);
      Iterator<Integer> values = rebuilt.values().iterator();
      for (Integer value : source.values()) {
        assertThat(values.next()).isEqualTo(value * 10);
      }
    }

    @Test
    @DisplayName("copy a subclass of IdentityHashMap into a plain one, never writing to it")
    void identitySubclass() {
      FrozenIdentityMap<String, Integer> source = new FrozenIdentityMap<>();
      source.put(new String("k"), 1);
      source.put(new String("k"), 2);
      source.frozen = true;
      MapEntries<String, Integer> entries = MapEntries.of(source);

      Map<String, String> rebuilt = entries.rebuild(List.of("one", "two"));

      assertThat(rebuilt).isExactlyInstanceOf(IdentityHashMap.class).hasSize(2);
      assertThat(new ArrayList<>(rebuilt.keySet())).isEqualTo(entries.keys());
      assertThat(rebuilt.values()).containsExactly("one", "two");
      assertThat(source).hasSize(2).containsValues(1, 2);
    }

    @Test
    @DisplayName("build a new map on each call")
    void newMapPerCall() {
      MapEntries<String, Integer> equalsEntries = MapEntries.of(Map.of("a", 1));
      MapEntries<String, Integer> identityEntries = MapEntries.of(sixEqualKeys());
      List<Integer> sixValues = List.of(0, 1, 2, 3, 4, 5);

      assertThat(equalsEntries.rebuild(List.of(2))).isNotSameAs(equalsEntries.rebuild(List.of(2)));
      assertThat(identityEntries.rebuild(sixValues))
          .isNotSameAs(identityEntries.rebuild(sixValues));
    }

    @Test
    @DisplayName("keep the keys read, whatever later happens to an identity source")
    void identitySnapshot() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();
      List<String> keys = new ArrayList<>(source.keySet());
      MapEntries<String, Integer> entries = MapEntries.of(source);

      source.clear();
      source.put(new String("later"), 99);
      Map<String, Integer> rebuilt = entries.rebuild(List.of(0, 1, 2, 3, 4, 5));

      assertThat(rebuilt).hasSize(6);
      Iterator<String> rebuiltKeys = rebuilt.keySet().iterator();
      for (String key : keys) {
        assertThat(rebuiltKeys.next()).isSameAs(key);
      }
    }

    @Test
    @DisplayName("keep a null value")
    void keepsNullValue() {
      Map<String, Integer> source = new LinkedHashMap<>();
      source.put("a", null);
      IdentityHashMap<String, Integer> identity = new IdentityHashMap<>(source);

      assertThat(MapEntries.of(source).rebuild(Arrays.asList((Integer) null)))
          .containsExactly(entry("a", null));
      assertThat(MapEntries.of(identity).rebuild(Arrays.asList((Integer) null)))
          .containsExactly(entry("a", null));
    }

    @Test
    @DisplayName("refuse a source holding two keys that equals cannot tell apart, naming the key")
    void refusesMerge() {
      MapEntries<Tag, Integer> entries = MapEntries.of(mergingSource());

      assertThatThrownBy(() -> entries.rebuild(List.of(10, 20)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(REFUSAL);
    }
  }

  @Nested
  @DisplayName("mutableCopy()")
  class MutableCopyTests {

    @Test
    @DisplayName("copies a map that compares by equals into a LinkedHashMap, in its order")
    void equalsSource() {
      Map<String, Integer> source = new LinkedHashMap<>();
      source.put("c", 3);
      source.put("a", 1);

      Map<String, Integer> copy = MapEntries.mutableCopy(source);
      copy.put("b", 2);

      assertThat(copy)
          .isInstanceOf(LinkedHashMap.class)
          .containsExactly(entry("c", 3), entry("a", 1), entry("b", 2));
      assertThat(source).containsExactly(entry("c", 3), entry("a", 1));
    }

    @Test
    @DisplayName("keeps a null key")
    void keepsNullKey() {
      Map<String, Integer> source = new HashMap<>();
      source.put(null, 1);

      assertThat(MapEntries.mutableCopy(source)).containsExactly(entry(null, 1));
    }

    @Test
    @DisplayName("copies an identity map into a new IdentityHashMap with every key, in its order")
    void identitySource() {
      IdentityHashMap<String, Integer> source = sixEqualKeys();

      Map<String, Integer> copy = MapEntries.mutableCopy(source);

      assertThat(copy).isNotSameAs(source);
      assertSameKeysInOrder(copy, source);
      assertThat(new ArrayList<>(copy.values())).isEqualTo(new ArrayList<>(source.values()));
    }

    @Test
    @DisplayName("copies a subclass of IdentityHashMap into a plain one")
    void identitySubclass() {
      FrozenIdentityMap<String, Integer> source = new FrozenIdentityMap<>();
      source.put(new String("k"), 1);
      source.put(new String("k"), 2);
      source.frozen = true;

      Map<String, Integer> copy = MapEntries.mutableCopy(source);
      copy.put(new String("k"), 3);

      assertThat(copy).isExactlyInstanceOf(IdentityHashMap.class).hasSize(3);
      assertThat(source).hasSize(2);
    }

    @Test
    @DisplayName("refuses a source holding two keys that equals cannot tell apart, naming the key")
    void refusesMerge() {
      Map<Tag, Integer> source = mergingSource();

      assertThatThrownBy(() -> MapEntries.mutableCopy(source))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(REFUSAL);
    }
  }
}
