// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;
import static org.higherkindedj.hkt.util.validation.Operation.OF;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Applicative implementation for {@link State}, using {@link StateKind.Witness} as the HKT marker.
 * An instance of this class is specific to a state type {@code S}. It extends {@link StateFunctor}.
 *
 * @param <S> The type of the state (fixed for this Applicative instance).
 * @see State
 * @see StateKind.Witness
 * @see StateFunctor
 */
public class StateApplicative<S> extends StateFunctor<S>
    implements Applicative<StateKind.Witness<S>> {

  /**
   * Lifts a value into a {@code State} context, represented as {@code Kind<StateKind.Witness<S>,
   * A>}. The state remains unchanged.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Must not be {@code null}.
   * @return A {@code Kind<StateKind.Witness<S>, A>} representing {@code State.pure(value)}.
   * @throws NullPointerException if {@code value} is null.
   */
  @Override
  @SuppressWarnings("NullableProblems") // State forbids null
  public <A> Kind<StateKind.Witness<S>, A> of(A value) {
    Validation.coreType().requireValue(value, StateApplicative.class, OF);
    return STATE.pure(value);
  }

  /**
   * Applies a function wrapped in a {@code Kind<StateKind.Witness<S>, Function<A, B>>}
   * (representing a {@code State<S, Function<A,B>>}) to a value wrapped in a {@code
   * Kind<StateKind.Witness<S>, A>} (representing a {@code State<S, A>}). The state is threaded
   * through both computations.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<StateKind.Witness<S>, Function<A, B>>} containing the function.
   * @param fa The {@code Kind<StateKind.Witness<S>, A>} containing the value.
   * @return A new {@code Kind<StateKind.Witness<S>, B>} resulting from the application. When run,
   *     it throws {@link NullPointerException} if the wrapped function returns null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid {@code State} representations.
   */
  @Override
  public <A, B> Kind<StateKind.Witness<S>, B> ap(
      Kind<StateKind.Witness<S>, ? extends Function<A, B>> ff, Kind<StateKind.Witness<S>, A> fa) {

    Validation.kind().validateAp(ff, fa);

    State<S, ? extends Function<A, B>> stateF = STATE.narrow(ff);
    State<S, A> stateA = STATE.narrow(fa);

    State<S, B> stateB =
        State.of(
            initialState -> {
              StateTuple<S, ? extends Function<A, B>> resultF = stateF.run(initialState);
              return stateA.map(resultF.value()).run(resultF.state());
            });

    return STATE.widen(stateB);
  }
}
