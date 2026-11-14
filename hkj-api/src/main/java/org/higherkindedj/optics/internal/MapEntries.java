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
import java.util.Objects;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A map's entries, read once in its iteration order, and the one rule by which the library builds a
 * new map from them: {@code Setter.forMapValues} here, and in hkj-core, which reads this package
 * through a qualified export, its map optics and the {@code parseValues} and {@code buildValues} of
 * {@code ValidatedParse} and {@code ValidatedBuild}.
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
 * {@code Map.copyOf} refuses it. The message names what refused it, and what to do instead, as its
 * {@link Refusal} says.
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
   * Builds the new map as {@link #rebuild} does, for a caller that hands it on read-only, as the
   * validated prisms do (the map optics hand back {@link #rebuild}'s map itself): an unmodifiable
   * view of a map that looks keys up by {@code equals}, and an {@code IdentityHashMap} as itself. A
   * view of an {@code IdentityHashMap} is not one, so the next map built from the view would look
   * its keys up by {@code equals}, and refuse or merge them; the {@code IdentityHashMap} itself is
   * still a copy, shared with nothing.
   *
   * @param newValues exactly one value for each key, in the order of {@link #keys()}
   * @param <W> the new value type
   * @return a new map in the order the source was read in, unmodifiable unless it is an {@code
   *     IdentityHashMap}
   * @throws IllegalArgumentException if the source holds two keys that {@code equals} cannot tell
   *     apart
   */
  <W extends @Nullable Object> Map<K, W> rebuildToHandBack(List<? extends W> newValues);

  /**
   * Throws the {@link IllegalArgumentException} that {@link #rebuild} would, without building a map
   * for the caller: for a caller that builds no map on some path, such as a parse that fails, but
   * refuses a source it could never rebuild on every path.
   *
   * @throws IllegalArgumentException if the source holds two keys that {@code equals} cannot tell
   *     apart
   */
  void requireDistinctKeys();

  /**
   * Reads the entries of {@code map} once, in its iteration order, for a map optic, whose refusal
   * points to traversing an {@code IdentityHashMap} copy. An {@code IdentityHashMap} is copied
   * here, so the maps built later hold the keys read now.
   *
   * @param map the source map
   * @param <K> the key type
   * @param <V> the value type
   * @return the entries of {@code map}
   */
  static <K, V extends @Nullable Object> MapEntries<K, V> of(final Map<K, V> map) {
    return of(map, Refusal.MAP_OPTIC);
  }

  /**
   * Reads the entries of {@code map} once, in its iteration order, as {@link #of(Map)} does, for a
   * caller that names itself in a refusal.
   *
   * @param map the source map
   * @param refusal what a refusal names
   * @param <K> the key type
   * @param <V> the value type
   * @return the entries of {@code map}
   */
  static <K, V extends @Nullable Object> MapEntries<K, V> of(
      final Map<K, V> map, final Refusal refusal) {
    Objects.requireNonNull(refusal, "refusal must not be null");
    if (map instanceof IdentityHashMap<K, V> identity) {
      final IdentityHashMap<K, V> table = identityCopy(identity);
      return read(table, (keys, values) -> new ByIdentity<>(table, keys, values));
    }
    return read(map, (keys, values) -> new ByEquals<>(map.getClass(), refusal, keys, values));
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
      ByEquals.requireNewKey(map.getClass(), Refusal.MAP_OPTIC, copy, entry.getKey(), before);
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

  /**
   * What a refusal names: what could not build the map, and what to do instead where the map is not
   * a view of an {@code IdentityHashMap}. The message reads {@code "<subject> this <class> without
   * losing an entry: ... If this is a view of an IdentityHashMap, pass the map itself; otherwise
   * <otherwise>. ..."}.
   *
   * @param subject what could not build the map, and the verb, such as {@code "parseValues cannot
   *     parse"}
   * @param otherwise what to do with a map that is not a view of an {@code IdentityHashMap}
   */
  record Refusal(String subject, String otherwise) {

    /** A map optic's refusal, which points to traversing an {@code IdentityHashMap} copy. */
    private static final Refusal MAP_OPTIC =
        new Refusal(
            "A map optic cannot rebuild",
            "traverse an IdentityHashMap copy of it, with"
                + " Traversals.forMapValuesCollecting(IdentityHashMap::new, rebuild)");

    /**
     * Refuses a null part.
     *
     * @throws NullPointerException if {@code subject} or {@code otherwise} is null
     */
    public Refusal {
      Objects.requireNonNull(subject, "subject must not be null");
      Objects.requireNonNull(otherwise, "otherwise must not be null");
    }
  }
}

/**
 * The entries of a map that looks keys up by {@code equals}, or claims to. Package-private, so that
 * only {@link MapEntries} builds one.
 *
 * @param source the source's class, which a refusal names
 * @param refusal what a refusal names beside the source's class and the two keys
 * @param keys the keys, in the source's iteration order
 * @param values the values, each at its key's index
 */
record ByEquals<K, V extends @Nullable Object>(
    Class<?> source, MapEntries.Refusal refusal, List<K> keys, List<V> values)
    implements MapEntries<K, V> {

  @Override
  public <W extends @Nullable Object> Map<K, W> rebuild(final List<? extends W> newValues) {
    final Map<K, W> rebuilt = LinkedHashMap.newLinkedHashMap(keys.size());
    for (int index = 0; index < keys.size(); index++) {
      rebuilt.put(keys.get(index), newValues.get(index));
      requireNewKey(source, refusal, rebuilt, keys.get(index), index);
    }
    return rebuilt;
  }

  @Override
  public <W extends @Nullable Object> Map<K, W> rebuildToHandBack(
      final List<? extends W> newValues) {
    return Collections.unmodifiableMap(rebuild(newValues));
  }

  @Override
  public void requireDistinctKeys() {
    rebuild(values);
  }

  /**
   * Refuses a put that did not grow {@code rebuilt}: its key is equal, by {@code equals}, to one
   * already there, which the message names alongside it.
   */
  static void requireNewKey(
      final Class<?> source,
      final MapEntries.Refusal refusal,
      final Map<?, ?> rebuilt,
      final @Nullable Object key,
      final int before) {
    if (rebuilt.size() == before) {
      // The map kept the earlier of the two keys, which is the one equal to this key.
      final List<?> kept = new ArrayList<>(rebuilt.keySet());
      final @Nullable Object earlier = kept.get(kept.indexOf(key));
      throw new IllegalArgumentException(
          refusal.subject()
              + " this "
              + source.getName()
              + " without losing an entry: its keys "
              + earlier
              + " and "
              + key
              + " are equal by equals(), and the rebuilt map looks keys up by equals(). If this is"
              + " a view of an IdentityHashMap, pass the map itself; otherwise "
              + refusal.otherwise()
              + ". A key changed after it went into the map also causes this.");
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

  /** The map itself: a read-only view of it would not be an {@code IdentityHashMap}. */
  @Override
  public <W extends @Nullable Object> Map<K, W> rebuildToHandBack(
      final List<? extends W> newValues) {
    return rebuild(newValues);
  }

  /** Never throws: an identity map keeps every key apart. */
  @Override
  public void requireDistinctKeys() {}
}
