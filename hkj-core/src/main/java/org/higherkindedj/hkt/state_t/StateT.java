// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.EVAL_STATE_T;
import static org.higherkindedj.hkt.util.validation.Operation.EXEC_STATE_T;
import static org.higherkindedj.hkt.util.validation.Operation.MAP_T;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Represents the StateT monad transformer as a Java record. It allows composing stateful
 * computations within another monad F.
 *
 * <p>A StateT computation is essentially a function that takes an initial state `S` and returns a
 * computation in the underlying monad `F` which yields a pair of the resulting value `A` and the
 * final state `S`.
 *
 * <p>The record holds only that function, so two {@code StateT} values are equal exactly when their
 * state functions are equal. The {@link Monad} for {@code F} is supplied where it is needed: to
 * {@link StateTMonad} for sequencing, and to {@link #evalStateT(Object, Monad)} and {@link
 * #execStateT(Object, Monad)} for projecting a result.
 *
 * @param <S> The type of the state.
 * @param <F> The higher-kinded type witness for the underlying monad.
 * @param <A> The type of the value produced by the computation.
 * @param runStateTFn The function defining the stateful computation. This function takes an initial
 *     state and returns a {@code Kind<F, StateTuple<S, A>>}.
 */
public record StateT<S, F extends WitnessArity<TypeArity.Unary>, A>(
    Function<S, Kind<F, StateTuple<S, A>>> runStateTFn) implements StateTKind<S, F, A> {

  private static final Class<StateT> STATE_T_CLASS = StateT.class;

  /**
   * Canonical constructor for {@code StateT}.
   *
   * @param runStateTFn The function defining the stateful computation. Must not be null.
   * @throws NullPointerException if runStateTFn is null.
   */
  public StateT {
    Validation.function().require(runStateTFn, "runStateTFn", CONSTRUCTION);
  }

  /**
   * Factory method to create a StateT instance. This delegates to the record's canonical
   * constructor.
   *
   * @param runStateTFn The function defining the stateful computation.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return A new StateT instance.
   * @throws NullPointerException if {@code runStateTFn} is null.
   */
  public static <S, F extends WitnessArity<TypeArity.Unary>, A> StateT<S, F, A> create(
      Function<S, Kind<F, StateTuple<S, A>>> runStateTFn) {
    return new StateT<>(runStateTFn);
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
   * <p>The monad maps the {@code F} result down to its value; {@link #runStateT(Object)} needs
   * none.
   *
   * @param initialState The initial state.
   * @param monad The {@link Monad} instance for the underlying monad {@code F}. Must not be null.
   * @return The final value within the underlying monad F.
   * @throws NullPointerException if {@code monad} is null.
   */
  public Kind<F, A> evalStateT(S initialState, Monad<F> monad) {
    Validation.transformer().requireOuterMonad(monad, STATE_T_CLASS, EVAL_STATE_T);
    return monad.map(StateTuple::value, runStateT(initialState));
  }

  /**
   * Runs the stateful computation and extracts only the final state, discarding the value.
   *
   * <p>The monad maps the {@code F} result down to its state; {@link #runStateT(Object)} needs
   * none.
   *
   * @param initialState The initial state.
   * @param monad The {@link Monad} instance for the underlying monad {@code F}. Must not be null.
   * @return The final state within the underlying monad F.
   * @throws NullPointerException if {@code monad} is null.
   */
  public Kind<F, S> execStateT(S initialState, Monad<F> monad) {
    Validation.transformer().requireOuterMonad(monad, STATE_T_CLASS, EXEC_STATE_T);
    return monad.map(StateTuple::state, runStateT(initialState));
  }

  /**
   * Transforms the outer monad layer of this {@code StateT} by applying the given function to the
   * result of each state transition, producing a new {@code StateT<S, G, A>}. The state-threading
   * logic is left untouched; only the monadic context of each computed {@link StateTuple} changes.
   *
   * <p>Because {@code StateT} wraps a function rather than a value, {@code mapT} composes {@code f}
   * after the result of {@code runStateTFn}:
   *
   * <pre>
   * state ──&gt; runStateTFn ──&gt; Kind&lt;F, StateTuple&lt;S, A&gt;&gt; ──&gt; f ──&gt; Kind&lt;G, StateTuple&lt;S, A&gt;&gt;
   * </pre>
   *
   * <p>This is useful for applying cross-cutting concerns (logging, retry, timeout) at the monad
   * level, or for switching between monadic contexts via a natural transformation.
   *
   * <p><b>Example: switching from IO to a virtual-thread task via a natural transformation:</b>
   *
   * <pre>{@code
   * StateT<Integer, IOKind.Witness, String> ioState = ...;
   * Natural<IOKind.Witness, VTaskKind.Witness> ioToVTask = ...;
   *
   * StateT<Integer, VTaskKind.Witness, String> vTaskState = ioState.mapT(ioToVTask::apply);
   * }</pre>
   *
   * @param f The function to apply to each computed {@code Kind<F, StateTuple<S, A>>}. Must not be
   *     null.
   * @param <G> The witness type of the target outer monad.
   * @return A new {@code StateT<S, G, A>} that applies {@code f} after each state transition.
   * @throws NullPointerException if {@code f} is null.
   */
  public <G extends WitnessArity<TypeArity.Unary>> StateT<S, G, A> mapT(
      Function<Kind<F, StateTuple<S, A>>, Kind<G, StateTuple<S, A>>> f) {
    Validation.function().require(f, "f", MAP_T);
    return StateT.create(s -> f.apply(this.runStateTFn().apply(s)));
  }
}
