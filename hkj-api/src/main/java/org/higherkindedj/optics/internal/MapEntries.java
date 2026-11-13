// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A map's entries, read once in its iteration order, and the one rule by which every map optic
 * builds the new map: {@code Setter.forMapValues} here, and the map optics of hkj-core, which reads
 * this package through a qualified export.
 *
 * <p>The new map holds one entry for each entry of the source, under the source's own key, in the
 * source's iteration order. An {@link IdentityHashMap} comes back an {@code IdentityHashMap}, so
 * keys that are equal but not the same object stay apart; it is copied with {@code clone()}, so
 * that its order holds. A subclass of {@code IdentityHashMap} is treated as a plain {@code
 * IdentityHashMap} copied from it: it is read, and comes back, in that copy's order, and none of
 * the subclass's own code runs on the result. Any other map comes back a {@link LinkedHashMap},
 * which looks keys up by {@code equals}. A source holding two keys that {@code equals} cannot tell
 * apart, such as a {@code TreeMap} whose comparator is finer than {@code equals}, would lose an
 * entry there, so it is refused with an {@link IllegalArgumentException} naming both keys, as
 * {@code Map.copyOf} refuses it.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public sealed interface MapEntries<K, V extends @Nullable Object> {

  /**
   * The source's keys, in the order it was read in.
   *
   * @return the keys, unmodifiable
   */
  List<K> keys();

  /**
   * The source's values, each at the index of its key in {@link #keys()}.
   *
   * @return the values, unmodifiable
   */
  List<V> values();

  /**
   * Builds a new map with the source's keys, the value at each index of {@code newValues} going to
   * the key at that index of {@link #keys()}. Each call builds its own map, so a deferred effect
   * that runs more than once never shares one.
   *
   * @param newValues exactly one value for each key, in the order of {@link #keys()}
   * @param <W> the new value type
   * @return a new map in the order the source was read in
   * @throws IllegalArgumentException if the source holds two keys that {@code equals} cannot tell
   *     apart
   */
  <W extends @Nullable Object> Map<K, W> rebuild(List<? extends W> newValues);

  /**
   * Reads the entries of {@code map} once, in its iteration order. An {@code IdentityHashMap} is
   * copied here, so the maps built later hold the keys read now.
   *
   * @param map the source map
   * @param <K> the key type
   * @param <V> the value type
   * @return the entries of {@code map}
   */
  static <K, V extends @Nullable Object> MapEntries<K, V> of(final Map<K, V> map) {
    if (map instanceof IdentityHashMap<K, V> identity) {
      final IdentityHashMap<K, V> table = identityCopy(identity);
      return read(table, (keys, values) -> new ByIdentity<>(table, keys, values));
    }
    return read(map, (keys, values) -> new ByEquals<>(map.getClass(), keys, values));
  }

  /**
   * A new, mutable map holding every entry of {@code map} in its iteration order: an {@code
   * IdentityHashMap} for an {@code IdentityHashMap}, as {@link MapEntries} describes, and a {@code
   * LinkedHashMap} for any other map.
   *
   * @param map the source map
   * @param <K> the key type
   * @param <V> the value type
   * @return a new map with the entries of {@code map}
   * @throws IllegalArgumentException if {@code map} holds two keys that {@code equals} cannot tell
   *     apart
   */
  static <K, V extends @Nullable Object> Map<K, V> mutableCopy(final Map<K, V> map) {
    if (map instanceof IdentityHashMap<K, V> identity) {
      return identityCopy(identity);
    }
    final Map<K, V> copy = LinkedHashMap.newLinkedHashMap(map.size());
    for (final Map.Entry<K, V> entry : map.entrySet()) {
      final int before = copy.size();
      copy.put(entry.getKey(), entry.getValue());
      ByEquals.requireNewKey(map.getClass(), copy, entry.getKey(), before);
    }
    return copy;
  }

  /**
   * A plain {@code IdentityHashMap} with the entries of {@code map}: a clone of an {@code
   * IdentityHashMap} itself, which keeps its order, and a fresh copy of a subclass, so that none of
   * the subclass's own code runs on the result.
   */
  private static <K, V extends @Nullable Object> IdentityHashMap<K, V> identityCopy(
      final IdentityHashMap<K, V> map) {
    if (map.getClass() != IdentityHashMap.class) {
      return new IdentityHashMap<>(map);
    }
    @SuppressWarnings("unchecked") // IdentityHashMap.clone() returns an IdentityHashMap<K, V>
    final IdentityHashMap<K, V> copy = (IdentityHashMap<K, V>) map.clone();
    return copy;
  }

  // One walk reads both lists, so a key and its value always share an index.
  private static <K, V extends @Nullable Object> MapEntries<K, V> read(
      final Map<K, V> map, final BiFunction<List<K>, List<V>, MapEntries<K, V>> into) {
    final List<K> keys = new ArrayList<>(map.size());
    final List<V> values = new ArrayList<>(map.size());
    for (final Map.Entry<K, V> entry : map.entrySet()) {
      keys.add(entry.getKey());
      values.add(entry.getValue());
    }
    return into.apply(Collections.unmodifiableList(keys), Collections.unmodifiableList(values));
  }
}

/**
 * The entries of a map that looks keys up by {@code equals}, or claims to. Package-private, so that
 * only {@link MapEntries} builds one.
 *
 * @param source the source's class, which a refusal names
 * @param keys the keys, in the source's iteration order
 * @param values the values, each at its key's index
 */
record ByEquals<K, V extends @Nullable Object>(Class<?> source, List<K> keys, List<V> values)
    implements MapEntries<K, V> {

  @Override
  public <W extends @Nullable Object> Map<K, W> rebuild(final List<? extends W> newValues) {
    final Map<K, W> rebuilt = LinkedHashMap.newLinkedHashMap(keys.size());
    for (int index = 0; index < keys.size(); index++) {
      rebuilt.put(keys.get(index), newValues.get(index));
      requireNewKey(source, rebuilt, keys.get(index), index);
    }
    return rebuilt;
  }

  /**
   * Refuses a put that did not grow {@code rebuilt}: its key is equal, by {@code equals}, to one
   * already there, which the message names alongside it.
   */
  static void requireNewKey(
      final Class<?> source,
      final Map<?, ?> rebuilt,
      final @Nullable Object key,
      final int before) {
    if (rebuilt.size() == before) {
      // The map kept the earlier of the two keys, which is the one equal to this key.
      final List<?> kept = new ArrayList<>(rebuilt.keySet());
      final @Nullable Object earlier = kept.get(kept.indexOf(key));
      throw new IllegalArgumentException(
          "A map optic cannot rebuild this "
              + source.getName()
              + " without losing an entry: its keys "
              + earlier
              + " and "
              + key
              + " are equal by equals(), and the rebuilt map looks keys up by equals(). If this is"
              + " a view of an IdentityHashMap, pass the map itself; otherwise traverse an"
              + " IdentityHashMap copy of it, with"
              + " Traversals.forMapValuesCollecting(IdentityHashMap::new, rebuild). A key changed"
              + " after it went into the map also causes this.");
    }
  }
}

/**
 * The entries of an {@code IdentityHashMap}, with a plain copy of it that each new map is cloned
 * from. Package-private, so that only {@link MapEntries} builds one and nothing writes to the copy.
 *
 * @param table a plain {@code IdentityHashMap} holding the entries read, never written to
 * @param keys the keys, in the table's iteration order
 * @param values the values, each at its key's index
 */
record ByIdentity<K, V extends @Nullable Object>(
    IdentityHashMap<K, V> table, List<K> keys, List<V> values) implements MapEntries<K, V> {

  @Override
  public <W extends @Nullable Object> Map<K, W> rebuild(final List<? extends W> newValues) {
    @SuppressWarnings("unchecked") // replaceAll overwrites every value before the map is returned
    final IdentityHashMap<K, W> rebuilt = (IdentityHashMap<K, W>) table.clone();
    // The clone iterates as the table did when the values were read, so the values line up, and
    // replacing a value moves no entry. Neither old value nor key is read.
    final Iterator<? extends W> next = newValues.iterator();
    rebuilt.replaceAll((_, _) -> next.next());
    return rebuilt;
  }
}
