// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Kind interface marker for {@link StateOp StateOp<S, A>} in Higher-Kinded-J.
 *
 * <p>The witness is parameterised by {@code S} (the state type), fixing it so that the HKT varies
 * only over {@code A}. This follows the same pattern as {@code ErrorOpKind.Witness<E>} which fixes
 * the error type.
 *
 * @param <S> The state type (fixed for a given effect composition)
 * @param <A> The result type (the varying parameter)
 */
@NullMarked
public interface StateOpKind<S, A> extends Kind<StateOpKind.Witness<S>, A> {

  /**
   * Phantom type marker for the {@code StateOp<S, ?>} type constructor.
   *
   * @param <S> The state type
   */
  final class Witness<S> implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      throw new UnsupportedOperationException("Witness class");
    }
  }
}
