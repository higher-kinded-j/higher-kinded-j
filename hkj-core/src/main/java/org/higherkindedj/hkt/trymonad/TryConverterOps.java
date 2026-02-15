// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Try types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface TryConverterOps {

  /**
   * Widens a concrete {@link Try}&lt;A&gt; instance into its HKT representation, {@link
   * Kind}&lt;{@link TryKind.Witness}, A&gt;.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param tryInstance The concrete {@link Try}&lt;A&gt; instance to widen. Must be non-null.
   * @return A non-null {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing the wrapped
   *     {@code Try} computation.
   * @throws NullPointerException if {@code tryInstance} is {@code null}.
   */
  <A> Kind<TryKind.Witness, A> widen(Try<A> tryInstance);

  /**
   * Narrows a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; back to its concrete {@link
   * Try}&lt;A&gt; representation.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param kind The {@code Kind<TryKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Try}&lt;A&gt; instance.
   * @throws KindUnwrapException if {@code kind} is {@code null}, or if the {@code kind} instance
   *     cannot be properly converted to a {@link Try} instance (e.g., wrong type or invalid
   *     internal state).
   */
  <A> Try<A> narrow(@Nullable Kind<TryKind.Witness, A> kind);
}
