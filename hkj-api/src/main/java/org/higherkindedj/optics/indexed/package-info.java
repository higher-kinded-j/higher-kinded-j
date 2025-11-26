// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Indexed optics that track the index/position of each focused element.
 *
 * <p>This package provides indexed variants of the standard optics, enabling position-aware
 * operations. Unlike regular optics that only provide access to values, indexed optics pass both
 * the index and value to modification functions.
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li><b>Position-Aware:</b> Know where each element came from
 *   <li><b>Debugging:</b> Track which elements are being modified
 *   <li><b>Conditional Logic:</b> Apply different logic based on position
 *   <li><b>Index Composition:</b> Compose indexed optics with paired indices using {@link
 *       org.higherkindedj.optics.indexed.Pair}
 * </ul>
 *
 * <h2>Core Types</h2>
 *
 * <dl>
 *   <dt>{@link org.higherkindedj.optics.indexed.IndexedOptic}
 *   <dd>Base interface for all indexed optics
 *   <dt>{@link org.higherkindedj.optics.indexed.IndexedTraversal}
 *   <dd>Zero or more elements with position tracking
 *   <dt>{@link org.higherkindedj.optics.indexed.IndexedFold}
 *   <dd>Read-only indexed aggregation
 *   <dt>{@link org.higherkindedj.optics.indexed.IndexedLens}
 *   <dd>Single indexed field access
 * </dl>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create an indexed traversal for lists
 * IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
 *
 * // Number each element
 * List<String> numbered = IndexedTraversals.imodify(
 *     ilist,
 *     (index, value) -> value + " #" + index,
 *     List.of("a", "b", "c")
 * );
 * // Result: ["a #0", "b #1", "c #2"]
 *
 * // Filter by index
 * IndexedTraversal<Integer, List<String>, String> evens = ilist.filterIndex(i -> i % 2 == 0);
 *
 * // Compose with regular optics
 * IndexedTraversal<Integer, List<User>, String> userNames =
 *     IndexedTraversals.<User>forList().andThen(userNameLens.asTraversal());
 *
 * // Compose two indexed optics (paired indices)
 * IndexedTraversal<Integer, List<Map<String, User>>, Map<String, User>> ilistMap =
 *     IndexedTraversals.forList();
 * IndexedTraversal<String, Map<String, User>, User> imap = IndexedTraversals.forMap();
 * IndexedTraversal<Pair<Integer, String>, List<Map<String, User>>, User> composed =
 *     ilistMap.iandThen(imap);
 * // Index is Pair(listIndex, mapKey)
 * }</pre>
 *
 * @see org.higherkindedj.optics.Optic
 * @see org.higherkindedj.optics.Traversal
 * @see org.higherkindedj.optics.Fold
 * @see org.higherkindedj.optics.Lens
 */
@NullMarked
package org.higherkindedj.optics.indexed;

import org.jspecify.annotations.NullMarked;
