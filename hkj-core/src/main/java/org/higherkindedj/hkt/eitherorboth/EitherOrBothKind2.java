// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind2 interface marker for {@link EitherOrBoth EitherOrBoth&lt;L, R&gt;} in Higher-Kinded-J.
 *
 * <p>This representation treats {@link EitherOrBoth} as a type constructor with two type
 * parameters, both of which can vary, enabling {@link org.higherkindedj.hkt.Bifunctor Bifunctor}
 * operations where the left (warning) and right (success) channels are transformed independently.
 *
 * <p>This is distinct from {@link EitherOrBothKind}, which fixes the left type for right-biased
 * {@link org.higherkindedj.hkt.Functor}/{@link org.higherkindedj.hkt.Monad} instances.
 *
 * @param <L> the type of the Left (warning/error) channel
 * @param <R> the type of the Right (success) channel
 * @see EitherOrBoth
 * @see EitherOrBothKind
 * @see org.higherkindedj.hkt.Bifunctor
 */
public interface EitherOrBothKind2<L, R> extends Kind2<EitherOrBothKind2.Witness, L, R> {

  /**
   * The phantom type marker (witness type) for the {@code EitherOrBoth<?, ?>} type constructor,
   * used as the tag for bifunctor operations.
   */
  final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {}
  }
}
