// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.ixed;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Provides standard {@link Ixed} instances for common Java collection types.
 *
 * <p>This class contains factory methods that create {@code Ixed} instances for:
 *
 * <ul>
 *   <li>{@link Map} - indexed by key type {@code K}
 *   <li>{@link List} - indexed by {@link Integer} position
 * </ul>
 *
 * <p>All instances are built on top of {@link At} instances, composing with a prism that unwraps
 * the optional layer. This ensures consistency between {@code At} and {@code Ixed} semantics.
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Map operations - only updates existing keys
 * Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
 * Map<String, Integer> scores = Map.of("alice", 100, "bob", 85);
 *
 * // Get value at key using convenience method
 * Optional<Integer> aliceScore = IxedInstances.get(mapIx, "alice", scores);
 * // aliceScore = Optional[100]
 *
 * // Update existing key
 * Map<String, Integer> updated = IxedInstances.update(mapIx, "alice", 110, scores);
 * // updated = {alice=110, bob=85}
 *
 * // Non-existent key - no change
 * Map<String, Integer> unchanged = IxedInstances.update(mapIx, "charlie", 90, scores);
 * // unchanged = {alice=100, bob=85}
 *
 * // Modify with function
 * Optional<Integer> bobScore = IxedInstances.get(mapIx, "bob", scores);
 * // bobScore = Optional[85]
 *
 * Map<String, Integer> updatedBob = IxedInstances.modify(mapIx, "bob", x -> x + 10, scores);
 * // updatedBob = {alice=100, bob=95}
 *
 * // List operations - only updates existing indices
 * Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();
 * List<String> names = List.of("alice", "bob", "charlie");
 *
 * // Update existing index
 * List<String> updatedNames = IxedInstances.modify(listIx, 1, String::toUpperCase, names);
 * // updatedNames = ["alice", "BOB", "charlie"]
 *
 * // Out of bounds index - no change
 * List<String> unchangedNames = IxedInstances.modify(listIx, 10, String::toUpperCase, names);
 * // unchangedNames = ["alice", "bob", "charlie"]
 * }</pre>
 *
 * <h3>Design Note:</h3>
 *
 * <p>These instances are intentionally built on {@link At} to maintain consistency. The composition
 * pattern is:
 *
 * <pre>{@code
 * at.at(index).asTraversal().andThen(somePrism.asTraversal())
 * }</pre>
 *
 * <p>This ensures that the boundary behaviour of {@code Ixed} aligns with {@code At}, and both can
 * be used interchangeably based on whether insert/delete semantics are needed.
 */
@NullMarked
public final class IxedInstances {

  /** Private constructor to prevent instantiation. */
  private IxedInstances() {}

  /**
   * Creates an {@link Ixed} instance for {@link Map} types.
   *
   * <p>The returned {@code Ixed} provides a traversal to an existing value at a given key:
   *
   * <ul>
   *   <li>{@code ix(key)} focuses on the value if key exists, or zero elements if absent
   *   <li>Modifications are no-ops for absent keys (no insertion)
   * </ul>
   *
   * <p><strong>Null Value Limitation:</strong> Due to Java's {@link Optional} semantics, null map
   * values cannot be distinguished from absent keys. A key with null value appears the same as an
   * absent key.
   *
   * <p><strong>Immutability:</strong> All operations return new {@link Map} instances, leaving the
   * original unchanged.
   *
   * @param <K> The key type of the map
   * @param <V> The value type of the map
   * @return An {@code Ixed} instance for maps
   */
  public static <K, V> Ixed<Map<K, V>, K, @Nullable V> mapIx() {
    return fromAt(AtInstances.mapAt());
  }

  /**
   * Creates an {@link Ixed} instance for {@link List} types.
   *
   * <p>The returned {@code Ixed} provides a traversal to an existing element at a given index:
   *
   * <ul>
   *   <li>{@code ix(index)} focuses on the element if index is valid, or zero elements if out of
   *       bounds
   *   <li>Modifications are no-ops for invalid indices (no insertion, no exception)
   * </ul>
   *
   * <p><strong>Bounds Checking:</strong> Unlike {@link At#insertOrUpdate}, updating at an
   * out-of-bounds index has no effect and returns the original list unchanged. This is consistent
   * with the "zero or one element" semantics of {@code Ixed}.
   *
   * <p><strong>Immutability:</strong> All operations return new {@link List} instances, leaving the
   * original unchanged.
   *
   * @param <A> The element type of the list
   * @return An {@code Ixed} instance for lists
   */
  public static <A> Ixed<List<A>, Integer, A> listIx() {
    return fromAt(AtInstances.listAt());
  }

  /**
   * Creates an {@link Ixed} instance for {@link List} types using a custom {@link At} instance.
   *
   * <p>This factory method allows using alternative {@code At} implementations, such as {@link
   * AtInstances#listAtWithPadding(Object)}.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * // Create Ixed that uses padding semantics from underlying At
   * At<List<String>, Integer, String> paddingAt = AtInstances.listAtWithPadding(null);
   * Ixed<List<String>, Integer, String> listIx = IxedInstances.listIxFrom(paddingAt);
   * }</pre>
   *
   * @param at The {@link At} instance to build upon
   * @param <A> The element type of the list
   * @return An {@code Ixed} instance for lists using the provided {@code At}
   */
  public static <A> Ixed<List<A>, Integer, A> listIxFrom(At<List<A>, Integer, A> at) {
    return fromAt(at);
  }

  /**
   * Creates a generic {@link Ixed} instance from any {@link At} instance.
   *
   * <p>This factory method allows creating {@code Ixed} for any type that has an {@code At}
   * instance. The resulting {@code Ixed} composes the {@code At} with a prism that unwraps the
   * optional layer.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * // Custom At for a tree structure
   * At<Tree<A>, Path, A> treeAt = ...;
   * Ixed<Tree<A>, Path, A> treeIx = IxedInstances.fromAt(treeAt);
   *
   * // Now can safely access tree nodes
   * List<A> nodes = Traversals.getAll(treeIx.ix(somePath), tree);
   * }</pre>
   *
   * @param at The {@link At} instance to convert
   * @param <S> The structure type
   * @param <I> The index type
   * @param <A> The value type
   * @return An {@code Ixed} instance derived from the provided {@code At}
   */
  public static <S, I, A> Ixed<S, I, A> fromAt(At<S, I, A> at) {
    Prism<Optional<A>, A> somePrism = Prisms.some();
    return index ->
        new Traversal<>() {
          @Override
          public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
              Function<A, Kind<F, A>> f, S source, Applicative<F> applicative) {
            Optional<A> optValue = at.at(index).get(source);
            if (optValue.isEmpty()) {
              // Do not modify if absent - this prevents deletion semantics from leaking
              return applicative.of(source);
            }
            // Value is present, so we can proceed with modification
            Kind<F, Optional<A>> newOptValueF = somePrism.modifyF(f, optValue, applicative);
            return applicative.map(
                newOptValue -> at.at(index).set(newOptValue, source), newOptValueF);
          }
        };
  }

  // =========================================================================
  // Convenience Methods for Common Operations
  // =========================================================================

  /**
   * Retrieves the value at the given index, if present.
   *
   * <p>This is a convenience method equivalent to:
   *
   * <pre>{@code
   * List<A> results = Traversals.getAll(ixed.ix(index), source);
   * return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
   * }</pre>
   *
   * @param ixed The Ixed instance
   * @param index The index to look up
   * @param source The structure to query
   * @param <S> The structure type
   * @param <I> The index type
   * @param <A> The value type
   * @return An {@link Optional} containing the value if present, or empty if absent
   */
  public static <S, I, A> Optional<A> get(Ixed<S, I, A> ixed, I index, S source) {
    List<A> results = Traversals.getAll(ixed.ix(index), source);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  /**
   * Updates the value at the given index if it exists.
   *
   * <p>If the index does not exist in the structure, the original structure is returned unchanged.
   * This differs from {@link At#insertOrUpdate}, which would insert the value at a missing index.
   *
   * @param ixed The Ixed instance
   * @param index The index to update
   * @param value The new value
   * @param source The original structure
   * @param <S> The structure type
   * @param <I> The index type
   * @param <A> The value type
   * @return A new structure with the value updated, or unchanged if the index is absent
   */
  public static <S, I, A> S update(Ixed<S, I, A> ixed, I index, A value, S source) {
    return Traversals.modify(ixed.ix(index), _ -> value, source);
  }

  /**
   * Modifies the value at the given index using the provided function, if the index exists.
   *
   * <p>If the index does not exist, the structure is returned unchanged.
   *
   * @param ixed The Ixed instance
   * @param index The index to modify
   * @param modifier The function to apply to the existing value
   * @param source The original structure
   * @param <S> The structure type
   * @param <I> The index type
   * @param <A> The value type
   * @return A new structure with the modified value, or unchanged if absent
   */
  public static <S, I, A> S modify(Ixed<S, I, A> ixed, I index, Function<A, A> modifier, S source) {
    return Traversals.modify(ixed.ix(index), modifier, source);
  }

  /**
   * Checks if a value is present at the given index.
   *
   * @param ixed The Ixed instance
   * @param index The index to check
   * @param source The structure to query
   * @param <S> The structure type
   * @param <I> The index type
   * @param <A> The value type
   * @return {@code true} if a value is present at the index, {@code false} otherwise
   */
  public static <S, I, A> boolean contains(Ixed<S, I, A> ixed, I index, S source) {
    return get(ixed, index, source).isPresent();
  }
}
