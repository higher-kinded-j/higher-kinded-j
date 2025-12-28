// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind2 interface marker for {@link Const Const&lt;C, A&gt;} in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Const} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs) with two type parameters.
 *
 * <p>This representation treats {@link Const} as a type constructor with two type parameters: a
 * constant value type {@code C} and a phantom type {@code A}. This enables bifunctor operations
 * where the constant value can be transformed independently of the phantom type.
 *
 * @param <C> The type of the constant value
 * @param <A> The phantom type parameter
 * @see Const
 * @see org.higherkindedj.hkt.Bifunctor
 */
public interface ConstKind2<C, A> extends Kind2<ConstKind2.Witness, C, A> {

  /**
   * The phantom type marker (witness type) for the {@code Const<?, ?>} type constructor. This
   * non-instantiable class acts as a tag to represent the {@code Const} type constructor for
   * bifunctor operations.
   */
  final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {} // Private constructor to prevent instantiation.
  }
}
