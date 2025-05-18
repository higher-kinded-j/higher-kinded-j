// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link Id} as a {@link Kind}. Provides methods for narrowing {@code
 * Kind<Id.Witness, A>} to {@code Id<A>} and wrapping {@code Id<A>} as {@code Kind<Id.Witness, A>}.
 */
public final class IdKindHelper {

  private IdKindHelper() {
    // Prevent instantiation
  }

  /**
   * Narrows a {@code Kind<Id.Witness, A>} to its concrete type {@code Id<A>}.
   *
   * @param kind The {@link Kind} to narrow. Must not be null.
   * @param <A> The type of the value.
   * @return The narrowed {@link Id} instance.
   * @throws NullPointerException if kind is null.
   * @throws ClassCastException if kind is not actually an instance of {@code Id}.
   */
  public static <A> @NonNull Id<A> narrow(@NonNull Kind<Id.Witness, A> kind) {
    Objects.requireNonNull(kind, "Kind cannot be null");
    return (Id<A>) kind;
  }

  /**
   * Wraps an {@code Id<A>} instance as a {@code Kind<Id.Witness, A>}.
   *
   * <p>This is essentially a type-safe cast, as {@link Id} already implements {@code
   * Kind<Id.Witness, A>}.
   *
   * @param id The {@link Id} instance to wrap. Must not be null.
   * @param <A> The type of the value.
   * @return The {@link Id} instance typed as a {@link Kind}.
   * @throws NullPointerException if id is null.
   */
  public static <A> @NonNull Kind<Id.Witness, A> wrap(@NonNull Id<A> id) {
    Objects.requireNonNull(id, "Id cannot be null");
    return id;
  }

  /**
   * Unwraps the value from a {@code Kind<Id.Witness, A>}. This is a convenience method that
   * combines narrowing and then calling {@link Id#value()}.
   *
   * @param kind The {@link Kind} to unwrap. Must not be null.
   * @param <A> The type of the value.
   * @return The underlying value. Can be null if the {@link Id} wrapped a null.
   */
  public static <A> @Nullable A unwrap(@NonNull Kind<Id.Witness, A> kind) {
    return narrow(kind).value(); // Uses the record's accessor
  }
}
