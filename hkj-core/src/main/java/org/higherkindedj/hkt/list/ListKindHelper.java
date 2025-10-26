// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link ListConverterOps} for widen/narrow operations, and providing additional
 * utility instance methods for {@link List} types in an HKT context.
 *
 * <p>Access these operations via the singleton {@code LIST}. For example: {@code
 * ListKindHelper.LIST.widen(new ArrayList<>());} Or, with static import: {@code import static
 * org.higherkindedj.hkt.list.ListKindHelper.LIST; LIST.widen(...);}
 */
public enum ListKindHelper implements ListConverterOps {
  LIST;

  private static final Class<List> LIST_CLASS = List.class;

  /**
   * Concrete implementation of {@link ListKind<A>}. This record wraps a {@code java.util.List<A>}
   * to make it a {@code ListKind<A>}.
   *
   * @param <A> The element type of the list.
   * @param list The list.
   */
  record ListHolder<A>(List<A> list) implements ListKind<A> {}

  /**
   * Widens a standard {@link java.util.List} into its higher-kinded representation, {@code
   * Kind<ListKind.Witness, A>}.
   *
   * @param list The {@link List} to widen. Must not be null.
   * @param <A> The element type of the list.
   * @return The higher-kinded representation of the list. Never null.
   * @throws NullPointerException if list is null.
   */
  @Override
  public <A> Kind<ListKind.Witness, A> widen(List<A> list) {
    Validation.kind().requireForWiden(list, LIST_CLASS);
    return of(list);
  }

  /**
   * Narrows a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}.
   *
   * <p>This implementation uses a holder-based approach with modern switch expressions for
   * consistent pattern matching.
   *
   * @param kind The higher-kinded representation of the list. May be null.
   * @param <A> The element type of the list.
   * @return The underlying {@link java.util.List}. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is null or not a valid
   *     ListKind representation.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> List<A> narrow(@Nullable Kind<ListKind.Witness, A> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind, LIST_CLASS, ListHolder.class, holder -> ((ListHolder<A>) holder).list());
  }

  /**
   * Narrows a higher-kinded representation of a list with a default value if the kind is null.
   *
   * @param kind The higher-kinded representation of the list. May be null.
   * @param defaultValue The list to return if {@code kind} is null. Must not be null.
   * @param <A> The element type of the list.
   * @return The unwrapped list, or {@code defaultValue} if {@code kind} is null. Never null.
   * @throws NullPointerException if defaultValue is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is not a valid ListKind
   *     representation.
   */
  public <A> List<A> narrowOr(@Nullable Kind<ListKind.Witness, A> kind, List<A> defaultValue) {
    Validation.kind().requireForWiden(defaultValue, LIST_CLASS);
    if (kind == null) {
      return defaultValue;
    }
    return narrow(kind);
  }

  /**
   * Factory method to create a {@code ListKind<A>} (specifically a {@code ListHolder<A>}) from a
   * standard {@link java.util.List}.
   *
   * @param list The list to wrap.
   * @param <A> The element type.
   * @return A new {@code ListKind<A>} instance.
   */
  public <A> ListKind<A> of(List<A> list) {
    return new ListHolder<>(list);
  }
}
