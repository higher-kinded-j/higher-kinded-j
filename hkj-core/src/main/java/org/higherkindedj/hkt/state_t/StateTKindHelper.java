// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.state.StateTuple;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link StateTConverterOps} for widen/narrow operations, and providing
 * additional factory and utility instance methods for {@link StateT} types.
 *
 * <p>Access these operations via the singleton {@code STATE_T}. For example: {@code
 * StateTKindHelper.STATE_T.widen(myStateTInstance);}
 */
public enum StateTKindHelper implements StateTConverterOps {
  STATE_T;

  public static final String TYPE_NAME = "StateT";

  /**
   * Widens a concrete {@link StateT} instance into its higher-kinded representation, {@code
   * Kind<StateTKind.Witness<S, F>, A>}. Implements {@link StateTConverterOps#widen}.
   *
   * <p>Since {@link StateT} already implements {@link StateTKind}, this method primarily serves as
   * a type-safe upcast.
   *
   * @param stateT The non-null {@link StateT} instance to widen.
   * @param <S> The state type of the {@code StateT}.
   * @param <F> The higher-kinded type witness for the underlying monad of {@code StateT}.
   * @param <A> The value type of the {@code StateT}.
   * @return The provided {@code stateT} instance, cast to its {@link Kind} representation.
   * @throws NullPointerException if {@code stateT} is {@code null}.
   */
  @Override
  public <S, F, A> Kind<StateTKind.Witness<S, F>, A> widen(StateT<S, F, A> stateT) {
    requireNonNullForWiden(stateT, TYPE_NAME);
    return stateT;
  }

  /**
   * Narrows a higher-kinded representation {@code Kind<StateTKind.Witness<S, F>, A>} back to a
   * concrete {@link StateT} instance. Implements {@link StateTConverterOps#narrow}.
   *
   * @param kind The {@link Kind} instance to narrow. Can be null.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return The concrete {@link StateT} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null} or
   *     not a valid {@link StateT} instance.
   */
  @Override
  public <S, F, A> StateT<S, F, A> narrow(@Nullable Kind<StateTKind.Witness<S, F>, A> kind) {
    return narrowKindWithTypeCheck(kind, StateT.class, TYPE_NAME);
  }

  /**
   * Creates a {@link StateT} instance directly from its core state-transition function.
   *
   * @param runStateTFn The non-null function {@code S -> Kind<F, StateTuple<S, A>>}.
   * @param monadF The non-null {@link Monad} instance for the underlying monad {@code F}.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A new, non-null {@link StateT} instance.
   * @throws NullPointerException if {@code runStateTFn} or {@code monadF} is null.
   */
  public <S, F, A> StateT<S, F, A> stateT(
      Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF) {
    requireNonNullFunction(runStateTFn, "runStateTFn for stateT");
    requireValidOuterMonad(monadF, "stateT");
    return StateT.create(runStateTFn, monadF);
  }

  /**
   * Lifts a computation {@code Kind<F, A>} from the underlying monad {@code F} into a {@link
   * StateT} context.
   *
   * @param monadF The non-null {@link Monad} instance for {@code F}.
   * @param fa The non-null computation {@code Kind<F, A>}.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A new, non-null {@link StateT} instance wrapping the lifted computation.
   * @throws NullPointerException if {@code monadF} or {@code fa} is null.
   */
  public <S, F, A> StateT<S, F, A> lift(Monad<F> monadF, Kind<F, A> fa) {
    requireValidOuterMonad(monadF, "lift");
    requireNonNullKind(fa, "Kind fa for lift");

    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.map(a -> StateTuple.of(s, a), fa);
    return stateT(runFn, monadF);
  }

  // --- Runner methods ---

  /**
   * Runs the {@link StateT} computation with an initial state.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A {@code Kind<F, StateTuple<S, A>>}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is not a valid
   *     {@code StateT} representation.
   * @throws NullPointerException if {@code kind} is null.
   */
  public <S, F, A> Kind<F, StateTuple<S, A>> runStateT(
      Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    requireNonNullKind(kind, "Kind for runStateT");
    // Note: initialState validation is handled by StateTuple constructor if needed
    return this.narrow(kind).runStateT(initialState);
  }

  /**
   * Runs the {@link StateT} computation and extracts the final value.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A {@code Kind<F, A>} representing the final value.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is not a valid
   *     {@code StateT} representation.
   * @throws NullPointerException if {@code kind} is null.
   */
  public <S, F, A> Kind<F, A> evalStateT(Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    requireNonNullKind(kind, "Kind for evalStateT");
    return this.narrow(kind).evalStateT(initialState);
  }

  /**
   * Runs the {@link StateT} computation and extracts the final state.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for the underlying monad.
   * @param <A> The value type.
   * @return A {@code Kind<F, S>} representing the final state.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is not a valid
   *     {@code StateT} representation.
   * @throws NullPointerException if {@code kind} is null.
   */
  public <S, F, A> Kind<F, S> execStateT(Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    requireNonNullKind(kind, "Kind for execStateT");
    return this.narrow(kind).execStateT(initialState);
  }
}
