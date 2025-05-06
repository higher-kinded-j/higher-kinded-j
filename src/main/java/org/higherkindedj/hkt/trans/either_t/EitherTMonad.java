package org.higherkindedj.hkt.trans.either_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} interface for the {@link EitherTKind} monad transformer.
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F} to operate. The
 * 'left' type {@code L} of the inner {@code Either} serves as the error type for this {@code
 * MonadError} instance. It uses {@link EitherTKindHelper} to convert between the {@code Kind}
 * representation ({@code EitherTKind<F, L, ?>}) and the concrete {@link EitherT} type.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind<?>}).
 * @param <L> The type of the 'left' (error) value in the inner {@link Either}.
 */
public class EitherTMonad<F, L> implements MonadError<EitherTKind<F, L, ?>, L> {

  private final @NonNull Monad<F> outerMonad;

  /**
   * Constructs an {@code EitherTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public EitherTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null for EitherTMonad");
  }

  /**
   * Lifts a 'right' value {@code r} into the {@code EitherTKind<F, L, ?>} context. This results in
   * {@code F<Right(r)>}.
   *
   * @param <R> The type of the 'right' value.
   * @param r The 'right' value to lift. Can be null if {@code R} is nullable.
   * @return A {@code Kind<EitherTKind<F, L, ?>, R>} representing the lifted 'right' value.
   */
  @Override
  public <R> @NonNull Kind<EitherTKind<F, L, ?>, R> of(@Nullable R r) {
    // Create a concrete EitherT holding F<Right(r)>
    EitherT<F, L, R> concreteEitherT = EitherT.right(outerMonad, r);
    // Wrap using the helper
    return EitherTKindHelper.wrap(concreteEitherT);
  }

  /**
   * Maps a function {@code f} over the 'right' value within a {@code Kind<EitherTKind<F, L, ?>,
   * R_IN>}. If the wrapped {@code Kind<F, Either<L, R_IN>>} contains a 'right' value, the function
   * is applied. If it contains a 'left' value, the 'left' value is propagated. The transformation
   * is applied within the context of the outer monad {@code F}.
   *
   * @param <R_IN> The original type of the 'right' value.
   * @param <R_OUT> The new type of the 'right' value after applying the function.
   * @param f The function to apply to the 'right' value. Must not be null.
   * @param fa The {@code Kind<EitherTKind<F, L, ?>, R_IN>} to map over. Must not be null.
   * @return A new {@code Kind<EitherTKind<F, L, ?>, R_OUT>} with the function applied to the
   *     'right' side.
   */
  @Override
  public <R_IN, R_OUT> @NonNull Kind<EitherTKind<F, L, ?>, R_OUT> map(
      @NonNull Function<R_IN, R_OUT> f, @NonNull Kind<EitherTKind<F, L, ?>, R_IN> fa) {
    Objects.requireNonNull(f, "Function f cannot be null for map");
    Objects.requireNonNull(fa, "Kind fa cannot be null for map");

    // Unwrap to the concrete EitherT
    EitherT<F, L, R_IN> eitherT = EitherTKindHelper.unwrap(fa);

    // Map over the F<Either<L, R_IN>>:
    // If Either is Right(r_in), it becomes Right(f.apply(r_in)).
    // If Either is Left(l), it remains Left(l) (typed as Either<L, R_OUT>).
    Kind<F, Either<L, R_OUT>> newValue = outerMonad.map(either -> either.map(f), eitherT.value());

    // Wrap the result
    return EitherTKindHelper.wrap(EitherT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<EitherTKind<F, L, ?>, Function<R_IN, R_OUT>>} to a
   * 'right' value wrapped in {@code Kind<EitherTKind<F, L, ?>, R_IN>}.
   *
   * <p>The behavior is as follows:
   *
   * <ul>
   *   <li>If both the function and value are 'right' (i.e., {@code F<Right(Function)>} and {@code
   *       F<Right(Value)>}), the function is applied, resulting in {@code F<Right(Result)>}.
   *   <li>If the function is 'left' (i.e., {@code F<Left(L)>}), that 'left' is propagated.
   *   <li>If the function is 'right' but the value is 'left' (i.e., {@code F<Left(L)>}), that
   *       'left' is propagated.
   *   <li>This logic is handled by {@code flatMap} and {@code map} on the inner {@link Either} and
   *       the outer monad {@code F}.
   * </ul>
   *
   * @param <R_IN> The type of the input 'right' value.
   * @param <R_OUT> The type of the result 'right' value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped 'right' value. Must not be null.
   * @return A new {@code Kind<EitherTKind<F, L, ?>, R_OUT>} representing the application.
   */
  @Override
  public <R_IN, R_OUT> @NonNull Kind<EitherTKind<F, L, ?>, R_OUT> ap(
      @NonNull Kind<EitherTKind<F, L, ?>, Function<R_IN, R_OUT>> ff,
      @NonNull Kind<EitherTKind<F, L, ?>, R_IN> fa) {
    Objects.requireNonNull(ff, "Kind ff cannot be null for ap");
    Objects.requireNonNull(fa, "Kind fa cannot be null for ap");

    // Unwrap to concrete types
    EitherT<F, L, Function<R_IN, R_OUT>> funcT = EitherTKindHelper.unwrap(ff);
    EitherT<F, L, R_IN> valT = EitherTKindHelper.unwrap(fa);

    Kind<F, Either<L, R_OUT>> resultValue =
        outerMonad.flatMap( // F<Either<L, Function<R_IN, R_OUT>>>
            eitherF -> // Either<L, Function<R_IN, R_OUT>>
            outerMonad.map( // F<Either<L, R_IN>>
                    eitherA -> // Either<L, R_IN>
                    eitherF.flatMap( // Apply function if eitherF is Right
                            eitherA
                                ::map), // Maps R_IN to R_OUT if eitherA is Right, else propagates
                    // Left
                    valT.value()),
            funcT.value());

    return EitherTKindHelper.wrap(EitherT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<EitherTKind<F, L, ?>, R_OUT>} to the
   * 'right' value within a {@code Kind<EitherTKind<F, L, ?>, R_IN>}, and flattens the result.
   *
   * <p>If the input {@code ma} contains {@code F<Right(r_in)>}, {@code f(r_in)} is invoked. The
   * resulting {@code Kind<EitherTKind<F, L, ?>, R_OUT>} (which internally is {@code F<Either<L,
   * R_OUT>>}) becomes the result. If {@code ma} contains {@code F<Left(l)>}, or if the inner {@code
   * Either} is {@code Left(l)}, that 'left' value is propagated as {@code F<Left(l)>} within the
   * {@code EitherTKind} context.
   *
   * @param <R_IN> The original type of the 'right' value.
   * @param <R_OUT> The type of the 'right' value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<EitherTKind<F, L, ?>, R_IN>} to transform. Must not be null.
   * @return A new {@code Kind<EitherTKind<F, L, ?>, R_OUT>}.
   */
  @Override
  public <R_IN, R_OUT> @NonNull Kind<EitherTKind<F, L, ?>, R_OUT> flatMap(
      @NonNull Function<R_IN, Kind<EitherTKind<F, L, ?>, R_OUT>> f,
      @NonNull Kind<EitherTKind<F, L, ?>, R_IN> ma) {
    Objects.requireNonNull(f, "Function f cannot be null for flatMap");
    Objects.requireNonNull(ma, "Kind ma cannot be null for flatMap");

    // Unwrap the input Kind
    EitherT<F, L, R_IN> eitherT = EitherTKindHelper.unwrap(ma);

    // Correct flatMap logic for EitherT:
    // The outer monad F is flatMapped. The function given to outerMonad.flatMap
    // takes an Either<L, R_IN> and must return a Kind<F, Either<L, R_OUT>>.
    Kind<F, Either<L, R_OUT>> newValue =
        outerMonad.flatMap(
            innerEither -> { // innerEither is of type Either<L, R_IN>
              if (innerEither.isRight()) {
                // If innerEither is Right, get the value and apply the function f.
                R_IN r_in = innerEither.getRight();
                // f.apply(r_in) returns Kind<EitherTKind<F, L, ?>, R_OUT>.
                // Unwrap this to get the concrete EitherT.
                EitherT<F, L, R_OUT> resultT = EitherTKindHelper.unwrap(f.apply(r_in));
                // The value of resultT is Kind<F, Either<L, R_OUT>>, which is what flatMap expects.
                return resultT.value();
              } else {
                // If innerEither is Left, propagate the Left value.
                // We need to create a Kind<F, Either<L, R_OUT>> that holds this Left.
                // So, we create an Either.left(innerEither.getLeft()) and lift it into F.
                return outerMonad.of(Either.left(innerEither.getLeft()));
              }
            },
            eitherT.value()); // eitherT.value() is Kind<F, Either<L, R_IN>>

    // Wrap the final EitherT
    return EitherTKindHelper.wrap(EitherT.fromKind(newValue));
  }

  // --- MonadError Methods ---

  /**
   * Raises an error in the {@code EitherTKind<F, L, ?>} context. For {@code EitherT}, an error is
   * represented by a 'left' value of type {@code L}. This method returns an {@code EitherTKind}
   * wrapping {@code F<Left(error)>}.
   *
   * @param <R> The type parameter for the 'right' side of the resulting {@code Kind}, though it
   *     will be absent.
   * @param error The 'left' (error) value. Can be null if {@code L} is nullable.
   * @return A {@code Kind<EitherTKind<F, L, ?>, R>} representing {@code F<Left(error)>}.
   */
  @Override
  public <R> @NonNull Kind<EitherTKind<F, L, ?>, R> raiseError(@Nullable L error) {
    // Create a concrete EitherT holding F<Left(error)>
    EitherT<F, L, R> concreteEitherT = EitherT.left(outerMonad, error);
    // Wrap using the helper
    return EitherTKindHelper.wrap(concreteEitherT);
  }

  /**
   * Handles an error (represented by a 'left' value) in the {@code Kind<EitherTKind<F, L, ?>, R>}.
   * If the input {@code ma} represents {@code F<Left(l)>}, the {@code handler} function is applied
   * to {@code l}. If {@code ma} represents {@code F<Right(r)>}, it is returned unchanged. This
   * operation is performed within the context of the outer monad {@code F}.
   *
   * @param <R> The type of the 'right' value.
   * @param ma The {@code Kind<EitherTKind<F, L, ?>, R>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Left(l)>}. It takes a
   *     value of type {@code L} and returns a new {@code Kind<EitherTKind<F, L, ?>, R>}. Must not
   *     be null.
   * @return A {@code Kind<EitherTKind<F, L, ?>, R>}, either the original or the result of the
   *     handler.
   */
  @Override
  public <R> @NonNull Kind<EitherTKind<F, L, ?>, R> handleErrorWith(
      @NonNull Kind<EitherTKind<F, L, ?>, R> ma,
      @NonNull Function<L, Kind<EitherTKind<F, L, ?>, R>> handler) {
    Objects.requireNonNull(ma, "Kind ma cannot be null for handleErrorWith");
    Objects.requireNonNull(handler, "Function handler cannot be null for handleErrorWith");

    // Unwrap the input Kind
    EitherT<F, L, R> eitherT = EitherTKindHelper.unwrap(ma);

    Kind<F, Either<L, R>> handledValue =
        outerMonad.flatMap( // Operating on F<Either<L, R>>
            innerEither -> { // innerEither is Either<L, R>
              if (innerEither.isRight()) {
                // Value is Right, lift it back into F: F<Right(r)>
                return outerMonad.of(innerEither);
              } else {
                // Value is Left, apply the handler to the left value.
                L leftVal = innerEither.getLeft();
                Kind<EitherTKind<F, L, ?>, R> resultKind = handler.apply(leftVal);
                // Unwrap the handler's result Kind to get EitherT<F, L, R>
                EitherT<F, L, R> resultT = EitherTKindHelper.unwrap(resultKind);
                // Return the F<Either<L, R>> from the handler's result.
                return resultT.value();
              }
            },
            eitherT.value() // The initial Kind<F, Either<L, R>>
            );
    // Wrap the final EitherT
    return EitherTKindHelper.wrap(EitherT.fromKind(handledValue));
  }
}
