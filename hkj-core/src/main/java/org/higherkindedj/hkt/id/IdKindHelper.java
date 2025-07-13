// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
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

  /**
   * Widens an {@code Id<A>} instance to a {@code Kind<Id.Witness, A>}.
   *
   * <p>This is essentially a type-safe cast, as {@link Id} already implements {@code
   * Kind<Id.Witness, A>}. Implements {@link IdConverterOps#widen}.
   *
   * @param id The {@link Id} instance to widen. Must not be null.
   * @param <A> The type of the value.
   * @return The {@link Id} instance typed as a {@link Kind}.
   * @throws NullPointerException if id is null.
   */
  @Override
  public <A> @NonNull Kind<Id.Witness, A> widen(@NonNull Id<A> id) {
    Objects.requireNonNull(id, "Id cannot be null");
    return id;
  }

  /**
   * Narrows a {@code Kind<Id.Witness, A>} to its concrete type {@code Id<A>}. Implements {@link
   * IdConverterOps#narrow}.
   *
   * @param kind The {@link Kind} to narrow. Must not be null.
   * @param <A> The type of the value.
   * @return The narrowed {@link Id} instance.
   * @throws NullPointerException if kind is null.
   */
  @Override
  public <A> @NonNull Id<A> narrow(@NonNull Kind<Id.Witness, A> kind) {
    Objects.requireNonNull(kind, "Kind cannot be null");
    return (Id<A>) kind;
  }

  /**
   * Unwraps the value from a {@code Kind<Id.Witness, A>}. This is a convenience method that
   * combines narrowing and then calling {@link Id#value()}.
   *
   * @param kind The {@link Kind} to unwrap. Must not be null.
   * @param <A> The type of the value.
   * @return The underlying value. Can be null if the {@link Id} wrapped a null.
   */
  public <A> @Nullable A unwrap(@NonNull Kind<Id.Witness, A> kind) {
    return this.narrow(kind).value();
  }
}
