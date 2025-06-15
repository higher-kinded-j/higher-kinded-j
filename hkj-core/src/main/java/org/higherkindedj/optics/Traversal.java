// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

/**
 * A Traversal focuses on zero or more parts 'A' within a structure 'S'. It allows modifying all
 * focused parts within an Applicative context.
 *
 * @param <S> The type of the whole structure.
 * @param <A> The type of the focused parts.
 */
public interface Traversal<S, A> {

  /**
   * Modifies all focused parts 'A' within an Applicative context 'F'.
   *
   * @param f The function to apply to each part, returning a value in a context.
   * @param source The whole structure.
   * @param applicative The Applicative instance for the context 'F'.
   * @param <F> The witness type for the Applicative context.
   * @return The updated structure 'S' inside the Applicative context 'F'.
   */
  <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Applicative<F> applicative);
}
