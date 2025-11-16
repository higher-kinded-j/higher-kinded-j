// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import org.higherkindedj.hkt.Kind;

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
   * Holder record that wraps a Free instance and implements FreeKind. This enables the concrete
   * Free type to be represented as a Kind.
   *
   * @param <F> The functor type
   * @param <A> The result type
   */
  record FreeHolder<F, A>(Free<F, A> free) implements FreeKind<F, A> {}

  /**
   * Widens a concrete Free type to its Kind representation.
   *
   * <p>This allows Free to be used with type classes that operate on Kind types.
   *
   * @param free The Free instance to widen
   * @param <F> The functor type
   * @param <A> The result type
   * @return The Kind representation of the Free instance
   */
  public <F, A> Kind<FreeKind.Witness<F>, A> widen(Free<F, A> free) {
    return new FreeHolder<>(free);
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
   * @throws ClassCastException if the Kind is not a FreeHolder
   */
  public <F, A> Free<F, A> narrow(Kind<FreeKind.Witness<F>, A> kind) {
    return ((FreeHolder<F, A>) kind).free();
  }
}
