package org.simulation.hkt.either;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Functor;

import java.util.function.Function;

import static org.simulation.hkt.either.EitherKindHelper.unwrap;
import static org.simulation.hkt.either.EitherKindHelper.wrap;

public class EitherFunctor<L> implements Functor<EitherKind<L, ?>> {
  /**
   * Applies a function to the Right value if 'ma' is a Right.
   * If 'ma' is a Left, propagates the Left value unchanged.
   *
   * @param f  The function to apply to the Right value.
   * @param ma The input EitherKind.
   * @param <A> The type of the Right value in the input EitherKind.
   * @param <B> The type of the Right value in the resulting EitherKind.
   * @return A new EitherKind with the function applied to the Right value, or the original Left propagated.
   */
  @Override
  public <A, B> Kind<EitherKind<L, ?>, B> map(Function<A, B> f, Kind<EitherKind<L, ?>, A> ma) {
    // 1. Unwrap the input Kind to get the concrete Either<L, A>
    Either<L, A> eitherA = unwrap(ma);

    // 2. Use Either's built-in map, which is right-biased.
    Either<L, B> resultEither = eitherA.map(f);

    // 3. Wrap the resulting Either<L, B> back into the Kind system.
    return wrap(resultEither);
  }
}
