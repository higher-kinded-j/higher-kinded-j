// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A final utility class providing static helper methods and factory functions for working with
 * indexed optics.
 *
 * <p>This class provides:
 *
 * <ul>
 *   <li>Factory methods for creating indexed traversals for common data structures
 *   <li>Convenience methods for modifying and extracting data with index awareness
 *   <li>Composition helpers for building complex indexed optics
 * </ul>
 */
@NullMarked
public final class IndexedTraversals {

  /** Private constructor to prevent instantiation. */
  private IndexedTraversals() {}

  /**
   * Creates an {@link IndexedTraversal} that focuses on every element within a {@link List}, with
   * the element's index as the key.
   *
   * <p>This is the canonical indexed traversal for lists, providing zero-based integer indices.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
   *
   * // Number each element
   * List<String> numbered = imodify(ilist, (i, s) -> s + " #" + i, List.of("a", "b", "c"));
   * // ["a #0", "b #1", "c #2"]
   *
   * // Process only even positions
   * IndexedTraversal<Integer, List<String>, String> evens = ilist.filterIndex(i -> i % 2 == 0);
   * List<String> modified = imodify(evens, (i, s) -> s.toUpperCase(), List.of("a", "b", "c", "d"));
   * // ["A", "b", "C", "d"]
   * }</pre>
   *
   * @param <A> The element type of the list
   * @return An {@link IndexedTraversal} for list elements with their indices
   */
  public static <A> IndexedTraversal<Integer, List<A>, A> forList() {
    return new IndexedTraversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> imodifyF(
          BiFunction<Integer, A, Kind<F, A>> f, List<A> source, Applicative<F> app) {
        if (source.isEmpty()) {
          return app.of(source);
        }

        // Apply f to each element with its index, collecting the results
        List<Kind<F, A>> modifiedEffects = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
          modifiedEffects.add(f.apply(i, source.get(i)));
        }

        // Sequence the effects into a single effect containing the list
        return sequenceList(modifiedEffects, app);
      }
    };
  }

  /**
   * Creates an {@link IndexedTraversal} that focuses on every value within a {@link Map}, with the
   * entry's key as the index.
   *
   * <p>This traversal provides access to all map values along with their keys.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedTraversal<String, Map<String, Integer>, Integer> imap = IndexedTraversals.forMap();
   *
   * Map<String, Integer> scores = Map.of("alice", 85, "bob", 92, "carol", 78);
   *
   * // Add prefix based on key
   * Map<String, String> labeled = imodify(
   *     IndexedTraversals.<String, Integer>forMap().andThen(intToStringTraversal),
   *     (key, score) -> key + ": " + score,
   *     scores
   * );
   *
   * // Get all key-value pairs
   * List<Pair<String, Integer>> indexed = toIndexedList(imap, scores);
   * // [Pair("alice", 85), Pair("bob", 92), Pair("carol", 78)]
   * }</pre>
   *
   * @param <K> The key type of the map
   * @param <V> The value type of the map
   * @return An {@link IndexedTraversal} for map values with their keys as indices
   */
  public static <K, V> IndexedTraversal<K, Map<K, V>, V> forMap() {
    return new IndexedTraversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Map<K, V>> imodifyF(
          BiFunction<K, V, Kind<F, V>> f, Map<K, V> source, Applicative<F> app) {
        if (source.isEmpty()) {
          return app.of(source);
        }

        // Collect keys and effects separately
        List<K> keys = new ArrayList<>(source.size());
        List<Kind<F, V>> effects = new ArrayList<>(source.size());
        for (Map.Entry<K, V> entry : source.entrySet()) {
          keys.add(entry.getKey());
          effects.add(f.apply(entry.getKey(), entry.getValue()));
        }

        // Sequence all effects into a single Kind<F, List<V>>
        Kind<F, List<V>> sequencedValues = sequenceList(effects, app);

        // Build the final map in one step
        return app.map(
            newValues -> {
              Map<K, V> newMap = new HashMap<>(keys.size());
              for (int i = 0; i < keys.size(); i++) {
                newMap.put(keys.get(i), newValues.get(i));
              }
              return newMap;
            },
            sequencedValues);
      }
    };
  }

  /**
   * Creates an {@link IndexedFold} that focuses on every element within a {@link List}, with the
   * element's index as the key.
   *
   * <p>This is the read-only version of {@link #forList()}.
   *
   * @param <A> The element type of the list
   * @return An {@link IndexedFold} for list elements with their indices
   */
  public static <A> IndexedFold<Integer, List<A>, A> foldList() {
    return IndexedTraversals.<A>forList().asIndexedFold();
  }

  /**
   * Creates an {@link IndexedFold} that focuses on every value within a {@link Map}, with the
   * entry's key as the index.
   *
   * <p>This is the read-only version of {@link #forMap()}.
   *
   * @param <K> The key type of the map
   * @param <V> The value type of the map
   * @return An {@link IndexedFold} for map values with their keys as indices
   */
  public static <K, V> IndexedFold<K, Map<K, V>, V> foldMap() {
    return IndexedTraversals.<K, V>forMap().asIndexedFold();
  }

  /**
   * Modifies all targets of an {@link IndexedTraversal} using a pure function that receives both
   * index and value.
   *
   * <p>This is a convenience method that wraps the function in the {@link Id} monad and immediately
   * unwraps the result.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
   * List<String> result = IndexedTraversals.imodify(
   *     ilist,
   *     (index, value) -> value + " at " + index,
   *     List.of("a", "b", "c")
   * );
   * // ["a at 0", "b at 1", "c at 2"]
   * }</pre>
   *
   * @param traversal The indexed traversal to use
   * @param f A function that takes index and value, returning the new value
   * @param source The source structure
   * @param <I> The index type
   * @param <S> The structure type
   * @param <A> The element type
   * @return A new, updated source structure
   */
  public static <I, S, A> @Nullable S imodify(
      final IndexedTraversal<I, S, A> traversal, final BiFunction<I, A, A> f, final S source) {
    BiFunction<I, A, Kind<IdKind.Witness, A>> fId = (i, a) -> Id.of(f.apply(i, a));
    Kind<IdKind.Witness, S> resultInId = traversal.imodifyF(fId, source, IdMonad.instance());
    return IdKindHelper.ID.narrow(resultInId).value();
  }

  /**
   * Extracts all targets of an {@link IndexedTraversal} along with their indices.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
   * List<Pair<Integer, String>> indexed = IndexedTraversals.toIndexedList(
   *     ilist,
   *     List.of("a", "b", "c")
   * );
   * // [Pair(0, "a"), Pair(1, "b"), Pair(2, "c")]
   * }</pre>
   *
   * @param traversal The indexed traversal to use
   * @param source The source structure
   * @param <I> The index type
   * @param <S> The structure type
   * @param <A> The element type
   * @return A list of index-value pairs
   */
  public static <I, S, A> List<Pair<I, A>> toIndexedList(
      final IndexedTraversal<I, S, A> traversal, final S source) {
    final List<Pair<I, A>> results = new ArrayList<>();
    traversal.imodifyF(
        (i, a) -> {
          results.add(new Pair<>(i, a));
          return Id.of(a);
        },
        source,
        IdMonad.instance());
    return results;
  }

  /**
   * Extracts all targets of an {@link IndexedTraversal}, discarding index information.
   *
   * @param traversal The indexed traversal to use
   * @param source The source structure
   * @param <I> The index type
   * @param <S> The structure type
   * @param <A> The element type
   * @return A list of all focused values
   */
  public static <I, S, A> List<A> getAll(
      final IndexedTraversal<I, S, A> traversal, final S source) {
    final List<A> results = new ArrayList<>();
    traversal.imodifyF(
        (i, a) -> {
          results.add(a);
          return Id.of(a);
        },
        source,
        IdMonad.instance());
    return results;
  }

  /**
   * Counts the number of focused elements in the structure.
   *
   * @param traversal The indexed traversal to use
   * @param source The source structure
   * @param <I> The index type
   * @param <S> The structure type
   * @param <A> The element type
   * @return The count of focused elements
   */
  public static <I, S, A> int length(final IndexedTraversal<I, S, A> traversal, final S source) {
    return toIndexedList(traversal, source).size();
  }

  /**
   * Sequences a list of effects into a single effect containing a list.
   *
   * <p>This is a helper method for implementing indexed traversals over collections.
   *
   * @param effects List of effects to sequence
   * @param app The Applicative instance
   * @param <F> The effect type
   * @param <A> The element type
   * @return A single effect containing the list of results
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Kind<F, List<A>> sequenceList(
      final List<Kind<F, A>> effects, final Applicative<F> app) {
    Kind<F, List<A>> result = app.of(new ArrayList<>());

    for (Kind<F, A> effect : effects) {
      result =
          app.map2(
              result,
              effect,
              (list, a) -> {
                List<A> newList = new ArrayList<>(list);
                newList.add(a);
                return newList;
              });
    }

    return result;
  }

  /**
   * Creates an affine {@link IndexedTraversal} that focuses on a single value only if it matches
   * the given index predicate.
   *
   * <p>This is useful for creating conditional indexed access patterns.
   *
   * @param indexPredicate Predicate on the index to determine if focusing should occur
   * @param <I> The index type
   * @param <A> The element type
   * @return An indexed traversal that focuses based on index condition
   */
  public static <I, A> IndexedTraversal<I, Pair<I, A>, A> filteredByIndex(
      final Predicate<? super I> indexPredicate) {
    return new IndexedTraversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Pair<I, A>> imodifyF(
          BiFunction<I, A, Kind<F, A>> f, Pair<I, A> source, Applicative<F> app) {
        if (indexPredicate.test(source.first())) {
          return app.map(
              newA -> new Pair<>(source.first(), newA), f.apply(source.first(), source.second()));
        } else {
          return app.of(source);
        }
      }
    };
  }

  /**
   * Converts a {@link Pair} to a {@link Tuple2}.
   *
   * <p>This is useful when you need to use the richer Tuple2 API from hkj-core after working with
   * indexed optics.
   *
   * @param pair The pair to convert
   * @param <A> The type of the first element
   * @param <B> The type of the second element
   * @return A Tuple2 with the same elements
   */
  public static <A, B> Tuple2<A, B> pairToTuple2(Pair<A, B> pair) {
    return new Tuple2<>(pair.first(), pair.second());
  }

  /**
   * Converts a {@link Tuple2} to a {@link Pair}.
   *
   * <p>This is useful when you need to work with indexed optics APIs that use Pair.
   *
   * @param tuple The tuple to convert
   * @param <A> The type of the first element
   * @param <B> The type of the second element
   * @return A Pair with the same elements
   */
  public static <A, B> Pair<A, B> tuple2ToPair(Tuple2<A, B> tuple) {
    return new Pair<>(tuple._1(), tuple._2());
  }

  /**
   * Converts a list of {@link Pair}s to a list of {@link Tuple2}s.
   *
   * @param pairs The list of pairs to convert
   * @param <A> The type of the first element
   * @param <B> The type of the second element
   * @return A list of Tuple2s
   */
  public static <A, B> List<Tuple2<A, B>> pairsToTuple2s(List<Pair<A, B>> pairs) {
    List<Tuple2<A, B>> result = new ArrayList<>(pairs.size());
    for (Pair<A, B> pair : pairs) {
      result.add(pairToTuple2(pair));
    }
    return result;
  }

  /**
   * Converts a list of {@link Tuple2}s to a list of {@link Pair}s.
   *
   * @param tuples The list of tuples to convert
   * @param <A> The type of the first element
   * @param <B> The type of the second element
   * @return A list of Pairs
   */
  public static <A, B> List<Pair<A, B>> tuple2sToPairs(List<Tuple2<A, B>> tuples) {
    List<Pair<A, B>> result = new ArrayList<>(tuples.size());
    for (Tuple2<A, B> tuple : tuples) {
      result.add(tuple2ToPair(tuple));
    }
    return result;
  }
}
