// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
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

  /** Error message for attempting to narrow a {@code null} {@link Kind}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for Optional";

  /** Error message for attempting to narrow a {@link Kind} of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalHolder: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG = "Input Optional cannot be null for widen";

  // Note: INVALID_HOLDER_STATE_MSG from original is not directly used by narrow if OptionalHolder
  // guarantees its internal 'optional' is non-null via constructor.
  // The OptionalHolder's compact constructor already enforces this.
  /**
   * Error message for an invalid state where the internal holder contains a {@code null} Optional.
   * This should not be reachable if {@link #widen(Optional)} ensures non-null inputs for the {@code
   * Optional} instance itself and OptionalHolder constructor enforces it.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "OptionalHolder contained null Optional instance";

  /**
   * Internal record implementing the {@link OptionalKind} interface. Changed to package-private for
   * potential test access.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param optional The concrete {@link Optional} instance. Must not be {@code null} itself.
   */
  record OptionalHolder<A>(@NonNull Optional<A> optional) implements OptionalKind<A> {
    /**
     * Constructs an {@code OptionalHolder}.
     *
     * @param optional The {@link Optional} to hold. Must not be null.
     * @throws NullPointerException if the provided {@code optional} instance is null.
     */
    OptionalHolder { // Compact constructor
      requireNonNull(optional, "Wrapped Optional instance cannot be null in OptionalHolder");
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
  public <A> @NonNull Kind<OptionalKind.Witness, A> widen(@NonNull Optional<A> optional) {
    requireNonNull(optional, INVALID_KIND_TYPE_NULL_MSG);
    return new OptionalHolder<>(optional);
  }

  /**
   * Narrows a {@code Kind<OptionalKind.Witness, A>} back to its concrete {@link Optional}{@code<A>}
   * type. Implements {@link OptionalConverterOps#narrow}.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param kind The {@code Kind<OptionalKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Optional}{@code <A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not an instance of
   *     {@code OptionalHolder}.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> @NonNull Optional<A> narrow(@Nullable Kind<OptionalKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case OptionalKindHelper.OptionalHolder<?> holder -> (Optional<A>) holder.optional();
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }
}
