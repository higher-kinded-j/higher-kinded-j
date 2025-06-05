// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link EitherConverterOps} for widen/narrow operations for {@link Either}
 * types.
 *
 * <p>Access these operations via the singleton {@code EITHER}. For example: {@code
 * EitherKindHelper.EITHER.widen(Either.right("value"));} Or, with static import: {@code import
 * static org.higherkindedj.hkt.either.EitherKindHelper.EITHER; EITHER.widen(...);}
 */
public enum EitherKindHelper implements EitherConverterOps {
  EITHER;

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for Either";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an EitherHolder: ";

  /**
   * Error message for when the internal holder in {@link #narrow(Kind)} contains a {@code null}
   * Either instance. This should ideally not occur if {@link #widen(Either)} enforces non-null
   * Either instances and EitherHolder guarantees its content.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "EitherHolder contained null Either instance";

  /**
   * Internal record implementing {@link EitherKind} to hold the concrete {@link Either} instance.
   * Changed to package-private for potential test access.
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @param either The non-null, actual {@link Either} instance.
   */
  record EitherHolder<L, R>(@NonNull Either<L, R> either) implements EitherKind<L, R> {}

  /**
   * Widens a concrete {@code Either<L, R>} instance into its higher-kinded representation, {@code
   * Kind<EitherKind.Witness<L>, R>}. Implements {@link EitherConverterOps#widen}.
   *
   * @param <L> The type of the "Left" value of the {@code Either}.
   * @param <R> The type of the "Right" value of the {@code Either}.
   * @param either The non-null, concrete {@code Either<L, R>} instance to widen.
   * @return A non-null {@code Kind<EitherKind.Witness<L>, R>} representing the wrapped {@code
   *     Either}.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  @Override
  public <L, R> @NonNull Kind<EitherKind.Witness<L>, R> widen(@NonNull Either<L, R> either) {
    Objects.requireNonNull(either, "Input Either cannot be null for widen");
    return new EitherHolder<>(either);
  }

  /**
   * Narrows a {@code Kind<EitherKind.Witness<L>, R>} back to its concrete {@code Either<L, R>}
   * type. Implements {@link EitherConverterOps#narrow}.
   *
   * @param <L> The type of the "Left" value of the target {@code Either}.
   * @param <R> The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind<EitherKind.Witness<L>, R>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@code Either<L, R>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not a representation
   *     of an {@code Either<L,R>}.
   */
  @Override
  public <L, R> @NonNull Either<L, R> narrow(@Nullable Kind<EitherKind.Witness<L>, R> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case EitherHolder<L, R> holder -> holder.either(); // Expect EitherHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }
}
