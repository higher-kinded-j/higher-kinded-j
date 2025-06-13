// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
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

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for ReaderT";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderT: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG = "Input ReaderT cannot be null for widen";

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
   *     be {@code @NonNull}.
   * @return A non-null {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} representing the wrapped
   *     {@code readerT}.
   * @throws NullPointerException if {@code readerT} is {@code null}.
   */
  @Override
  public <F, R_ENV, A> @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, A> widen(
      @NonNull ReaderT<F, R_ENV, A> readerT) {
    Objects.requireNonNull(readerT, INVALID_KIND_TYPE_NULL_MSG);
    // ReaderT<F,R_ENV,A> is already a ReaderTKind<F,R_ENV,A>,
    // which is a Kind<ReaderTKind.Witness<F,R_ENV>,A>.
    return readerT;
  }

  /**
   * Narrows a {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} back to its concrete {@link ReaderT
   * ReaderT&lt;F, R_ENV, A&gt;} type. Implements {@link ReaderTConverterOps#narrow}.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT} within its outer monad.
   * @param kind The {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} instance to narrow. May be
   *     {@code null}.
   * @return The underlying, non-null {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not an instance of
   *     {@link ReaderT}.
   */
  @Override
  public <F, R_ENV, A> @NonNull ReaderT<F, R_ENV, A> narrow(
      @Nullable Kind<ReaderTKind.Witness<F, R_ENV>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(ReaderTKindHelper.INVALID_KIND_NULL_MSG);
      case ReaderT<F, R_ENV, A> directReaderT -> directReaderT;
      default ->
          throw new KindUnwrapException(
              ReaderTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }
}
