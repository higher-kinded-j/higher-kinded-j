// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
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

  private static final Class<Either> EITHER_CLASS = Either.class;

  /**
   * Widens a concrete {@code Either<L, R>} instance into its higher-kinded representation, {@code
   * Kind<EitherKind.Witness<L>, R>}. Implements {@link EitherConverterOps#widen}.
   *
   * <p>Since {@code Left} and {@code Right} directly implement {@code EitherKind}, this method
   * performs a simple type-safe cast without requiring a wrapper object.
   *
   * @param <L> The type of the "Left" value of the {@code Either}.
   * @param <R> The type of the "Right" value of the {@code Either}.
   * @param either The concrete {@code Either<L, R>} instance to widen. Must not be null.
   * @return A {@code Kind<EitherKind.Witness<L>, R>} representing the {@code Either}. Never null.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <L, R> Kind<EitherKind.Witness<L>, R> widen(Either<L, R> either) {
    Validation.kind().requireForWiden(either, EITHER_CLASS);
    return (Kind<EitherKind.Witness<L>, R>) either;
  }

  /**
   * Narrows a {@code Kind<EitherKind.Witness<L>, R>} back to its concrete {@code Either<L, R>}
   * type. Implements {@link EitherConverterOps#narrow}.
   *
   * <p>Since {@code Left} and {@code Right} directly implement {@code EitherKind}, this method
   * performs a direct type check and cast without needing to unwrap from a holder.
   *
   * @param <L> The type of the "Left" value of the target {@code Either}.
   * @param <R> The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind<EitherKind.Witness<L>, R>} instance to narrow. May be {@code null}.
   * @return The underlying {@code Either<L, R>} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null} or not an instance of {@code Either}.
   */
  @Override
  public <L, R> Either<L, R> narrow(@Nullable Kind<EitherKind.Witness<L>, R> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, EITHER_CLASS);
  }

  /**
   * Widens a concrete {@code Either<L, R>} instance into its Kind2 representation, {@code
   * Kind2<EitherKind2.Witness, L, R>}. Implements {@link EitherConverterOps#widen2}.
   *
   * <p>Since {@code Left} and {@code Right} directly implement {@code EitherKind2}, this method
   * performs a simple type-safe cast without requiring a wrapper object.
   *
   * @param <L> The type of the "Left" value of the {@code Either}.
   * @param <R> The type of the "Right" value of the {@code Either}.
   * @param either The concrete {@code Either<L, R>} instance to widen. Must not be null.
   * @return A {@code Kind2<EitherKind2.Witness, L, R>} representing the {@code Either}. Never null.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <L, R> Kind2<EitherKind2.Witness, L, R> widen2(Either<L, R> either) {
    Validation.kind().requireForWiden(either, EITHER_CLASS);
    return (Kind2<EitherKind2.Witness, L, R>) either;
  }

  /**
   * Narrows a {@code Kind2<EitherKind2.Witness, L, R>} back to its concrete {@code Either<L, R>}
   * type. Implements {@link EitherConverterOps#narrow2}.
   *
   * <p>Since {@code Left} and {@code Right} directly implement {@code EitherKind2}, this method
   * performs a direct type check and cast without needing to unwrap from a holder.
   *
   * @param <L> The type of the "Left" value of the target {@code Either}.
   * @param <R> The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind2<EitherKind2.Witness, L, R>} instance to narrow. May be {@code
   *     null}.
   * @return The underlying {@code Either<L, R>} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null} or not an instance of {@code Either}.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <L, R> Either<L, R> narrow2(@Nullable Kind2<EitherKind2.Witness, L, R> kind) {
    if (kind == null) {
      throw new org.higherkindedj.hkt.exception.KindUnwrapException(
          "Cannot narrow null Kind2 for Either");
    }
    if (!(kind instanceof Either<?, ?>)) {
      throw new org.higherkindedj.hkt.exception.KindUnwrapException(
          "Kind2 instance cannot be narrowed to Either (received: "
              + kind.getClass().getSimpleName()
              + ")");
    }
    return (Either<L, R>) kind;
  }
}
