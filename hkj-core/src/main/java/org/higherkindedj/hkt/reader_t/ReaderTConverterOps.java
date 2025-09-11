// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to ReaderT types and their Kind
 * representations. The methods are generic to handle the outer monad (F), environment type (R_ENV),
 * and value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface ReaderTConverterOps {

  /**
   * Widens a concrete {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>}.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param readerT The concrete {@link ReaderT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code readerT} is {@code null}.
   */
  <F, R_ENV, A> Kind<ReaderTKind.Witness<F, R_ENV>, A> widen(ReaderT<F, R_ENV, A> readerT);

  /**
   * Narrows a {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} back to its concrete {@link ReaderT
   * ReaderT&lt;F, R_ENV, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param kind The {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link ReaderT} instance.
   */
  <F, R_ENV, A> ReaderT<F, R_ENV, A> narrow(@Nullable Kind<ReaderTKind.Witness<F, R_ENV>, A> kind);
}
