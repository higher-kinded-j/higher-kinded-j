package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.unwrap;
import static org.higherkindedj.hkt.state.StateKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Applicative implementation for {@code StateKind<S, ?>}.
 *
 * @param <S> The type of the state (fixed for this Applicative instance).
 */
public class StateApplicative<S> extends StateFunctor<S> implements Applicative<StateKind<S, ?>> {

  @Override
  public <A> @NonNull Kind<StateKind<S, ?>, A> of(@Nullable A value) {
    // 'of' creates a State that ignores the initial state and returns the value,
    // leaving the state unchanged. Use the static helper.
    return StateKindHelper.pure(value);
  }

  @Override
  public <A, B> @NonNull Kind<StateKind<S, ?>, B> ap(
      @NonNull Kind<StateKind<S, ?>, Function<A, B>> ff, @NonNull Kind<StateKind<S, ?>, A> fa) {

    State<S, Function<A, B>> stateF = unwrap(ff);
    State<S, A> stateA = unwrap(fa);

    // Implement ap for State: s0 -> { (f, s1) = stateF(s0); (a, s2) = stateA(s1); return (f(a),
    // s2); }
    State<S, B> stateB =
        State.of(
            initialState -> {
              // Run the function state computation
              State.StateTuple<S, Function<A, B>> resultF = stateF.run(initialState);
              Function<A, B> func = resultF.value();
              S stateS1 = resultF.state();

              // Run the value state computation with the intermediate state S1
              State.StateTuple<S, A> resultA = stateA.run(stateS1);
              A val = resultA.value();
              S stateS2 = resultA.state();

              // Apply the function to the value, return final state S2
              if (func == null) {
                // Decide behavior if function is null. Common is to throw, or propagate
                // conceptually.
                // Let's throw for now, or adjust based on desired semantics (e.g., return
                // default/null B).
                throw new NullPointerException("Function wrapped in State for 'ap' was null");
              }
              return new State.StateTuple<>(func.apply(val), stateS2);
            });

    return wrap(stateB);
  }
}
