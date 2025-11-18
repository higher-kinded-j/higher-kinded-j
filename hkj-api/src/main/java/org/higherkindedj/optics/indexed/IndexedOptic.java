// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Optic;
import org.jspecify.annotations.NullMarked;

/**
 * An abstract representation of an indexed optic that provides access to both the index and value
 * during modifications. This is the base interface for all indexed optics (IndexedLens,
 * IndexedTraversal, IndexedFold).
 *
 * <p>Indexed optics are crucial for position-aware operations where you need to know the location
 * of each element being processed. Common use cases include:
 *
 * <ul>
 *   <li>Position-aware transformations (e.g., numbering items)
 *   <li>Debugging and tracking which elements are modified
 *   <li>Conditional logic based on position
 *   <li>Maintaining element provenance
 * </ul>
 *
 * <p>The index type {@code I} can be any type appropriate for the structure:
 *
 * <ul>
 *   <li>{@code Integer} for lists/arrays
 *   <li>{@code K} for {@code Map<K, V>}
 *   <li>{@code String} for named fields
 *   <li>{@code Tuple2<I, J>} for composed indexed optics
 * </ul>
 *
 * @param <I> The index type
 * @param <S> The source/target structure type
 * @param <A> The focused element type
 */
@NullMarked
public interface IndexedOptic<I, S, A> {

  /**
   * The fundamental operation of any indexed optic. It applies a function to the focused parts,
   * providing both the index and value, and returns the updated structure wrapped in an Applicative
   * context.
   *
   * <p>This operation enables position-aware transformations where the modification function can
   * use the index to determine how to transform each element.
   *
   * @param f The function to apply to each focused part, receiving both index and value
   * @param source The source structure
   * @param app The Applicative instance for the context 'F'
   * @param <F> The witness type for the Applicative context
   * @return The updated structure 'S' wrapped in the context 'F'
   */
  <F> Kind<F, S> imodifyF(BiFunction<I, A, Kind<F, A>> f, S source, Applicative<F> app);

  /**
   * Converts this indexed optic to a regular (non-indexed) optic by discarding the index
   * information.
   *
   * <p>This is useful when you want to use an indexed optic in a context that expects a regular
   * optic, or when the index information is not needed for a particular operation.
   *
   * @return A regular {@link Optic} that ignores index information
   */
  default Optic<S, S, A, A> unindexed() {
    IndexedOptic<I, S, A> self = this;
    return new Optic<>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        return self.imodifyF((i, a) -> f.apply(a), s, app);
      }
    };
  }

  /**
   * Composes this indexed optic with another indexed optic to create a new indexed optic with
   * paired indices.
   *
   * <p>When composing two indexed optics, the resulting optic tracks both indices using a {@link
   * Pair}. This allows you to maintain full provenance information through the composition.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<Map<String, User>>, Map<String, User>> listTraversal = ...;
   * IndexedTraversal<String, Map<String, User>, User> mapTraversal = ...;
   *
   * IndexedTraversal<Pair<Integer, String>, List<Map<String, User>>, User> composed =
   *     listTraversal.iandThen(mapTraversal);
   *
   * // Access both indices during modification
   * composed.imodifyF((indices, user) -> {
   *     int listIndex = indices.first();
   *     String mapKey = indices.second();
   *     // ... position-aware transformation
   * }, source, app);
   * }</pre>
   *
   * @param other The indexed optic to compose with
   * @param <J> The index type of the other optic
   * @param <B> The focus type of the other optic
   * @return A new indexed optic with paired indices
   */
  default <J, B> IndexedOptic<Pair<I, J>, S, B> iandThen(IndexedOptic<J, A, B> other) {
    IndexedOptic<I, S, A> self = this;
    return new IndexedOptic<>() {
      @Override
      public <F> Kind<F, S> imodifyF(
          BiFunction<Pair<I, J>, B, Kind<F, B>> f, S source, Applicative<F> app) {
        return self.imodifyF(
            (i, a) -> other.imodifyF((j, b) -> f.apply(new Pair<>(i, j), b), a, app), source, app);
      }
    };
  }

  /**
   * Composes this indexed optic with a regular (non-indexed) optic, preserving the index from this
   * optic.
   *
   * <p>This is useful when you want to focus deeper into a structure but only need the index from
   * the outer level.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<User>, User> users = ...;
   * Traversal<User, String> email = ...;
   *
   * IndexedTraversal<Integer, List<User>, String> userEmails = users.andThen(email);
   * // Index represents user position in list, not email position
   * }</pre>
   *
   * @param other The regular optic to compose with
   * @param <B> The focus type of the other optic
   * @return A new indexed optic that preserves this optic's index
   */
  default <B> IndexedOptic<I, S, B> andThen(Optic<A, A, B, B> other) {
    IndexedOptic<I, S, A> self = this;
    return new IndexedOptic<>() {
      @Override
      public <F> Kind<F, S> imodifyF(BiFunction<I, B, Kind<F, B>> f, S source, Applicative<F> app) {
        return self.imodifyF((i, a) -> other.modifyF(b -> f.apply(i, b), a, app), source, app);
      }
    };
  }
}
