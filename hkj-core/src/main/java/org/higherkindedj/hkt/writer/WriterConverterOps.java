// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Writer types and their Kind
 * representations. The methods are generic to handle the log type (W) and value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface WriterConverterOps {

  /**
   * Widens a concrete {@link Writer Writer&lt;W, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<WriterKind.Witness<W>, A>}.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param writer The concrete {@link Writer Writer&lt;W, A&gt;} instance to widen. Must be
   *     non-null.
   * @return A non-null {@code Kind<WriterKind.Witness<W>, A>} representing the wrapped {@code
   *     Writer}.
   * @throws NullPointerException if {@code writer} is {@code null}.
   */
  <W, A> @NonNull Kind<WriterKind.Witness<W>, A> widen(@NonNull Writer<W, A> writer);

  /**
   * Narrows a {@code Kind<WriterKind.Witness<W>, A>} back to its concrete {@link Writer
   * Writer&lt;W, A&gt;} type.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} instance to narrow. Can be
   *     {@code @Nullable}.
   * @return The unwrapped, non-null {@link Writer Writer&lt;W, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is null or not an instance of the
   *     expected underlying holder type for Writer.
   */
  <W, A> @NonNull Writer<W, A> narrow(@Nullable Kind<WriterKind.Witness<W>, A> kind);
}
