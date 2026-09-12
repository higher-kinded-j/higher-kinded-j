// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * The build half of a {@link ValidatedPrism}: a total rendering of a domain value into a source,
 * with no way back.
 *
 * <p>{@link #build} never fails on a domain value, and the bulk forms render whole containers with
 * the same totality. The name places it in the validated-boundary family rather than claiming a
 * validation of its own: it is the direction such a boundary writes through, and nothing in it can
 * fail. A {@code ValidatedPrism} is a {@code ValidatedBuild}, so every prism serves wherever only
 * building is asked for. A value with no {@code parse} at all, the write side of a wire that is
 * never read back, is made with {@link #of}, or exposed by a build-only {@code @GenerateMapping} as
 * {@code asValidatedBuild()}.
 *
 * @param <S> the source (wire) type
 * @param <A> the domain type
 */
public sealed interface ValidatedBuild<S, A> permits ValidatedPrism, ValidatedBuild.Of {

  /**
   * Renders the domain value to the source type; total by construction.
   *
   * @param value the domain value; must not be null
   * @return the rendered source value (non-null)
   */
  S build(A value);

  /**
   * Renders every domain value; total like {@link #build}.
   *
   * @param values the domain values; neither the list nor its elements may be null
   * @return the rendered wire values, immutable (non-null)
   * @throws NullPointerException if {@code values} or one of its elements is null (the message
   *     names the offending index)
   */
  default List<S> buildAll(List<? extends A> values) {
    Objects.requireNonNull(values, "values must not be null");
    List<S> built = new ArrayList<>(values.size());
    int i = 0;
    for (A value : values) {
      built.add(build(Objects.requireNonNull(value, "values[" + i + "] must not be null")));
      i++;
    }
    return List.copyOf(built);
  }

  /**
   * Renders every element of a set; total like {@link #build}. Iteration order is preserved, and
   * two domain values that render to the same wire value collapse to one element, mirroring {@link
   * ValidatedParse#parseAll(Set)}.
   *
   * @param values the domain values; neither the set nor its elements may be null
   * @return the rendered wire values, immutable and in iteration order (non-null)
   * @throws NullPointerException if {@code values} or one of its elements is null
   */
  default Set<S> buildAll(Set<? extends A> values) {
    Objects.requireNonNull(values, "values must not be null");
    Set<S> built = LinkedHashSet.newLinkedHashSet(values.size());
    for (A value : values) {
      built.add(build(Objects.requireNonNull(value, "values must not contain a null element")));
    }
    // Set.copyOf does not preserve iteration order, so wrap the LinkedHashSet instead.
    return Collections.unmodifiableSet(built);
  }

  /**
   * Renders every element of an array; total like {@link #build}.
   *
   * @param values the domain values; neither the array nor its elements may be null
   * @param newArray the wire array constructor, typically {@code Wire[]::new}; must not be null
   * @return the rendered wire values, a fresh array in the source's order (non-null)
   * @throws NullPointerException if {@code values}, {@code newArray}, or one of the elements is
   *     null (the message names the offending index)
   */
  default S[] buildAll(A[] values, IntFunction<S[]> newArray) {
    Objects.requireNonNull(values, "values must not be null");
    Objects.requireNonNull(newArray, "newArray must not be null");
    S[] built = newArray.apply(values.length);
    for (int i = 0; i < values.length; i++) {
      built[i] = build(Objects.requireNonNull(values[i], "values[" + i + "] must not be null"));
    }
    return built;
  }

  /**
   * Renders every value of a map; total like {@link #build}. Keys pass through untouched and entry
   * order is preserved.
   *
   * @param values the domain values by key; neither the map, its keys, nor its values may be null
   * @param <K> the key type, carried through unchanged
   * @return the rendered wire values by key, immutable and in entry order (non-null)
   * @throws NullPointerException if {@code values}, one of its keys, or one of its values is null
   *     (the message names the offending key where one exists)
   */
  default <K> Map<K, S> buildValues(Map<K, ? extends A> values) {
    Objects.requireNonNull(values, "values must not be null");
    Map<K, S> built = LinkedHashMap.newLinkedHashMap(values.size());
    for (Map.Entry<K, ? extends A> entry : values.entrySet()) {
      K key = Objects.requireNonNull(entry.getKey(), "values must not contain a null key");
      built.put(
          key,
          build(Objects.requireNonNull(entry.getValue(), "values[" + key + "] must not be null")));
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return Collections.unmodifiableMap(built);
  }

  /**
   * Renders every key of a map; total like {@link #build}. Values pass through untouched and entry
   * order is preserved. Domain keys that render to the same wire key collapse to one entry, the
   * total mirror of {@link ValidatedParse#parseKeys(Map)}' located collision.
   *
   * @param values the domain values by key; neither the map, its keys, nor its values may be null
   * @param <V> the value type, carried through unchanged
   * @return the rendered keys' map, immutable and in entry order (non-null)
   * @throws NullPointerException if {@code values}, one of its keys, or one of its values is null
   *     (the message names the offending key) — a pass-through value is still a value, and {@link
   *     ValidatedParse#parseKeys(Map)} rejects a null one, so rendering it would build a wire the
   *     same prism refuses to read back
   */
  default <V> Map<S, V> buildKeys(Map<? extends A, V> values) {
    Objects.requireNonNull(values, "values must not be null");
    Map<S, V> built = LinkedHashMap.newLinkedHashMap(values.size());
    for (Map.Entry<? extends A, V> entry : values.entrySet()) {
      A key = Objects.requireNonNull(entry.getKey(), "values must not contain a null key");
      built.put(
          build(key),
          Objects.requireNonNull(entry.getValue(), "values[" + key + "] must not be null"));
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return Collections.unmodifiableMap(built);
  }

  /**
   * Renders both sides of a map; total like {@link #build}, and the reverse of {@link
   * ValidatedParse#parseEntries(Map, ValidatedParse)}. The receiver renders the keys and {@code
   * valueBuild} the values; entry order is preserved. Only the build direction of either side is
   * used, so any {@code ValidatedPrism} serves as {@code valueBuild} too.
   *
   * @param values the domain entries; neither the map, its keys, nor its values may be null
   * @param valueBuild the build rendering the values; must not be null
   * @param <SV> the wire value type
   * @param <V> the domain value type
   * @return the rendered entries, immutable and in entry order (non-null)
   * @throws NullPointerException if {@code values}, {@code valueBuild}, one of the keys, or one of
   *     the values is null (the message names the offending key where one exists)
   */
  default <SV, V> Map<S, SV> buildEntries(
      Map<? extends A, ? extends V> values, ValidatedBuild<SV, V> valueBuild) {
    Objects.requireNonNull(values, "values must not be null");
    Objects.requireNonNull(valueBuild, "valueBuild must not be null");
    Map<S, SV> built = LinkedHashMap.newLinkedHashMap(values.size());
    for (Map.Entry<? extends A, ? extends V> entry : values.entrySet()) {
      A key = Objects.requireNonNull(entry.getKey(), "values must not contain a null key");
      built.put(
          build(key),
          valueBuild.build(
              Objects.requireNonNull(entry.getValue(), "values[" + key + "] must not be null")));
    }
    // Map.copyOf does not preserve entry order, so wrap the LinkedHashMap instead.
    return Collections.unmodifiableMap(built);
  }

  /**
   * Creates a {@code ValidatedBuild} from its one direction, for a domain value that is rendered
   * into a source and never read back.
   *
   * @param build the total direction; must not be null
   * @param <S> the source type
   * @param <A> the domain type
   * @return the build (non-null)
   * @throws NullPointerException if {@code build} is null
   */
  static <S, A> ValidatedBuild<S, A> of(Function<? super A, ? extends S> build) {
    Objects.requireNonNull(build, "build must not be null");
    return new Of<>(build);
  }

  /**
   * The one-directional implementation wrapping a build function.
   *
   * <p>Created by {@link ValidatedBuild#of}; not usually named directly.
   *
   * @param buildFn the build direction; never null
   * @param <S> the source type
   * @param <A> the domain type
   */
  record Of<S, A>(Function<? super A, ? extends S> buildFn) implements ValidatedBuild<S, A> {

    /**
     * Canonical constructor; validates.
     *
     * @throws NullPointerException if {@code buildFn} is null
     */
    public Of {
      Objects.requireNonNull(buildFn, "buildFn must not be null");
    }

    @Override
    public S build(A value) {
      Objects.requireNonNull(value, "value must not be null");
      return Objects.requireNonNull(buildFn.apply(value), "build must not return null");
    }
  }
}
