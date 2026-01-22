// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Defines the conversion operations (widen/narrow) for {@link Context} types.
 *
 * <p>These operations allow converting between the concrete {@link Context Context<R, A>} type and
 * its higher-kinded representation {@code Kind<ContextKind.Witness<R>, A>}.
 *
 * @see Context
 * @see ContextKind
 * @see ContextKindHelper
 */
public interface ContextConverterOps {

  /**
   * Widens a concrete {@link Context Context<R, A>} to its higher-kinded representation.
   *
   * @param context The context to widen. Must not be null.
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result.
   * @return The higher-kinded representation.
   * @throws NullPointerException if {@code context} is null.
   */
  <R, A> Kind<ContextKind.Witness<R>, A> widen(Context<R, A> context);

  /**
   * Narrows a higher-kinded representation back to a concrete {@link Context Context<R, A>}.
   *
   * @param kind The higher-kinded representation. May be null.
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result.
   * @return The concrete Context.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the kind is invalid.
   */
  <R, A> Context<R, A> narrow(@Nullable Kind<ContextKind.Witness<R>, A> kind);
}
