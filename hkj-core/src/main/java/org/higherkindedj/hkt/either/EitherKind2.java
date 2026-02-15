// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind2 interface marker for {@link Either Either&lt;L, R&gt;} in Higher-Kinded-J.
 *
 * <p>This interface, along with its nested {@link Witness} class, allows {@link Either} to be
 * treated abstractly in contexts requiring higher-kinded types (HKTs) with two type parameters.
 *
 * <p>This representation treats {@link Either} as a type constructor with two type parameters, both
 * of which can vary. This enables bifunctor operations where both the left (error) and right
 * (success) types can be transformed independently.
 *
 * <p>This is distinct from {@link EitherKind}, which fixes the left type parameter for use with
 * {@link org.higherkindedj.hkt.Functor} and {@link org.higherkindedj.hkt.Monad} instances.
 *
 * @param <L> The type of the Left value (error/alternative channel)
 * @param <R> The type of the Right value (success channel)
 * @see Either
 * @see EitherKind
 * @see org.higherkindedj.hkt.Bifunctor
 */
public interface EitherKind2<L, R> extends Kind2<EitherKind2.Witness, L, R> {

  /**
   * The phantom type marker (witness type) for the {@code Either<?, ?>} type constructor. This
   * non-instantiable class acts as a tag to represent the {@code Either} type constructor for
   * bifunctor operations.
   */
  final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {} // Private constructor to prevent instantiation.
  }
}
