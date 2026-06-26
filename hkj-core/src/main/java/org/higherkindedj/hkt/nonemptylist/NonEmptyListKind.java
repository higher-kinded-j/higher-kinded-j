// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the {@link NonEmptyList} type in Higher-Kinded-J. Represents {@code
 * NonEmptyList} as a type constructor 'F' in {@code Kind<F, A>}.
 *
 * <p>Because {@link NonEmptyList} directly implements {@code NonEmptyListKind}, every {@code
 * NonEmptyList} is already a {@code Kind<NonEmptyListKind.Witness, A>}; widen/narrow via {@link
 * NonEmptyListKindHelper} need no wrapper type and have zero runtime overhead.
 *
 * @param <A> the (non-null) element type
 */
public interface NonEmptyListKind<A> extends Kind<NonEmptyListKind.Witness, A> {

  /**
   * The phantom type marker (witness type) for the {@code NonEmptyList} type constructor. This is
   * used as the 'F' in {@code Kind<F, A>} for {@code NonEmptyList}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
