// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Kind interface marker for {@link ErrorOp ErrorOp<E, A>} in Higher-Kinded-J.
 *
 * <p>The witness is parameterised by {@code E} (the error type), fixing it so that the HKT varies
 * only over {@code A}. This follows the same pattern as {@code EitherKind.Witness<L>} which fixes
 * the left type.
 *
 * @param <E> The error type (fixed for a given effect composition)
 * @param <A> The result type (the varying parameter)
 */
@NullMarked
public interface ErrorOpKind<E, A> extends Kind<ErrorOpKind.Witness<E>, A> {

  /**
   * Phantom type marker for the {@code ErrorOp<E, ?>} type constructor.
   *
   * @param <E> The error type
   */
  final class Witness<E> implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      throw new UnsupportedOperationException("Witness class");
    }
  }
}
