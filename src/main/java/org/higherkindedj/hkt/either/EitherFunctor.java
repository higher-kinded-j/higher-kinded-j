package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.unwrap;
import static org.higherkindedj.hkt.either.EitherKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

public class EitherFunctor<L> implements Functor<EitherKind<L, ?>> {
  /**
   * Applies a function to the Right value if 'ma' is a Right. If 'ma' is a Left, propagates the
   * Left value unchanged.
   *
   * @param f The function to apply to the Right value. (NonNull)
   * @param ma The input EitherKind. (NonNull)
   * @param <A> The type of the Right value in the input EitherKind.
   * @param <B> The type of the Right value in the resulting EitherKind.
   * @return A new EitherKind with the function applied to the Right value, or the original Left
   *     propagated. (NonNull)
   */
  @Override
  public <A, B> @NonNull Kind<EitherKind<L, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<EitherKind<L, ?>, A> ma) {
    // 1. Unwrap the input Kind to get the concrete Either<L, A>
    Either<L, A> eitherA = unwrap(ma); // unwrap handles null/invalid ma

    // 2. Use Either's built-in map, which is right-biased.
    Either<L, B> resultEither = eitherA.map(f); // map requires non-null f

    // 3. Wrap the resulting Either<L, B> back into the Kind system.
    return wrap(resultEither); // wrap requires non-null resultEither
  }
}
