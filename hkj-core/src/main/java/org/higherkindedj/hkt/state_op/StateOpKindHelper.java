// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Helper for converting between concrete {@link StateOp} and its HKT representation {@link
 * StateOpKind}.
 *
 * @see StateOp
 * @see StateOpKind
 */
@NullMarked
public enum StateOpKindHelper {
  /** Singleton instance. */
  STATE_OP;

  record StateOpHolder<S, A>(StateOp<S, A> op) implements StateOpKind<S, A> {}

  /**
   * Widens a concrete {@code StateOp<S, A>} into its Kind representation.
   *
   * @param op The concrete StateOp instance. Must not be null.
   * @param <S> The state type
   * @param <A> The result type
   * @return The widened Kind representation
   */
  public <S, A> Kind<StateOpKind.Witness<S>, A> widen(StateOp<S, A> op) {
    Validation.kind().requireForWiden(op, StateOp.class);
    return new StateOpHolder<>(op);
  }

  /**
   * Narrows a Kind representation back to concrete {@code StateOp<S, A>}.
   *
   * @param kind The Kind representation. Must not be null.
   * @param <S> The state type
   * @param <A> The result type
   * @return The concrete StateOp
   */
  @SuppressWarnings("unchecked")
  public <S, A> StateOp<S, A> narrow(Kind<StateOpKind.Witness<S>, A> kind) {
    Validation.kind().requireNonNull(kind, FROM_KIND);
    return ((StateOpHolder<S, A>) kind).op();
  }
}
