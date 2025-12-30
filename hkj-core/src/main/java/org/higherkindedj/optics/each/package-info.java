// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Provides instances of the {@link org.higherkindedj.optics.Each} type class for common Java types.
 *
 * <p>The Each type class represents types that have a canonical element-wise traversal. This
 * package provides ready-to-use instances for standard Java collection types and monadic types.
 *
 * <h2>Available Instances</h2>
 *
 * <table border="1">
 *   <caption>Each Instances by Type</caption>
 *   <tr><th>Type</th><th>Factory Method</th><th>Indexed?</th><th>Index Type</th></tr>
 *   <tr><td>{@code List<A>}</td><td>{@code listEach()}</td><td>Yes</td><td>{@code Integer}</td></tr>
 *   <tr><td>{@code Set<A>}</td><td>{@code setEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code Map<K,V>}</td><td>{@code mapValuesEach()}</td><td>Yes</td><td>{@code K}</td></tr>
 *   <tr><td>{@code Optional<A>}</td><td>{@code optionalEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code Maybe<A>}</td><td>{@code maybeEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code Either<L,R>}</td><td>{@code eitherRightEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code Try<A>}</td><td>{@code trySuccessEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code Validated<E,A>}</td><td>{@code validatedEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code A[]}</td><td>{@code arrayEach()}</td><td>Yes</td><td>{@code Integer}</td></tr>
 *   <tr><td>{@code Stream<A>}</td><td>{@code streamEach()}</td><td>No</td><td>-</td></tr>
 *   <tr><td>{@code String}</td><td>{@code stringCharsEach()}</td><td>Yes</td><td>{@code Integer}</td></tr>
 *   <tr><td>{@code Kind<F,A>}</td><td>{@code fromTraverse(Traverse)}</td><td>No</td><td>-</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * import org.higherkindedj.optics.each.EachInstances;
 * import org.higherkindedj.optics.Each;
 * import org.higherkindedj.optics.Traversal;
 * import org.higherkindedj.optics.util.Traversals;
 *
 * // Get an Each instance for List
 * Each<List<String>, String> listEach = EachInstances.listEach();
 *
 * // Use the traversal
 * Traversal<List<String>, String> trav = listEach.each();
 * List<String> result = Traversals.modify(trav, String::toUpperCase, list);
 *
 * // Use indexed access
 * listEach.<Integer>eachWithIndex().ifPresent(indexed -> {
 *     IndexedTraversals.imodify(indexed, (i, s) -> i + ": " + s, list);
 * });
 * }</pre>
 *
 * @see org.higherkindedj.optics.Each
 * @see org.higherkindedj.optics.each.EachInstances
 */
@NullMarked
package org.higherkindedj.optics.each;

import org.jspecify.annotations.NullMarked;
