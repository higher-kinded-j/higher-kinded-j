// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link IdConverterOps} for widen/narrow operations, and providing an additional
 * unwrap instance method for {@link Id} types.
 *
 * <p>Access these operations via the singleton {@code ID}. For example: {@code
 * IdKindHelper.ID.widen(myIdValue);} Or, with static import: {@code import static
 * org.higherkindedj.hkt.id.IdKindHelper.ID; ID.widen(...);}
 */
public enum IdKindHelper implements IdConverterOps {
  ID;

  private static final Class<Id> ID_CLASS = Id.class;

  /**
   * Widens an {@code Id<A>} instance to a {@code Kind<IdKind.Witness, A>}.
   *
   * <p>This is essentially a type-safe cast, as {@link Id} already implements {@code
   * Kind<IdKind.Witness, A>}.
   *
   * @param id The {@link Id} instance to widen. Must not be null.
   * @param <A> The type of the value.
   * @return The {@link Id} instance typed as a {@link Kind}. Never null.
   * @throws NullPointerException if id is null.
   */
  @Override
  public <A> Kind<IdKind.Witness, A> widen(Id<A> id) {
    Validation.kind().requireForWiden(id, ID_CLASS);
    return id;
  }

  /**
   * Narrows a {@code Kind<IdKind.Witness, A>} to its concrete type {@code Id<A>}.
   *
   * @param kind The {@link Kind} to narrow. May be null.
   * @param <A> The type of the value.
   * @return The narrowed {@link Id} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is null or not an Id
   *     instance.
   */
  @Override
  public <A> Id<A> narrow(@Nullable Kind<IdKind.Witness, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, ID_CLASS);
  }

  /**
   * Unwraps the value from a {@code Kind<IdKind.Witness, A>}. This is a convenience method that
   * combines narrowing and then calling {@link Id#value()}.
   *
   * @param kind The {@link Kind} to unwrap. Must not be null.
   * @param <A> The type of the value.
   * @return The underlying value. Can be null if the {@link Id} wrapped a null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is null or not an Id
   *     instance.
   */
  public <A> @Nullable A unwrap(Kind<IdKind.Witness, A> kind) {
    return this.narrow(kind).value();
  }
}
