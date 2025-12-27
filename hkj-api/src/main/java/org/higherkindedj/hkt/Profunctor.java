// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * A Profunctor is a type constructor {@code P} of kind {@code * -> * -> *} that is contravariant in
 * its first argument and covariant in its second argument. In other words, it represents "things
 * that consume A's and produce B's".
 *
 * <p>Profunctors are particularly useful for modeling optics, parsers, serialisers, and other
 * bidirectional transformations. The key operations are:
 *
 * <ul>
 *   <li>{@code lmap}: Pre-compose with a function on the input (contravariant)
 *   <li>{@code rmap}: Post-compose with a function on the output (covariant)
 *   <li>{@code dimap}: Do both operations simultaneously
 * </ul>
 *
 * <p>Laws that must be satisfied:
 *
 * <pre>
 * 1. Identity: dimap(id, id, p) == p
 * 2. Composition: dimap(f1.compose(f2), g1.andThen(g2), p) == dimap(f2, g1, dimap(f1, g2, p))
 * </pre>
 *
 * @param <P> The profunctor type constructor witness (e.g., {@code FunctionKind.Witness})
 */
@NullMarked
public interface Profunctor<P extends WitnessArity<TypeArity.Binary>> {

  /**
   * Map over the input (contravariant).
   *
   * @param f Function to pre-compose with
   * @param pab The profunctor value
   * @param <A> Original input type
   * @param <B> Output type (unchanged)
   * @param <C> New input type
   * @return Profunctor that first applies {@code f}, then the original profunctor
   */
  default <A, B, C> Kind2<P, C, B> lmap(Function<? super C, ? extends A> f, Kind2<P, A, B> pab) {
    return dimap(f, Function.identity(), pab);
  }

  /**
   * Map over the output (covariant).
   *
   * @param g Function to post-compose with
   * @param pab The profunctor value
   * @param <A> Input type (unchanged)
   * @param <B> Original output type
   * @param <C> New output type
   * @return Profunctor that applies the original profunctor, then {@code g}
   */
  default <A, B, C> Kind2<P, A, C> rmap(Function<? super B, ? extends C> g, Kind2<P, A, B> pab) {
    return dimap(Function.identity(), g, pab);
  }

  /**
   * Map over both input and output simultaneously (contravariant on input, covariant on output).
   * This is the fundamental operation from which {@code lmap} and {@code rmap} can be derived.
   *
   * @param f Function to pre-compose with (contravariant)
   * @param g Function to post-compose with (covariant)
   * @param pab The profunctor value
   * @param <A> Original input type
   * @param <B> Original output type
   * @param <C> New input type
   * @param <D> New output type
   * @return Transformed profunctor
   */
  <A, B, C, D> Kind2<P, C, D> dimap(
      Function<? super C, ? extends A> f, Function<? super B, ? extends D> g, Kind2<P, A, B> pab);
}
