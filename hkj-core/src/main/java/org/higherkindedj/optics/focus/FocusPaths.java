// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.indexed.Pair;
import org.jspecify.annotations.NullMarked;

/**
 * Static utilities for creating optics used in Focus path navigation.
 *
 * <p>This class provides pre-built optics for common collection types that are used internally by
 * the Focus DSL for methods like {@code each()}, {@code at()}, {@code some()}, and {@code atKey()}.
 *
 * <h2>Supported Types</h2>
 *
 * <ul>
 *   <li>{@link List} - via {@link #listElements()} and {@link #listAt(int)}
 *   <li>{@link Map} - via {@link #mapValues()} and {@link #mapAt(Object)}
 *   <li>{@link Optional} - via {@link #optionalSome()}
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Get a traversal over list elements
 * Traversal<List<String>, String> strings = FocusPaths.listElements();
 *
 * // Get an affine for a specific index
 * Affine<List<String>, String> second = FocusPaths.listAt(1);
 *
 * // Get an affine for Optional unwrapping
 * Affine<Optional<String>, String> some = FocusPaths.optionalSome();
 * }</pre>
 */
@NullMarked
public final class FocusPaths {

  private FocusPaths() {
    // Utility class
  }

  // ===== List Optics =====

  /**
   * Creates a traversal over all elements in a list.
   *
   * @param <E> the element type
   * @return a traversal focusing on all list elements
   */
  public static <E> Traversal<List<E>, E> listElements() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<E>> modifyF(
          Function<E, Kind<F, E>> f, List<E> source, Applicative<F> app) {
        if (source.isEmpty()) {
          return app.of(source);
        }

        // Start with the first element
        Kind<F, List<E>> result = app.map(List::of, f.apply(source.get(0)));

        // Combine with remaining elements
        for (int i = 1; i < source.size(); i++) {
          Kind<F, E> modifiedElement = f.apply(source.get(i));
          result = app.map2(result, modifiedElement, FocusPaths::appendToList);
        }

        return result;
      }
    };
  }

  /**
   * Creates an affine focusing on a specific index in a list.
   *
   * <p>The affine will return empty if the index is out of bounds. Setting a value at an index
   * requires the index to be within the current list size.
   *
   * @param index the index to focus on
   * @param <E> the element type
   * @return an affine focusing on the element at the given index
   */
  public static <E> Affine<List<E>, E> listAt(int index) {
    return Affine.of(
        list ->
            (index >= 0 && index < list.size()) ? Optional.of(list.get(index)) : Optional.empty(),
        (list, element) -> {
          if (index >= 0 && index < list.size()) {
            List<E> result = new ArrayList<>(list);
            result.set(index, element);
            return result;
          }
          return list;
        });
  }

  /**
   * Creates an affine focusing on the first element of a list.
   *
   * @param <E> the element type
   * @return an affine focusing on the first element
   */
  public static <E> Affine<List<E>, E> listHead() {
    return listAt(0);
  }

  /**
   * Creates an affine focusing on the last element of a list.
   *
   * @param <E> the element type
   * @return an affine focusing on the last element
   */
  public static <E> Affine<List<E>, E> listLast() {
    return Affine.of(
        list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1)),
        (list, element) -> {
          if (!list.isEmpty()) {
            List<E> result = new ArrayList<>(list);
            result.set(result.size() - 1, element);
            return result;
          }
          return list;
        });
  }

  /**
   * Creates a prism that decomposes a list into its head (first element) and tail (remaining
   * elements).
   *
   * <p>This is the classic functional programming "cons" pattern, where a non-empty list is viewed
   * as a pair of its first element and the rest. Also known as "headTail" decomposition.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, Pair<String, List<String>>> cons = FocusPaths.listCons();
   *
   * // Decompose: [a, b, c] -> Pair(a, [b, c])
   * Optional<Pair<String, List<String>>> result = cons.getOptional(List.of("a", "b", "c"));
   *
   * // Build: Pair(x, [y, z]) -> [x, y, z]
   * List<String> built = cons.build(Pair.of("x", List.of("y", "z")));
   * }</pre>
   *
   * @param <E> the element type
   * @return a prism from list to (head, tail) pair
   * @see #listSnoc() for the reverse decomposition (init, last)
   */
  public static <E> Prism<List<E>, Pair<E, List<E>>> listCons() {
    return Prism.of(
        list -> {
          if (list.isEmpty()) {
            return Optional.empty();
          }
          return Optional.of(Pair.of(list.get(0), List.copyOf(list.subList(1, list.size()))));
        },
        pair -> {
          List<E> result = new ArrayList<>(pair.second().size() + 1);
          result.add(pair.first());
          result.addAll(pair.second());
          return List.copyOf(result);
        });
  }

  /**
   * Alias for {@link #listCons()} using Java-familiar naming.
   *
   * @param <E> the element type
   * @return a prism from list to (head, tail) pair
   * @see #listCons()
   */
  public static <E> Prism<List<E>, Pair<E, List<E>>> listHeadTail() {
    return listCons();
  }

  /**
   * Creates a prism that decomposes a list into its init (all but last element) and last element.
   *
   * <p>This is the reverse of "cons", known as "snoc" (cons spelled backwards). A non-empty list is
   * viewed as a pair of all elements except the last, and the last element. Also known as
   * "initLast" decomposition.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = FocusPaths.listSnoc();
   *
   * // Decompose: [1, 2, 3] -> Pair([1, 2], 3)
   * Optional<Pair<List<Integer>, Integer>> result = snoc.getOptional(List.of(1, 2, 3));
   *
   * // Build: Pair([1, 2], 3) -> [1, 2, 3]
   * List<Integer> built = snoc.build(Pair.of(List.of(1, 2), 3));
   * }</pre>
   *
   * @param <E> the element type
   * @return a prism from list to (init, last) pair
   * @see #listCons() for the opposite decomposition (head, tail)
   */
  public static <E> Prism<List<E>, Pair<List<E>, E>> listSnoc() {
    return Prism.of(
        list -> {
          if (list.isEmpty()) {
            return Optional.empty();
          }
          int lastIndex = list.size() - 1;
          return Optional.of(Pair.of(List.copyOf(list.subList(0, lastIndex)), list.get(lastIndex)));
        },
        pair -> {
          List<E> result = new ArrayList<>(pair.first().size() + 1);
          result.addAll(pair.first());
          result.add(pair.second());
          return List.copyOf(result);
        });
  }

  /**
   * Alias for {@link #listSnoc()} using Java-familiar naming.
   *
   * @param <E> the element type
   * @return a prism from list to (init, last) pair
   * @see #listSnoc()
   */
  public static <E> Prism<List<E>, Pair<List<E>, E>> listInitLast() {
    return listSnoc();
  }

  /**
   * Creates a prism that matches empty lists.
   *
   * <p>This prism is the complement of {@link #listCons()} and {@link #listSnoc()}. It matches only
   * empty lists and builds empty lists from {@link Unit}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, Unit> empty = FocusPaths.listEmpty();
   *
   * // Check if list is empty
   * boolean isEmpty = empty.matches(List.of());  // true
   * boolean hasElements = empty.matches(List.of("a"));  // false
   *
   * // Build an empty list
   * List<String> emptyList = empty.build(Unit.INSTANCE);  // []
   * }</pre>
   *
   * @param <E> the element type
   * @return a prism matching empty lists
   */
  public static <E> Prism<List<E>, Unit> listEmpty() {
    return Prism.of(
        list -> list.isEmpty() ? Optional.of(Unit.INSTANCE) : Optional.empty(), _ -> List.of());
  }

  /**
   * Creates an affine focusing on the tail (all elements except the first) of a list.
   *
   * <p>The affine returns empty for empty lists. Setting replaces all elements after the first.
   *
   * @param <E> the element type
   * @return an affine focusing on the tail
   */
  public static <E> Affine<List<E>, List<E>> listTail() {
    return Affine.of(
        list ->
            list.isEmpty()
                ? Optional.empty()
                : Optional.of(List.copyOf(list.subList(1, list.size()))),
        (list, newTail) -> {
          if (list.isEmpty()) {
            return list;
          }
          List<E> result = new ArrayList<>(newTail.size() + 1);
          result.add(list.get(0));
          result.addAll(newTail);
          return List.copyOf(result);
        });
  }

  /**
   * Creates an affine focusing on the init (all elements except the last) of a list.
   *
   * <p>The affine returns empty for empty lists. Setting replaces all elements before the last.
   *
   * @param <E> the element type
   * @return an affine focusing on the init
   */
  public static <E> Affine<List<E>, List<E>> listInit() {
    return Affine.of(
        list ->
            list.isEmpty()
                ? Optional.empty()
                : Optional.of(List.copyOf(list.subList(0, list.size() - 1))),
        (list, newInit) -> {
          if (list.isEmpty()) {
            return list;
          }
          List<E> result = new ArrayList<>(newInit.size() + 1);
          result.addAll(newInit);
          result.add(list.get(list.size() - 1));
          return List.copyOf(result);
        });
  }

  // ===== Map Optics =====

  /**
   * Creates a traversal over all values in a map.
   *
   * <p>Note: The traversal order depends on the map implementation. For predictable ordering, use a
   * {@code LinkedHashMap} or {@code TreeMap}.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a traversal focusing on all map values
   */
  public static <K, V> Traversal<Map<K, V>, V> mapValues() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Map<K, V>> modifyF(
          Function<V, Kind<F, V>> f, Map<K, V> source, Applicative<F> app) {
        if (source.isEmpty()) {
          return app.of(source);
        }

        // Convert to list for ordered processing
        List<Map.Entry<K, V>> entries = new ArrayList<>(source.entrySet());

        // Start with the first entry
        Map.Entry<K, V> first = entries.get(0);
        Kind<F, Map<K, V>> result =
            app.map(v -> Map.of(first.getKey(), v), f.apply(first.getValue()));

        // Combine with remaining entries
        for (int i = 1; i < entries.size(); i++) {
          Map.Entry<K, V> entry = entries.get(i);
          Kind<F, V> modifiedValue = f.apply(entry.getValue());
          K key = entry.getKey();
          result = app.map2(result, modifiedValue, (map, v) -> putInMap(map, key, v));
        }

        return result;
      }
    };
  }

  /**
   * Creates an affine focusing on a specific key in a map.
   *
   * <p>The affine will return empty if the key is not present. Setting a value will add or update
   * the key.
   *
   * @param key the key to focus on
   * @param <K> the key type
   * @param <V> the value type
   * @return an affine focusing on the value at the given key
   */
  public static <K, V> Affine<Map<K, V>, V> mapAt(K key) {
    return Affine.of(
        map -> Optional.ofNullable(map.get(key)),
        (map, value) -> {
          Map<K, V> result = new HashMap<>(map);
          result.put(key, value);
          return result;
        },
        map -> {
          Map<K, V> result = new HashMap<>(map);
          result.remove(key);
          return result;
        });
  }

  // ===== Optional Optics =====

  /**
   * Creates an affine that unwraps an Optional.
   *
   * <p>The affine returns empty if the Optional is empty. Setting always creates a present
   * Optional.
   *
   * @param <E> the inner type
   * @return an affine for unwrapping Optional
   */
  public static <E> Affine<Optional<E>, E> optionalSome() {
    return Affine.of(
        Function.identity(), (opt, value) -> Optional.of(value), opt -> Optional.empty());
  }

  // ===== Nullable Optics =====

  /**
   * Creates an affine that treats null as absent.
   *
   * <p>This is useful for working with legacy APIs or records that use null to represent absent
   * values instead of {@link Optional}. The affine wraps null checks in Optional semantics,
   * allowing seamless integration with the Focus DSL.
   *
   * <p>Example:
   *
   * <pre>{@code
   * record LegacyUser(String name, @Nullable String nickname) {}
   *
   * // Create a path to the nullable nickname field
   * FocusPath<LegacyUser, @Nullable String> nicknamePath = LegacyUserFocus.nickname();
   *
   * // Chain with nullable() to get an AffinePath that handles null safely
   * AffinePath<LegacyUser, String> safeNickname = nicknamePath.nullable();
   *
   * // Now use it like any other AffinePath
   * Optional<String> result = safeNickname.getOptional(user);  // Empty if null
   * LegacyUser updated = safeNickname.set("Ally", user);       // Sets the nickname
   * }</pre>
   *
   * @param <E> the element type (non-null)
   * @return an affine that treats null as absent
   */
  @SuppressWarnings("nullness") // Intentionally working with nullable values
  public static <E> Affine<E, E> nullable() {
    return Affine.of(Optional::ofNullable, (ignored, value) -> value);
  }

  // ===== Array Optics =====

  /**
   * Creates a traversal over all elements in an array.
   *
   * @param <E> the element type
   * @return a traversal focusing on all array elements
   */
  public static <E> Traversal<E[], E> arrayElements() {
    return new Traversal<>() {
      @Override
      @SuppressWarnings("unchecked")
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, E[]> modifyF(
          Function<E, Kind<F, E>> f, E[] source, Applicative<F> app) {
        if (source.length == 0) {
          return app.of(source);
        }

        // Start with the first element wrapped in a list
        Kind<F, List<E>> result = app.map(List::of, f.apply(source[0]));

        // Combine with remaining elements
        for (int i = 1; i < source.length; i++) {
          Kind<F, E> modifiedElement = f.apply(source[i]);
          result = app.map2(result, modifiedElement, FocusPaths::appendToList);
        }

        // Convert back to array
        return app.map(
            list ->
                list.toArray(
                    (E[])
                        java.lang.reflect.Array.newInstance(
                            source.getClass().getComponentType(), list.size())),
            result);
      }
    };
  }

  /**
   * Creates an affine focusing on a specific index in an array.
   *
   * @param index the index to focus on
   * @param <E> the element type
   * @return an affine focusing on the element at the given index
   */
  public static <E> Affine<E[], E> arrayAt(int index) {
    return Affine.of(
        arr -> (index >= 0 && index < arr.length) ? Optional.of(arr[index]) : Optional.empty(),
        (arr, element) -> {
          if (index >= 0 && index < arr.length) {
            E[] result = arr.clone();
            result[index] = element;
            return result;
          }
          return arr;
        });
  }

  // ===== Helper Methods =====

  private static <E> List<E> appendToList(List<E> list, E element) {
    List<E> result = new ArrayList<>(list);
    result.add(element);
    return result;
  }

  private static <K, V> Map<K, V> putInMap(Map<K, V> map, K key, V value) {
    Map<K, V> result = new HashMap<>(map);
    result.put(key, value);
    return result;
  }
}
