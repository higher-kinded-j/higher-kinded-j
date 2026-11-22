// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.lazy.ThrowableSupplier;

/**
 * Effect context for typed error handling across effectful operations.
 *
 * <p>ErrorContext wraps {@link EitherT} with a user-friendly API, hiding the complexity of
 * higher-kinded types while preserving the full capability of the transformer. It provides typed
 * error handling for computations that may fail.
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #io(ThrowableSupplier, Function)} - Create from a computation that may throw
 *   <li>{@link #ioEither(Supplier)} - Create from a computation returning Either
 *   <li>{@link #success(Object)} - Create a successful context
 *   <li>{@link #failure(Object)} - Create a failed context
 *   <li>{@link #fromEither(Either)} - Create from an existing Either
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Either<ApiError, Profile> result = ErrorContext
 *     .<ApiError, User>io(
 *         () -> userService.fetch(userId),
 *         ApiError::fromException)
 *     .via(user -> ErrorContext.io(
 *         () -> profileService.fetch(user.profileId()),
 *         ApiError::fromException))
 *     .recover(err -> Profile.defaultProfile())
 *     .runIO()
 *     .unsafeRun();
 * }</pre>
 *
 * @param <F> the underlying effect type witness (e.g., {@code IOKind.Witness})
 * @param <E> the error type
 * @param <A> the success value type
 */
public final class ErrorContext<F extends WitnessArity<TypeArity.Unary>, E, A>
    implements EffectContext<F, A> {

  private final EitherT<F, E, A> transformer;
  private final Monad<F> outerMonad;
  private final EitherTMonad<F, E> eitherTMonad;

  private ErrorContext(EitherT<F, E, A> transformer, Monad<F> outerMonad) {
    this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
    this.outerMonad = Objects.requireNonNull(outerMonad, "outerMonad must not be null");
    this.eitherTMonad = new EitherTMonad<>(outerMonad);
  }

  // --- Factory Methods for IO-based contexts ---

  /**
   * Creates an ErrorContext from a computation that may throw exceptions.
   *
   * <p>Exceptions thrown during computation are caught and converted to the error type via the
   * provided mapper. A success always holds a value, so a computation that returns null is treated
   * the same way: the mapper sees a {@link NullPointerException}.
   *
   * @param computation the computation to execute; must not be null, and must not return null
   * @param errorMapper converts exceptions to the error type; must not be null, and must not return
   *     null; a null result fails the context with a NullPointerException when it runs
   * @param <E> the error type
   * @param <A> the success value type
   * @return a new ErrorContext wrapping the computation
   * @throws NullPointerException if computation or errorMapper is null
   */
  public static <E, A> ErrorContext<IOKind.Witness, E, A> io(
      ThrowableSupplier<A> computation, Function<? super Throwable, ? extends E> errorMapper) {
    Objects.requireNonNull(computation, "computation must not be null");
    Objects.requireNonNull(errorMapper, "errorMapper must not be null");

    IO<Either<E, A>> io =
        IO.delay(
            () -> {
              try {
                return Either.right(
                    Objects.requireNonNull(computation.get(), "computation must not return null"));
              } catch (Throwable t) {
                return Either.left(mappedError(errorMapper, t));
              }
            });

    EitherT<IOKind.Witness, E, A> transformer = EitherT.fromKind(IO_OP.widen(io));
    return new ErrorContext<>(transformer, IOMonad.INSTANCE);
  }

  /** Holds a mapped error to the non-null contract, keeping the failure it was mapped from. */
  private static <E> E mappedError(
      Function<? super Throwable, ? extends E> errorMapper, Throwable failure) {
    E error = errorMapper.apply(failure);
    if (error == null) {
      NullPointerException refused = new NullPointerException("errorMapper must not return null");
      refused.initCause(failure);
      throw refused;
    }
    return error;
  }

  /**
   * Creates an ErrorContext from a computation that returns an Either.
   *
   * <p>Use this when the computation already handles its own error representation.
   *
   * @param computation the computation returning Either; must not be null
   * @param <E> the error type
   * @param <A> the success value type
   * @return a new ErrorContext wrapping the computation
   * @throws NullPointerException if computation is null
   */
  public static <E, A> ErrorContext<IOKind.Witness, E, A> ioEither(
      Supplier<Either<E, A>> computation) {
    Objects.requireNonNull(computation, "computation must not be null");

    IO<Either<E, A>> io = IO.delay(computation);
    EitherT<IOKind.Witness, E, A> transformer = EitherT.fromKind(IO_OP.widen(io));
    return new ErrorContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a successful ErrorContext containing the given value.
   *
   * @param value the success value; must not be null
   * @param <E> the error type
   * @param <A> the success value type
   * @return a new ErrorContext representing success
   * @throws NullPointerException if value is null
   */
  public static <E, A> ErrorContext<IOKind.Witness, E, A> success(A value) {
    EitherT<IOKind.Witness, E, A> transformer = EitherT.right(IOMonad.INSTANCE, value);
    return new ErrorContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a failed ErrorContext containing the given error.
   *
   * @param error the error value; must not be null
   * @param <E> the error type
   * @param <A> the success value type
   * @return a new ErrorContext representing failure
   * @throws NullPointerException if error is null
   */
  public static <E, A> ErrorContext<IOKind.Witness, E, A> failure(E error) {
    Objects.requireNonNull(error, "error must not be null");
    EitherT<IOKind.Witness, E, A> transformer = EitherT.left(IOMonad.INSTANCE, error);
    return new ErrorContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates an ErrorContext from an existing Either value.
   *
   * @param either the Either to wrap; must not be null
   * @param <E> the error type
   * @param <A> the success value type
   * @return a new ErrorContext wrapping the Either
   * @throws NullPointerException if either is null
   */
  public static <E, A> ErrorContext<IOKind.Witness, E, A> fromEither(Either<E, A> either) {
    Objects.requireNonNull(either, "either must not be null");
    EitherT<IOKind.Witness, E, A> transformer = EitherT.fromEither(IOMonad.INSTANCE, either);
    return new ErrorContext<>(transformer, IOMonad.INSTANCE);
  }

  // --- Chainable Operations ---

  @Override
  public <B> ErrorContext<F, E, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Kind<EitherTKind.Witness<F, E>, B> result =
        eitherTMonad.map(mapper, EITHER_T.widen(transformer));
    return new ErrorContext<>(EITHER_T.narrow(result), outerMonad);
  }

  /**
   * Chains a dependent computation that returns an ErrorContext.
   *
   * <p>The function is applied to the success value, and the context it returns becomes the new
   * context. If this context holds an error, the function is not applied and the error passes
   * through.
   *
   * <p>javac checks the step. It must be an {@code ErrorContext} over the same effect, and it may
   * fail with {@code E} or a subtype of it, such as one variant of a sealed error. A step with
   * another error type does not compile: map its error with {@link #mapError} first.
   *
   * <p>A chain whose first context is typed on one variant stays on that variant. Type the first
   * context on the wider error, as in {@code ErrorContext.<AppError, User>success(user)}, or widen
   * it with {@code .<AppError>mapError(e -> e)}.
   *
   * @param fn the function to apply, returning the next ErrorContext; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or an error context
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> ErrorContext<F, E, B> via(
      Function<? super A, ? extends ErrorContext<F, ? extends E, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<EitherTKind.Witness<F, E>, B> result =
        eitherTMonad.flatMap(
            a -> {
              ErrorContext<F, ? extends E, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              return EITHER_T.widen(covary(next));
            },
            EITHER_T.widen(transformer));

    return new ErrorContext<>(EITHER_T.narrow(result), outerMonad);
  }

  /**
   * Chains a dependent computation that returns an ErrorContext. This is another name for {@link
   * #via}, matching traditional monad terminology, and javac checks the step the same way.
   *
   * @param fn the function to apply, returning the next ErrorContext; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or an error context
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> ErrorContext<F, E, B> flatMap(
      Function<? super A, ? extends ErrorContext<F, ? extends E, B>> fn) {
    return via(fn);
  }

  /**
   * Sequences an independent computation, discarding this context's value.
   *
   * <p>This is useful for sequencing effects where only the final result matters. The supplied
   * context may fail with {@code E} or a subtype of it, as a step of {@link #via} may.
   *
   * @param supplier provides the next context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context from the supplier
   * @throws NullPointerException if supplier is null or returns null
   */
  public <B> ErrorContext<F, E, B> then(
      Supplier<? extends ErrorContext<F, ? extends E, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  /** Reads a step whose error is a subtype of {@code E} as failing with {@code E}. */
  @SuppressWarnings("unchecked") // immutable, and only produces its error: unobservable
  private static <F extends WitnessArity<TypeArity.Unary>, E, B> EitherT<F, E, B> covary(
      ErrorContext<F, ? extends E, B> step) {
    return (EitherT<F, E, B>) step.transformer;
  }

  // --- Recovery Operations ---

  /**
   * Recovers from an error by providing a fallback value.
   *
   * <p>If this context contains an error, the recovery function is applied to produce a success
   * value. If this context is already successful, it is returned unchanged.
   *
   * @param recovery the function to apply to the error to produce a value; must not be null
   * @return a context containing either the original value or the recovered value
   * @throws NullPointerException if recovery is null
   */
  public ErrorContext<F, E, A> recover(Function<? super E, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    Kind<EitherTKind.Witness<F, E>, A> result =
        eitherTMonad.handleErrorWith(
            EITHER_T.widen(transformer),
            error ->
                eitherTMonad.of(
                    Objects.requireNonNull(
                        recovery.apply(error), "recovery must not return null")));

    return new ErrorContext<>(EITHER_T.narrow(result), outerMonad);
  }

  /**
   * Recovers from an error by providing a fallback ErrorContext.
   *
   * <p>If this context contains an error, the recovery function is applied to produce an
   * alternative context. If this context is already successful, it is returned unchanged. The
   * fallback may fail with {@code E} or a subtype of it, as a step of {@link #via} may.
   *
   * @param recovery the function to apply to the error to produce a fallback context; must not be
   *     null
   * @return either this context (if successful) or the fallback context
   * @throws NullPointerException if recovery is null or returns null
   */
  public ErrorContext<F, E, A> recoverWith(
      Function<? super E, ? extends ErrorContext<F, ? extends E, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    Kind<EitherTKind.Witness<F, E>, A> result =
        eitherTMonad.handleErrorWith(
            EITHER_T.widen(transformer),
            error -> {
              ErrorContext<F, ? extends E, A> next = recovery.apply(error);
              Objects.requireNonNull(next, "recovery must not return null");
              return EITHER_T.widen(covary(next));
            });

    return new ErrorContext<>(EITHER_T.narrow(result), outerMonad);
  }

  /**
   * Provides an alternative context if this one represents an error.
   *
   * <p>This is a convenience method that ignores the specific error and provides a fallback. The
   * fallback may fail with {@code E} or a subtype of it, as a step of {@link #via} may.
   *
   * @param alternative provides the fallback context; must not be null
   * @return either this context (if successful) or the alternative
   * @throws NullPointerException if alternative is null or returns null
   */
  public ErrorContext<F, E, A> orElse(
      Supplier<? extends ErrorContext<F, ? extends E, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return recoverWith(ignored -> alternative.get());
  }

  /**
   * Transforms the error type without affecting success values.
   *
   * <p>If this context contains an error, the function is applied to transform it. If this context
   * is successful, the value is unchanged but the error type is updated.
   *
   * @param mapper the function to transform the error; must not be null, and must not return null;
   *     a null result fails the context with a NullPointerException when it runs
   * @param <E2> the new error type
   * @return a context with the transformed error type
   * @throws NullPointerException if mapper is null
   */
  public <E2> ErrorContext<F, E2, A> mapError(Function<? super E, ? extends E2> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    // We need to map over the inner Either to transform the error
    Kind<F, Either<E, A>> underlying = transformer.value();
    Kind<F, Either<E2, A>> mapped =
        outerMonad.map(
            either ->
                either.mapLeft(
                    e -> Objects.requireNonNull(mapper.apply(e), "mapper must not return null")),
            underlying);

    EitherT<F, E2, A> newTransformer = EitherT.fromKind(mapped);
    return new ErrorContext<>(newTransformer, outerMonad);
  }

  // --- Execution Methods (IO-specific) ---

  /**
   * Runs the computation and returns an IOPath containing the Either result.
   *
   * <p>This method is only available when F is IOKind.Witness.
   *
   * @return an IOPath that will produce the Either when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<Either<E, A>> runIO() {
    Kind<F, Either<E, A>> underlying = transformer.value();
    IO<Either<E, A>> io = IO_OP.narrow((Kind<IOKind.Witness, Either<E, A>>) underlying);
    return Path.ioPath(io);
  }

  /**
   * Runs the computation and returns the success value, throwing on error.
   *
   * <p>If the computation results in an error, it is wrapped in a RuntimeException and thrown.
   *
   * @return the success value
   * @throws RuntimeException if the computation results in an error
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runIOOrThrow() {
    Either<E, A> result = runIO().unsafeRun();
    if (result.isRight()) {
      return result.getRight();
    } else {
      throw new RuntimeException("ErrorContext failed with: " + result.getLeft());
    }
  }

  /**
   * Runs the computation and returns the success value, or a default on error.
   *
   * @param defaultValue the value to return if the computation fails
   * @return the success value or the default
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runIOOrElse(A defaultValue) {
    Either<E, A> result = runIO().unsafeRun();
    return result.isRight() ? result.getRight() : defaultValue;
  }

  /**
   * Runs the computation and returns the success value, or applies a handler on error.
   *
   * @param errorHandler the function to apply to the error to produce a value
   * @return the success value or the result of the error handler
   * @throws NullPointerException if errorHandler is null
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runIOOrElseGet(Function<? super E, ? extends A> errorHandler) {
    Objects.requireNonNull(errorHandler, "errorHandler must not be null");
    Either<E, A> result = runIO().unsafeRun();
    return result.isRight() ? result.getRight() : errorHandler.apply(result.getLeft());
  }

  // --- Escape Hatch ---

  /**
   * Returns the underlying EitherT transformer.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying EitherT transformer
   */
  public EitherT<F, E, A> toEitherT() {
    return transformer;
  }

  @Override
  public Kind<?, A> underlying() {
    return EITHER_T.widen(transformer);
  }

  @Override
  public String toString() {
    return "ErrorContext(" + transformer + ")";
  }
}
