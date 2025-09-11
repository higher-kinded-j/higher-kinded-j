// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.higherkindedj.hkt.Kind;
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

  public static final String INVALID_KIND_TYPE_NULL_MSG = "list cannot be null for widen";

  /**
   * Widens a standard {@link java.util.List} into its higher-kinded representation, {@code
   * Kind<ListKind.Witness, A>}. Implements {@link ListConverterOps#widen}.
   *
   * <p>This method uses the static {@code ListKind.of(list)} factory method.
   *
   * @param list The {@link List} to widen. Must not be null.
   * @param <A> The element type of the list.
   * @return The higher-kinded representation of the list.
   */
  @Override
  public <A> Kind<ListKind.Witness, A> widen(List<A> list) {
    Objects.requireNonNull(list, INVALID_KIND_TYPE_NULL_MSG);
    return ListKind.of(list);
  }

  /**
   * Narrows a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}. Implements {@link ListConverterOps#narrow}.
   *
   * <p>This method uses the {@code ListKind.narrow(kind).unwrap()} pattern.
   *
   * @param kind The higher-kinded representation of the list. Can be null.
   * @param <A> The element type of the list.
   * @return The underlying {@link java.util.List}. Returns an empty list if {@code kind} is null.
   * @throws ClassCastException if the provided {@code kind} is not actually a {@code ListKind}.
   */
  @Override
  public <A> List<A> narrow(@Nullable Kind<ListKind.Witness, A> kind) {
    if (kind == null) {
      return Collections.emptyList();
    }
    // ListKind.narrow performs the cast to ListKind<A>
    // and then unwrap() is called on the ListKind<A> interface.
    return ListKind.narrow(kind).unwrap();
  }

  /**
   * Narrows a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}, providing a default list if the kind is null.
   *
   * @param kind The higher-kinded representation of the list.
   * @param defaultValue The list to return if {@code kind} is null. Must not be null.
   * @param <A> The element type of the list.
   * @return The unwrapped list, or {@code defaultValue} if {@code kind} is null.
   * @throws ClassCastException if the provided {@code kind} is not actually a {@code ListKind}.
   */
  public <A> List<A> unwrapOr(@Nullable Kind<ListKind.Witness, A> kind, List<A> defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue cannot be null");
    if (kind == null) {
      return defaultValue;
    }
    return ListKind.narrow(kind).unwrap();
  }
}
