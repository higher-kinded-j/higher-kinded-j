package org.higherkindedj.hkt.trans;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * MonadError instance for the EitherT transformer. It requires a Monad instance for the outer
 * context F. This instance handles errors of type L, corresponding to the Left type of the inner
 * Either.
 *
 * @param <F> The witness type of the outer Monad.
 * @param <L> The Left type of the inner Either (the error type E for this MonadError).
 */
// Implement MonadError<WitnessType, ErrorType>
public class EitherTMonad<F, L> implements MonadError<EitherTKind<F, L, ?>, L> {

  private final @NonNull Monad<F> outerMonad;

  // Note: We only need Monad<F> capabilities to implement MonadError for the inner Either.
  // If we wanted to *also* handle errors from F itself (type E), we'd need MonadError<F, E>.

  /**
   * Creates a MonadError instance for EitherT.
   *
   * @param outerMonad The Monad instance for the outer context F. Must not be null.
   */
  public EitherTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad = Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null");
  }

  // --- Applicative Methods ---

  @Override
  public <A> @NonNull Kind<EitherTKind<F, L, ?>, A> of(@Nullable A value) {
    // Lift a pure value 'a' into Right(a) within the outer monad F.
    return EitherT.right(outerMonad, value);
  }

  @Override
  public <A, B> @NonNull Kind<EitherTKind<F, L, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<EitherTKind<F, L, ?>, A> fa) {
    EitherT<F, L, A> eitherT = (EitherT<F, L, A>) fa; // Safe cast in HKT higherkindedj
    // Map the inner Either using the outer monad's map
    Kind<F, Either<L, B>> newValue = outerMonad.map(either -> either.map(f), eitherT.value());
    return EitherT.fromKind(newValue);
  }

  @Override
  public <A, B> @NonNull Kind<EitherTKind<F, L, ?>, B> ap(
      @NonNull Kind<EitherTKind<F, L, ?>, Function<A, B>> ff,
      @NonNull Kind<EitherTKind<F, L, ?>, A> fa) {
    EitherT<F, L, Function<A, B>> funcT = (EitherT<F, L, Function<A, B>>) ff;
    EitherT<F, L, A> valT = (EitherT<F, L, A>) fa;

    // Use outer monad's flatMap/map (or ap if available and suitable)
    Kind<F, Either<L, B>> resultValue =
        outerMonad.flatMap(
            eitherF -> { // eitherF is Either<L, Function<A, B>>
              if (eitherF.isLeft()) {
                // Function is Left, propagate Left, lifting it into F
                return outerMonad.of(Either.left(eitherF.getLeft()));
              } else {
                // Function is Right(f), now map the value monad F<Either<L, A>>
                return outerMonad.map(
                    eitherA -> eitherA.map(eitherF.getRight()), // Apply f only if eitherA is Right
                    valT.value());
              }
            },
            funcT.value() // Apply flatMap to the F<Either<L, Function>>
            );

    return EitherT.fromKind(resultValue);
  }

  // --- Monad Method ---

  @Override
  public <A, B> @NonNull Kind<EitherTKind<F, L, ?>, B> flatMap(
      @NonNull Function<A, Kind<EitherTKind<F, L, ?>, B>> f,
      @NonNull Kind<EitherTKind<F, L, ?>, A> ma) {
    EitherT<F, L, A> eitherT = (EitherT<F, L, A>) ma; // Safe cast in HKT higherkindedj

    Kind<F, Either<L, B>> newValue =
        outerMonad.flatMap(
            either ->
                either.fold(
                    l ->
                        outerMonad.of(
                            Either.left(l)), // If inner is Left(l), lift it back: F<Left(l)>
                    r -> { // If inner is Right(r), apply f(r) which returns EitherT<F,L,B>
                      EitherT<F, L, B> resultT =
                          (EitherT<F, L, B>) f.apply(r); // Apply f, cast result
                      return resultT.value(); // Get the inner F<Either<L,B>>
                    }),
            eitherT.value() // Apply the flatMap to the F<Either<L,A>> value
            );
    return EitherT.fromKind(newValue);
  }

  // --- MonadError Methods ---

  /**
   * Lifts an error value 'l' into the EitherT context as {@code F<Left(l)>}.
   *
   * @param error The error value (type L) to lift.
   * @param <A> The phantom type parameter for the Right side.
   * @return An EitherT representing the error.
   */
  @Override
  public <A> @NonNull Kind<EitherTKind<F, L, ?>, A> raiseError(L error) {
    // Lift the Either error 'L' into the context using the static helper
    return EitherT.left(outerMonad, error);
  }

  /**
   * Handles an error 'L' within the EitherT context. If the wrapped {@code Kind<F, Either<L, A>>}
   * eventually results in a {@code Right}, it's returned unchanged. If it results in a {@code
   * Left(l)}, the 'handler' function {@code L -> EitherT<F, L, A>} is applied to 'l'.
   *
   * <p>Note: This primarily handles errors represented by the inner Either's Left type 'L'. Errors
   * from the outer monad F (e.g., Throwable for CompletableFuture) are not directly handled here
   * unless the outerMonad's flatMap implementation propagates them into the handler's execution
   * context.
   *
   * @param ma The EitherT value potentially containing an error L.
   * @param handler Function {@code L -> Kind<EitherTKind<F, L, ?>, A>} to handle the error.
   * @param <A> The type of the Right value.
   * @return Original EitherT if Right, or the result of the handler applied to the Left value.
   */
  @Override
  public <A> @NonNull Kind<EitherTKind<F, L, ?>, A> handleErrorWith(
      @NonNull Kind<EitherTKind<F, L, ?>, A> ma,
      @NonNull Function<L, Kind<EitherTKind<F, L, ?>, A>> handler) {
    EitherT<F, L, A> eitherT = (EitherT<F, L, A>) ma; // Safe cast

    // Use outerMonad's flatMap to access the inner Either<L, A> once F completes.
    Kind<F, Either<L, A>> handledValue =
        outerMonad.flatMap(
            either ->
                either.fold(
                    l -> { // Inner is Left(l), apply the handler.
                      // handler(l) returns Kind<EitherTKind<F, L, ?>, A>
                      EitherT<F, L, A> resultT = (EitherT<F, L, A>) handler.apply(l);
                      // Return the F<Either<L,A>> from the handler's result.
                      return resultT.value();
                    },
                    r ->
                        outerMonad.of(
                            Either.right(
                                r)) // Inner is Right(r), lift it back F<Right(r)>. No handling
                    // needed.
                    ),
            eitherT.value() // Apply flatMap to the original F<Either<L,A>>
            );

    return EitherT.fromKind(handledValue);
  }
}
