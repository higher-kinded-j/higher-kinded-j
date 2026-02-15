// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface for FreeAp, enabling higher-kinded type representation.
 *
 * <p>This interface allows FreeAp to be used with type classes like Functor and Applicative. The
 * Witness type parameter F represents the underlying instruction set that FreeAp is built over.
 *
 * <p>The witness type {@code FreeApKind.Witness<F>} represents the partially applied type
 * constructor {@code FreeAp<F, _>}, allowing FreeAp to participate in the HKT simulation.
 *
 * @param <F> The instruction set type constructor (witness type)
 * @param <A> The result type
 */
public interface FreeApKind<F, A> extends Kind<FreeApKind.Witness<F>, A> {

  /**
   * Witness type for FreeAp.
   *
   * <p>This is used as a type-level marker to represent the FreeAp type constructor in the
   * higher-kinded type system. The type parameter F represents the underlying instruction set.
   *
   * @param <F> The instruction set type constructor
   */
  final class Witness<F> implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      // Prevents instantiation - this is a phantom type
    }
  }
}
