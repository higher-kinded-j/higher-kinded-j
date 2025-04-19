package org.simulation.hkt;

import java.util.function.Function;

/**
 * Represents the Applicative Functor type class.
 * Extends Functor and adds 'of' (lifting) and 'ap' (apply) operations.
 * Applicative allows applying a function wrapped in the context F
 * to a value wrapped in the context F.
 *
 * @param <F> The witness type representing the type constructor (e.g., ListKind.class).
 */
public interface Applicative<F> extends Functor<F> {

  /**
   * Lifts a plain value 'a' into the Applicative context F.
   * Also known as 'pure' or 'return' or 'unit'.
   *
   * @param value The value to lift.
   * @param <A>   The type of the value.
   * @return The value wrapped in the context F (e.g., ListKind<A>, OptionalKind<A>).
   */
  <A> Kind<F, A> of(A value);

  /**
   * Applies a function wrapped in the context F to a value wrapped in the context F.
   *
   * @param ff  The context F containing the function A -> B.
   * @param fa  The context F containing the value of type A.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function and the type within the resulting context.
   * @return A new context F containing the result of applying the function (if both function and value are available).
   */
  <A, B> Kind<F, B> ap(Kind<F, Function<A, B>> ff, Kind<F, A> fa);

  // --- Default methods providing alternative ways to map (optional) ---
  // map can be defined using of and ap, demonstrating their relationship.
  // Override the default map from Functor if you want to define it via ap.
  // default <A, B> Kind<F, B> map(Function<A, B> f, Kind<F, A> fa) {
  //     return ap(of(f), fa);
  // }

  // map2, map3, etc., are often provided for convenience using ap/map
  default <A, B, C> Kind<F, C> map2(Kind<F, A> fa, Kind<F, B> fb, Function<A, Function<B, C>> f) {
    return ap(map(f, fa), fb);
  }

  default <A, B, C> Kind<F, C> map2(Kind<F, A> fa, Kind<F, B> fb, java.util.function.BiFunction<A, B, C> f) {
    // Curried version for ap: A -> (B -> C)
    Function<A, Function<B, C>> curried = a -> b -> f.apply(a, b);
    // Apply curried function inside the context: map(curried, fa) gives Kind<F, Function<B,C>>
    // Then apply that to fb using ap.
    return ap(map(curried, fa), fb);
  }
}
