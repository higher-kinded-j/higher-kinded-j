// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind2 interface marker for {@link Tuple2 Tuple2&lt;A, B&gt;} in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Tuple2} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs) with two type parameters.
 *
 * <p>This representation treats {@link Tuple2} as a type constructor with two type parameters, both
 * of which can vary. This enables bifunctor operations where both the first and second elements can
 * be transformed independently.
 *
 * @param <A> The type of the first element
 * @param <B> The type of the second element
 * @see Tuple2
 * @see org.higherkindedj.hkt.Bifunctor
 */
public interface Tuple2Kind2<A, B> extends Kind2<Tuple2Kind2.Witness, A, B> {

  /**
   * The phantom type marker (witness type) for the {@code Tuple2<?, ?>} type constructor. This
   * non-instantiable class acts as a tag to represent the {@code Tuple2} type constructor for
   * bifunctor operations.
   */
  final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {} // Private constructor to prevent instantiation.
  }
}
