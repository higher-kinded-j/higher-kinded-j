// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list; // Assuming a package structure

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Represents {@link java.util.List} as a Higher-Kinded Type. This interface, {@code ListKind<A>},
 * is the HKT representation for {@code List<A>}. It extends {@code Kind<ListKind.Witness, A>},
 * where {@code ListKind.Witness} is the phantom type marker for the List type constructor.
 *
 * @param <A> The element type of the list.
 */
public interface ListKind<A> extends Kind<ListKind.Witness, A> {

  /**
   * The phantom type marker for the List type constructor. This is used as the 'F' in {@code
   * Kind<F, A>}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
