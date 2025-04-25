package org.simulation.hkt.state;

import static org.simulation.hkt.state.StateKindHelper.unwrap;
import static org.simulation.hkt.state.StateKindHelper.wrap;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;

/**
 * Functor implementation for StateKind<S, ?>.
 *
 * @param <S> The type of the state (fixed for this Functor instance).
 */
public class StateFunctor<S> implements Functor<StateKind<S, ?>> {

  @Override
  public <A, B> @NonNull Kind<StateKind<S, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<StateKind<S, ?>, A> fa) {
    // 1. Unwrap the input Kind<StateKind<S, ?>, A> to get the underlying State<S, A>
    State<S, A> stateA = unwrap(fa);

    // 2. Use the State's own map method.
    State<S, B> stateB = stateA.map(f);

    // 3. Wrap the resulting State<S, B> back into StateKind<S, B>
    return wrap(stateB);
  }
}
