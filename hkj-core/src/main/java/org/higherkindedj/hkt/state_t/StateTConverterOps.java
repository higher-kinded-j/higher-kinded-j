// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to StateT types and their Kind
 * representations. The methods are generic to handle the state type (S), outer monad (F), and value
 * type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface StateTConverterOps {

  /**
   * Widens a concrete {@link StateT StateT&lt;S, F, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<StateTKind.Witness<S, F>, A>}.
   *
   * @param <S> The state type of the {@code StateT}.
   * @param <F> The higher-kinded type witness for the underlying monad of {@code StateT}.
   * @param <A> The value type of the {@code StateT}.
   * @param stateT The concrete {@link StateT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code stateT} is {@code null}.
   */
  <S, F, A> Kind<StateTKind.Witness<S, F>, A> widen(StateT<S, F, A> stateT);

  /**
   * Narrows a {@code Kind<StateTKind.Witness<S, F>, A>} back to its concrete {@link StateT
   * StateT&lt;S, F, A&gt;} type.
   *
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @param kind The {@code Kind<StateTKind.Witness<S, F>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link StateT StateT&lt;S, F, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link StateT} instance.
   */
  <S, F, A> StateT<S, F, A> narrow(@Nullable Kind<StateTKind.Witness<S, F>, A> kind);
}
