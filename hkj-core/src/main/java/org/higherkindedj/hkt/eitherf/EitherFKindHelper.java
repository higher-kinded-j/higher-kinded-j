// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Helper for converting between concrete {@link EitherF} and its HKT representation {@link
 * EitherFKind}.
 *
 * <p>Access operations via the singleton {@code EITHERF}:
 *
 * <pre>{@code
 * EitherFKindHelper.EITHERF.widen(EitherF.left(someOp));
 * EitherFKindHelper.EITHERF.narrow(someKind);
 * }</pre>
 *
 * @see EitherF
 * @see EitherFKind
 */
@NullMarked
public enum EitherFKindHelper {
  /** Singleton instance. */
  EITHERF;

  /**
   * Holder record that implements {@link EitherFKind}.
   *
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   */
  record EitherFHolder<F extends WitnessArity<?>, G extends WitnessArity<?>, A>(
      EitherF<F, G, A> value) implements EitherFKind<F, G, A> {}

  /**
   * Widens a concrete {@code EitherF<F, G, A>} into its Kind representation.
   *
   * @param eitherF The concrete EitherF instance. Must not be null.
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   * @return The widened Kind representation
   */
  public <F extends WitnessArity<?>, G extends WitnessArity<?>, A>
      Kind<EitherFKind.Witness<F, G>, A> widen(EitherF<F, G, A> eitherF) {
    Validation.kind().requireForWiden(eitherF, EitherF.class);
    return new EitherFHolder<>(eitherF);
  }

  /**
   * Narrows a Kind representation back to concrete {@code EitherF<F, G, A>}.
   *
   * @param kind The Kind representation. Must not be null.
   * @param <F> The witness type for the left effect algebra
   * @param <G> The witness type for the right effect algebra
   * @param <A> The result type
   * @return The concrete EitherF
   * @throws ClassCastException if the kind is not an EitherFHolder
   * @throws NullPointerException if kind is null
   */
  @SuppressWarnings("unchecked")
  public <F extends WitnessArity<?>, G extends WitnessArity<?>, A> EitherF<F, G, A> narrow(
      Kind<EitherFKind.Witness<F, G>, A> kind) {
    Validation.kind().requireNonNull(kind, FROM_KIND);
    return ((EitherFHolder<F, G, A>) kind).value();
  }
}
