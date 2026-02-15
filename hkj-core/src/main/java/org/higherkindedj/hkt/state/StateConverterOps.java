// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to State types and their Kind
 * representations. The methods are generic to handle the state type (S) and value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface StateConverterOps {

  /**
   * Widens a concrete {@link State}{@code <S, A>} instance into its higher-kinded representation,
   * {@code Kind<StateKind.Witness<S>, A>}.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param state The concrete {@link State}{@code <S, A>} instance to widen. Must be non-null.
   * @return A non-null {@code Kind<StateKind.Witness<S>, A>} representing the wrapped {@code
   *     State}.
   * @throws NullPointerException if {@code state} is {@code null}.
   */
  <S, A> Kind<StateKind.Witness<S>, A> widen(State<S, A> state);

  /**
   * Narrows a {@code Kind<StateKind.Witness<S>, A>} back to its concrete {@link State}{@code <S,
   * A>} type.
   *
   * @param <S> The type of the state.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<StateKind.Witness<S>, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link State}{@code <S, A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not an instance of the
   *     expected underlying holder type for State.
   */
  <S, A> State<S, A> narrow(@Nullable Kind<StateKind.Witness<S>, A> kind);
}
