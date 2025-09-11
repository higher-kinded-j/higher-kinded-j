// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list; // Assuming a package structure

import java.util.List;
import org.higherkindedj.hkt.Kind;

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
  final class Witness {
    private Witness() {}
  }

  /**
   * Unwraps this HKT back to a standard {@link java.util.List}.
   *
   * @return The underlying {@link java.util.List<A>}.
   */
  List<A> unwrap();

  /**
   * Narrows a {@code Kind<ListKind.Witness, A>} to a {@code ListKind<A>}. This is a safe cast when
   * the underlying type is known to be for List.
   *
   * @param kind The higher-kinded representation of a List.
   * @param <A> The element type.
   * @return The {@code ListKind<A>} instance.
   */
  static <A> ListKind<A> narrow(Kind<Witness, A> kind) {
    return (ListKind<A>) kind;
  }

  /**
   * Factory method to create a {@code ListKind<A>} (specifically a {@code ListView<A>}) from a
   * standard {@link java.util.List}.
   *
   * @param list The list to wrap.
   * @param <A> The element type.
   * @return A new {@code ListKind<A>} instance.
   */
  static <A> ListKind<A> of(List<A> list) {
    return new ListView<>(list);
  }

  /**
   * Concrete implementation of {@link ListKind<A>}. This record wraps a {@code java.util.List<A>}
   * to make it a {@code ListKind<A>}.
   *
   * @param <A> The element type of the list.
   * @param list The list.
   */
  record ListView<A>(List<A> list) implements ListKind<A> {
    @Override
    public List<A> unwrap() {
      return list;
    }
  }
}
