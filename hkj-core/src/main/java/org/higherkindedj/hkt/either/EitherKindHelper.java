// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind;
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
   * Internal record implementing {@link EitherKind} to hold the concrete {@link Either} instance.
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @param either The non-null, actual {@link Either} instance.
   */
  record EitherHolder<L, R>(Either<L, R> either) implements EitherKind<L, R> {

    public EitherHolder {
      Validation.kind().requireForWiden(either, EITHER_CLASS);
    }
  }

  /**
   * Widens a concrete {@code Either<L, R>} instance into its higher-kinded representation, {@code
   * Kind<EitherKind.Witness<L>, R>}. Implements {@link EitherConverterOps#widen}.
   *
   * @param <L> The type of the "Left" value of the {@code Either}.
   * @param <R> The type of the "Right" value of the {@code Either}.
   * @param either The concrete {@code Either<L, R>} instance to widen. Must not be null.
   * @return A {@code Kind<EitherKind.Witness<L>, R>} representing the wrapped {@code Either}. Never
   *     null.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  @Override
  public <L, R> Kind<EitherKind.Witness<L>, R> widen(Either<L, R> either) {
    return new EitherHolder<>(either);
  }

  /**
   * Narrows a {@code Kind<EitherKind.Witness<L>, R>} back to its concrete {@code Either<L, R>}
   * type. Implements {@link EitherConverterOps#narrow}.
   *
   * <p>This implementation uses a holder-based approach with modern switch expressions for
   * consistent pattern matching.
   *
   * @param <L> The type of the "Left" value of the target {@code Either}.
   * @param <R> The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind<EitherKind.Witness<L>, R>} instance to narrow. May be {@code null}.
   * @return The underlying {@code Either<L, R>} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null} or not a representation of an {@code Either<L,R>}.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <L, R> Either<L, R> narrow(@Nullable Kind<EitherKind.Witness<L>, R> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind,
            EITHER_CLASS,
            EitherHolder.class,
            holder -> {
              // Safe cast due to type erasure and holder validation
              return ((EitherHolder<L, R>) holder).either();
            });
  }
}
