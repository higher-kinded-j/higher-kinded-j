// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

/**
 * Describes the cardinality of elements within a traversable container type.
 *
 * <p>This enum is used by {@link TraversableGenerator} implementations to indicate how many
 * elements a container type can hold. For a generator that names an optic expression, it determines
 * the path type used in the Focus DSL; a generator that names none takes no part in Focus widening:
 *
 * <ul>
 *   <li>{@link #ZERO_OR_ONE} → {@code AffinePath} (optional navigation)
 *   <li>{@link #ZERO_OR_MORE} → {@code TraversalPath} (collection navigation)
 * </ul>
 *
 * @see TraversableGenerator#getCardinality()
 * @since 0.3.8
 */
public enum Cardinality {
  /**
   * The container holds zero or one element.
   *
   * <p>Examples: {@code Optional<T>}, {@code Maybe<T>}, {@code Either<L, R>}, {@code Try<T>},
   * {@code Validated<E, A>}.
   *
   * <p>Maps to {@code AffinePath} in the Focus DSL, for a generator that names an optic.
   */
  ZERO_OR_ONE,

  /**
   * The container holds zero or more elements.
   *
   * <p>Examples: {@code List<T>}, {@code Set<T>}, {@code Map<K, V>}, arrays, and third-party
   * collection types from Eclipse Collections, Guava, Vavr, and Apache Commons.
   *
   * <p>Maps to {@code TraversalPath} in the Focus DSL, for a generator that names an optic.
   */
  ZERO_OR_MORE
}
