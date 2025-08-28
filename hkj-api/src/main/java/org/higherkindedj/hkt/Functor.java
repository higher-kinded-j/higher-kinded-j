// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Functor type class. A Functor is a type constructor F supporting a 'map' operation
 * that allows applying a function to the value(s) inside the structure F without changing the
 * structure itself.
 *
 * <p>This interface uses the {@link Kind} for higher-kinded types in Java.
 *
 * @param <F> The witness type representing the type constructor (e.g., ListKind.class,
 *     OptionalKind.class). This 'F' corresponds to the 'F' in the simulated higher-kinded type
 *     {@code F<A>}.
 */
@NullMarked
public interface Functor<F> {

  /**
   * Applies a function to the value(s) contained within the Functor context.
   *
   * <p>Implementations should adhere to the Functor laws:
   *
   * <ol>
   *   <li>Identity: {@code map(a -> a, fa)} should be equivalent to {@code fa}.
   *   <li>Composition: {@code map(g.compose(f), fa)} should be equivalent to {@code map(g, map(f,
   *       fa))}.
   * </ol>
   *
   * @param f The function to apply to the wrapped value(s). Assumed non-null.
   * @param fa The Functor structure containing the value(s) of type A. Assumed non-null.
   * @param <A> The type of the value(s) inside the input Functor structure.
   * @param <B> The type of the value(s) inside the output Functor structure.
   * @return A new Functor structure containing the result(s) of applying the function {@code f},
   *     maintaining the original structure F. Guaranteed non-null.
   */
  <A, B> @NonNull Kind<F, B> map(final Function<? super A, ? extends B> f, final Kind<F, A> fa);
}
