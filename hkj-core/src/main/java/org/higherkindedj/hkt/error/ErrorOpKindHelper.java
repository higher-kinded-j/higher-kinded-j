// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

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

  record ErrorOpHolder<E, A>(ErrorOp<E, A> op) implements ErrorOpKind<E, A> {}

  /**
   * Widens a concrete {@code ErrorOp<E, A>} into its Kind representation.
   *
   * @param op The concrete ErrorOp instance. Must not be null.
   * @param <E> The error type
   * @param <A> The result type
   * @return The widened Kind representation
   */
  public <E, A> Kind<ErrorOpKind.Witness<E>, A> widen(ErrorOp<E, A> op) {
    Validation.kind().requireForWiden(op, ErrorOp.class);
    return new ErrorOpHolder<>(op);
  }

  /**
   * Narrows a Kind representation back to concrete {@code ErrorOp<E, A>}.
   *
   * @param kind The Kind representation. Must not be null.
   * @param <E> The error type
   * @param <A> The result type
   * @return The concrete ErrorOp
   */
  @SuppressWarnings("unchecked")
  public <E, A> ErrorOp<E, A> narrow(Kind<ErrorOpKind.Witness<E>, A> kind) {
    Validation.kind().requireNonNull(kind, FROM_KIND);
    return ((ErrorOpHolder<E, A>) kind).op();
  }
}
