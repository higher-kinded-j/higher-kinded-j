// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to WriterT types and their Kind
 * representations.
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface WriterTConverterOps {

  /**
   * Widens a concrete {@link WriterT WriterT&lt;F, W, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<WriterTKind.Witness<F, W>, A>}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param writerT The concrete {@link WriterT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code writerT} is null.
   */
  <F extends WitnessArity<TypeArity.Unary>, W, A> Kind<WriterTKind.Witness<F, W>, A> widen(
      WriterT<F, W, A> writerT);

  /**
   * Narrows a {@code Kind<WriterTKind.Witness<F, W>, A>} back to its concrete {@link WriterT
   * WriterT&lt;F, W, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param kind The {@code Kind<WriterTKind.Witness<F, W>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link WriterT WriterT&lt;F, W, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link WriterT} instance.
   */
  <F extends WitnessArity<TypeArity.Unary>, W, A> WriterT<F, W, A> narrow(
      @Nullable Kind<WriterTKind.Witness<F, W>, A> kind);
}
