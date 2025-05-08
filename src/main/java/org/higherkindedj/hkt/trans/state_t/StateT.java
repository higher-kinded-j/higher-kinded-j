package org.higherkindedj.hkt.trans.state_t;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Represents the StateT monad transformer. It allows composing stateful computations within another
 * monad F.
 *
 * <p>A StateT computation is essentially a function that takes an initial state `S` and returns a
 * computation in the underlying monad `F` which yields a pair of the resulting value `A` and the
 * final state `S`.
 *
 * @param <S> The type of the state.
 * @param <F> The higher-kinded type witness for the underlying monad.
 * @param <A> The type of the value produced by the computation.
 */
public final class StateT<S, F, A> implements StateTKind<S, F, A> {

  private final Function<S, Kind<F, StateTuple<S, A>>> runStateTFn;
  private final Monad<F> monadF; // Keep a reference to the Monad instance for F

  // Private constructor, use factory methods in StateTKindHelper
  private StateT(Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF) {
    this.runStateTFn = requireNonNull(runStateTFn, "runStateTFn cannot be null");
    this.monadF = requireNonNull(monadF, "monadF cannot be null");
  }

  /**
   * Factory method to create a StateT instance. It's recommended to use methods in {@link
   * StateTKindHelper} for creation.
   *
   * @param runStateTFn The function defining the stateful computation.
   * @param monadF The Monad instance for the underlying monad F.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return A new StateT instance.
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
    return runStateTFn.apply(initialState);
  }

  /**
   * Runs the stateful computation and extracts only the final value, discarding the state.
   *
   * @param initialState The initial state.
   * @return The final value within the underlying monad F.
   */
  public Kind<F, A> evalStateT(S initialState) {
    return monadF.map(StateTuple::value, runStateT(initialState));
  }

  /**
   * Runs the stateful computation and extracts only the final state, discarding the value.
   *
   * @param initialState The initial state.
   * @return The final state within the underlying monad F.
   */
  public Kind<F, S> execStateT(S initialState) {
    return monadF.map(StateTuple::state, runStateT(initialState));
  }

  /**
   * Provides access to the underlying Monad instance for F. Useful for implementing Monad
   * operations for StateT.
   *
   * @return The Monad<F> instance.
   */
  Monad<F> monadF() {
    return monadF;
  }

  // Consider adding equals, hashCode, and toString if needed,
  // though comparing functions can be tricky. For simplicity, they are omitted here.
  // Equality might be based on running the function with a sample state if F's results are
  // comparable.

  @Override
  public String toString() {
    return "StateT(...)";
  }
}
