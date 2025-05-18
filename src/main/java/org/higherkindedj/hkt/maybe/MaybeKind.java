// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the Maybe type in Higher-Kinded-J. Represents Maybe as a type
 * constructor 'F' in {@code Kind<F, A>}.
 *
 * @param <A> The type of the value potentially held by the Maybe.
 */
public interface MaybeKind<A> extends Kind<MaybeKind.Witness, A> {
  /**
   * The phantom type marker (witness type) for the Maybe type constructor. This is used as the 'F'
   * in {@code Kind<F, A>} for Maybe.
   */
  final class Witness {
    // Private constructor to prevent instantiation of the witness type itself.
    private Witness() {}
  }
}
