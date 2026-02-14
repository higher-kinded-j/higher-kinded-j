// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the {@link Trampoline} type in Higher-Kinded-J. Represents {@code
 * Trampoline} as a type constructor 'F' in {@code Kind<F, A>}.
 *
 * <p>This interface enables {@code Trampoline} to be used with Higher-Kinded Type abstractions such
 * as {@link org.higherkindedj.hkt.Functor}, {@link org.higherkindedj.hkt.Applicative}, and {@link
 * org.higherkindedj.hkt.Monad}.
 *
 * @param <A> The type of the value produced when the trampoline computation completes.
 */
public interface TrampolineKind<A> extends Kind<TrampolineKind.Witness, A> {
  /**
   * The phantom type marker (witness type) for the {@link Trampoline} type constructor. This is
   * used as the 'F' in {@code Kind<F, A>} for {@code Trampoline}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    // Private constructor to prevent instantiation of the witness type itself.
    private Witness() {}
  }
}
