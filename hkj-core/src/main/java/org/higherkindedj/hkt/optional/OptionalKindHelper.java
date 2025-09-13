// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link OptionalConverterOps} for widen/narrow operations for {@link
 * java.util.Optional} types.
 *
 * <p>Access these operations via the singleton {@code OPTIONAL}. For example: {@code
 * OptionalKindHelper.OPTIONAL.widen(Optional.of("value"));} Or, with static import: {@code import
 * static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL; OPTIONAL.widen(...);}
 */
public enum OptionalKindHelper implements OptionalConverterOps {
  OPTIONAL;

  public static final String TYPE_NAME = "Optional";

  /**
   * Internal record implementing the {@link OptionalKind} interface.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param optional The concrete {@link Optional} instance. Must not be {@code null} itself.
   */
  record OptionalHolder<A>(Optional<A> optional) implements OptionalKind<A> {
    /**
     * Constructs an {@code OptionalHolder}.
     *
     * @param optional The {@link Optional} to hold. Must not be null.
     * @throws NullPointerException if the provided {@code optional} instance is null.
     */
    OptionalHolder { // Compact constructor
      requireNonNullForHolder(optional, TYPE_NAME);
    }
  }

  /**
   * Widens a concrete {@link Optional}{@code <A>} instance into its higher-kinded representation,
   * {@code Kind<OptionalKind.Witness, A>}. Implements {@link OptionalConverterOps#widen}.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param optional The concrete {@link Optional}{@code <A>} instance to widen. Must not be {@code
   *     null} (though it can be {@code Optional.empty()}).
   * @return A non-null {@link Kind<OptionalKind.Witness,A>} representing the wrapped {@code
   *     Optional}.
   * @throws NullPointerException if {@code optional} is {@code null}.
   */
  @Override
  public <A> Kind<OptionalKind.Witness, A> widen(Optional<A> optional) {
    requireNonNullForWiden(optional, TYPE_NAME);
    return new OptionalHolder<>(optional);
  }

  /**
   * Narrows a {@code Kind<OptionalKind.Witness, A>} back to its concrete {@link Optional}{@code<A>}
   * type. Implements {@link OptionalConverterOps#narrow}.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param kind The {@code Kind<OptionalKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Optional}{@code <A>} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null} or not an instance of {@code OptionalHolder}.
   */
  @Override
  public <A> Optional<A> narrow(@Nullable Kind<OptionalKind.Witness, A> kind) {
    return narrowKind(kind, TYPE_NAME, this::narrowInternal);
  }

  /** Internal narrowing implementation that performs the actual type checking and extraction. */
  @SuppressWarnings("unchecked")
  private <A> Optional<A> narrowInternal(Kind<OptionalKind.Witness, A> kind) {
    return switch (kind) {
      case OptionalKindHelper.OptionalHolder<?> holder -> (Optional<A>) holder.optional();
      default -> throw new ClassCastException(); // Will be caught and wrapped by narrowKind
    };
  }
}
