// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to {@link Trampoline} types and their
 * {@link Kind} representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface TrampolineConverterOps {

  /**
   * Widens a concrete {@link Trampoline}&lt;A&gt; instance into its higher-kinded representation,
   * {@code Kind<TrampolineKind.Witness, A>}.
   *
   * @param <A> The element type of the {@code Trampoline}.
   * @param trampoline The concrete {@link Trampoline}&lt;A&gt; instance to widen. Must be non-null.
   * @return A non-null {@link Kind<TrampolineKind.Witness, A>} representing the wrapped {@code
   *     Trampoline}.
   * @throws NullPointerException if {@code trampoline} is {@code null}.
   */
  <A> Kind<TrampolineKind.Witness, A> widen(Trampoline<A> trampoline);

  /**
   * Narrows a {@code Kind<TrampolineKind.Witness, A>} back to its concrete {@link
   * Trampoline}&lt;A&gt; type.
   *
   * @param <A> The element type of the {@code Trampoline}.
   * @param kind The {@code Kind<TrampolineKind.Witness, A>} instance to narrow. May be {@code
   *     null}.
   * @return The underlying, non-null {@link Trampoline}&lt;A&gt; instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of the
   *     expected underlying holder type, or if the holder contains a null {@code Trampoline}
   *     instance.
   */
  <A> Trampoline<A> narrow(@Nullable Kind<TrampolineKind.Witness, A> kind);
}
