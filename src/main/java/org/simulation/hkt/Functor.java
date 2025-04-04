package org.simulation.hkt;

import java.util.function.Function;

/**
 * Represents the Functor type class.
 * A Functor is a type constructor F supporting a 'map' operation
 * that allows applying a function to the value(s) inside the structure F
 * without changing the structure itself.
 * <p>
 * This interface uses the {@link Kind} simulation for higher-kinded types in Java.
 *
 * @param <F> The witness type representing the type constructor (e.g., ListKind.class, OptionalKind.class).
 * This 'F' corresponds to the 'F' in the simulated higher-kinded type F<A>.
 */
public interface Functor<F> {

  /**
   * Applies a function to the value(s) contained within the Functor context.
   * <p>
   * Implementations should adhere to the Functor laws:
   * <ol>
   * <li>Identity: {@code map(a -> a, fa)} should be equivalent to {@code fa}.</li>
   * <li>Composition: {@code map(g.compose(f), fa)} should be equivalent to {@code map(g, map(f, fa))}.</li>
   * </ol>
   *
   * @param f   The function to apply to the wrapped value(s).
   * @param fa  The Functor structure containing the value(s) of type A.
   * @param <A> The type of the value(s) inside the input Functor structure.
   * @param <B> The type of the value(s) inside the output Functor structure.
   * @return A new Functor structure containing the result(s) of applying the function {@code f},
   * maintaining the original structure F.
   */
  <A, B> Kind<F, B> map(Function<A, B> f, Kind<F, A> fa);
}
