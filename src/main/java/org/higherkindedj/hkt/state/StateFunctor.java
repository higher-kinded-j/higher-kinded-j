package org.higherkindedj.hkt.state;

import static org.higherkindedj.hkt.state.StateKindHelper.unwrap;
import static org.higherkindedj.hkt.state.StateKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Functor implementation for {@link State}, using {@link StateKind.Witness} as the HKT marker. An
 * instance of this class is specific to a state type {@code S}.
 *
 * @param <S> The type of the state (fixed for this Functor instance).
 * @see State
 * @see StateKind.Witness
 */
public class StateFunctor<S> implements Functor<StateKind.Witness> {

  /**
   * Maps a function over a {@code Kind<StateKind.Witness, A>}.
   *
   * @param <A> The input value type of the {@code State} computation.
   * @param <B> The output value type after applying the function.
   * @param f The non-null function to apply to the value of the {@code State} computation.
   * @param fa The {@code Kind<StateKind.Witness, A>} representing the {@code State<S, A>}
   *     computation.
   * @return A new {@code Kind<StateKind.Witness, B>} representing the {@code State<S, B>}
   *     computation.
   */
  @Override
  public <A, B> @NonNull Kind<StateKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<StateKind.Witness, A> fa) {
    State<S, A> stateA = unwrap(fa);
    State<S, B> stateB = stateA.map(f);
    return wrap(stateB);
  }
}
