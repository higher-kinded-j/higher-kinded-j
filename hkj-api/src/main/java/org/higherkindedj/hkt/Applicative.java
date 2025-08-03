// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface Applicative<F> extends Functor<F> {

  <A> @NonNull Kind<F, A> of(@Nullable A value);

  <A, B> @NonNull Kind<F, B> ap(
      @NonNull Kind<F, ? extends Function<A, B>> ff, @NonNull Kind<F, A> fa);

  // --- mapN implementations ---

  /**
   * Combines two values {@code fa} and {@code fb}, both in the applicative context {@code F}, using
   * a curried pure function {@code f: A -> (B -> C)}.
   *
   * <p>This version is implemented using the more common BiFunction-based map2.
   *
   * @param fa The first non-null applicative value {@code Kind<F, A>}.
   * @param fb The second non-null applicative value {@code Kind<F, B>}.
   * @param f A non-null pure function that takes a value of type {@code A} and returns a function
   *     from {@code B} to {@code C}.
   * @param <A> The type of the value in {@code fa}.
   * @param <B> The type of the value in {@code fb}.
   * @param <C> The type of the result of the combined computation.
   * @return A non-null {@code Kind<F, C>} containing the result.
   */
  default <A, B, C> @NonNull Kind<F, C> map2(
      @NonNull Kind<F, A> fa, @NonNull Kind<F, B> fb, @NonNull Function<A, Function<B, C>> f) {
    // Delegate to the BiFunction version, which is now the base implementation
    return map2(fa, fb, (a, b) -> f.apply(a).apply(b));
  }

  /**
   * Combines two values {@code fa} and {@code fb}, both in the applicative context {@code F}, using
   * a pure {@link BiFunction BiFunction&lt;A, B, C&gt;}.
   *
   * <p>This is the primary, most flexible version of map2.
   *
   * @param fa The first non-null applicative value {@code Kind<F, A>}.
   * @param fb The second non-null applicative value {@code Kind<F, B>}.
   * @param f A non-null pure {@link BiFunction} to combine the values.
   * @param <A> The type of the value in {@code fa}.
   * @param <B> The type of the value in {@code fb}.
   * @param <C> The type of the result of applying {@code f}.
   * @return A non-null {@code Kind<F, C>} containing the result.
   */
  default <A, B, C> @NonNull Kind<F, C> map2(
      @NonNull Kind<F, A> fa,
      @NonNull Kind<F, B> fb,
      @NonNull BiFunction<? super A, ? super B, ? extends C> f) {
    // The implementation is now based on map and ap, with a curried function.
    // The key is that the lambda `a -> b -> f.apply(a, b)` helps the compiler
    // resolve the wildcard types correctly before they are passed to map.
    return ap(map(a -> b -> f.apply(a, b), fa), fb);
  }

  /**
   * Combines three values {@code fa}, {@code fb}, and {@code fc} using a pure {@link Function3}.
   */
  default <A, B, C, R> @NonNull Kind<F, R> map3(
      @NonNull Kind<F, A> fa,
      @NonNull Kind<F, B> fb,
      @NonNull Kind<F, C> fc,
      @NonNull Function3<? super A, ? super B, ? super C, ? extends R> f) {
    return ap(map2(fa, fb, (a, b) -> c -> f.apply(a, b, c)), fc);
  }

  /**
   * Combines four values {@code fa}, {@code fb}, {@code fc}, and {@code fd} using a pure {@link
   * Function4}.
   */
  default <A, B, C, D, R> @NonNull Kind<F, R> map4(
      @NonNull Kind<F, A> fa,
      @NonNull Kind<F, B> fb,
      @NonNull Kind<F, C> fc,
      @NonNull Kind<F, D> fd,
      @NonNull Function4<? super A, ? super B, ? super C, ? super D, ? extends R> f) {
    return ap(map3(fa, fb, fc, (a, b, c) -> d -> f.apply(a, b, c, d)), fd);
  }

  /**
   * Combines five values {@code fa}, {@code fb}, {@code fc}, {@code fd}, and {@code fe} using a
   * pure {@link Function5}.
   */
  default <A, B, C, D, E, R> @NonNull Kind<F, R> map5(
      @NonNull Kind<F, A> fa,
      @NonNull Kind<F, B> fb,
      @NonNull Kind<F, C> fc,
      @NonNull Kind<F, D> fd,
      @NonNull Kind<F, E> fe,
      @NonNull Function5<? super A, ? super B, ? super C, ? super D, ? super E, ? extends R> f) {
    return ap(map4(fa, fb, fc, fd, (a, b, c, d) -> e -> f.apply(a, b, c, d, e)), fe);
  }
}
