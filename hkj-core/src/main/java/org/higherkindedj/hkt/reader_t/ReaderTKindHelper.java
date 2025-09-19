// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.higherkindedj.hkt.util.ErrorHandling.*;

import org.higherkindedj.hkt.Kind;
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

  public static final String TYPE_NAME = "ReaderT";

  /**
   * Widens a concrete {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>}. Implements {@link
   * ReaderTConverterOps#widen}.
   *
   * <p>Since {@link ReaderT} directly implements {@link ReaderTKind} (which extends {@code
   * Kind<ReaderTKind.Witness<F, R_ENV>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param readerT The concrete {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance to widen. Must
   *     not be null.
   * @return A non-null {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} representing the wrapped
   *     {@code readerT}.
   * @throws NullPointerException if {@code readerT} is {@code null}.
   */
  @Override
  public <F, R_ENV, A> Kind<ReaderTKind.Witness<F, R_ENV>, A> widen(ReaderT<F, R_ENV, A> readerT) {
    requireNonNullForWiden(readerT, TYPE_NAME);
    // readerT is already an ReaderTKind<F, R_ENV, A>, which is a Kind<ReaderTKind.Witness<F,
    // R_ENV>, A>.
    return readerT;
  }

  /**
   * Narrows a {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} back to its concrete {@link ReaderT
   * ReaderT&lt;F, R_ENV, A&gt;} type. Implements {@link ReaderTConverterOps#narrow}.
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
    return narrowKindWithTypeCheck(kind, ReaderT.class, TYPE_NAME);
  }
}
