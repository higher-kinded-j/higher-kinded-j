// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the VTask type in Higher-Kinded-J. Represents VTask as a type
 * constructor 'F' in {@code Kind<F, A>}. The witness type F is VTaskKind.Witness.
 *
 * <p>{@code VTask} is an effect type for computations that execute on Java virtual threads,
 * providing lightweight concurrency with structured cancellation. Like {@code IO}, VTask is lazy
 * and only executes when explicitly run.
 *
 * @param <A> The type of the value produced by the VTask computation.
 * @see org.higherkindedj.hkt.Kind
 */
public interface VTaskKind<A> extends Kind<VTaskKind.Witness, A> {
  /**
   * The phantom type marker for the VTask type constructor. This is used as the 'F' in {@code
   * Kind<F, A>}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {} // Private constructor to prevent instantiation
  }
}
