package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link Functor} interface for {@link Try}, using {@link TryKind.Witness}.
 *
 * @see Try
 * @see TryKind.Witness
 */
public class TryFunctor implements Functor<TryKind.Witness> {

  /**
   * Maps a function over a {@code Kind<TryKind.Witness, A>}.
   *
   * @param <A> The input type of the {@code Try}.
   * @param <B> The output type after applying the function.
   * @param f The function to apply if the {@code Try} is a {@link Try.Success}.
   * @param fa The {@code Kind<TryKind.Witness, A>} to map over.
   * @return A new {@code Kind<TryKind.Witness, B>} representing the result of the map operation.
   */
  @Override
  public <A, B> @NonNull Kind<TryKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<TryKind.Witness, A> fa) {
    Try<A> tryA = unwrap(fa);
    Try<B> resultTry = tryA.map(f);
    return wrap(resultTry);
  }
}
