// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to IO types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface IOConverterOps {

  /**
   * Widens a concrete {@link IO<A>} instance into its higher-kinded representation, {@code
   * Kind<IOKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param io The non-null, concrete {@link IO<A>} instance to widen.
   * @return A non-null {@link Kind<IOKind.Witness, A>} representing the wrapped {@code IO}
   *     computation.
   * @throws NullPointerException if {@code io} is {@code null}.
   */
  <A> @NonNull Kind<IOKind.Witness, A> widen(@NonNull IO<A> io);

  /**
   * Narrows a {@code Kind<IOKind.Witness, A>} back to its concrete {@link IO<A>} type.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param kind The {@code Kind<IOKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link IO<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     the expected underlying holder type for IO.
   */
  <A> @NonNull IO<A> narrow(@Nullable Kind<IOKind.Witness, A> kind);
}
