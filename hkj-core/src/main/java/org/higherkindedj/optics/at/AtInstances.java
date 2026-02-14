// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.at;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Lens;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Provides standard {@link At} instances for common Java collection types.
 *
 * <p>This class contains factory methods that create {@code At} instances for:
 *
 * <ul>
 *   <li>{@link Map} - indexed by key type {@code K}
 *   <li>{@link List} - indexed by {@link Integer} position
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Map operations
 * At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
 * Map<String, Integer> scores = Map.of("alice", 100);
 *
 * // Insert/update
 * Map<String, Integer> updated = mapAt.insertOrUpdate("bob", 85, scores);
 *
 * // Remove
 * Map<String, Integer> removed = mapAt.remove("alice", updated);
 *
 * // List operations
 * At<List<String>, Integer, String> listAt = AtInstances.listAt();
 * List<String> names = new ArrayList<>(List.of("alice", "bob", "charlie"));
 *
 * // Get element
 * Optional<String> second = listAt.get(1, names);  // Optional["bob"]
 *
 * // Remove element (shifts indices)
 * List<String> afterRemove = listAt.remove(1, names);  // ["alice", "charlie"]
 * }</pre>
 */
@NullMarked
public final class AtInstances {

  /** Private constructor to prevent instantiation. */
  private AtInstances() {}

  /**
   * Creates an {@link At} instance for {@link Map} types.
   *
   * <p>The returned {@code At} provides a lens to the optional presence of a value at a given key:
   *
   * <ul>
   *   <li>{@code get(key)} returns {@code Optional.empty()} if key is absent or has null value
   *   <li>{@code get(key)} returns {@code Optional.of(value)} if key is present with non-null value
   *   <li>{@code set(Optional.empty())} removes the key from the map
   *   <li>{@code set(Optional.of(value))} puts the key-value pair in the map
   * </ul>
   *
   * <p><strong>Null Value Limitation:</strong> Due to Java's {@link Optional} semantics, null map
   * values cannot be distinguished from absent keys. {@code Optional.ofNullable(null)} returns
   * {@code Optional.empty()}, so a key with null value appears the same as an absent key. If you
   * need to distinguish these cases, consider using a wrapper type or avoiding null values.
   *
   * <p><strong>Immutability:</strong> All operations return new {@link Map} instances, leaving the
   * original unchanged.
   *
   * @param <K> The key type of the map
   * @param <V> The value type of the map
   * @return An {@code At} instance for maps
   */
  public static <K, V> At<Map<K, V>, K, @Nullable V> mapAt() {
    return key ->
        Lens.of(
            map -> Optional.ofNullable(map.get(key)),
            (map, optValue) -> {
              Map<K, V> newMap = new HashMap<>(map);
              if (optValue.isPresent()) {
                newMap.put(key, optValue.get());
              } else {
                newMap.remove(key);
              }
              return newMap;
            });
  }

  /**
   * Creates an {@link At} instance for {@link List} types.
   *
   * <p>The returned {@code At} provides a lens to the optional presence of a value at a given
   * index:
   *
   * <ul>
   *   <li>{@code get(index)} returns {@code Optional.empty()} if index is out of bounds
   *   <li>{@code get(index)} returns {@code Optional.of(value)} if index is valid
   *   <li>{@code set(Optional.empty())} removes the element at index (shifts subsequent elements)
   *   <li>{@code set(Optional.of(value))} updates the element at index (must be in bounds)
   * </ul>
   *
   * <p><strong>Important:</strong> Removing an element shifts all subsequent indices. This means
   * that after a removal, the list size decreases and elements after the removed index have new
   * positions.
   *
   * <p><strong>Bounds Checking:</strong> Setting a value at an out-of-bounds index will throw an
   * {@link IndexOutOfBoundsException}. Use {@link #listAtWithPadding(Object)} for auto-expanding
   * behavior.
   *
   * <p><strong>Immutability:</strong> All operations return new {@link List} instances, leaving the
   * original unchanged.
   *
   * @param <A> The element type of the list
   * @return An {@code At} instance for lists
   */
  public static <A> At<List<A>, Integer, A> listAt() {
    return index ->
        Lens.of(
            list ->
                (index >= 0 && index < list.size())
                    ? Optional.ofNullable(list.get(index))
                    : Optional.empty(),
            (list, optValue) -> {
              List<A> newList = new ArrayList<>(list);
              if (optValue.isPresent()) {
                if (index < 0 || index >= newList.size()) {
                  throw new IndexOutOfBoundsException(
                      "Index " + index + " out of bounds for list of size " + newList.size());
                }
                newList.set(index, optValue.get());
              } else {
                if (index >= 0 && index < newList.size()) {
                  newList.remove((int) index);
                }
                // If index is out of bounds for removal, no-op (nothing to remove)
              }
              return newList;
            });
  }

  /**
   * Creates an {@link At} instance for {@link List} types with automatic null-padding for
   * insertions beyond current size.
   *
   * <p>This variant automatically expands the list with null values when setting a value at an
   * index beyond the current size:
   *
   * <ul>
   *   <li>Setting index 5 on a list of size 3 will pad indices 3 and 4 with the default value
   *   <li>Useful for sparse list representations
   * </ul>
   *
   * <p><strong>Warning:</strong> This behavior can lead to unexpected nulls in your list. Use with
   * caution.
   *
   * @param <A> The element type of the list
   * @param defaultValue The value to use for padding (typically null)
   * @return An {@code At} instance for lists with padding behavior
   */
  public static <A> At<List<A>, Integer, A> listAtWithPadding(@Nullable A defaultValue) {
    return index ->
        Lens.of(
            list ->
                (index >= 0 && index < list.size())
                    ? Optional.ofNullable(list.get(index))
                    : Optional.empty(),
            (list, optValue) -> {
              List<A> newList = new ArrayList<>(list);
              if (optValue.isPresent()) {
                if (index < 0) {
                  throw new IndexOutOfBoundsException("Index cannot be negative: " + index);
                }
                // Pad the list if necessary
                while (newList.size() <= index) {
                  newList.add(defaultValue);
                }
                newList.set(index, optValue.get());
              } else {
                if (index >= 0 && index < newList.size()) {
                  newList.remove((int) index);
                }
              }
              return newList;
            });
  }
}
