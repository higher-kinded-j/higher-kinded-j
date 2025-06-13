// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@link State}{@code <S, A>} type. In the HKT pattern {@code Kind<F,
 * A>}:
 *
 * <ul>
 *   <li>{@code F} is represented by {@link StateKind.Witness}.
 *   <li>{@code A} is the value type produced by the state computation.
 * </ul>
 *
 * The state type {@code S} is associated with concrete {@code State<S,A>} instances and specific
 * Functor/Applicative/Monad instances for {@code StateKind.Witness} (e.g., {@code StateMonad<S>}).
 *
 * @param <S> The type of the state. This parameter is part of the concrete {@link State} type but
 *     not directly part of the {@link StateKind.Witness} for HKT.
 * @param <A> The value type produced by the state computation. This corresponds to the {@code A} in
 *     {@code Kind<StateKind.Witness, A>}.
 * @see State
 * @see StateKind.Witness
 * @see StateKindHelper
 */
public interface StateKind<S, A> extends Kind<StateKind.Witness<S>, A> {

  /**
   * The phantom type marker (witness type) for the State type constructor. This is used as the 'F'
   * in {@code Kind<F, A>} for State. The state type 'S' is managed at the level of concrete State
   * instances and their corresponding Functor/Applicative/Monad instances (e.g., {@code
   * StateMonad<S>}).
   *
   * @param <TYPE_S> The type of the environment {@code S} associated with this witness.
   */
  final class Witness<TYPE_S> {
    // Private constructor to prevent instantiation of the witness type itself.
    // Its purpose is purely for type-level representation.
    private Witness() {}
  }
}
