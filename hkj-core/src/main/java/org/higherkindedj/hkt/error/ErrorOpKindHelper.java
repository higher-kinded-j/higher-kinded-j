// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Helper for converting between concrete {@link ErrorOp} and its HKT representation {@link
 * ErrorOpKind}.
 *
 * @see ErrorOp
 * @see ErrorOpKind
 */
@NullMarked
public enum ErrorOpKindHelper {
  /** Singleton instance. */
  ERROR_OP;

  /**
   * Widens a concrete {@code ErrorOp<E, A>} into its Kind representation.
   *
   * <p>Since {@code ErrorOp} extends {@code ErrorOpKind}, this is a cast-free upcast: the validated
   * {@code op} is already a {@code Kind<ErrorOpKind.Witness<E>, A>}, with no wrapper object.
   *
   * @param op The concrete ErrorOp instance. Must not be null.
   * @param <E> The error type
   * @param <A> The result type
   * @return The widened Kind representation
   */
  public <E, A> Kind<ErrorOpKind.Witness<E>, A> widen(ErrorOp<E, A> op) {
    Validation.kind().requireForWiden(op, ErrorOp.class);
    return op;
  }

  /**
   * Narrows a Kind representation back to concrete {@code ErrorOp<E, A>}.
   *
   * @param kind The Kind representation. Must not be null.
   * @param <E> The error type
   * @param <A> The result type
   * @return The concrete ErrorOp
   */
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <E, A> ErrorOp<E, A> narrow(@Nullable Kind<ErrorOpKind.Witness<E>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, ErrorOp.class);
  }
}
