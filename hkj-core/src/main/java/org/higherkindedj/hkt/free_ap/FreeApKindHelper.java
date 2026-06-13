// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
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
  /**
   * Widens a concrete FreeAp type to its Kind representation.
   *
   * <p>Since {@code FreeAp} extends {@code FreeApKind}, this is a cast-free upcast: the validated
   * {@code freeAp} is already a {@code Kind<FreeApKind.Witness<F>, A>}, with no wrapper object.
   *
   * @param freeAp The FreeAp instance to widen. Must not be null.
   * @param <F> The instruction set type
   * @param <A> The result type
   * @return The Kind representation of the FreeAp instance
   * @throws NullPointerException if freeAp is null
   */
  public <F extends WitnessArity<TypeArity.Unary>, A> Kind<FreeApKind.Witness<F>, A> widen(
      FreeAp<F, A> freeAp) {
    Validation.kind().requireForWiden(freeAp, FreeAp.class);
    return freeAp;
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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null} or
   *     not an instance of {@code FreeAp}.
   */
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <F extends WitnessArity<TypeArity.Unary>, A> FreeAp<F, A> narrow(
      @Nullable Kind<FreeApKind.Witness<F>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, FreeAp.class);
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
