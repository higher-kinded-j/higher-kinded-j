// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

/**
 * A sealed interface representing a Tuple. Tuples are finite, heterogeneous, and immutable ordered
 * sequences of elements.
 *
 * <p>This interface serves as a common super-type for all structural tuple records (e.g., {@link
 * Tuple2}, {@link Tuple3}) and acts as a single entry point for creating tuples via its static
 * {@code of} factory methods. Using these factory methods provides a concise and consistent way to
 * instantiate tuples of different arities.
 *
 * @see Tuple2
 * @see Tuple3
 * @see Tuple4
 * @see Tuple5
 */
public sealed interface Tuple permits Tuple2, Tuple3, Tuple4, Tuple5 {

  /**
   * Creates a {@link Tuple2} containing two elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @return A new {@link Tuple2} instance.
   */
  static <A, B> Tuple2<A, B> of(A a, B b) {
    return new Tuple2<>(a, b);
  }

  /**
   * Creates a {@link Tuple3} containing three elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @return A new {@link Tuple3} instance.
   */
  static <A, B, C> Tuple3<A, B, C> of(A a, B b, C c) {
    return new Tuple3<>(a, b, c);
  }

  /**
   * Creates a {@link Tuple4} containing four elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @return A new {@link Tuple4} instance.
   */
  static <A, B, C, D> Tuple4<A, B, C, D> of(A a, B b, C c, D d) {
    return new Tuple4<>(a, b, c, d);
  }

  /**
   * Creates a {@link Tuple5} containing five elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @return A new {@link Tuple5} instance.
   */
  static <A, B, C, D, E> Tuple5<A, B, C, D, E> of(A a, B b, C c, D d, E e) {
    return new Tuple5<>(a, b, c, d, e);
  }
}
