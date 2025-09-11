// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * Monad implementation for {@link State}, using {@link StateKind.Witness} as the HKT marker. An
 * instance of this class is specific to a state type {@code S}. It extends {@link
 * StateApplicative}.
 *
 * @param <S> The type of the state (fixed for this Monad instance).
 * @see State
 * @see StateKind.Witness
 * @see StateApplicative
 */
public class StateMonad<S> extends StateApplicative<S> implements Monad<StateKind.Witness<S>> {

  /**
   * Sequentially composes two {@code State} actions, passing the result of the first into a
   * function that produces the second {@code State} action (represented as a {@code Kind}), and
   * flattening the result. The state is threaded through.
   *
   * @param <A> The input value type of the first {@code State} computation.
   * @param <B> The output value type of the {@code State} computation produced by function {@code
   *     f}.
   * @param f The non-null function that takes the successful result of {@code ma} and returns a new
   *     {@code Kind<StateKind.Witness<S>, B>}.
   * @param ma The first {@code Kind<StateKind.Witness<S>, A>} representing a {@code State<S,A>}
   *     computation.
   * @return A new {@code Kind<StateKind.Witness<S>, B>} representing the composed and flattened
   *     operation.
   */
  @Override
  public <A, B> Kind<StateKind.Witness<S>, B> flatMap(
      Function<? super A, ? extends Kind<StateKind.Witness<S>, B>> f,
      Kind<StateKind.Witness<S>, A> ma) {

    State<S, A> stateA = STATE.narrow(ma);

    State<S, B> stateB =
        stateA.flatMap(
            a -> {
              Kind<StateKind.Witness<S>, B> kindB = f.apply(a);
              return STATE.narrow(kindB); // narrow here expects Kind<StateKind.Witness<S>, B>
            });

    return STATE.widen(stateB);
  }
}
