// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * A final utility class providing static factory methods for creating {@link Traversal}s for {@link
 * Tuple2} types.
 *
 * <p>These combinators allow traversing tuple structures when both elements share the same type,
 * enabling operations like "modify both elements of a pair simultaneously".
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Modify both elements of a tuple
 * Traversal<Tuple2<Integer, Integer>, Integer> bothInts = TupleTraversals.both();
 * Tuple2<Integer, Integer> pair = new Tuple2<>(10, 20);
 * Tuple2<Integer, Integer> doubled = Traversals.modify(bothInts, x -> x * 2, pair);
 * // Result: Tuple2(20, 40)
 *
 * // Get both elements
 * List<Integer> values = Traversals.getAll(bothInts, pair);
 * // Result: [10, 20]
 * }</pre>
 */
@NullMarked
public final class TupleTraversals {

  /** Private constructor to prevent instantiation. */
  private TupleTraversals() {}

  /**
   * Creates a {@code Traversal} that focuses on both elements of a {@link Tuple2} when they share
   * the same type.
   *
   * <p>This traversal has cardinality 2, focusing on both the first and second elements of the
   * tuple. Effectful functions are applied to both positions, and the results are sequenced using
   * the applicative's {@code map2} operation.
   *
   * <p>This is particularly useful for:
   *
   * <ul>
   *   <li>Applying the same transformation to both tuple elements
   *   <li>Collecting both values from a tuple
   *   <li>Composing with other traversals for nested structures
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Tuple2<String, String>, String> bothStrings = TupleTraversals.both();
   *
   * // Modify both elements
   * Tuple2<String, String> names = new Tuple2<>("alice", "bob");
   * Tuple2<String, String> capitalized = Traversals.modify(
   *     bothStrings,
   *     s -> s.substring(0, 1).toUpperCase() + s.substring(1),
   *     names
   * );
   * // Result: Tuple2("Alice", "Bob")
   *
   * // Extract both elements
   * List<String> allNames = Traversals.getAll(bothStrings, names);
   * // Result: ["alice", "bob"]
   *
   * // Compose with other traversals
   * Traversal<List<Tuple2<Integer, Integer>>, Integer> allInts =
   *     Traversals.<Tuple2<Integer, Integer>>forList()
   *         .andThen(TupleTraversals.both());
   * }</pre>
   *
   * @param <A> The type of both elements in the tuple.
   * @return A {@code Traversal} focusing on both elements of the tuple.
   */
  public static <A> Traversal<Tuple2<A, A>, A> both() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, Tuple2<A, A>> modifyF(
          final Function<A, Kind<F, A>> f,
          final Tuple2<A, A> source,
          final Applicative<F> applicative) {
        return Traversals.traverseTuple2Both(source, f, applicative);
      }
    };
  }
}
