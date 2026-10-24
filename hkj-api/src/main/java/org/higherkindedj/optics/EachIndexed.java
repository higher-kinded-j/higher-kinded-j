// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.jspecify.annotations.Nullable;

/**
 * An {@link Each} whose elements carry a meaningful index, with the index type {@code I} fixed at
 * the type level.
 *
 * <p>{@link #indexedTraversal()} returns an {@link IndexedTraversal} whose index type is {@code I},
 * so a wrong index type is a compile error. This mirrors {@link At} and {@link Ixed}, which also
 * carry their index type at the type level. {@link Each#supportsIndexed()} shows how code holding a
 * plain {@link Each} reaches it.
 *
 * <pre>{@code
 * EachIndexed<Integer, List<String>, String> listEach = EachInstances.listEach();
 *
 * // Index type is Integer, checked at compile time.
 * IndexedTraversal<Integer, List<String>, String> indexed = listEach.indexedTraversal();
 *
 * List<String> numbered = IndexedTraversals.imodify(
 *     indexed, (i, s) -> (i + 1) + ". " + s, List.of("first", "second", "third"));
 * }</pre>
 *
 * @param <I> the index type (e.g. {@code Integer} for lists, arrays and strings, {@code K} for
 *     maps)
 * @param <S> the container type
 * @param <A> the element type
 * @see Each
 * @see At
 * @see Ixed
 */
@FunctionalInterface
public interface EachIndexed<I, S extends @Nullable Object, A extends @Nullable Object>
    extends Each<S, A> {

  /**
   * Returns the indexed traversal for this container, with a fixed, compile-time-checked index type
   * {@code I}.
   *
   * @return the indexed traversal; never null
   */
  IndexedTraversal<I, S, A> indexedTraversal();

  /**
   * The element traversal, derived from {@link #indexedTraversal()} by dropping the index.
   *
   * @return a {@link Traversal} focusing on all elements; never null
   */
  @Override
  default Traversal<S, A> each() {
    return indexedTraversal().asTraversal();
  }
}
