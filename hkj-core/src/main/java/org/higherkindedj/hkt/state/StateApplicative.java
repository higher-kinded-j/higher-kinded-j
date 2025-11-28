// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

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

  private static final Class<StateApplicative> STATE_APPLICATIVE_CLASS = StateApplicative.class;

  /**
   * Lifts a value into a {@code State} context, represented as {@code Kind<StateKind.Witness<S>,
   * A>}. The state remains unchanged.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A {@code Kind<StateKind.Witness<S>, A>} representing {@code State.pure(value)}.
   */
  @Override
  public <A> Kind<StateKind.Witness<S>, A> of(@Nullable A value) {
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
   * @return A new {@code Kind<StateKind.Witness<S>, B>} resulting from the application.
   * @throws NullPointerException if {@code ff} or {@code fa} is null, or if the function wrapped in
   *     the State is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid {@code State} representations.
   */
  @Override
  public <A, B> Kind<StateKind.Witness<S>, B> ap(
      Kind<StateKind.Witness<S>, ? extends Function<A, B>> ff, Kind<StateKind.Witness<S>, A> fa) {

    Validation.kind().validateAp(ff, fa, STATE_APPLICATIVE_CLASS);

    State<S, ? extends Function<A, B>> stateF = STATE.narrow(ff);
    State<S, A> stateA = STATE.narrow(fa);

    State<S, B> stateB =
        State.of(
            initialState -> {
              StateTuple<S, ? extends Function<A, B>> resultF = stateF.run(initialState);
              Function<A, B> func = resultF.value();
              S stateS1 = resultF.state();

              StateTuple<S, A> resultA = stateA.run(stateS1);
              A val = resultA.value();
              S stateS2 = resultA.state();

              if (func == null) {
                throw new NullPointerException("Function wrapped in State for 'ap' was null");
              }
              return new StateTuple<>(func.apply(val), stateS2);
            });

    return STATE.widen(stateB);
  }
}
