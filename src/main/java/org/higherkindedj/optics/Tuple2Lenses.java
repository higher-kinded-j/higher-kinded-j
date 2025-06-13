package org.higherkindedj.optics;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.tuple.Tuple2;

/**
 * A container for static methods that create lenses for {@link Tuple2}.
 * This provides foundational optics that are widely useful, especially for
 * monads like Writer and State that are built on tuples.
 */
public final class Tuple2Lenses {

  private Tuple2Lenses() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Creates a Lens that focuses on the first element (_1) of a {@link Tuple2}.
   *
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @return A non-null {@link Lens} for the first element.
   */
  public static <A, B> Lens<Tuple2<A, B>, A> _1() {
    return new Lens<>() {
      @Override
      public A get(Tuple2<A, B> source) {
        return source._1();
      }

      @Override
      public Tuple2<A, B> set(A newValue, Tuple2<A, B> source) {
        return new Tuple2<>(newValue, source._2());
      }

      @Override
      public <F> Kind<F, Tuple2<A, B>> modifyF(
          Function<A, Kind<F, A>> f, Tuple2<A, B> source, Functor<F> functor) {
        // 1. Apply the effectful function `f` to the focused part `_1()`.
        //    This yields a `Kind<F, A>`.
        Kind<F, A> fa = f.apply(source._1());

        // 2. Use the provided Functor `F` to map over the result.
        //    The mapping function takes the new value of type `A` (once the
        //    effect `F` is resolved) and reconstructs the `Tuple2` with
        //    the original `_2()` value.
        return functor.map(a -> new Tuple2<>(a, source._2()), fa);
      }
    };
  }

  /**
   * Creates a Lens that focuses on the second element (_2) of a {@link Tuple2}.
   *
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @return A non-null {@link Lens} for the second element.
   */
  public static <B, A> Lens<Tuple2<A, B>, B> _2() {
    return new Lens<>() {
      @Override
      public B get(Tuple2<A, B> source) {
        return source._2();
      }

      @Override
      public Tuple2<A, B> set(B newValue, Tuple2<A, B> source) {
        return new Tuple2<>(source._1(), newValue);
      }

      @Override
      public <F> Kind<F, Tuple2<A, B>> modifyF(
          Function<B, Kind<F, B>> f, Tuple2<A, B> source, Functor<F> functor) {
        // 1. Apply the effectful function `f` to the focused part `_2()`.
        //    This yields a `Kind<F, B>`.
        Kind<F, B> fb = f.apply(source._2());

        // 2. Use the provided Functor `F` to map over the result.
        //    The mapping function takes the new value of type `B` and
        //    reconstructs the `Tuple2` with the original `_1()` value.
        return functor.map(b -> new Tuple2<>(source._1(), b), fb);
      }
    };
  }
}
