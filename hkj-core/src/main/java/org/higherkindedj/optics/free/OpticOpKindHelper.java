// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
   * Widens a concrete OpticOp into its Kind representation. Since {@code OpticOp} extends {@code
   * OpticOpKind}, this is a cast-free upcast with no wrapper object.
   *
   * @param op The optic operation
   * @param <A> The result type
   * @return The widened Kind representation
   */
  public <A> Kind<OpticOpKind.Witness, A> widen(OpticOp<?, A> op) {
    Validation.kind().requireForWiden(op, OpticOp.class);
    return op;
  }

  /**
   * Narrows a Kind representation back to concrete OpticOp.
   *
   * @param kind The Kind representation
   * @param <A> The result type
   * @return The concrete OpticOp
   */
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <A> OpticOp<?, A> narrow(@Nullable Kind<OpticOpKind.Witness, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, OpticOp.class);
  }
}
