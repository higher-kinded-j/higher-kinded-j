// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.KindValidator;
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

  private static final Class<List> TYPE = List.class;

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
    KindValidator.requireForWiden(list, TYPE);
    return ListKind.of(list);
  }

  /**
   * Narrows a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}.
   *
   * @param kind The higher-kinded representation of the list. May be null.
   * @param <A> The element type of the list.
   * @return The underlying {@link java.util.List}. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is null or not a valid
   *     ListKind representation.
   */
  @Override
  public <A> List<A> narrow(@Nullable Kind<ListKind.Witness, A> kind) {
    return KindValidator.narrow(kind, TYPE, this::extractList);
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
  public <A> List<A> unwrapOr(@Nullable Kind<ListKind.Witness, A> kind, List<A> defaultValue) {
    KindValidator.requireForWiden(defaultValue, TYPE);
    if (kind == null) {
      return defaultValue;
    }
    return ListKind.narrow(kind).unwrap();
  }

  /**
   * Internal extraction method for narrowing operations.
   *
   * @throws ClassCastException if kind is not a ListKind (will be caught and wrapped by
   *     KindValidator)
   */
  private <A> List<A> extractList(Kind<ListKind.Witness, A> kind) {
    return switch (kind) {
      case ListKind<A> listKind -> listKind.unwrap();
      default -> throw new ClassCastException();
    };
  }
}
