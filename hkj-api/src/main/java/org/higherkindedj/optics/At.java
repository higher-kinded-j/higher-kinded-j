// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.Function;

/**
 * A type class for structures that support indexed access with insertion and deletion semantics.
 *
 * <p>{@code At} provides a {@link Lens} focusing on the optional presence of a value at a given
 * index. This enables CRUD (Create, Read, Update, Delete) operations on indexed structures like
 * {@link java.util.Map} or {@link java.util.List}.
 *
 * <p>The key insight is that setting to {@link Optional#empty()} deletes the entry, while setting
 * to {@link Optional#of(Object)} inserts or updates the entry. This makes {@code At} more powerful
 * than a simple indexed accessor.
 *
 * <h3>Example Usage:</h3>
 *
 * <pre>{@code
 * // Create an At instance for Map<String, Integer>
 * At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
 *
 * Map<String, Integer> scores = new HashMap<>();
 * scores.put("alice", 100);
 *
 * // Insert a new entry
 * Map<String, Integer> updated = mapAt.insertOrUpdate("bob", 85, scores);
 * // scores is unchanged, updated = {alice=100, bob=85}
 *
 * // Remove an entry
 * Map<String, Integer> afterRemove = mapAt.remove("alice", updated);
 * // afterRemove = {bob=85}
 *
 * // Check for presence
 * Optional<Integer> bobScore = mapAt.at("bob").get(afterRemove);
 * // bobScore = Optional[85]
 *
 * // Compose with other optics
 * Lens<Map<String, Integer>, Optional<Integer>> aliceLens = mapAt.at("alice");
 * }</pre>
 *
 * <h3>Composition with Prisms:</h3>
 *
 * <p>To compose through the {@code Optional} layer, use a {@code Prism} that unwraps {@code
 * Optional}:
 *
 * <pre>{@code
 * Lens<User, Map<String, Address>> addressMapLens = ...;
 * At<Map<String, Address>, String, Address> mapAt = AtInstances.mapAt();
 * Prism<Optional<Address>, Address> somePrism = Prisms.some();
 *
 * // Compose to get a Traversal (0-or-1 focus)
 * Traversal<User, Address> homeAddressTraversal =
 *     addressMapLens
 *         .andThen(mapAt.at("home"))  // Lens<User, Optional<Address>>
 *         .asTraversal()
 *         .andThen(somePrism.asTraversal());
 * }</pre>
 *
 * @param <S> The structure type (e.g., {@code Map<K, V>} or {@code List<A>})
 * @param <I> The index type (e.g., {@code K} for maps, {@code Integer} for lists)
 * @param <A> The value type stored at each index
 */
@FunctionalInterface
public interface At<S, I, A> {

  /**
   * Returns a {@link Lens} that focuses on the optional presence of a value at the given index.
   *
   * <p>The returned lens has the following semantics:
   *
   * <ul>
   *   <li>{@code get(source)} returns {@link Optional#empty()} if the index is absent, or {@link
   *       Optional#of(Object)} if present
   *   <li>{@code set(Optional.empty(), source)} removes the entry at the index
   *   <li>{@code set(Optional.of(value), source)} inserts or updates the entry at the index
   * </ul>
   *
   * <p>Note on null values: Due to Java's {@link Optional} semantics, an index present with a
   * {@code null} value is indistinguishable from an absent index. Both will result in {@link
   * Optional#empty()} from a {@code get} operation.
   *
   * @param index The index to focus on
   * @return A {@link Lens} focusing on {@code Optional<A>} at the given index
   */
  Lens<S, Optional<A>> at(I index);

  /**
   * Retrieves the value at the given index, if present.
   *
   * <p>This is a convenience method equivalent to {@code at(index).get(source)}.
   *
   * @param index The index to look up
   * @param source The structure to query
   * @return An {@link Optional} containing the value if present, or empty if absent
   */
  default Optional<A> get(I index, S source) {
    return at(index).get(source);
  }

  /**
   * Sets the optional value at the given index.
   *
   * <p>This is a convenience method equivalent to {@code at(index).set(value, source)}.
   *
   * @param index The index to modify
   * @param value The optional value to set (empty to remove, present to insert/update)
   * @param source The original structure
   * @return A new structure with the modification applied
   */
  default S set(I index, Optional<A> value, S source) {
    return at(index).set(value, source);
  }

  /**
   * Removes the entry at the given index.
   *
   * <p>This is equivalent to {@code at(index).set(Optional.empty(), source)}.
   *
   * @param index The index to remove
   * @param source The original structure
   * @return A new structure with the entry removed (or unchanged if not present)
   */
  default S remove(I index, S source) {
    return at(index).set(Optional.empty(), source);
  }

  /**
   * Inserts or updates the value at the given index.
   *
   * <p>This is equivalent to {@code at(index).set(Optional.of(value), source)}.
   *
   * @param index The index to insert or update
   * @param value The value to set
   * @param source The original structure
   * @return A new structure with the entry inserted or updated
   */
  default S insertOrUpdate(I index, A value, S source) {
    return at(index).set(Optional.of(value), source);
  }

  /**
   * Modifies the value at the given index if present, using the provided function.
   *
   * <p>If the index is absent, the structure is returned unchanged.
   *
   * @param index The index to modify
   * @param modifier The function to apply to the existing value
   * @param source The original structure
   * @return A new structure with the modified value, or unchanged if absent
   */
  default S modify(I index, Function<A, A> modifier, S source) {
    return at(index).modify(opt -> opt.map(modifier), source);
  }

  /**
   * Checks if a value is present at the given index.
   *
   * @param index The index to check
   * @param source The structure to query
   * @return {@code true} if a value is present at the index, {@code false} otherwise
   */
  default boolean contains(I index, S source) {
    return get(index, source).isPresent();
  }
}
