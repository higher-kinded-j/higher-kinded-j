// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

/**
 * An abstract representation of an optic using the Profunctor representation. This is the core,
 * shared interface that all other optics (Lens, Prism, etc.) implement.
 *
 * <p>An optic can be thought of as a way to "focus" on a part 'A' within a whole 'S' and
 * potentially change its type to 'B', which in turn changes the whole structure's type to 'T'.
 *
 * @param <S> The type of the original whole structure.
 * @param <T> The type of the resulting whole structure.
 * @param <A> The type of the original focused part.
 * @param <B> The type of the resulting focused part.
 */
public interface Optic<S, T, A, B> {

  /**
   * The fundamental operation of any optic. It applies a function to the focused part 'A' to
   * produce a new part 'B' wrapped in an Applicative context 'F', and returns the updated structure
   * 'T' also wrapped in 'F'.
   *
   * @param f The function to apply to the focused part.
   * @param s The source structure.
   * @param app The Applicative instance for the context 'F'.
   * @param <F> The witness type for the Applicative context.
   * @return The updated structure 'T' wrapped in the context 'F'.
   */
  <F> Kind<F, T> modifyF(Function<A, Kind<F, B>> f, S s, Applicative<F> app);

  /**
   * Composes this optic with another optic. This is the universal composition method that works for
   * any combination of optics.
   *
   * @param other The optic to compose with.
   * @param <C> The type of the new focused part.
   * @param <D> The type of the new resulting part.
   * @return A new, composed Optic.
   */
  default <C, D> Optic<S, T, C, D> andThen(Optic<A, B, C, D> other) {
    Optic<S, T, A, B> self = this;
    return new Optic<>() {
      @Override
      public <F> Kind<F, T> modifyF(Function<C, Kind<F, D>> f, S s, Applicative<F> app) {
        // The composition law: apply the first optic's modifyF to the result
        // of the second optic's modifyF.
        return self.modifyF(a -> other.modifyF(f, a, app), s, app);
      }
    };
  }
}
