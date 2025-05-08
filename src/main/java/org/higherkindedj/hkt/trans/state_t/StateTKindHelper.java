package org.higherkindedj.hkt.trans.state_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Helper class for working with {@link StateT} and {@link StateTKind}. Provides factory methods and
 * utility functions for wrapping, unwrapping, and basic creation/lifting.
 *
 * <p>Note: Operations requiring 'pure' (like get, put, modify) are typically provided by the
 * StateTMonad instance itself or separate utilities requiring the specific Monad<F> instance.
 */
public final class StateTKindHelper {

  // Private constructor to prevent instantiation
  private StateTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Wraps a {@link StateT} instance into its higher-kinded representation {@link StateTKind}. Since
   * StateT implements StateTKind, this mainly serves as a type-safe check and cast. It ensures the
   * input is not null.
   *
   * @param stateT The StateT instance. Must not be null.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return The StateT instance cast to StateTKind.
   * @throws NullPointerException if stateT is null.
   */
  public static <S, F, A> StateTKind<S, F, A> wrap(StateT<S, F, A> stateT) {
    // *** THIS IS THE CRITICAL NULL CHECK ***
    Objects.requireNonNull(stateT, "stateT cannot be null");
    // If stateT is not null, it already implements StateTKind<S, F, A>
    return stateT;
  }

  /**
   * Unwraps a {@link StateTKind} back into a concrete {@link StateT} instance. Uses {@link
   * StateTKind#narrow(Kind)}.
   *
   * @param kind The higher-kinded StateT representation.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return The concrete StateT instance.
   * @throws ClassCastException if the kind is not actually a StateT or if kind is null.
   */
  public static <S, F, A> StateT<S, F, A> unwrap(Kind<StateTKind.Witness<S, F>, A> kind) {
    // Delegates to the narrow method which performs the cast
    return StateTKind.narrow(kind);
  }

  /**
   * Creates a StateT instance directly from the state-transition function. This is the most
   * fundamental way to create a StateT.
   *
   * @param runStateTFn The function S -> F<StateTuple<S, A>>.
   * @param monadF The Monad instance for the underlying monad F (needed internally by StateT).
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return A new StateT instance.
   */
  public static <S, F, A> StateT<S, F, A> stateT(
      Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF) {
    // Delegates creation to StateT's factory method
    return StateT.<S, F, A>create(runStateTFn, monadF);
  }

  /**
   * Lifts a computation from the underlying monad F into StateT. The state remains unchanged.
   *
   * @param monadF The Monad instance for F.
   * @param fa The computation in the underlying monad F.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return A StateT instance wrapping the lifted computation.
   */
  public static <S, F, A> StateT<S, F, A> lift(Monad<F> monadF, Kind<F, A> fa) {
    // Pass arguments as (state, value) -> (s, a) assuming StateTuple.of is defined as of(S, A)
    Function<S, Kind<F, StateTuple<S, A>>> runFn =
        s -> monadF.map(a -> StateTuple.of(s, a), fa); // StateTuple.of takes (state, value)
    return stateT(runFn, monadF);
  }

  // --- Runner methods ---

  /**
   * Runs the StateT computation with an initial state. Convenience method mirroring the instance
   * method.
   *
   * @param kind The StateT computation.
   * @param initialState The initial state.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return The result within the underlying monad F.
   * @throws ClassCastException if kind is null or not a StateT.
   */
  public static <S, F, A> Kind<F, StateTuple<S, A>> runStateT(
      Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    // unwrap will throw ClassCastException if kind is null or invalid
    return unwrap(kind).runStateT(initialState);
  }

  /**
   * Runs the StateT computation and extracts the final value. Convenience method mirroring the
   * instance method.
   *
   * @param kind The StateT computation.
   * @param initialState The initial state.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return The final value within the underlying monad F.
   * @throws ClassCastException if kind is null or not a StateT.
   */
  public static <S, F, A> Kind<F, A> evalStateT(
      Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    // unwrap will throw ClassCastException if kind is null or invalid
    return unwrap(kind).evalStateT(initialState);
  }

  /**
   * Runs the StateT computation and extracts the final state. Convenience method mirroring the
   * instance method.
   *
   * @param kind The StateT computation.
   * @param initialState The initial state.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @param <A> The value type.
   * @return The final state within the underlying monad F.
   * @throws ClassCastException if kind is null or not a StateT.
   */
  public static <S, F, A> Kind<F, S> execStateT(
      Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    // unwrap will throw ClassCastException if kind is null or invalid
    return unwrap(kind).execStateT(initialState);
  }
}
