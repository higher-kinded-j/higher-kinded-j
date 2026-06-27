// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to {@link NonEmptyList} types and their
 * Kind representations. The methods are generic to handle the element type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface NonEmptyListConverterOps {

  /**
   * Widens a concrete {@link NonEmptyList}&lt;A&gt; instance into its higher-kinded representation,
   * {@code Kind<NonEmptyListKind.Witness, A>}.
   *
   * @param <A> the element type of the {@code NonEmptyList}
   * @param nonEmptyList the concrete {@link NonEmptyList}&lt;A&gt; instance to widen; must be
   *     non-null
   * @return a non-null {@code Kind<NonEmptyListKind.Witness, A>} representing the wrapped {@code
   *     NonEmptyList}
   * @throws NullPointerException if {@code nonEmptyList} is {@code null}
   */
  <A> Kind<NonEmptyListKind.Witness, A> widen(NonEmptyList<A> nonEmptyList);

  /**
   * Narrows a {@code Kind<NonEmptyListKind.Witness, A>} back to its concrete {@link
   * NonEmptyList}&lt;A&gt; type.
   *
   * @param <A> the element type of the {@code NonEmptyList}
   * @param kind the {@code Kind<NonEmptyListKind.Witness, A>} instance to narrow; may be {@code
   *     null}
   * @return the underlying, non-null {@link NonEmptyList}&lt;A&gt; instance
   * @throws KindUnwrapException if {@code kind} is {@code null} or is not an instance of {@code
   *     NonEmptyList}
   */
  <A> NonEmptyList<A> narrow(@Nullable Kind<NonEmptyListKind.Witness, A> kind);
}
