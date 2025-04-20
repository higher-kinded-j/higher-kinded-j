package org.simulation.hkt.either;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;
import org.simulation.hkt.MonadError;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;

import static org.simulation.hkt.either.EitherKindHelper.*;

import java.util.function.Function;

/**
 * Monad and MonadError implementation for EitherKind<L, R>.
 * This implementation is right-biased, meaning map and flatMap operate on the Right value.
 * The Left type 'L' is fixed for a given instance of this Monad and represents the Error type 'E'.
 *
 * @param <L> The fixed type for the Left value (the Error type).
 */

public class EitherMonad<L> extends EitherFunctor<L> implements MonadError<EitherKind<L, ?>, L> {



  /**
   * Lifts a value into the 'Right' side of the Either context.
   * of(value) is equivalent to Either.right(value) wrapped in the Kind.
   *
   * @param value The value to lift (becomes the Right value).
   * @param <R>   The type of the Right value.
   * @return An EitherKind representing Right(value).
   */
  @Override
  public <R> Kind<EitherKind<L, ?>, R> of(R value) {
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


  @Override
  public <A, B> Kind<EitherKind<L, ?>, B> ap(Kind<EitherKind<L, ?>, Function<A, B>> ff, Kind<EitherKind<L, ?>, A> fa) {
    Either<L, Function<A, B>> eitherF = unwrap(ff);
    Either<L, A> eitherA = unwrap(fa);

    // If eitherF is Left, return it.
    // If eitherA is Left, return it.
    // If both are Right, apply the function.
    // Either's flatMap/map handles the Left propagation.
    Either<L, B> resultEither = eitherF.flatMap(f -> eitherA.map(f)); // flatMap on function, map on value

    return wrap(resultEither);
  }

  @Override
  public <A, B, C, R> Kind<EitherKind<L, ?>, R> map3(
          Kind<EitherKind<L, ?>, A> fa,
          Kind<EitherKind<L, ?>, B> fb,
          Kind<EitherKind<L, ?>, C> fc,
          Function3<A, B, C, R> f) {

    // 1. Unwrap all inputs once
    Either<L, A> eitherA = EitherKindHelper.unwrap(fa);
    if (eitherA.isLeft()) {
      // Short-circuit: return the first Left found, correctly typed.
      // Need to cast because the Kind signature expects R, but we return L.
      // This cast is safe because the context is EitherKind<L, ?>
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fa;
      return leftResult;
    }

    Either<L, B> eitherB = EitherKindHelper.unwrap(fb);
    if (eitherB.isLeft()) {
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fb;
      return leftResult;
    }

    Either<L, C> eitherC = EitherKindHelper.unwrap(fc);
    if (eitherC.isLeft()) {
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fc;
      return leftResult;
    }

    // 2. If all are Right, apply the function directly
    A a = eitherA.getRight(); // Safe due to isLeft() checks
    B b = eitherB.getRight();
    C c = eitherC.getRight();
    R result = f.apply(a, b, c);

    // 3. Wrap the final result
    return EitherKindHelper.wrap(Either.right(result));
  }

  @Override
  public <A, B, C, D, R> Kind<EitherKind<L, ?>, R> map4(
          Kind<EitherKind<L, ?>, A> fa,
          Kind<EitherKind<L, ?>, B> fb,
          Kind<EitherKind<L, ?>, C> fc,
          Kind<EitherKind<L, ?>, D> fd,
          Function4<A, B, C, D, R> f) {

    // 1. Unwrap all inputs once
    Either<L, A> eitherA = EitherKindHelper.unwrap(fa);
    if (eitherA.isLeft()) {
      // Short-circuit: return the first Left found, correctly typed.
      // Need to cast because the Kind signature expects R, but we return L.
      // This cast is safe because the context is EitherKind<L, ?>
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fa;
      return leftResult;
    }

    Either<L, B> eitherB = EitherKindHelper.unwrap(fb);
    if (eitherB.isLeft()) {
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fb;
      return leftResult;
    }

    Either<L, C> eitherC = EitherKindHelper.unwrap(fc);
    if (eitherC.isLeft()) {
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fc;
      return leftResult;
    }

    Either<L, D> eitherD = EitherKindHelper.unwrap(fd);
    if (eitherD.isLeft()) {
      @SuppressWarnings("unchecked")
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fd;
      return leftResult;
    }

    // 2. If all are Right, apply the function directly
    A a = eitherA.getRight(); // Safe due to isLeft() checks
    B b = eitherB.getRight();
    C c = eitherC.getRight();
    D d = eitherD.getRight();
    R result = f.apply(a, b, c, d);

    // 3. Wrap the final result
    return EitherKindHelper.wrap(Either.right(result));
  }

  // --- MonadError Methods ---

  /**
   * Lifts an error L into the Either context as Left(error).
   *
   * @param error The error value (type L).
   * @param <A>   The phantom type parameter for the Right side.
   * @return An EitherKind representing Left(error).
   */
  @Override
  public <A> Kind<EitherKind<L, ?>, A> raiseError(L error) {
    return wrap(Either.left(error)); //
  }

  /**
   * Handles an error L within the Either context.
   * If 'ma' is Right, it's returned unchanged.
   * If 'ma' is Left(e), the 'handler' function is applied to 'e'.
   *
   * @param ma      The EitherKind value.
   * @param handler Function L -> Kind<EitherKind<L, ?>, A> to handle the error.
   * @param <A>     The type of the Right value.
   * @return Original Kind if Right, or result of handler applied to Left value.
   */
  @Override
  public <A> Kind<EitherKind<L, ?>, A> handleErrorWith(Kind<EitherKind<L, ?>, A> ma, Function<L, Kind<EitherKind<L, ?>, A>> handler) {
    Either<L, A> either = unwrap(ma); //

    // Use fold to handle both cases
    return either.fold( //
            leftValue -> handler.apply(leftValue), // If Left, apply handler
            rightValue -> ma // If Right, return original Kind
    );

        /* Alternative using isLeft:
        if (either.isLeft()) {
            L error = either.getLeft();
            return handler.apply(error);
        } else {
            return ma; // It's Right, return unchanged
        }
        */
  }
}

