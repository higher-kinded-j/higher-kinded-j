// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to List types and their Kind
 * representations. The methods are generic to handle the element type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface ListConverterOps {

  /**
   * Widens a standard {@link java.util.List} into its higher-kinded representation, {@code
   * Kind<ListKind.Witness, A>}.
   *
   * @param list The {@link List} to widen. Must not be null.
   * @param <A> The element type of the list.
   * @return The higher-kinded representation of the list.
   */
  <A> Kind<ListKind.Witness, A> widen(List<A> list);

  /**
   * Narrows a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}.
   *
   * @param kind The higher-kinded representation of the list. Can be null.
   * @param <A> The element type of the list.
   * @return The underlying {@link java.util.List}. Returns an empty list if {@code kind} is null.
   * @throws ClassCastException if the provided {@code kind} is not actually a representation of a
   *     List.
   */
  <A> List<A> narrow(@Nullable Kind<ListKind.Witness, A> kind);
}
