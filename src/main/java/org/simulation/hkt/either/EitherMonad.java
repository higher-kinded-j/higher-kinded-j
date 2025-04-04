package org.simulation.hkt.either;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import static org.simulation.hkt.either.EitherKindHelper.*;

import java.util.function.Function;

/**
 * Monad implementation for EitherKind<L, R>.
 * This implementation is right-biased, meaning map and flatMap operate on the Right value.
 * The Left type 'L' is fixed for a given instance of this Monad.
 *
 * @param <L> The fixed type for the Left value.
 */
public class EitherMonad<L> extends EitherFunctor<L> implements Monad<EitherKind<L, ?>> {



  /**
   * Lifts a value into the 'Right' side of the Either context.
   * pure(value) is equivalent to Either.right(value) wrapped in the Kind.
   *
   * @param value The value to lift (becomes the Right value).
   * @param <R>   The type of the Right value.
   * @return An EitherKind representing Right(value).
   */
  @Override
  public <R> Kind<EitherKind<L, ?>, R> pure(R value) {
    // Create a Right instance and wrap it.
    return wrap(Either.right(value));
  }

  /**
   * Sequences operations for Either, operating on the Right value.
   * If 'ma' is Right(a), applies 'f' to 'a' to get a new EitherKind.
   * If 'ma' is Left(l), propagates the Left value unchanged.
   *
   * @param f  The function to apply to the Right value, returning a new EitherKind.
   * @param ma The input EitherKind.
   * @param <A> The type of the Right value in the input EitherKind.
   * @param <B> The type of the Right value in the resulting EitherKind.
   * @return The resulting EitherKind after applying 'f' or the original Left propagated.
   */
  @Override
  public <A, B> Kind<EitherKind<L, ?>, B> flatMap(Function<A, Kind<EitherKind<L, ?>, B>> f, Kind<EitherKind<L, ?>, A> ma) {
    // 1. Unwrap the input Kind to get the concrete Either<L, A>
    Either<L, A> eitherA = unwrap(ma);

    // 2. Use Either's built-in flatMap.
    //    The function given to Either.flatMap must return Either<L, B>.
    //    Our function 'f' returns Kind<EitherKind<L,?>, B>, so we unwrap its result inside the lambda.
    Either<L, B> resultEither = eitherA.flatMap(a -> {
      Kind<EitherKind<L, ?>, B> kindB = f.apply(a);
      return unwrap(kindB); // Unwrap Kind<..., B> to Either<L, B>
    });

    // 3. Wrap the final Either<L, B> back into the Kind system.
    return wrap(resultEither);
  }



}

