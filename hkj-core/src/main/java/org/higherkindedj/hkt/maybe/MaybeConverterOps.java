// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Maybe types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface MaybeConverterOps {

  /**
   * Widens a concrete {@link Maybe}&lt;A&gt; instance into its higher-kinded representation, {@code
   * Kind<MaybeKind.Witness, A>}.
   *
   * @param <A> The element type of the {@code Maybe}.
   * @param maybe The concrete {@link Maybe}&lt;A&gt; instance to widen. Must be non-null.
   * @return A non-null {@link Kind<MaybeKind.Witness, A>} representing the wrapped {@code Maybe}.
   * @throws NullPointerException if {@code maybe} is {@code null}.
   */
  <A> Kind<MaybeKind.Witness, A> widen(Maybe<A> maybe);

  /**
   * Narrows a {@code Kind<MaybeKind.Witness, A>} back to its concrete {@link Maybe}&lt;A&gt; type.
   *
   * @param <A> The element type of the {@code Maybe}.
   * @param kind The {@code Kind<MaybeKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Maybe}&lt;A&gt; instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of the
   *     expected underlying holder type, or if the holder contains a null Maybe instance.
   */
  <A> Maybe<A> narrow(@Nullable Kind<MaybeKind.Witness, A> kind);
}
