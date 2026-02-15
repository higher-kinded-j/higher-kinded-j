// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Kind interface marker for the {@link Id} type in Higher-Kinded-J. Represents {@code Id} as a type
 * constructor 'F' in {@code Kind<F, A>}.
 *
 * <p>This interface enables {@code Id} (the Identity monad) to be used with Higher-Kinded Type
 * abstractions such as {@link org.higherkindedj.hkt.Functor}, {@link
 * org.higherkindedj.hkt.Applicative}, and {@link org.higherkindedj.hkt.Monad}.
 *
 * @param <A> The type of the value wrapped by the Id.
 */
@NullMarked
public interface IdKind<A> extends Kind<IdKind.Witness, A> {
  /**
   * The phantom type marker (witness type) for the {@link Id} type constructor. This is used as the
   * 'F' in {@code Kind<F, A>} for {@code Id}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    // Private constructor to prevent instantiation of the witness type itself.
    private Witness() {}
  }
}
