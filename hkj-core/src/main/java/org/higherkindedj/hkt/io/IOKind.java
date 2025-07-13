// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the IO type in Higher-Kinded-J. Represents IO as a type constructor 'F'
 * in {@code Kind<F, A>}. The witness type F is IOKind.Witness.
 *
 * @param <A> The type of the value produced by the IO operation.
 */
public interface IOKind<A> extends Kind<IOKind.Witness, A> {
  /**
   * The phantom type marker for the IO type constructor. This is used as the 'F' in {@code Kind<F,
   * A>}.
   */
  final class Witness {
    private Witness() {} // Private constructor to prevent instantiation
  }
}
