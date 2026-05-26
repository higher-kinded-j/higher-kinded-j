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
 * @param <S> The type of the state.
 * @param <F> The higher-kinded type witness for the underlying monad.
 * @param <A> The type of the value produced by the computation.
 * @param runStateTFn The function defining the stateful computation. This function takes an initial
 *     state and returns a {@code Kind<F, StateTuple<S, A>>}.
 * @param monadF The {@link Monad} instance for the underlying monad {@code F}, used to sequence
 *     operations within that monad.
 */
public record StateT<S, F extends WitnessArity<TypeArity.Unary>, A>(
    Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF)
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
    Validation.function().require(runStateTFn, "runStateTFn", CONSTRUCTION);
    Validation.transformer().requireOuterMonad(monadF, STATE_T_CLASS, CONSTRUCTION);
  }

  /**
   * Accessor for the stored {@link Monad} instance.
   *
   * <p>This explicit accessor overrides the record's auto-generated accessor solely to carry the
   * {@link Deprecated} annotation; the runtime behaviour is identical to the implicit accessor.
   *
   * @return The {@link Monad} instance for the underlying monad {@code F}.
   * @deprecated since 0.4.6, scheduled for removal in 0.5.0. The {@code monadF} record component
   *     itself will be removed in 0.5.0 so that two {@code StateT} values with the same state
   *     function are considered equal regardless of which {@link Monad} instance they were
   *     constructed with. Pass the {@link Monad} explicitly to {@link #evalStateT(Object, Monad)}
   *     and {@link #execStateT(Object, Monad)} instead. See <a
   *     href="https://github.com/higher-kinded-j/higher-kinded-j/issues/445">issue #445</a>.
   */
  @Deprecated(since = "0.4.6", forRemoval = true)
  public Monad<F> monadF() {
    return monadF;
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
  public static <S, F extends WitnessArity<TypeArity.Unary>, A> StateT<S, F, A> create(
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
   * @deprecated since 0.4.6, scheduled for removal in 0.5.0. The {@code monadF} record component is
   *     being removed; pass the {@link Monad} explicitly via {@link #evalStateT(Object, Monad)}.
   *     See <a href="https://github.com/higher-kinded-j/higher-kinded-j/issues/445">issue #445</a>.
   */
  @Deprecated(since = "0.4.6", forRemoval = true)
  public Kind<F, A> evalStateT(S initialState) {
    return this.monadF.map(StateTuple::value, runStateT(initialState));
  }

  /**
   * Runs the stateful computation and extracts only the final value, discarding the state.
   *
   * <p>This overload accepts the {@link Monad} instance explicitly rather than relying on the
   * record's stored {@code monadF} component. New code should prefer this form; the no-monad
   * overload is deprecated for removal in 0.5.0 when {@code monadF} is dropped from the record
   * components.
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
   * Transforms the outer monad layer of this {@code StateT} by applying the given function to the
   * result of each state transition, producing a new {@code StateT<S, G, A>} that uses {@code
   * monadG} for sequencing. The state-threading logic is left untouched — only the monadic context
   * of each computed {@link StateTuple} changes.
   *
   * <p>Because {@code StateT} stores its {@link Monad} instance internally, switching from {@code
   * F} to {@code G} requires a new {@code Monad<G>} to be supplied.
   *
   * <p>This is useful for applying cross-cutting concerns (logging, retry, timeout) at the monad
   * level, or for switching between monadic contexts via a natural transformation.
   *
   * <p><b>Example — switching from IO to Task via a natural transformation:</b>
   *
   * <pre>{@code
   * StateT<Integer, IOKind.Witness, String> ioState = ...;
   * Natural<IOKind.Witness, TaskKind.Witness> ioToTask = ...;
   * Monad<TaskKind.Witness> taskMonad = ...;
   *
   * StateT<Integer, TaskKind.Witness, String> taskState =
   *     ioState.mapT(taskMonad, ioToTask::apply);
   * }</pre>
   *
   * @param monadG The {@link Monad} instance for the target monad {@code G}. Must not be null.
   * @param f The function to apply to each computed {@code Kind<F, StateTuple<S, A>>}. Must not be
   *     null.
   * @param <G> The witness type of the target outer monad.
   * @return A new {@code StateT<S, G, A>} that applies {@code f} after each state transition.
   * @throws NullPointerException if {@code monadG} or {@code f} is null.
   */
  public <G extends WitnessArity<TypeArity.Unary>> StateT<S, G, A> mapT(
      Monad<G> monadG, Function<Kind<F, StateTuple<S, A>>, Kind<G, StateTuple<S, A>>> f) {
    Validation.transformer().requireOuterMonad(monadG, STATE_T_CLASS, MAP_T);
    Validation.function().require(f, "f", MAP_T);
    return StateT.create(s -> f.apply(this.runStateTFn().apply(s)), monadG);
  }

  /**
   * Runs the stateful computation and extracts only the final state, discarding the value.
   *
   * @param initialState The initial state.
   * @return The final state within the underlying monad F.
   * @deprecated since 0.4.6, scheduled for removal in 0.5.0. The {@code monadF} record component is
   *     being removed; pass the {@link Monad} explicitly via {@link #execStateT(Object, Monad)}.
   *     See <a href="https://github.com/higher-kinded-j/higher-kinded-j/issues/445">issue #445</a>.
   */
  @Deprecated(since = "0.4.6", forRemoval = true)
  public Kind<F, S> execStateT(S initialState) {
    return this.monadF.map(StateTuple::state, runStateT(initialState));
  }

  /**
   * Runs the stateful computation and extracts only the final state, discarding the value.
   *
   * <p>This overload accepts the {@link Monad} instance explicitly rather than relying on the
   * record's stored {@code monadF} component. New code should prefer this form; the no-monad
   * overload is deprecated for removal in 0.5.0 when {@code monadF} is dropped from the record
   * components.
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
}
