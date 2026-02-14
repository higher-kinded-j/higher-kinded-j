// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Kind interface marker for the Optional type in Higher-Kinded-J. Represents Optional as a type
 * constructor 'F' in {@code Kind<F, A>}. The witness type F is OptionalKind.Witness.
 *
 * @param <A> The type of the value potentially held by the Optional.
 */
@NullMarked
public interface OptionalKind<A> extends Kind<OptionalKind.Witness, A> {

  /**
   * The phantom type marker for the Optional type constructor. This is used as the 'F' in {@code
   * Kind<F, A>}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {} // Private constructor to prevent instantiation
  }
}
