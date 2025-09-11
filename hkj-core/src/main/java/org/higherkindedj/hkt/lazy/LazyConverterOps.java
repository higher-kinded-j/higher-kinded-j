// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Lazy types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface LazyConverterOps {

  /**
   * Widens a concrete {@link Lazy<A>} instance into its higher-kinded representation, {@code
   * Kind<LazyKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param lazy The non-null, concrete {@link Lazy<A>} instance to widen.
   * @return A non-null {@link Kind<LazyKind.Witness, A>} representing the wrapped {@code Lazy}
   *     computation.
   * @throws NullPointerException if {@code lazy} is {@code null}.
   */
  <A> Kind<LazyKind.Witness, A> widen(Lazy<A> lazy);

  /**
   * Narrows a {@code Kind<LazyKind.Witness, A>} back to its concrete {@link Lazy<A>} type.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param kind The {@code Kind<LazyKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Lazy<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     the expected underlying holder type for Lazy.
   */
  <A> Lazy<A> narrow(@Nullable Kind<LazyKind.Witness, A> kind);
}
