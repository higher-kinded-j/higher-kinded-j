// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link ReaderTConverterOps} for widen/narrow operations for {@link ReaderT}
 * types.
 *
 * <p>Access these operations via the singleton {@code READER_T}. For example: {@code
 * ReaderTKindHelper.READER_T.widen(myReaderTInstance);}
 */
public enum ReaderTKindHelper implements ReaderTConverterOps {
  READER_T;

  private static final Class<ReaderT> READER_T_CLASS = ReaderT.class;

  /**
   * Widens a concrete {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>}.
   *
   * <p>Since {@link ReaderT} directly implements {@link ReaderTKind} (which extends {@code
   * Kind<ReaderTKind.Witness<F, R_ENV>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param readerT The concrete {@link ReaderT} instance to widen. Must not be null.
   * @return The {@code Kind} representation of the {@code readerT}.
   * @throws NullPointerException if {@code readerT} is null.
   */
  @Override
  public <F, R_ENV, A> Kind<ReaderTKind.Witness<F, R_ENV>, A> widen(ReaderT<F, R_ENV, A> readerT) {
    Validation.kind().requireForWiden(readerT, READER_T_CLASS);
    return readerT;
  }

  /**
   * Narrows a {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} back to its concrete {@link ReaderT
   * ReaderT&lt;F, R_ENV, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param kind The {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is null or not a
   *     valid {@link ReaderT} instance.
   */
  @Override
  public <F, R_ENV, A> ReaderT<F, R_ENV, A> narrow(
      @Nullable Kind<ReaderTKind.Witness<F, R_ENV>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, READER_T_CLASS);
  }
}
