// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.test;

import org.higherkindedj.hkt.Kind;

/** Helper for converting between Identity and Kind representations. */
public enum IdentityKindHelper {
  /** Singleton instance. */
  IDENTITY;

  /** Holder record for Identity. */
  record IdentityHolder<A>(Identity<A> identity) implements IdentityKind<A> {}

  /**
   * Widens an Identity to its Kind representation.
   *
   * @param identity The identity to widen
   * @param <A> The value type
   * @return The Kind representation
   */
  public <A> Kind<IdentityKind.Witness, A> widen(Identity<A> identity) {
    return new IdentityHolder<>(identity);
  }

  /**
   * Narrows a Kind back to an Identity.
   *
   * @param kind The Kind to narrow
   * @param <A> The value type
   * @return The concrete Identity
   */
  public <A> Identity<A> narrow(Kind<IdentityKind.Witness, A> kind) {
    return ((IdentityHolder<A>) kind).identity();
  }
}
