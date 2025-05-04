package org.higherkindedj.hkt.trans.either_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents the Either Transformer Monad (EitherT), implemented as an immutable record.
 *
 * <p>EitherT simplifies working with nested monadic structures of the form {@code F<Either<L, R>>},
 * where {@code F} is an outer monad (like {@code CompletableFutureKind} or {@code OptionalKind})
 * and {@code Either} is used for inner error handling (with {@code L} typically representing an
 * error type and {@code R} representing a success type).
 *
 * <p>This record wraps a single value of type {@link Kind}{@code <F, Either<L, R>>}. Operations
 * like {@code map}, {@code flatMap}, {@code ap}, etc., are defined in the corresponding type class
 * instance, {@link EitherTMonad}, which operates on instances of this {@code EitherT} record.
 *
 * <p>Using {@code EitherT} allows chaining operations that return {@code Either} within the context
 * of the outer monad {@code F}, automatically handling the short-circuiting logic of {@code
 * Either.Left} and propagating the context of {@code F}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind<?>}, {@code
 *     CompletableFutureKind<?>}). This monad dictates the context in which the {@code Either} is
 *     evaluated (e.g., asynchronous, optional).
 * @param <L> The type for the {@code Left} side of the inner {@link Either}, typically used for
 *     errors or alternative results.
 * @param <R> The type for the {@code Right} side of the inner {@link Either}, typically used for
 *     successful results.
 * @param value The wrapped {@link Kind}{@code <F, Either<L, R>>} representing the nested monadic
 *     structure. (NonNull)
 * @see EitherTKind
 * @see EitherTMonad
 * @see Either
 * @see Kind
 */
public record EitherT<F, L, R>(@NonNull Kind<F, Either<L, R>> value)
    implements EitherTKind<F, L, R> {

  /**
   * Canonical constructor for the EitherT record. Ensures the wrapped value is not null. Typically,
   * instances should be created using the provided static factory methods.
   *
   * @param value The {@link Kind}{@code <F, Either<L, R>>} to wrap. Must not be null. (NonNull)
   * @throws NullPointerException if the provided value is null.
   */
  public EitherT {
    Objects.requireNonNull(value, "Wrapped value cannot be null");
  }

  // --- Static Factory Methods ---

  /**
   * Wraps an existing {@code Kind<F, Either<L, R>>} value into an EitherT.
   *
   * <p>This is the most direct way to create an EitherT if you already have the nested structure,
   * for example, as the result of an asynchronous operation defined in {@link
   * org.higherkindedj.example.order.workflow.OrderWorkflowSteps}.
   *
   * @param value The {@code Kind<F, Either<L, R>>} to wrap. Must not be null. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <L> The Left type of the inner Either.
   * @param <R> The Right type of the inner Either.
   * @return A new {@code EitherT<F, L, R>} instance wrapping the provided value. (NonNull)
   * @throws NullPointerException if value is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> fromKind(@NonNull Kind<F, Either<L, R>> value) {
    return new EitherT<>(value);
  }

  /**
   * Lifts a pure 'success' value {@code r} into the EitherT context.
   *
   * <p>This is equivalent to creating {@code Either.right(r)} and then lifting that into the outer
   * monad {@code F} using {@code outerMonad.of()}. The result is {@code EitherT(F<Right(r)>)}.
   * Requires the {@link Monad} instance for the outer context {@code F}.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param r The 'Right' value to lift. Can be null if the specific {@code Either} allows it.
   *     (Nullable)
   * @param <F> The witness type of the outer monad.
   * @param <L> The Left type (phantom, as this represents success).
   * @param <R> The Right type.
   * @return An {@code EitherT<F, L, R>} instance representing success in both the outer (F) and
   *     inner (Either) contexts. (NonNull)
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> right(
      @NonNull Monad<F> outerMonad, @Nullable R r) {
    // Either.right allows null, outerMonad.of must handle null if R is nullable
    Kind<F, Either<L, R>> lifted = outerMonad.of(Either.right(r)); // of returns NonNull Kind
    return new EitherT<>(lifted);
  }

  /**
   * Lifts a pure 'error' value {@code l} into the EitherT context.
   *
   * <p>This is equivalent to creating {@code Either.left(l)} and then lifting that into the outer
   * monad {@code F} using {@code outerMonad.of()}. The result is {@code EitherT<F<Left(l)>>)}.
   * Requires the {@link Monad} instance for the outer context {@code F}. This is often used as the
   * implementation for {@code MonadError.raiseError} for EitherT.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param l The 'Left' value to lift (representing an error or alternative result). (Nullable,
   *     depends on L)
   * @param <F> The witness type of the outer monad.
   * @param <L> The Left type.
   * @param <R> The Right type (phantom, as this represents failure).
   * @return An {@code EitherT<F, L, R>} instance representing an inner failure within the outer
   *     context {@code F}. (NonNull)
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> left(
      @NonNull Monad<F> outerMonad, @Nullable L l) {
    // Either.left allows null
    Kind<F, Either<L, R>> lifted = outerMonad.of(Either.left(l));
    return new EitherT<>(lifted);
  }

  /**
   * Lifts a plain {@code Either<L, R>} value into the EitherT context.
   *
   * <p>This lifts the given {@code Either} into the outer monad {@code F} using {@code
   * outerMonad.of()}. Useful for integrating results from synchronous functions that return {@code
   * Either} into an {@code EitherT} workflow. The result is {@code EitherT(F<Either(input)>)}.
   * Requires the {@link Monad} instance for the outer context {@code F}.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param either The {@code Either<L, R>} value to lift. Must not be null. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return An {@code EitherT<F, L, R>} instance wrapping the lifted {@code Either}. (NonNull)
   * @throws NullPointerException if either is null.
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> fromEither(
      @NonNull Monad<F> outerMonad, @NonNull Either<L, R> either) {
    Objects.requireNonNull(either, "Input Either cannot be null for fromEither");
    Kind<F, Either<L, R>> lifted = outerMonad.of(either);
    return new EitherT<>(lifted);
  }

  /**
   * Lifts a value {@code fr} already in the outer monad context {@code Kind<F, R>} into the EitherT
   * context.
   *
   * <p>This maps the value inside {@code F} using {@code Either::right}, effectively transforming
   * {@code F<R>} into {@code F<Right<R>>}, and then wraps the result in {@code EitherT}. The result
   * is {@code EitherT(F<Right(R)>)}. If the original {@code fr} represented an error state in the
   * outer monad {@code F} (e.g., an empty Optional, a failed Future), that state is preserved.
   * Requires the {@link Monad} instance for the outer context {@code F} (as Monad extends Functor
   * which provides map).
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param fr The value already wrapped in the outer monad context {@code Kind<F, R>}. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <L> The Left type (phantom, as the value is lifted into the Right side).
   * @param <R> The Right type.
   * @return An {@code EitherT<F, L, R>} instance where the original value from {@code fr} is now on
   *     the {@code Right} side of the inner {@code Either}. (NonNull)
   */
  public static <F, L, R> @NonNull EitherT<F, L, R> liftF(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, R> fr) {
    // Either::right accepts nullable input if R is nullable
    Kind<F, Either<L, R>> mapped = outerMonad.map(Either::right, fr);
    return new EitherT<>(mapped);
  }

  /**
   * Accessor for the wrapped {@link Kind}{@code <F, Either<L, R>>} value. Generated automatically
   * by the record. Use this method to extract the underlying nested structure when interacting with
   * the outer monad {@code F} directly or at the boundary of an {@code EitherT} computation (e.g.,
   * in {@link EitherTMonad}).
   *
   * @return The underlying {@link Kind}{@code <F, Either<L, R>>}. (NonNull)
   */
  @Override
  public @NonNull Kind<F, Either<L, R>> value() {
    return value;
  }
}
