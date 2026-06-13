// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import org.higherkindedj.optics.indexed.IndexedTraversal;

/**
 * An {@link Each} whose elements carry a meaningful index, with the index type {@code I} fixed at
 * the type level.
 *
 * <p>This is the type-safe successor to {@link Each#eachWithIndex()}. That method let the caller
 * choose the index type {@code <I>} freely even though the underlying traversal's index type is
 * fixed, so a mismatched request compiled but failed at runtime with a {@link ClassCastException}.
 * {@code EachIndexed} instead exposes the underlying {@link IndexedTraversal} directly through
 * {@link #indexedTraversal()}, so the index type is checked at compile time. This mirrors {@link
 * At} and {@link Ixed}, which also carry their index type at the type level.
 *
 * <pre>{@code
 * EachIndexed<Integer, List<String>, String> listEach = EachInstances.listEach();
 *
 * // Index type is Integer, checked at compile time — no Optional, no cast.
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
public interface EachIndexed<I, S, A> extends Each<S, A> {

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

  /**
   * {@inheritDoc}
   *
   * @deprecated since 0.4.7, for removal in 0.5.0. Use {@link #indexedTraversal()} — it carries the
   *     index type {@code I} at the type level, so an incorrect index type is a compile error
   *     rather than a runtime {@link ClassCastException}.
   */
  @Override
  @Deprecated(since = "0.4.7", forRemoval = true)
  @SuppressWarnings({"unchecked", "removal"}) // deprecated bridge; prefer indexedTraversal()
  default <J> Optional<IndexedTraversal<J, S, A>> eachWithIndex() {
    return Optional.of((IndexedTraversal<J, S, A>) indexedTraversal());
  }
}
