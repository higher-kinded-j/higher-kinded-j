// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link EitherTConverterOps} for widen/narrow operations for {@link EitherT}
 * types.
 *
 * <p>Access these operations via the singleton {@code EITHER_T}. For example: {@code
 * EitherTKindHelper.EITHER_T.widen(myEitherTInstance);}
 */
public enum EitherTKindHelper implements EitherTConverterOps {
  EITHER_T;

  private static final Class<EitherT> EITHER_T_CLASS = EitherT.class;

  /**
   * Widens a concrete {@link EitherT EitherT&lt;F, L, R&gt;} instance into its {@link Kind}
   * representation, {@code Kind<EitherTKind.Witness<F, L>, R>}. Implements {@link
   * EitherTConverterOps#widen}.
   *
   * <p>This is a direct cast as {@code EitherT} implements {@code EitherTKind}.
   *
   * @param <F> The witness type of the outer monad in {@code EitherT}.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @param eitherT The concrete {@link EitherT} instance to widen. Must not be null.
   * @return The {@code Kind} representation. Never null.
   * @throws NullPointerException if {@code eitherT} is null.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <F, L, R> Kind<EitherTKind.Witness<F, L>, R> widen(EitherT<F, L, R> eitherT) {
    KindValidator.requireForWiden(eitherT, EITHER_T_CLASS);
    return (Kind<EitherTKind.Witness<F, L>, R>) eitherT;
  }

  /**
   * Narrows a {@code Kind<EitherTKind.Witness<F, L>, R>} back to its concrete {@link EitherT
   * EitherT&lt;F, L, R&gt;} type. Implements {@link EitherTConverterOps#narrow}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @param kind The {@code Kind<EitherTKind.Witness<F, L>, R>} to narrow. Can be null.
   * @return The unwrapped {@link EitherT EitherT&lt;F, L, R&gt;} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is null or not an
   *     {@link EitherT} instance.
   */
  @Override
  public <F, L, R> EitherT<F, L, R> narrow(@Nullable Kind<EitherTKind.Witness<F, L>, R> kind) {
    return KindValidator.narrowWithTypeCheck(kind, EITHER_T_CLASS);
  }
}
