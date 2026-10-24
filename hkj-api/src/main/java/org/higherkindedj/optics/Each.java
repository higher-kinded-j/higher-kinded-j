// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Objects;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.jspecify.annotations.Nullable;

/**
 * A type class for structures that have a canonical element-wise traversal.
 *
 * <p>{@code Each} provides a {@link Traversal} that focuses on all elements within a container
 * type. This enables uniform traversal operations across different container types like {@link
 * java.util.List}, {@link java.util.Map}, {@link java.util.Optional}, arrays, and custom
 * collections.
 *
 * <p>Unlike {@link org.higherkindedj.hkt.Traverse} which works on higher-kinded types {@code
 * Kind<F, A>}, Each works directly on concrete types, making it more convenient for everyday use
 * with standard Java collections.
 *
 * <h2>Relationship to Other Type Classes:</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.Traverse} - HKT version for {@code Kind<F, A>}; Each wraps
 *       this for concrete types
 *   <li>{@link At} - Focuses on a single element by index with insert/delete capability
 *   <li>{@link Ixed} - Focuses on a single element by index (read/update only)
 *   <li>{@code Each} - Focuses on ALL elements at once
 * </ul>
 *
 * <h2>Example Usage:</h2>
 *
 * <pre>{@code
 * // Get an Each instance for List
 * Each<List<String>, String> listEach = EachInstances.listEach();
 *
 * // Use the traversal to modify all elements
 * Traversal<List<String>, String> traversal = listEach.each();
 * List<String> result = Traversals.modify(traversal, String::toUpperCase, list);
 *
 * // Get all elements
 * List<String> elements = Traversals.getAll(traversal, list);
 *
 * // Use with FocusDSL
 * TraversalPath<User, Order> allOrders = userOrdersPath.each(listEach);
 * }</pre>
 *
 * <h3>Indexed Traversal:</h3>
 *
 * <p>Some containers support indexed access, where each element has an associated index (e.g.,
 * position in a List, key in a Map). Their instances implement {@link EachIndexed}, whose {@link
 * EachIndexed#indexedTraversal()} returns an {@link IndexedTraversal} that provides access to both
 * index and value:
 *
 * <pre>{@code
 * EachIndexed<Integer, List<String>, String> listEach = EachInstances.listEach();
 *
 * // Get indexed traversal (index type is Integer for List)
 * IndexedTraversal<Integer, List<String>, String> indexed = listEach.indexedTraversal();
 *
 * // Use to modify with position awareness
 * List<String> numbered = IndexedTraversals.imodify(
 *     indexed,
 *     (index, value) -> value + " #" + index,
 *     list
 * );
 * }</pre>
 *
 * <h3>Creating Custom Each Instances:</h3>
 *
 * <p>To create an Each instance for a custom type, implement the {@link #each()} method:
 *
 * <pre>{@code
 * public static <A> Each<Tree<A>, A> treeEach() {
 *     return new Each<>() {
 *         @Override
 *         public Traversal<Tree<A>, A> each() {
 *             return new Traversal<>() {
 *                 @Override
 *                 public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Tree<A>> modifyF(
 *                         Function<A, Kind<F, A>> f,
 *                         Tree<A> source,
 *                         Applicative<F> app) {
 *                     // Implement tree traversal logic
 *                 }
 *             };
 *         }
 *     };
 * }
 * }</pre>
 *
 * @param <S> The container type (e.g., {@code List<A>}, {@code Map<K, V>}, {@code Optional<A>})
 * @param <A> The element type within the container
 * @see Traversal
 * @see EachIndexed
 * @see At
 * @see Ixed
 * @see org.higherkindedj.hkt.Traverse
 */
@FunctionalInterface
public interface Each<S extends @Nullable Object, A extends @Nullable Object> {

  /**
   * Returns the canonical traversal for all elements in this container.
   *
   * <p>The returned traversal focuses on every element of type {@code A} within {@code S}, enabling
   * bulk read and modification operations. The traversal order depends on the container type:
   *
   * <ul>
   *   <li>List: Elements are traversed in order (index 0, 1, 2, ...)
   *   <li>Set: Traversal order depends on the Set implementation
   *   <li>Map: Values are traversed; order depends on the Map implementation
   *   <li>Optional/Maybe: The single element (if present) is traversed
   * </ul>
   *
   * <p>The traversal is lawful and satisfies the standard traversal laws:
   *
   * <ul>
   *   <li>Identity: {@code modifyF(of, s, app) == app.of(s)}
   *   <li>Composition: Traversals compose associatively
   * </ul>
   *
   * @return A {@link Traversal} focusing on all elements; never null
   */
  Traversal<S, A> each();

  /**
   * Checks if this {@code Each} instance supports indexed traversal, that is, whether it is an
   * {@link EachIndexed}.
   *
   * <p>To reach the indexed traversal from code that holds only an {@code Each<S, A>}, narrow with
   * a type pattern. A cast to a particular index type would be unchecked.
   *
   * <pre>{@code
   * if (each instanceof EachIndexed<?, S, A> indexed) {
   *     IndexedTraversal<?, S, A> traversal = indexed.indexedTraversal();
   * }
   * }</pre>
   *
   * @return {@code true} if this is an {@link EachIndexed}; {@code false} otherwise
   */
  default boolean supportsIndexed() {
    return this instanceof EachIndexed<?, ?, ?>;
  }

  /**
   * Creates an Each instance from an existing Traversal.
   *
   * <p>This factory method allows wrapping any Traversal as an Each instance, which is useful for
   * integrating existing traversals with APIs that expect Each.
   *
   * <pre>{@code
   * Traversal<MyContainer<String>, String> myTraversal = ...;
   * Each<MyContainer<String>, String> myEach = Each.fromTraversal(myTraversal);
   * }</pre>
   *
   * @param traversal The traversal to wrap; must not be null
   * @param <S> The container type
   * @param <A> The element type
   * @return An Each instance that delegates to the given traversal
   */
  static <S extends @Nullable Object, A extends @Nullable Object> Each<S, A> fromTraversal(
      Traversal<S, A> traversal) {
    Objects.requireNonNull(traversal, "traversal must not be null");
    return () -> traversal;
  }

  /**
   * Creates an {@link EachIndexed} instance from an existing {@link IndexedTraversal}, preserving
   * its index type {@code I} at the type level.
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();
   * EachIndexed<Integer, List<String>, String> each = Each.fromIndexedTraversal(indexed);
   *
   * Traversal<List<String>, String> trav = each.each();
   * IndexedTraversal<Integer, List<String>, String> iTrav = each.indexedTraversal();
   * }</pre>
   *
   * @param indexedTraversal The indexed traversal to wrap; must not be null
   * @param <I> The index type of the traversal, carried through to the result
   * @param <S> The container type
   * @param <A> The element type
   * @return An {@link EachIndexed} that provides both regular and indexed traversal
   */
  static <I, S extends @Nullable Object, A extends @Nullable Object>
      EachIndexed<I, S, A> fromIndexedTraversal(IndexedTraversal<I, S, A> indexedTraversal) {
    Objects.requireNonNull(indexedTraversal, "indexedTraversal must not be null");
    return () -> indexedTraversal;
  }
}
