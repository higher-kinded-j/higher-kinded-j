// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.each;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.util.TraverseTraversals;
import org.jspecify.annotations.NullMarked;

/**
 * Provides standard {@link Each} instances for common Java types.
 *
 * <p>This class contains factory methods that create {@code Each} instances for:
 *
 * <ul>
 *   <li>{@link List} - traverses all elements with Integer index support
 *   <li>{@link Set} - traverses all elements (no index support)
 *   <li>{@link Map} - traverses all values with key as index
 *   <li>{@link Optional} - traverses the value if present (0 or 1 element)
 *   <li>Arrays - traverses all elements with Integer index support
 *   <li>{@link Stream} - traverses all elements (consumed during traversal)
 *   <li>{@link String} - traverses all characters with Integer index support
 * </ul>
 *
 * <p>For hkj-core types (Maybe, Either, Try, Validated), see {@link
 * org.higherkindedj.optics.extensions.EachExtensions}.
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // List traversal
 * Each<List<String>, String> listEach = EachInstances.listEach();
 * Traversal<List<String>, String> trav = listEach.each();
 * List<String> upper = Traversals.modify(trav, String::toUpperCase, list);
 *
 * // Map values traversal
 * Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
 * List<Integer> values = Traversals.getAll(mapEach.each(), map);
 *
 * // With indexed access
 * Each<List<String>, String> listEach = EachInstances.listEach();
 * listEach.<Integer>eachWithIndex().ifPresent(indexed -> {
 *     List<String> numbered = IndexedTraversals.imodify(
 *         indexed,
 *         (i, s) -> (i + 1) + ". " + s,
 *         list
 *     );
 * });
 *
 * // Use with FocusDSL
 * TraversalPath<User, Order> allOrders = userPath.via(ordersLens).each(listEach);
 * }</pre>
 *
 * @see Each
 * @see Traversal
 * @see IndexedTraversal
 */
@NullMarked
public final class EachInstances {

  /** Private constructor to prevent instantiation. */
  private EachInstances() {}

  // ===== List =====

  /**
   * Creates an {@link Each} instance for {@link List} types.
   *
   * <p>The returned {@code Each} traverses all elements in order and supports indexed access via
   * {@link Each#eachWithIndex()}.
   *
   * @param <A> The element type of the list
   * @return An {@code Each} instance for lists
   */
  public static <A> Each<List<A>, A> listEach() {
    return new ListEach<>();
  }

  private static final class ListEach<A> implements Each<List<A>, A> {
    @Override
    public Traversal<List<A>, A> each() {
      return Traversals.forList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> Optional<IndexedTraversal<I, List<A>, A>> eachWithIndex() {
      IndexedTraversal<Integer, List<A>, A> indexed = IndexedTraversals.forList();
      return Optional.of((IndexedTraversal<I, List<A>, A>) indexed);
    }
  }

  // ===== Set =====

  /**
   * Creates an {@link Each} instance for {@link Set} types.
   *
   * <p>The returned {@code Each} traverses all elements. Traversal order depends on the Set
   * implementation. Does not support indexed access.
   *
   * @param <A> The element type of the set
   * @return An {@code Each} instance for sets
   */
  public static <A> Each<Set<A>, A> setEach() {
    return new SetEach<>();
  }

  private static final class SetEach<A> implements Each<Set<A>, A> {
    @Override
    public Traversal<Set<A>, A> each() {
      return TraverseTraversals.forSet();
    }
    // No indexed traversal for Set
  }

  // ===== Map Values =====

  /**
   * Creates an {@link Each} instance for {@link Map} values.
   *
   * <p>The returned {@code Each} traverses all values in the map, with the corresponding key as the
   * index in {@link Each#eachWithIndex()}.
   *
   * @param <K> The key type of the map
   * @param <V> The value type of the map
   * @return An {@code Each} instance for map values
   */
  public static <K, V> Each<Map<K, V>, V> mapValuesEach() {
    return new MapValuesEach<>();
  }

  private static final class MapValuesEach<K, V> implements Each<Map<K, V>, V> {
    @Override
    public Traversal<Map<K, V>, V> each() {
      return Traversals.forMapValues();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> Optional<IndexedTraversal<I, Map<K, V>, V>> eachWithIndex() {
      IndexedTraversal<K, Map<K, V>, V> indexed = IndexedTraversals.forMap();
      return Optional.of((IndexedTraversal<I, Map<K, V>, V>) indexed);
    }
  }

  // ===== Optional =====

  /**
   * Creates an {@link Each} instance for {@link Optional} types.
   *
   * <p>The returned {@code Each} traverses the value if present (0 or 1 element). Does not support
   * indexed access.
   *
   * @param <A> The element type of the optional
   * @return An {@code Each} instance for optionals
   */
  public static <A> Each<Optional<A>, A> optionalEach() {
    return new OptionalEach<>();
  }

  private static final class OptionalEach<A> implements Each<Optional<A>, A> {
    @Override
    public Traversal<Optional<A>, A> each() {
      return Traversals.forOptional();
    }
    // No indexed traversal for Optional
  }

  // ===== Array =====

  /**
   * Creates an {@link Each} instance for array types.
   *
   * <p>The returned {@code Each} traverses all elements in order and supports indexed access via
   * {@link Each#eachWithIndex()}.
   *
   * <p><strong>Note:</strong> Due to Java's type erasure, array instances cannot be cached and a
   * new instance is created for each call.
   *
   * @param <A> The element type of the array
   * @return An {@code Each} instance for arrays
   */
  public static <A> Each<A[], A> arrayEach() {
    return new ArrayEach<>();
  }

  private static final class ArrayEach<A> implements Each<A[], A> {
    @Override
    public Traversal<A[], A> each() {
      return FocusPaths.arrayElements();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> Optional<IndexedTraversal<I, A[], A>> eachWithIndex() {
      return Optional.of((IndexedTraversal<I, A[], A>) createArrayIndexedTraversal());
    }

    private IndexedTraversal<Integer, A[], A> createArrayIndexedTraversal() {
      return new IndexedTraversal<>() {
        @Override
        public <F extends WitnessArity<TypeArity.Unary>> Kind<F, A[]> imodifyF(
            BiFunction<Integer, A, Kind<F, A>> f, A[] source, Applicative<F> app) {
          if (source.length == 0) {
            return app.of(source);
          }

          List<Kind<F, A>> modifiedEffects = new ArrayList<>(source.length);
          for (int i = 0; i < source.length; i++) {
            modifiedEffects.add(f.apply(i, source[i]));
          }

          Kind<F, List<A>> sequenced = IndexedTraversals.sequenceList(modifiedEffects, app);

          return app.map(
              list -> {
                @SuppressWarnings("unchecked")
                A[] result =
                    (A[])
                        java.lang.reflect.Array.newInstance(
                            source.getClass().getComponentType(), list.size());
                for (int i = 0; i < list.size(); i++) {
                  result[i] = list.get(i);
                }
                return result;
              },
              sequenced);
        }
      };
    }
  }

  // ===== Stream =====

  /**
   * Creates an {@link Each} instance for {@link Stream} types.
   *
   * <p><strong>Warning:</strong> Streams can only be consumed once. After using this traversal, the
   * original stream will be exhausted. The result will be a new stream.
   *
   * @param <A> The element type of the stream
   * @return An {@code Each} instance for streams
   */
  public static <A> Each<Stream<A>, A> streamEach() {
    return new StreamEach<>();
  }

  private static final class StreamEach<A> implements Each<Stream<A>, A> {
    @Override
    public Traversal<Stream<A>, A> each() {
      return TraverseTraversals.forStream();
    }
    // No indexed traversal for Stream
  }

  // ===== String (Characters) =====

  /**
   * Creates an {@link Each} instance for {@link String} that traverses individual characters.
   *
   * <p>The returned {@code Each} traverses all characters in order and supports indexed access via
   * {@link Each#eachWithIndex()}.
   *
   * @return An {@code Each} instance for string characters
   */
  public static Each<String, Character> stringCharsEach() {
    return new StringCharsEach();
  }

  private static final class StringCharsEach implements Each<String, Character> {
    @Override
    public Traversal<String, Character> each() {
      return new StringCharsTraversal();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> Optional<IndexedTraversal<I, String, Character>> eachWithIndex() {
      return Optional.of(
          (IndexedTraversal<I, String, Character>) new StringCharsIndexedTraversal());
    }
  }

  private static final class StringCharsTraversal implements Traversal<String, Character> {
    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> modifyF(
        Function<Character, Kind<F, Character>> f, String source, Applicative<F> app) {
      if (source.isEmpty()) {
        return app.of(source);
      }

      List<Kind<F, Character>> modifiedEffects = new ArrayList<>(source.length());
      for (int i = 0; i < source.length(); i++) {
        modifiedEffects.add(f.apply(source.charAt(i)));
      }

      Kind<F, List<Character>> sequenced = IndexedTraversals.sequenceList(modifiedEffects, app);

      return app.map(
          chars -> {
            StringBuilder sb = new StringBuilder(chars.size());
            for (Character c : chars) {
              sb.append(c);
            }
            return sb.toString();
          },
          sequenced);
    }
  }

  private static final class StringCharsIndexedTraversal
      implements IndexedTraversal<Integer, String, Character> {
    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> imodifyF(
        BiFunction<Integer, Character, Kind<F, Character>> f, String source, Applicative<F> app) {
      if (source.isEmpty()) {
        return app.of(source);
      }

      List<Kind<F, Character>> modifiedEffects = new ArrayList<>(source.length());
      for (int i = 0; i < source.length(); i++) {
        modifiedEffects.add(f.apply(i, source.charAt(i)));
      }

      Kind<F, List<Character>> sequenced = IndexedTraversals.sequenceList(modifiedEffects, app);

      return app.map(
          chars -> {
            StringBuilder sb = new StringBuilder(chars.size());
            for (Character c : chars) {
              sb.append(c);
            }
            return sb.toString();
          },
          sequenced);
    }
  }

  // ===== From Traverse (Generic) =====

  /**
   * Creates an {@link Each} instance from a {@link Traverse} type class instance.
   *
   * <p>This factory method bridges the {@code Traverse} type class (which works on higher-kinded
   * types {@code Kind<F, A>}) to the {@code Each} type class (which works on concrete types).
   *
   * <pre>{@code
   * // Create Each from a Traverse instance
   * Traverse<TreeKind.Witness> treeTraverse = TreeTraverse.INSTANCE;
   * Each<Kind<TreeKind.Witness, String>, String> treeEach =
   *     EachInstances.fromTraverse(treeTraverse);
   *
   * // Use like any other Each
   * Traversal<Kind<TreeKind.Witness, String>, String> trav = treeEach.each();
   * }</pre>
   *
   * @param <F> The witness type of the traversable container
   * @param <A> The element type within the container
   * @param traverse The Traverse instance for the container type; must not be null
   * @return An {@code Each} instance wrapping the Traverse
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Each<Kind<F, A>, A> fromTraverse(
      Traverse<F> traverse) {
    return () -> TraverseTraversals.forTraverse(traverse);
  }
}
