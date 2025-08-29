/**
 * This package provides a collection of functional optics (Lens, Prism, Traversal) for the
 * higher-kinded-j library. Optics are powerful, composable tools for inspecting and manipulating
 * complex, immutable data structures in a purely functional way.
 *
 * <h2>Core Concepts</h2>
 *
 * <p>Optics allow you to "focus" on a specific part of a larger data structure and perform
 * operations like getting, setting, or modifying it. When an operation is performed, the optic
 * rebuilds the entire structure with the change, preserving immutability.
 *
 * <p>This package provides the following key optic types:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.optics.Lens}: For focusing on a single field within a product type
 *       (e.g., a record or class). A Lens provides a way to get and set a part of a whole. For
 *       example, focusing on the {@code street} field of an {@code Address} record.
 *   <li>{@link org.higherkindedj.optics.Prism}: For focusing on a single variant within a sum type
 *       (e.g., a sealed interface). A Prism can be seen as a "partial" Lens—it allows you to get an
 *       optional value (only if the variant matches) and to construct the whole from a part. For
 *       example, focusing on the {@code JsonString} case of a {@code Json} sealed interface.
 *   <li>{@link org.higherkindedj.optics.Traversal}: For focusing on multiple values within a
 *       structure (e.g., all elements in a {@code List}, or all values in a {@code Map}). A
 *       Traversal allows you to modify all focused elements at once.
 * </ul>
 *
 * <h2>Composition</h2>
 *
 * <p>The true power of optics comes from their ability to be composed. You can chain optics
 * together to focus deeply into nested data structures. For example, you could compose a Prism and
 * a Lens to focus on a field inside a specific variant of a sealed type.
 *
 * <pre>{@code
 * // Example: Composing a Prism and a Lens
 * // Given a Prism<Result, Json> and a Lens<Json, String>
 * // You can compose them to create a Traversal<Result, String>
 * Traversal<Result, String> composed = successPrism.asTraversal().andThen(jsonStringLens.asTraversal());
 * }</pre>
 *
 * <h2>Integration with HKT</h2>
 *
 * <p>The optics in this package are built on top of the {@link org.higherkindedj.hkt.Kind}
 * abstraction and integrate with type classes like {@link org.higherkindedj.hkt.Functor} and {@link
 * org.higherkindedj.hkt.Applicative}. This allows for powerful, generic operations like traversing
 * a structure and applying effectful modifications using an Applicative context (e.g., validating
 * and modifying fields at the same time).
 */
@NullMarked
package org.higherkindedj.optics;

import org.jspecify.annotations.NullMarked;
