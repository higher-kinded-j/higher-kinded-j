// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface for the Free monad, enabling higher-kinded type representation.
 *
 * <p>This interface allows Free to be used with type classes like Functor and Monad. The Witness
 * type parameter represents the functor F that the Free monad is built over.
 *
 * @param <F> The functor type (witness)
 * @param <A> The result type
 */
public interface FreeKind<F extends WitnessArity<?>, A> extends Kind<FreeKind.Witness<F>, A> {

  /**
   * Witness type for the Free monad. This is used as a type-level marker to represent the Free type
   * constructor in the higher-kinded type system.
   *
   * @param <F> The functor type over which Free is constructed
   */
  final class Witness<F extends WitnessArity<?>> implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      // Prevents instantiation - this is a phantom type
    }
  }
}
