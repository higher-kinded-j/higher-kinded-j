// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Kind interface marker for the {@link EitherF EitherF<F, G, A>} type in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link EitherF} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs). For HKT purposes, {@code
 * EitherF<F, G, ?>} is treated as a type constructor that takes one type argument {@code A}.
 *
 * <p>The witness is parameterised by both {@code F} and {@code G} (the two effect algebras being
 * composed), fixing them so that the HKT varies only over {@code A}.
 *
 * @param <F> The witness type for the left effect algebra
 * @param <G> The witness type for the right effect algebra
 * @param <A> The result type (the varying parameter)
 * @see EitherF
 * @see EitherFKindHelper
 */
@NullMarked
public interface EitherFKind<F extends WitnessArity<?>, G extends WitnessArity<?>, A>
    extends Kind<EitherFKind.Witness<F, G>, A> {

  /**
   * Phantom type marker (witness type) for the {@code EitherF<F, G, ?>} type constructor.
   * Parameterised by {@code F} and {@code G} to fix both effect algebras.
   *
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   */
  final class Witness<F extends WitnessArity<?>, G extends WitnessArity<?>>
      implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      throw new UnsupportedOperationException("Witness class");
    }
  }
}
