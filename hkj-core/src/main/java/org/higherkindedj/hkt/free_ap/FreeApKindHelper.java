// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Helper for converting between FreeAp and Kind representations.
 *
 * <p>This enum provides utilities for:
 *
 * <ul>
 *   <li>Widening concrete FreeAp types to their Kind representation
 *   <li>Narrowing Kind representations back to concrete FreeAp types
 * </ul>
 *
 * <p>Access these operations via the singleton {@code FREE_AP}. For example:
 *
 * <pre>{@code
 * FreeApKindHelper.FREE_AP.widen(FreeAp.pure(42));
 * }</pre>
 *
 * <p>Or, with static import:
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
 * FREE_AP.widen(...);
 * }</pre>
 */
public enum FreeApKindHelper {
  /** Singleton instance of the FreeApKindHelper. */
  FREE_AP;

  /**
   * Holder record that wraps a FreeAp instance and implements FreeApKind.
   *
   * <p>This enables the concrete FreeAp type to be represented as a Kind.
   *
   * @param <F> The instruction set type
   * @param <A> The result type
   */
  record FreeApHolder<F extends WitnessArity<TypeArity.Unary>, A>(FreeAp<F, A> freeAp)
      implements FreeApKind<F, A> {}

  /**
   * Widens a concrete FreeAp type to its Kind representation.
   *
   * <p>This allows FreeAp to be used with type classes that operate on Kind types.
   *
   * @param freeAp The FreeAp instance to widen. Must not be null.
   * @param <F> The instruction set type
   * @param <A> The result type
   * @return The Kind representation of the FreeAp instance
   * @throws NullPointerException if freeAp is null
   */
  public <F extends WitnessArity<TypeArity.Unary>, A> Kind<FreeApKind.Witness<F>, A> widen(
      FreeAp<F, A> freeAp) {
    if (freeAp == null) {
      throw new NullPointerException("FreeAp to widen cannot be null");
    }
    return new FreeApHolder<>(freeAp);
  }

  /**
   * Narrows a Kind representation back to a concrete FreeAp type.
   *
   * <p>This is the inverse of {@link #widen(FreeAp)}.
   *
   * @param kind The Kind representation to narrow. May be null.
   * @param <F> The instruction set type
   * @param <A> The result type
   * @return The concrete FreeAp instance
   * @throws KindUnwrapException if kind is null or not a valid FreeApKind representation
   */
  @SuppressWarnings("unchecked")
  public <F extends WitnessArity<TypeArity.Unary>, A> FreeAp<F, A> narrow(
      @Nullable Kind<FreeApKind.Witness<F>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException("Cannot narrow null Kind to FreeAp");
    }
    if (kind instanceof FreeApHolder<?, ?> holder) {
      return (FreeAp<F, A>) holder.freeAp();
    }
    throw new KindUnwrapException(
        "Cannot narrow Kind to FreeAp: expected FreeApHolder but got " + kind.getClass());
  }

  /**
   * Convenience factory that creates a pure FreeAp and wraps it as a Kind.
   *
   * @param value The value to lift
   * @param <F> The instruction set type
   * @param <A> The value type
   * @return A Kind representing FreeAp containing the pure value
   */
  public <F extends WitnessArity<TypeArity.Unary>, A> Kind<FreeApKind.Witness<F>, A> pure(A value) {
    return widen(FreeAp.pure(value));
  }

  /**
   * Convenience factory that lifts a Kind into FreeAp and wraps it as a Kind.
   *
   * @param fa The Kind to lift. Must not be null.
   * @param <F> The instruction set type
   * @param <A> The result type
   * @return A Kind representing FreeAp containing the lifted instruction
   * @throws NullPointerException if fa is null
   */
  public <F extends WitnessArity<TypeArity.Unary>, A> Kind<FreeApKind.Witness<F>, A> lift(
      Kind<F, A> fa) {
    return widen(FreeAp.lift(fa));
  }
}
