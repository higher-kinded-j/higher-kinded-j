package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.unwrap;
// import static org.higherkindedj.hkt.state.StateKindHelper.wrap; // wrap is inherited or called
// via pure

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
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
public class StateApplicative<S> extends StateFunctor<S> implements Applicative<StateKind.Witness> {

  /**
   * Lifts a value into a {@code State} context, represented as {@code Kind<StateKind.Witness, A>}.
   * The state remains unchanged.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A {@code Kind<StateKind.Witness, A>} representing {@code State.pure(value)}.
   */
  @Override
  public <A> @NonNull Kind<StateKind.Witness, A> of(@Nullable A value) {
    return StateKindHelper.pure(value);
  }

  /**
   * Applies a function wrapped in a {@code Kind<StateKind.Witness, Function<A, B>>} (representing a
   * {@code State<S, Function<A,B>>}) to a value wrapped in a {@code Kind<StateKind.Witness, A>}
   * (representing a {@code State<S, A>}). The state is threaded through both computations.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<StateKind.Witness, Function<A, B>>} containing the function.
   * @param fa The {@code Kind<StateKind.Witness, A>} containing the value.
   * @return A new {@code Kind<StateKind.Witness, B>} resulting from the application.
   */
  @Override
  public <A, B> @NonNull Kind<StateKind.Witness, B> ap(
      @NonNull Kind<StateKind.Witness, Function<A, B>> ff, @NonNull Kind<StateKind.Witness, A> fa) {

    State<S, Function<A, B>> stateF = unwrap(ff);
    State<S, A> stateA = unwrap(fa);

    State<S, B> stateB =
        State.of(
            initialState -> {
              State.StateTuple<S, Function<A, B>> resultF = stateF.run(initialState);
              Function<A, B> func = resultF.value();
              S stateS1 = resultF.state();

              State.StateTuple<S, A> resultA = stateA.run(stateS1);
              A val = resultA.value();
              S stateS2 = resultA.state();

              if (func == null) {
                throw new NullPointerException("Function wrapped in State for 'ap' was null");
              }
              return new State.StateTuple<>(func.apply(val), stateS2);
            });

    return StateKindHelper.wrap(stateB); // Use StateKindHelper.wrap for consistency
  }
}
