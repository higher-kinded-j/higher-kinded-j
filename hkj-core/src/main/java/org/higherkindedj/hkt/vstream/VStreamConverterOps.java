// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to VStream types and their Kind
 * representations. The methods are generic to handle the element type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 *
 * @see VStream
 * @see VStreamKind
 * @see VStreamKindHelper
 */
public interface VStreamConverterOps {

  /**
   * Widens a concrete {@link VStream} instance into its higher-kinded representation, {@code
   * Kind<VStreamKind.Witness, A>}.
   *
   * @param <A> The element type of the {@code VStream}.
   * @param vstream The non-null, concrete {@link VStream} instance to widen.
   * @return A non-null {@link Kind} representing the wrapped {@code VStream}.
   * @throws NullPointerException if {@code vstream} is {@code null}.
   */
  <A> Kind<VStreamKind.Witness, A> widen(VStream<A> vstream);

  /**
   * Narrows a {@code Kind<VStreamKind.Witness, A>} back to its concrete {@link VStream} type.
   *
   * @param <A> The element type of the {@code VStream}.
   * @param kind The {@code Kind<VStreamKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link VStream} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     the expected underlying holder type for VStream.
   */
  <A> VStream<A> narrow(@Nullable Kind<VStreamKind.Witness, A> kind);
}
