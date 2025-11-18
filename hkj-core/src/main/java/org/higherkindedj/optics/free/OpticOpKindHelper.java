// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NullMarked;

/**
 * Helper for converting between concrete {@link OpticOp} and its HKT representation {@link
 * OpticOpKind}.
 *
 * <p>This enables OpticOp to participate in the Free monad DSL while maintaining type safety.
 */
@NullMarked
public enum OpticOpKindHelper {
  /** Singleton instance. */
  OP;

  /**
   * Holder record that implements OpticOpKind.
   *
   * @param <A> The result type
   */
  record OpticOpHolder<A>(OpticOp<?, A> op) implements OpticOpKind<A> {}

  /**
   * Widens a concrete OpticOp into its Kind representation.
   *
   * @param op The optic operation
   * @param <A> The result type
   * @return The widened Kind representation
   */
  public <A> Kind<OpticOpKind.Witness, A> widen(OpticOp<?, A> op) {
    return new OpticOpHolder<>(op);
  }

  /**
   * Narrows a Kind representation back to concrete OpticOp.
   *
   * @param kind The Kind representation
   * @param <A> The result type
   * @return The concrete OpticOp
   */
  @SuppressWarnings("unchecked")
  public <A> OpticOp<?, A> narrow(Kind<OpticOpKind.Witness, A> kind) {
    return ((OpticOpHolder<A>) kind).op();
  }
}
