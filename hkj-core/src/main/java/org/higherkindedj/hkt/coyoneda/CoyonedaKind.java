// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface for Coyoneda, enabling higher-kinded type representation.
 *
 * <p>This interface allows Coyoneda to be used with type classes like Functor. The Witness type
 * parameter F represents the underlying type constructor that Coyoneda wraps.
 *
 * <p>The witness type {@code CoyonedaKind.Witness<F>} represents the partially applied type
 * constructor {@code Coyoneda<F, _>}, allowing Coyoneda to participate in the HKT simulation.
 *
 * @param <F> The underlying type constructor (witness type)
 * @param <A> The result type
 */
public interface CoyonedaKind<F, A> extends Kind<CoyonedaKind.Witness<F>, A> {

  /**
   * Witness type for Coyoneda.
   *
   * <p>This is used as a type-level marker to represent the Coyoneda type constructor in the
   * higher-kinded type system. The type parameter F represents the underlying type constructor that
   * Coyoneda wraps.
   *
   * @param <F> The underlying type constructor
   */
  final class Witness<F> implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      // Prevents instantiation - this is a phantom type
    }
  }
}
