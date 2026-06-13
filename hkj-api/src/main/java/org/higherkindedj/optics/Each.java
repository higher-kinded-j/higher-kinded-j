// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import org.higherkindedj.optics.indexed.IndexedTraversal;

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
 * position in a List, key in a Map). Use {@link #eachWithIndex()} to get an {@link
 * IndexedTraversal} that provides access to both index and value:
 *
 * <pre>{@code
 * Each<List<String>, String> listEach = EachInstances.listEach();
 *
 * // Get indexed traversal (index type is Integer for List)
 * Optional<IndexedTraversal<Integer, List<String>, String>> indexed =
 *     listEach.eachWithIndex();
 *
 * // Use to modify with position awareness
 * indexed.ifPresent(iTraversal -> {
 *     List<String> numbered = IndexedTraversals.imodify(
 *         iTraversal,
 *         (index, value) -> value + " #" + index,
 *         list
 *     );
 * });
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
 * @see At
 * @see Ixed
 * @see org.higherkindedj.hkt.Traverse
 */
@FunctionalInterface
public interface Each<S, A> {

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
   * Returns an indexed traversal if the container supports indexed access, choosing the index type
   * at the call site.
   *
   * <p>This method is unsound: the caller picks {@code <I>} freely, but the underlying traversal
   * has a fixed index type, so a mismatched request compiles and then fails at runtime with a
   * {@link ClassCastException}. Indexing instances now implement {@link EachIndexed}, whose {@link
   * EachIndexed#indexedTraversal()} fixes the index type at the type level — prefer that.
   *
   * @param <I> the requested index type
   * @return an {@link Optional} of the {@link IndexedTraversal} if indexed access is supported;
   *     {@link Optional#empty()} otherwise
   * @deprecated since 0.4.7, for removal in 0.5.0. Narrow to {@link EachIndexed} and call {@link
   *     EachIndexed#indexedTraversal()} instead — the index type is then checked at compile time.
   */
  @Deprecated(since = "0.4.7", forRemoval = true)
  default <I> Optional<IndexedTraversal<I, S, A>> eachWithIndex() {
    return Optional.empty();
  }

  /**
   * Checks if this {@code Each} instance supports indexed traversal — i.e. whether it is an {@link
   * EachIndexed}.
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
  static <S, A> Each<S, A> fromTraversal(Traversal<S, A> traversal) {
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
  static <I, S, A> EachIndexed<I, S, A> fromIndexedTraversal(
      IndexedTraversal<I, S, A> indexedTraversal) {
    return () -> indexedTraversal;
  }
}
