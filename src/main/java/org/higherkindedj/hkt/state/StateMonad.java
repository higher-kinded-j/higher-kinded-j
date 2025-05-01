package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.unwrap;
import static org.higherkindedj.hkt.state.StateKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

/**
 * Monad implementation for StateKind<S, ?>.
 *
 * @param <S> The type of the state (fixed for this Monad instance).
 */
public class StateMonad<S> extends StateApplicative<S> implements Monad<StateKind<S, ?>> {

  @Override
  public <A, B> @NonNull Kind<StateKind<S, ?>, B> flatMap(
      @NonNull Function<A, Kind<StateKind<S, ?>, B>> f, @NonNull Kind<StateKind<S, ?>, A> ma) {

    // 1. Unwrap the initial Kind to get the State<S, A>
    State<S, A> stateA = unwrap(ma);

    // 2. Use the State's own flatMap method.
    //    The function given to State.flatMap must return State<S, B>.
    //    Our function 'f' returns Kind<StateKind<S, ?>, B>, so we unwrap its result inside the
    // lambda.
    State<S, B> stateB =
        stateA.flatMap(
            a -> {
              Kind<StateKind<S, ?>, B> kindB = f.apply(a);
              // Adapt f to return State<S, B> by unwrapping the Kind
              return unwrap(kindB);
            });

    // 3. Wrap the resulting State<S, B> back into the Kind system.
    return wrap(stateB);
  }
}
