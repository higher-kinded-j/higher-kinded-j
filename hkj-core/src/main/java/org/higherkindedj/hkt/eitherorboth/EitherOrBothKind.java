// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for {@link EitherOrBoth EitherOrBoth&lt;L, R&gt;} in Higher-Kinded-J.
 *
 * <p>For HKT purposes, {@code EitherOrBoth<L, ?>} (with a fixed "Left" type {@code L}) is treated
 * as a type constructor {@code F} taking one type argument {@code R} (the "Right" type). This
 * facilitates right-biased typeclass instances such as {@link org.higherkindedj.hkt.Functor
 * Functor}, {@link org.higherkindedj.hkt.Monad Monad} and {@link org.higherkindedj.hkt.Traverse
 * Traverse}.
 *
 * <p>Because {@link EitherOrBoth} directly implements {@code EitherOrBothKind}, widen/narrow via
 * {@link EitherOrBothKindHelper} is a cast-free upcast with zero runtime overhead.
 *
 * @param <L> the type of the "Left" value, captured by the {@link Witness} for HKT representation
 * @param <R> the type of the "Right" value, the parameter that varies for {@code
 *     EitherOrBothKind.Witness<L>}
 * @see EitherOrBoth
 * @see EitherOrBothKind.Witness
 * @see EitherOrBothKindHelper
 */
public interface EitherOrBothKind<L, R> extends Kind<EitherOrBothKind.Witness<L>, R> {

  /**
   * The phantom type marker (witness type) for the {@code EitherOrBoth<L, ?>} type constructor,
   * partially applied with the "Left" type {@code TYPE_L}.
   *
   * @param <TYPE_L> the "Left" type {@code L} associated with this witness
   */
  final class Witness<TYPE_L> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
