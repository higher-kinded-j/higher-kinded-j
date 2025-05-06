package org.higherkindedj.hkt.trans.either_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents the concrete implementation of the Either Transformer Monad (EitherT). It wraps a
 * monadic value of type {@code Kind<F, Either<L, R>>}, where {@code F} is the outer monad, {@code
 * L} is the type of the 'left' (error/alternative) value, and {@code R} is the type of the 'right'
 * (success) value.
 *
 * <p>This class is a record, making it an immutable data holder for the wrapped value. To use
 * {@code EitherT} as a {@code Kind} in higher-kinded type simulations, it should be
 * wrapped/unwrapped using {@link EitherTKindHelper}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind<?>}).
 * @param <L> The type of the 'left' value in the inner {@link Either}.
 * @param <R> The type of the 'right' value in the inner {@link Either}.
 * @param value The underlying monadic value {@code Kind<F, Either<L, R>>}. Must not be null.
 */
public record EitherT<F, L, R>(@NonNull Kind<F, Either<L, R>> value) {

  /**
   * Canonical constructor for {@code EitherT}.
   *
   * @param value The underlying monadic value {@code Kind<F, Either<L, R>>}.
   * @throws NullPointerException if {@code value} is null.
   */
  public EitherT {
    Objects.requireNonNull(value, "Wrapped value cannot be null for EitherT");
  }

  /**
   * Creates an {@code EitherT} from an existing {@code Kind<F, Either<L, R>>}.
   *
   * @param value The monadic value to wrap. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @return A new {@code EitherT} instance.
   * @throws NullPointerException if {@code value} is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> fromKind(@NonNull Kind<F, Either<L, R>> value) {
    return new EitherT<>(value);
  }

  /**
   * Lifts a 'right' value {@code r} into {@code EitherT<F, L, R>}, resulting in {@code
   * F<Right(r)>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param r The 'right' value to wrap. Can be null if {@code R} is nullable.
   * @param <F> The witness type of the outer monad.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @return A new {@code EitherT} instance representing {@code outerMonad.of(Either.right(r))}.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> right(
      @NonNull Monad<F> outerMonad, @Nullable R r) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for right");
    Kind<F, Either<L, R>> lifted = outerMonad.of(Either.right(r));
    return new EitherT<>(lifted);
  }

  /**
   * Lifts a 'left' value {@code l} into {@code EitherT<F, L, R>}, resulting in {@code F<Left(l)>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param l The 'left' value to wrap. Can be null if {@code L} is nullable.
   * @param <F> The witness type of the outer monad.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @return A new {@code EitherT} instance representing {@code outerMonad.of(Either.left(l))}.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> left(
      @NonNull Monad<F> outerMonad, @Nullable L l) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for left");
    Kind<F, Either<L, R>> lifted = outerMonad.of(Either.left(l));
    return new EitherT<>(lifted);
  }

  /**
   * Lifts a plain {@link Either<L, R>} into {@code EitherT<F, L, R>}, resulting in {@code
   * F<Either<L, R>>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param either The {@link Either} instance to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @return A new {@code EitherT} instance representing {@code outerMonad.of(either)}.
   * @throws NullPointerException if {@code outerMonad} or {@code either} is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> fromEither(
      @NonNull Monad<F> outerMonad, @NonNull Either<L, R> either) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for fromEither");
    Objects.requireNonNull(either, "Input Either cannot be null for fromEither");
    Kind<F, Either<L, R>> lifted = outerMonad.of(either);
    return new EitherT<>(lifted);
  }

  /**
   * Lifts a monadic value {@code Kind<F, R>} into {@code EitherT<F, L, R>}, treating the value
   * {@code R} as a 'right' value. This results in {@code F<Either<L, R>>} where the {@code Either}
   * is {@code Right(r)}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param fr The monadic value {@code Kind<F, R>} to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <L> The type of the 'left' value.
   * @param <R> The type of the 'right' value.
   * @return A new {@code EitherT} instance.
   * @throws NullPointerException if {@code outerMonad} or {@code fr} is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> liftF(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, R> fr) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for liftF");
    Objects.requireNonNull(fr, "Input Kind<F, R> cannot be null for liftF");
    Kind<F, Either<L, R>> mapped = outerMonad.map(Either::right, fr);
    return new EitherT<>(mapped);
  }

  /**
   * Accessor for the underlying monadic value.
   *
   * @return The {@code @NonNull Kind<F, Either<L, R>>} wrapped by this {@code EitherT}.
   */
  public @NonNull Kind<F, Either<L, R>> value() {
    return value;
  }
}
