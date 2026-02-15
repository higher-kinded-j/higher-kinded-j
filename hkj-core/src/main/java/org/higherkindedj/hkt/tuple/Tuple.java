// Copyright (c) 2025 - 2026 Magnus Smith
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
 * @see Tuple6
 * @see Tuple7
 * @see Tuple8
 * @see Tuple9
 * @see Tuple10
 * @see Tuple11
 * @see Tuple12
 */
public sealed interface Tuple
    permits Tuple2,
        Tuple3,
        Tuple4,
        Tuple5,
        Tuple6,
        Tuple7,
        Tuple8,
        Tuple9,
        Tuple10,
        Tuple11,
        Tuple12 {

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

  /**
   * Creates a {@link Tuple6} containing six elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @return A new {@link Tuple6} instance.
   */
  static <A, B, C, D, E, F> Tuple6<A, B, C, D, E, F> of(A a, B b, C c, D d, E e, F f) {
    return new Tuple6<>(a, b, c, d, e, f);
  }

  /**
   * Creates a {@link Tuple7} containing seven elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param g The seventh element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @param <G> The type of the seventh element.
   * @return A new {@link Tuple7} instance.
   */
  static <A, B, C, D, E, F, G> Tuple7<A, B, C, D, E, F, G> of(A a, B b, C c, D d, E e, F f, G g) {
    return new Tuple7<>(a, b, c, d, e, f, g);
  }

  /**
   * Creates a {@link Tuple8} containing eight elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param g The seventh element.
   * @param h The eighth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @param <G> The type of the seventh element.
   * @param <H> The type of the eighth element.
   * @return A new {@link Tuple8} instance.
   */
  static <A, B, C, D, E, F, G, H> Tuple8<A, B, C, D, E, F, G, H> of(
      A a, B b, C c, D d, E e, F f, G g, H h) {
    return new Tuple8<>(a, b, c, d, e, f, g, h);
  }

  /**
   * Creates a {@link Tuple9} containing nine elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param g The seventh element.
   * @param h The eighth element.
   * @param i The ninth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @param <G> The type of the seventh element.
   * @param <H> The type of the eighth element.
   * @param <I> The type of the ninth element.
   * @return A new {@link Tuple9} instance.
   */
  static <A, B, C, D, E, F, G, H, I> Tuple9<A, B, C, D, E, F, G, H, I> of(
      A a, B b, C c, D d, E e, F f, G g, H h, I i) {
    return new Tuple9<>(a, b, c, d, e, f, g, h, i);
  }

  /**
   * Creates a {@link Tuple10} containing ten elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param g The seventh element.
   * @param h The eighth element.
   * @param i The ninth element.
   * @param j The tenth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @param <G> The type of the seventh element.
   * @param <H> The type of the eighth element.
   * @param <I> The type of the ninth element.
   * @param <J> The type of the tenth element.
   * @return A new {@link Tuple10} instance.
   */
  static <A, B, C, D, E, F, G, H, I, J> Tuple10<A, B, C, D, E, F, G, H, I, J> of(
      A a, B b, C c, D d, E e, F f, G g, H h, I i, J j) {
    return new Tuple10<>(a, b, c, d, e, f, g, h, i, j);
  }

  /**
   * Creates a {@link Tuple11} containing eleven elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param g The seventh element.
   * @param h The eighth element.
   * @param i The ninth element.
   * @param j The tenth element.
   * @param k The eleventh element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @param <G> The type of the seventh element.
   * @param <H> The type of the eighth element.
   * @param <I> The type of the ninth element.
   * @param <J> The type of the tenth element.
   * @param <K> The type of the eleventh element.
   * @return A new {@link Tuple11} instance.
   */
  static <A, B, C, D, E, F, G, H, I, J, K> Tuple11<A, B, C, D, E, F, G, H, I, J, K> of(
      A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k) {
    return new Tuple11<>(a, b, c, d, e, f, g, h, i, j, k);
  }

  /**
   * Creates a {@link Tuple12} containing twelve elements.
   *
   * @param a The first element.
   * @param b The second element.
   * @param c The third element.
   * @param d The fourth element.
   * @param e The fifth element.
   * @param f The sixth element.
   * @param g The seventh element.
   * @param h The eighth element.
   * @param i The ninth element.
   * @param j The tenth element.
   * @param k The eleventh element.
   * @param l The twelfth element.
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @param <C> The type of the third element.
   * @param <D> The type of the fourth element.
   * @param <E> The type of the fifth element.
   * @param <F> The type of the sixth element.
   * @param <G> The type of the seventh element.
   * @param <H> The type of the eighth element.
   * @param <I> The type of the ninth element.
   * @param <J> The type of the tenth element.
   * @param <K> The type of the eleventh element.
   * @param <L> The type of the twelfth element.
   * @return A new {@link Tuple12} instance.
   */
  static <A, B, C, D, E, F, G, H, I, J, K, L> Tuple12<A, B, C, D, E, F, G, H, I, J, K, L> of(
      A a, B b, C c, D d, E e, F f, G g, H h, I i, J j, K k, L l) {
    return new Tuple12<>(a, b, c, d, e, f, g, h, i, j, k, l);
  }
}
