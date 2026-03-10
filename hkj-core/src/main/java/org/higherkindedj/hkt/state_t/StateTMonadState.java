// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Provides a {@link MonadState} instance for {@link StateT}.
 *
 * <p>This class extends {@link StateTMonad} to inherit all monad operations and adds the
 * state-specific operations {@code get()} and {@code put()} from {@link MonadState}.
 *
 * @param <S> The type of the state.
 * @param <F> The witness type of the outer monad.
 * @see StateTMonad
 * @see MonadState
 * @see StateT
 */
public class StateTMonadState<S, F extends WitnessArity<TypeArity.Unary>> extends StateTMonad<S, F>
    implements MonadState<StateTKind.Witness<S, F>, S> {

  /**
   * Constructs a {@link StateTMonadState} instance.
   *
   * @param monadF The {@link Monad} instance for the underlying monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code monadF} is null.
   */
  public StateTMonadState(Monad<F> monadF) {
    super(monadF);
  }

  /**
   * Returns the current state.
   *
   * <p>This is equivalent to {@code StateT(s -> F<StateTuple(s, s)>)}.
   *
   * @return A {@code Kind} representing a {@code StateT} that yields the current state.
   */
  @Override
  public Kind<StateTKind.Witness<S, F>, S> get() {
    Function<S, Kind<F, StateTuple<S, S>>> runFn = s -> monadF.of(StateTuple.of(s, s));
    return StateT.<S, F, S>create(runFn, monadF);
  }

  /**
   * Replaces the current state with the given value.
   *
   * <p>This is equivalent to {@code StateT(_ -> F<StateTuple(s, Unit.INSTANCE)>)}.
   *
   * @param s The new state value.
   * @return A {@code Kind} representing a {@code StateT} that sets the state and yields {@link
   *     Unit#INSTANCE}.
   */
  @Override
  public Kind<StateTKind.Witness<S, F>, Unit> put(S s) {
    Function<S, Kind<F, StateTuple<S, Unit>>> runFn =
        _ -> monadF.of(StateTuple.of(s, Unit.INSTANCE));
    return StateT.<S, F, Unit>create(runFn, monadF);
  }
}
