// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to VTask types and their Kind
 * representations. The methods are generic to handle the value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 *
 * @see VTask
 * @see VTaskKind
 * @see VTaskKindHelper
 */
public interface VTaskConverterOps {

  /**
   * Widens a concrete {@link VTask<A>} instance into its higher-kinded representation, {@code
   * Kind<VTaskKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code VTask} computation.
   * @param vtask The non-null, concrete {@link VTask<A>} instance to widen.
   * @return A non-null {@link Kind<VTaskKind.Witness, A>} representing the wrapped {@code VTask}
   *     computation.
   * @throws NullPointerException if {@code vtask} is {@code null}.
   */
  <A> Kind<VTaskKind.Witness, A> widen(VTask<A> vtask);

  /**
   * Narrows a {@code Kind<VTaskKind.Witness, A>} back to its concrete {@link VTask<A>} type.
   *
   * @param <A> The result type of the {@code VTask} computation.
   * @param kind The {@code Kind<VTaskKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link VTask<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     the expected underlying holder type for VTask.
   */
  <A> VTask<A> narrow(@Nullable Kind<VTaskKind.Witness, A> kind);
}
