// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link WriterTConverterOps} for widen/narrow operations for {@link WriterT}
 * types.
 *
 * <p>Access these operations via the singleton {@code WRITER_T}. For example: {@code
 * WriterTKindHelper.WRITER_T.widen(myWriterTInstance);}
 */
public enum WriterTKindHelper implements WriterTConverterOps {
  WRITER_T;

  private static final Class<WriterT> WRITER_T_CLASS = WriterT.class;

  /**
   * Widens a concrete {@link WriterT WriterT&lt;F, W, A&gt;} instance into its {@link Kind}
   * representation.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param writerT The concrete {@link WriterT} instance to widen. Must not be null.
   * @return The {@code Kind} representation. Never null.
   * @throws NullPointerException if {@code writerT} is null.
   */
  @Override
  public <F extends WitnessArity<TypeArity.Unary>, W, A> Kind<WriterTKind.Witness<F, W>, A> widen(
      WriterT<F, W, A> writerT) {
    Validation.kind().requireForWiden(writerT, WRITER_T_CLASS);
    return writerT;
  }

  /**
   * Narrows a {@code Kind<WriterTKind.Witness<F, W>, A>} back to its concrete {@link WriterT
   * WriterT&lt;F, W, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param kind The {@code Kind} to narrow. Can be null.
   * @return The unwrapped {@link WriterT} instance. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is null or not a
   *     {@link WriterT} instance.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <F extends WitnessArity<TypeArity.Unary>, W, A> WriterT<F, W, A> narrow(
      @Nullable Kind<WriterTKind.Witness<F, W>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, WRITER_T_CLASS);
  }
}
