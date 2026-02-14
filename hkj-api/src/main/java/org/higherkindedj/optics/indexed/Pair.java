// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import org.jspecify.annotations.NullMarked;

/**
 * A simple immutable pair of two values, used for composing indices in indexed optics.
 *
 * <p>When two indexed optics are composed (e.g., an indexed traversal over a list composed with an
 * indexed traversal over a map), the resulting optic tracks both indices as a {@code Pair}.
 *
 * <p>This is a minimal record type designed for use within the optics API. For more feature-rich
 * tuple operations in hkj-core, see {@code org.higherkindedj.hkt.tuple.Tuple2} which provides
 * bifunctor operations and validation.
 *
 * <p>Example:
 *
 * <pre>{@code
 * IndexedTraversal<Integer, List<Map<String, User>>, Map<String, User>> ilist = ...;
 * IndexedTraversal<String, Map<String, User>, User> imap = ...;
 *
 * IndexedTraversal<Pair<Integer, String>, List<Map<String, User>>, User> composed =
 *     ilist.iandThen(imap);
 *
 * // Access the paired indices
 * composed.imodifyF((indices, user) -> {
 *     int listIndex = indices.first();
 *     String mapKey = indices.second();
 *     // ... position-aware transformation
 * }, source, app);
 * }</pre>
 *
 * @param <A> The type of the first element (typically the outer index)
 * @param <B> The type of the second element (typically the inner index)
 * @param first The first element of the pair
 * @param second The second element of the pair
 */
@NullMarked
public record Pair<A, B>(A first, B second) {

  /**
   * Creates a new pair with the first element transformed.
   *
   * @param newFirst The new first element
   * @param <C> The type of the new first element
   * @return A new pair with the transformed first element
   */
  public <C> Pair<C, B> withFirst(C newFirst) {
    return new Pair<>(newFirst, second);
  }

  /**
   * Creates a new pair with the second element transformed.
   *
   * @param newSecond The new second element
   * @param <C> The type of the new second element
   * @return A new pair with the transformed second element
   */
  public <C> Pair<A, C> withSecond(C newSecond) {
    return new Pair<>(first, newSecond);
  }

  /**
   * Swaps the elements of this pair.
   *
   * @return A new pair with elements swapped
   */
  public Pair<B, A> swap() {
    return new Pair<>(second, first);
  }

  /**
   * Creates a pair from two values.
   *
   * @param first The first element
   * @param second The second element
   * @param <A> The type of the first element
   * @param <B> The type of the second element
   * @return A new pair
   */
  public static <A, B> Pair<A, B> of(A first, B second) {
    return new Pair<>(first, second);
  }
}
