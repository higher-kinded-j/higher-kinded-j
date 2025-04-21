package org.simulation.hkt;

import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;

import java.util.function.BiFunction;
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


  // --- mapN implementations ---
  default <A, B, C> Kind<F, C> map2(Kind<F, A> fa, Kind<F, B> fb, Function<A, Function<B, C>> f) {
    return ap(map(f, fa), fb);
  }


  /**
   * Combines two applicative values using a BiFunction.
   *
   * @param fa The first applicative value.
   * @param fb The second applicative value.
   * @param f  The BiFunction to combine the values.
   * @param <A> Type of the value in fa.
   * @param <B> Type of the value in fb.
   * @param <C> Type of the result.
   * @return Applicative context containing the result of applying f.
   */
  default <A, B, C> Kind<F, C> map2(Kind<F, A> fa, Kind<F, B> fb, BiFunction<A, B, C> f) {
    // Curried version for ap: A -> (B -> C)
    Function<A, Function<B, C>> curried = a -> b -> f.apply(a, b);
    // Apply curried function inside the context: map(curried, fa) gives Kind<F, Function<B,C>>
    // Then apply that to fb using ap.
    return ap(map(curried, fa), fb);
  }

  /**
   * Combines three applicative values using a Function3 (you would need to define this interface).
   * Example using a conceptual Function3 interface: {@code interface Function3<T1, T2, T3, R> { R apply(T1 t1, T2 t2, T3 t3); }}
   *
   * @param fa The first applicative value.
   * @param fb The second applicative value.
   * @param fc The third applicative value.
   * @param f  The Function3 to combine the values.
   * @param <A> Type of the value in fa.
   * @param <B> Type of the value in fb.
   * @param <C> Type of the value in fc.
   * @param <R> Type of the result.
   * @return Applicative context containing the result of applying f.
   */
  default <A, B, C, R> Kind<F, R> map3(Kind<F, A> fa, Kind<F, B> fb, Kind<F, C> fc, Function3<A, B, C, R> f) {
    // Curry the function: A -> B -> C -> R
    Function<A, Function<B, Function<C, R>>> curried = a -> b -> c -> f.apply(a, b, c);
    // map(curried, fa) -> Kind<F, Function<B, Function<C, R>>>
    // ap(..., fb)      -> Kind<F, Function<C, R>>
    // ap(..., fc)      -> Kind<F, R>
    return ap(ap(map(curried, fa), fb), fc);
  }


  /**
   * Combines four applicative values using a Function4 (you would need to define this interface).
   * Example using a conceptual Function4 interface: {@code interface Function4<T1, T2, T3, T4, R> { R apply(T1 t1, T2 t2, T3 t3, T4 t4); }}
   *
   * @param fa The first applicative value.
   * @param fb The second applicative value.
   * @param fc The third applicative value.
   * @param fd The fourth applicative value.
   * @param f  The Function4 to combine the values.
   * @param <A> Type of the value in fa.
   * @param <B> Type of the value in fb.
   * @param <C> Type of the value in fc.
   * @param <D> Type of the value in fd.
   * @param <R> Type of the result.
   * @return Applicative context containing the result of applying f.
   */

  default <A, B, C, D, R> Kind<F, R> map4(Kind<F, A> fa, Kind<F, B> fb, Kind<F, C> fc, Kind<F, D> fd, Function4<A, B, C, D, R> f) {
    // Curry the function: A -> B -> C -> D -> R
    Function<A, Function<B, Function<C, Function<D, R>>>> curried = a -> b -> c -> d -> f.apply(a, b, c, d);
    // map(curried, fa) -> Kind<F, Function<B, Function<C, Function<D, R>>>>
    // ap(..., fb)      -> Kind<F, Function<C, Function<D, R>>>
    // ap(..., fc)      -> Kind<F, Function<D, R>>
    // ap(..., fd)      -> Kind<F, R>
    return ap(ap(ap(map(curried, fa), fb), fc), fd);
  }


}
