// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
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

  /** Error message for when a null {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for StateT";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a StateT: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG =
      "StateT instance to widen cannot be null.";

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
    Objects.requireNonNull(stateT, INVALID_KIND_TYPE_NULL_MSG);
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
   * @throws KindUnwrapException if {@code kind} is {@code null} or not a valid {@link StateT}
   *     instance.
   */
  @Override
  public <S, F, A> StateT<S, F, A> narrow(@Nullable Kind<StateTKind.Witness<S, F>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }
    if (kind instanceof StateT) {
      return (StateT<S, F, A>) kind;
    }
    throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
  }

  /**
   * Creates a {@link StateT} instance directly from its core state-transition function.
   *
   * @param runStateTFn The non-null function {@code S -> Kind<F, StateTuple<S, A>>}.
   * @param monadF The non-null {@link Monad} instance for the underlying monad {@code F}.
   * @return A new, non-null {@link StateT} instance.
   */
  public <S, F, A> StateT<S, F, A> stateT(
      Function<S, Kind<F, StateTuple<S, A>>> runStateTFn, Monad<F> monadF) {
    return StateT.create(runStateTFn, monadF);
  }

  /**
   * Lifts a computation {@code Kind<F, A>} from the underlying monad {@code F} into a {@link
   * StateT} context.
   *
   * @param monadF The non-null {@link Monad} instance for {@code F}.
   * @param fa The non-null computation {@code Kind<F, A>}.
   * @return A new, non-null {@link StateT} instance wrapping the lifted computation.
   */
  public <S, F, A> StateT<S, F, A> lift(Monad<F> monadF, Kind<F, A> fa) {
    Objects.requireNonNull(monadF, "Monad<F> for lift cannot be null.");
    Objects.requireNonNull(fa, "Kind<F, A> to lift cannot be null.");
    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.map(a -> StateTuple.of(s, a), fa);
    return stateT(runFn, monadF);
  }

  // --- Runner methods ---

  /**
   * Runs the {@link StateT} computation with an initial state.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state.
   * @return A {@code Kind<F, StateTuple<S, A>>}.
   */
  public <S, F, A> Kind<F, StateTuple<S, A>> runStateT(
      Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    return this.narrow(kind).runStateT(initialState);
  }

  /**
   * Runs the {@link StateT} computation and extracts the final value.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state.
   * @return A {@code Kind<F, A>} representing the final value.
   */
  public <S, F, A> Kind<F, A> evalStateT(Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    return this.narrow(kind).evalStateT(initialState);
  }

  /**
   * Runs the {@link StateT} computation and extracts the final state.
   *
   * @param kind The non-null {@code StateT} computation, as a {@code Kind}.
   * @param initialState The initial state.
   * @return A {@code Kind<F, S>} representing the final state.
   */
  public <S, F, A> Kind<F, S> execStateT(Kind<StateTKind.Witness<S, F>, A> kind, S initialState) {
    return this.narrow(kind).execStateT(initialState);
  }
}
