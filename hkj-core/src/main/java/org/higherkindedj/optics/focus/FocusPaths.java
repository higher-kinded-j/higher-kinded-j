// Copyright (c) 2025 Magnus Smith
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
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Traversal;
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
      public <F> Kind<F, List<E>> modifyF(
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
      public <F> Kind<F, Map<K, V>> modifyF(
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
      public <F> Kind<F, E[]> modifyF(Function<E, Kind<F, E>> f, E[] source, Applicative<F> app) {
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
