// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link NonEmptyListConverterOps} for widen/narrow operations on {@link
 * NonEmptyList}.
 *
 * <p>Access these operations via the singleton {@code NON_EMPTY_LIST}. For example: {@code
 * NonEmptyListKindHelper.NON_EMPTY_LIST.widen(NonEmptyList.of("value"));} Or, with static import:
 * {@code import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;
 * NON_EMPTY_LIST.widen(...);}
 */
public enum NonEmptyListKindHelper implements NonEmptyListConverterOps {
  NON_EMPTY_LIST;

  private static final Class<NonEmptyList> NON_EMPTY_LIST_CLASS = NonEmptyList.class;

  /**
   * Widens a concrete {@link NonEmptyList}&lt;A&gt; into its HKT representation, {@code
   * Kind<NonEmptyListKind.Witness, A>}.
   *
   * <p>Since {@code NonEmptyList} implements {@code NonEmptyListKind}, this is a cast-free upcast.
   *
   * @param <A> the element type
   * @param nonEmptyList the instance to widen; must be non-null
   * @return the {@code Kind} representation of {@code nonEmptyList}
   * @throws NullPointerException if {@code nonEmptyList} is {@code null}
   */
  @Override
  public <A> Kind<NonEmptyListKind.Witness, A> widen(NonEmptyList<A> nonEmptyList) {
    Validation.kind().requireForWiden(nonEmptyList, NON_EMPTY_LIST_CLASS);
    return nonEmptyList;
  }

  /**
   * Narrows a {@code Kind<NonEmptyListKind.Witness, A>} back to a concrete {@link
   * NonEmptyList}&lt;A&gt;.
   *
   * <p>Since {@code NonEmptyList} directly implements {@code NonEmptyListKind}, this is a direct
   * type check and cast with no holder to unwrap.
   *
   * @param <A> the element type
   * @param kind the {@code Kind} to narrow; may be {@code null}
   * @return the underlying, non-null {@link NonEmptyList}&lt;A&gt;
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null} or
   *     not a {@code NonEmptyList}
   */
  @Override
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <A> NonEmptyList<A> narrow(@Nullable Kind<NonEmptyListKind.Witness, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, NON_EMPTY_LIST_CLASS);
  }
}
