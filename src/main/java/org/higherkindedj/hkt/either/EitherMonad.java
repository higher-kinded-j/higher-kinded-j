package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Monad and MonadError implementation for {@code EitherKind<L, R>}. This implementation is
 * right-biased, meaning map and flatMap operate on the Right value. The Left type 'L' is fixed for
 * a given instance of this Monad and represents the Error type 'E'.
 *
 * @param <L> The fixed type for the Left value (the Error type).
 */
public class EitherMonad<L> extends EitherFunctor<L> implements MonadError<EitherKind<L, ?>, L> {

  /**
   * Lifts a value into the 'Right' side of the Either context. of(value) is equivalent to
   * Either.right(value) wrapped in the Kind.
   *
   * @param value The value to lift (becomes the Right value). (Nullable, as Either.right allows
   *     null)
   * @param <R> The type of the Right value.
   * @return An EitherKind representing Right(value). (NonNull)
   */
  @Override
  public <R> @NonNull Kind<EitherKind<L, ?>, R> of(@Nullable R value) {
    // Create a Right instance and wrap it.
    return wrap(Either.right(value));
  }

  /**
   * Sequences operations for Either, operating on the Right value. If 'ma' is Right(a), applies 'f'
   * to 'a' to get a new EitherKind. If 'ma' is Left(l), propagates the Left value unchanged.
   *
   * @param f The function to apply to the Right value, returning a new EitherKind. (NonNull)
   * @param ma The input EitherKind. (NonNull)
   * @param <A> The type of the Right value in the input EitherKind.
   * @param <B> The type of the Right value in the resulting EitherKind.
   * @return The resulting EitherKind after applying 'f' or the original Left propagated. (NonNull)
   */
  @Override
  public <A, B> @NonNull Kind<EitherKind<L, ?>, B> flatMap(
      @NonNull Function<A, Kind<EitherKind<L, ?>, B>> f, @NonNull Kind<EitherKind<L, ?>, A> ma) {
    // 1. Unwrap the input Kind to get the concrete Either<L, A>
    Either<L, A> eitherA = unwrap(ma); // Handles null/invalid ma

    // 2. Use Either's built-in flatMap.
    //    The function given to Either.flatMap must return Either<L, B>.
    //    Our function 'f' returns Kind<EitherKind<L,?>, B>, so we unwrap its result inside the
    // lambda.
    Either<L, B> resultEither =
        eitherA.flatMap(
            a -> {
              Kind<EitherKind<L, ?>, B> kindB = f.apply(a); // f requires NonNull
              return unwrap(kindB); // Unwrap Kind<..., B> to Either<L, B>
            });

    // 3. Wrap the final Either<L, B> back into the Kind system.
    return wrap(resultEither); // wrap requires non-null resultEither
  }

  @Override
  public <A, B> @NonNull Kind<EitherKind<L, ?>, B> ap(
      @NonNull Kind<EitherKind<L, ?>, Function<A, B>> ff, @NonNull Kind<EitherKind<L, ?>, A> fa) {
    Either<L, Function<A, B>> eitherF = unwrap(ff); // Handles null/invalid ff
    Either<L, A> eitherA = unwrap(fa); // Handles null/invalid fa

    // If eitherF is Left, return it.
    // If eitherA is Left, return it.
    // If both are Right, apply the function.
    // Either's flatMap/map handles the Left propagation.
    Either<L, B> resultEither =
        eitherF.flatMap(f -> eitherA.map(f)); // flatMap on function, map on value

    return wrap(resultEither);
  }

  @Override
  public <A, B, C, R> @NonNull Kind<EitherKind<L, ?>, R> map3(
      @NonNull Kind<EitherKind<L, ?>, A> fa,
      @NonNull Kind<EitherKind<L, ?>, B> fb,
      @NonNull Kind<EitherKind<L, ?>, C> fc,
      @NonNull Function3<A, B, C, R> f) {

    // 1. Unwrap all inputs once
    Either<L, A> eitherA = unwrap(fa);
    if (eitherA.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fa;
      return leftResult;
    }

    Either<L, B> eitherB = unwrap(fb);
    if (eitherB.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fb;
      return leftResult;
    }

    Either<L, C> eitherC = unwrap(fc);
    if (eitherC.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fc;
      return leftResult;
    }

    // 2. If all are Right, apply the function directly
    // Note: getRight() can return null if R is nullable, function f needs to handle this.
    A a = eitherA.getRight();
    B b = eitherB.getRight();
    C c = eitherC.getRight();
    R result = f.apply(a, b, c);

    // 3. Wrap the final result - result might be null, Either.right handles it
    return wrap(Either.right(result));
  }

  @Override
  public <A, B, C, D, R> @NonNull Kind<EitherKind<L, ?>, R> map4(
      @NonNull Kind<EitherKind<L, ?>, A> fa,
      @NonNull Kind<EitherKind<L, ?>, B> fb,
      @NonNull Kind<EitherKind<L, ?>, C> fc,
      @NonNull Kind<EitherKind<L, ?>, D> fd,
      @NonNull Function4<A, B, C, D, R> f) {

    // 1. Unwrap all inputs once
    Either<L, A> eitherA = unwrap(fa);
    if (eitherA.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fa;
      return leftResult;
    }

    Either<L, B> eitherB = unwrap(fb);
    if (eitherB.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fb;
      return leftResult;
    }

    Either<L, C> eitherC = unwrap(fc);
    if (eitherC.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fc;
      return leftResult;
    }

    Either<L, D> eitherD = unwrap(fd);
    if (eitherD.isLeft()) {
      @SuppressWarnings("unchecked") // Safe cast for error propagation
      Kind<EitherKind<L, ?>, R> leftResult = (Kind<EitherKind<L, ?>, R>) fd;
      return leftResult;
    }

    // 2. If all are Right, apply the function directly
    A a = eitherA.getRight();
    B b = eitherB.getRight();
    C c = eitherC.getRight();
    D d = eitherD.getRight();
    R result = f.apply(a, b, c, d);

    // 3. Wrap the final result - result might be null, Either.right handles it
    return wrap(Either.right(result));
  }

  // --- MonadError Methods ---

  /**
   * Lifts an error L into the Either context as Left(error).
   *
   * @param error The error value (type L). (Nullable)
   * @param <A> The phantom type parameter for the Right side.
   * @return An EitherKind representing Left(error). (NonNull)
   */
  @Override
  public <A> @NonNull Kind<EitherKind<L, ?>, A> raiseError(@Nullable L error) {
    return wrap(Either.left(error)); // Either.left accepts null
  }

  /**
   * Handles an error L within the Either context. If 'ma' is Right, it's returned unchanged. If
   * 'ma' is Left(e), the 'handler' function is applied to 'e'.
   *
   * @param ma The EitherKind value. (NonNull)
   * @param handler Function L -> {@code Kind<EitherKind<L, ?>, A>} to handle the error. (NonNull)
   * @param <A> The type of the Right value.
   * @return Original Kind if Right, or result of handler applied to Left value. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<EitherKind<L, ?>, A> handleErrorWith(
      @NonNull Kind<EitherKind<L, ?>, A> ma,
      @NonNull Function<L, Kind<EitherKind<L, ?>, A>> handler) {
    Either<L, A> either = unwrap(ma); // Handles null/invalid ma

    // Use fold to handle both cases
    return either.fold( //
        leftValue -> handler.apply(leftValue), // If Left, apply handler (handler assumed NonNull)
        rightValue -> ma // If Right, return original Kind (ma is NonNull)
        );

    /* Alternative using isLeft:
    if (either.isLeft()) {
        L error = either.getLeft(); // Error can be null
        return handler.apply(error); // Handler must return NonNull Kind
    } else {
        return ma; // It's Right, return unchanged (ma is NonNull)
    }
    */
  }
}
