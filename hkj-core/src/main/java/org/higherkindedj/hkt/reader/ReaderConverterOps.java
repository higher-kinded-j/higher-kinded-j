// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Reader types and their Kind
 * representations. The methods are generic to handle the environment type (R) and value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface ReaderConverterOps {

  /**
   * Widens a concrete {@link Reader Reader&lt;R, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<ReaderKind.Witness<R>, A>}.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param reader The concrete {@link Reader Reader&lt;R, A&gt;} instance to widen. Must be
   *     non-null.
   * @return A non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the wrapped {@code
   *     Reader}.
   * @throws NullPointerException if {@code reader} is {@code null}.
   */
  <R, A> Kind<ReaderKind.Witness<R>, A> widen(Reader<R, A> reader);

  /**
   * Narrows a {@code Kind<ReaderKind.Witness<R>, A>} back to its concrete {@link Reader
   * Reader&lt;R, A&gt;} type.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param kind The {@code Kind<ReaderKind.Witness<R>, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Reader Reader&lt;R, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not an instance of the
   *     expected underlying holder type for Reader.
   */
  <R, A> Reader<R, A> narrow(@Nullable Kind<ReaderKind.Witness<R>, A> kind);
}
