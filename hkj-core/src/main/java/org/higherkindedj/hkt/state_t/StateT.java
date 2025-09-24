// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.util.validation.DomainValidator;
import org.higherkindedj.hkt.util.validation.FunctionValidator;

/**
 * Represents the StateT monad transformer as a Java record. It allows composing stateful
 * computations within another monad F.
 *
 * <p>A StateT computation is essentially a function that takes an initial state `S` and returns a
 * computation in the underlying monad `F` which yields a pair of the resulting value `A` and the
 * final state `S`.
 *
 * @param <S> The type of the state.
 * @param <F> The higher-kinded type witness for the underlying monad.
 * @param <A> The type of the value produced by the computation.
 * @param runStateTFn The function defining the stateful computation. This function takes an initial
 *     state and returns a {@code Kind<F, StateTuple<S, A>>}.
 * @param monadF The {@link Monad} instance for the underlying monad {@code F}, used to sequence
 *     operations within that monad.
 */
public record StateT<S, F, A>(Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF)
    implements StateTKind<S, F, A> {

  private static final Class<StateT> STATE_T_CLASS = StateT.class;

  /**
   * Canonical constructor for {@code StateT}.
   *
   * @param runStateTFn The function defining the stateful computation. Must not be null.
   * @param monadF The Monad instance for the underlying monad F. Must not be null.
   * @throws NullPointerException if runStateTFn or monadF is null.
   */
  public StateT {
    FunctionValidator.requireFunction(runStateTFn, "runStateTFn", STATE_T_CLASS, CONSTRUCTION);
    DomainValidator.requireOuterMonad(monadF, STATE_T_CLASS, CONSTRUCTION);
  }

  /**
   * Factory method to create a StateT instance. This delegates to the record's canonical
   * constructor.
   *
   * @param runStateTFn The function defining the stateful computation.
   * @param monadF The Monad instance for the underlying monad F.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return A new StateT instance.
   * @throws NullPointerException if {@code runStateTFn} or {@code monadF} is null.
   */
  public static <S, F, A> StateT<S, F, A> create(
      Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF) {
    return new StateT<>(runStateTFn, monadF);
  }

  /**
   * Runs the stateful computation with an initial state.
   *
   * @param initialState The initial state.
   * @return The result of the computation within the underlying monad F, containing the final value
   *     and state.
   */
  public Kind<F, StateTuple<S, A>> runStateT(S initialState) {
    return this.runStateTFn.apply(initialState);
  }

  /**
   * Runs the stateful computation and extracts only the final value, discarding the state.
   *
   * @param initialState The initial state.
   * @return The final value within the underlying monad F.
   */
  public Kind<F, A> evalStateT(S initialState) {
    return this.monadF.map(StateTuple::value, runStateT(initialState));
  }

  /**
   * Runs the stateful computation and extracts only the final state, discarding the value.
   *
   * @param initialState The initial state.
   * @return The final state within the underlying monad F.
   */
  public Kind<F, S> execStateT(S initialState) {
    return this.monadF.map(StateTuple::state, runStateT(initialState));
  }
}
