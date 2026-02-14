// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

/**
 * A type class for structures that support safe indexed access to existing elements.
 *
 * <p>{@code Ixed} provides a {@link Traversal} focusing on zero or one element at a given index.
 * Unlike {@link At}, which provides CRUD (Create, Read, Update, Delete) operations via {@code
 * Optional}, {@code Ixed} only allows reading and updating existing elements. If the index is not
 * present, the traversal focuses on zero elements.
 *
 * <p>This makes {@code Ixed} ideal for safe, partial access patterns where you want to modify an
 * element only if it exists, without the ability to insert or delete.
 *
 * <h3>Example Usage:</h3>
 *
 * <pre>{@code
 * // Create an Ixed instance for Map<String, Integer>
 * Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
 *
 * Map<String, Integer> scores = new HashMap<>();
 * scores.put("alice", 100);
 * scores.put("bob", 85);
 *
 * // Get the traversal for a specific key
 * Traversal<Map<String, Integer>, Integer> aliceTraversal = mapIx.ix("alice");
 *
 * // Use Traversals utility methods for operations
 * List<Integer> aliceScore = Traversals.getAll(aliceTraversal, scores);
 * // aliceScore = [100]
 *
 * // Get a non-existent element returns empty list
 * List<Integer> charlieScore = Traversals.getAll(mapIx.ix("charlie"), scores);
 * // charlieScore = []
 *
 * // Update an existing element
 * Map<String, Integer> updated = Traversals.modify(aliceTraversal, x -> x + 5, scores);
 * // updated = {alice=105, bob=85}
 *
 * // Trying to update a non-existent element has no effect
 * Map<String, Integer> unchanged = Traversals.modify(mapIx.ix("charlie"), x -> x + 10, scores);
 * // unchanged = {alice=100, bob=85} (charlie not added)
 * }</pre>
 *
 * <h3>Relationship to At:</h3>
 *
 * <p>{@code Ixed} can be derived from {@link At} by composing with a prism that unwraps the
 * optional:
 *
 * <pre>{@code
 * // Ixed is At composed with a "some" prism
 * At<Map<K, V>, K, V> at = AtInstances.mapAt();
 * Prism<Optional<V>, V> somePrism = Prisms.some();
 *
 * Traversal<Map<K, V>, V> ixTraversal =
 *     at.at(key).asTraversal().andThen(somePrism.asTraversal());
 * }</pre>
 *
 * <h3>Composition:</h3>
 *
 * <p>The traversal returned by {@code ix} composes naturally with other optics:
 *
 * <pre>{@code
 * Lens<User, Map<String, Address>> addressMapLens = ...;
 * Ixed<Map<String, Address>, String, Address> mapIx = IxedInstances.mapIx();
 * Lens<Address, String> cityLens = ...;
 *
 * // Compose to get a Traversal that focuses on the city of a specific address
 * Traversal<User, String> homeCityTraversal =
 *     addressMapLens.asTraversal()
 *         .andThen(mapIx.ix("home"))
 *         .andThen(cityLens.asTraversal());
 *
 * // Modify the city if the home address exists
 * User updatedUser = Traversals.modify(
 *     homeCityTraversal,
 *     city -> city.toUpperCase(),
 *     user
 * );
 * }</pre>
 *
 * <h3>Convenience Methods:</h3>
 *
 * <p>For convenience, use the {@code Traversals} utility class from {@code hkj-core} to perform
 * common operations on the returned traversal:
 *
 * <ul>
 *   <li>{@code Traversals.getAll(ixed.ix(index), source)} - Get all focused elements (0 or 1)
 *   <li>{@code Traversals.modify(ixed.ix(index), f, source)} - Modify if present
 * </ul>
 *
 * @param <S> The structure type (e.g., {@code Map<K, V>} or {@code List<A>})
 * @param <I> The index type (e.g., {@code K} for maps, {@code Integer} for lists)
 * @param <A> The value type at each index
 */
@FunctionalInterface
public interface Ixed<S, I, A> {

  /**
   * Returns a {@link Traversal} that focuses on zero or one element at the given index.
   *
   * <p>The returned traversal has the following semantics:
   *
   * <ul>
   *   <li>If the index exists in the structure, focuses on that single element
   *   <li>If the index does not exist, focuses on zero elements (modifications have no effect)
   *   <li>Cannot insert new elements or delete existing ones
   * </ul>
   *
   * <p>Use {@code Traversals.getAll()} and {@code Traversals.modify()} from {@code hkj-core} for
   * convenient operations on the returned traversal.
   *
   * @param index The index to focus on
   * @return A {@link Traversal} focusing on zero or one element at the given index
   */
  Traversal<S, A> ix(I index);
}
