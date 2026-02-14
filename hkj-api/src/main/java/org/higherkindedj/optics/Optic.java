// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * An abstract representation of an optic using the Profunctor representation. This is the core,
 * shared interface that all other optics (Lens, Prism, etc.) implement.
 *
 * <p>An optic can be thought of as a way to "focus" on a part 'A' within a whole 'S' and
 * potentially change its type to 'B', which in turn changes the whole structure's type to 'T'.
 *
 * <p>With profunctor support, optics can be manipulated using profunctor-style operations like
 * {@code contramap}, {@code map}, and {@code dimap} for powerful type transformations.
 *
 * @param <S> The type of the original whole structure.
 * @param <T> The type of the resulting whole structure.
 * @param <A> The type of the original focused part.
 * @param <B> The type of the resulting focused part.
 */
@NullMarked
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
  <F extends WitnessArity<TypeArity.Unary>> Kind<F, T> modifyF(
      Function<A, Kind<F, B>> f, S s, Applicative<F> app);

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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, T> modifyF(
          Function<C, Kind<F, D>> f, S s, Applicative<F> app) {
        return self.modifyF(a -> other.modifyF(f, a, app), s, app);
      }
    };
  }

  /**
   * Pre-compose with a function on the source type (contravariant operation). This is equivalent to
   * the profunctor {@code lmap} operation.
   *
   * @param f Function to apply before this optic
   * @param <C> New source type
   * @return A new optic that first applies {@code f}
   */
  default <C> Optic<C, T, A, B> contramap(Function<? super C, ? extends S> f) {
    Optic<S, T, A, B> self = this;
    return new Optic<C, T, A, B>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, T> modifyF(
          Function<A, Kind<F, B>> g, C c, Applicative<F> app) {
        return self.modifyF(g, f.apply(c), app);
      }
    };
  }

  /**
   * Post-compose with a function on the target type (covariant operation). This is equivalent to
   * the profunctor {@code rmap} operation.
   *
   * @param g Function to apply after this optic
   * @param <U> New target type
   * @return A new optic that applies {@code g} to the result
   */
  default <U> Optic<S, U, A, B> map(Function<? super T, ? extends U> g) {
    Optic<S, T, A, B> self = this;
    return new Optic<S, U, A, B>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, U> modifyF(
          Function<A, Kind<F, B>> f, S s, Applicative<F> app) {
        return app.map(g, self.modifyF(f, s, app));
      }
    };
  }

  /**
   * Apply both contravariant and covariant transformations simultaneously. This is equivalent to
   * the profunctor {@code dimap} operation.
   *
   * @param f Function to apply before this optic (contravariant)
   * @param g Function to apply after this optic (covariant)
   * @param <C> New source type
   * @param <U> New target type
   * @return A new transformed optic
   */
  default <C, U> Optic<C, U, A, B> dimap(
      Function<? super C, ? extends S> f, Function<? super T, ? extends U> g) {
    // Implement dimap directly to avoid type inference issues with chaining
    Optic<S, T, A, B> self = this;
    return new Optic<C, U, A, B>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, U> modifyF(
          Function<A, Kind<F, B>> h, C c, Applicative<F> app) {
        S s = f.apply(c);
        Kind<F, T> result = self.modifyF(h, s, app);
        return app.map(g, result);
      }
    };
  }
}
