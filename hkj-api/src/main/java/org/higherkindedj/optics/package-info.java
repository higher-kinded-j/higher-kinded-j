/**
 * This package provides a collection of functional optics (Lens, Prism, Affine, Traversal, and
 * more) for the higher-kinded-j library. Optics are powerful, composable tools for inspecting and
 * manipulating complex, immutable data structures in a purely functional way.
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
 *       (e.g., a record or class). A Lens provides a way to get and set a part of a whole.
 *   <li>{@link org.higherkindedj.optics.Prism}: For focusing on a single variant within a sum type
 *       (e.g., a sealed interface). A Prism can be seen as a "partial" Lens—it allows you to get an
 *       optional value (only if the variant matches) and to construct the whole from a part.
 *   <li>{@link org.higherkindedj.optics.Affine}: For focusing on zero or one element within a
 *       structure. An Affine combines the partial access of a Prism with the update capability of a
 *       Lens, making it ideal for optional fields in records or nullable properties.
 *   <li>{@link org.higherkindedj.optics.Traversal}: For focusing on multiple values within a
 *       structure (e.g., all elements in a {@code List}). A Traversal allows you to modify all
 *       focused elements at once.
 *   <li>{@link org.higherkindedj.optics.Iso}: For isomorphisms—bidirectional, lossless conversions
 *       between two types.
 *   <li>{@link org.higherkindedj.optics.Fold}: For read-only aggregation over multiple elements
 *       using a monoid.
 *   <li>{@link org.higherkindedj.optics.Getter}: For read-only access to a single value within a
 *       structure.
 *   <li>{@link org.higherkindedj.optics.Setter}: For write-only modification of values within a
 *       structure.
 *   <li>{@link org.higherkindedj.optics.At}: A type class for indexed CRUD operations with
 *       insert/delete semantics via {@code Optional}.
 *   <li>{@link org.higherkindedj.optics.Ixed}: A type class for safe indexed access to existing
 *       elements, providing a Traversal that focuses on zero or one element.
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
 * <h2>Indexed Access with At and Ixed</h2>
 *
 * <p>The {@link org.higherkindedj.optics.At} and {@link org.higherkindedj.optics.Ixed} type classes
 * provide complementary patterns for indexed access:
 *
 * <ul>
 *   <li>{@code At} provides CRUD operations via {@code Lens<S, Optional<A>>}, allowing insertion
 *       and deletion.
 *   <li>{@code Ixed} provides safe read/update via {@code Traversal<S, A>}, focusing only on
 *       existing elements.
 * </ul>
 *
 * <pre>{@code
 * // At: Can insert and delete
 * At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
 * Map<String, Integer> withNew = mapAt.insertOrUpdate("bob", 85, scores);
 * Map<String, Integer> withoutAlice = mapAt.remove("alice", scores);
 *
 * // Ixed: Only updates existing elements
 * Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
 * Map<String, Integer> updated = IxedInstances.update(mapIx, "alice", 110, scores); // Updates existing
 * Map<String, Integer> unchanged = IxedInstances.update(mapIx, "bob", 85, scores);  // No-op if missing
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
