// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Helper for converting between Free and Kind representations.
 *
 * <p>This enum provides utilities for:
 *
 * <ul>
 *   <li>Widening concrete Free types to their Kind representation
 *   <li>Narrowing Kind representations back to concrete Free types
 * </ul>
 *
 * <p>The singleton pattern ensures a single instance exists for these operations.
 */
public enum FreeKindHelper {
  /** Singleton instance of the FreeKindHelper. */
  FREE;

  /**
   * Widens a concrete Free type to its Kind representation.
   *
   * <p>Since {@code Free} extends {@code FreeKind}, this is a cast-free upcast: the validated
   * {@code free} is already a {@code Kind<FreeKind.Witness<F>, A>}, so no wrapper object is
   * allocated.
   *
   * @param free The Free instance to widen
   * @param <F> The functor type
   * @param <A> The result type
   * @return The Kind representation of the Free instance
   */
  public <F extends WitnessArity<TypeArity.Unary>, A> Kind<FreeKind.Witness<F>, A> widen(
      Free<F, A> free) {
    Validation.kind().requireForWiden(free, Free.class);
    return free;
  }

  /**
   * Narrows a Kind representation back to a concrete Free type.
   *
   * <p>This is the inverse of {@link #widen(Free)}.
   *
   * @param kind The Kind representation to narrow
   * @param <F> The functor type
   * @param <A> The result type
   * @return The concrete Free instance
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null} or
   *     not an instance of {@code Free}.
   */
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <F extends WitnessArity<TypeArity.Unary>, A> Free<F, A> narrow(
      @Nullable Kind<FreeKind.Witness<F>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, Free.class);
  }
}
