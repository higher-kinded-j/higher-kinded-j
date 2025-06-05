// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link MonadError} type class for {@link java.util.concurrent.CompletableFuture},
 * using {@link CompletableFutureKind.Witness} as the higher-kinded type witness and {@link
 * Throwable} as the error type.
 *
 * <p>This class extends {@link CompletableFutureMonad} and adds error handling capabilities:
 *
 * <ul>
 *   <li>{@link #raiseError(Throwable)}: Creates a {@code CompletableFuture} that is already
 *       completed exceptionally.
 *   <li>{@link #handleErrorWith(Kind, Function)}: Allows recovery from an exceptionally completed
 *       {@code CompletableFuture} by providing a new {@code CompletableFuture}.
 * </ul>
 *
 * @see MonadError
 * @see CompletableFutureMonad
 * @see CompletableFuture
 * @see CompletableFutureKind.Witness
 */
public class CompletableFutureMonadError extends CompletableFutureMonad
    implements MonadError<CompletableFutureKind.Witness, Throwable> {

  /** Constructs a new {@code CompletableFutureMonadError} instance. */
  public CompletableFutureMonadError() {
    // Default constructor
  }

  /**
   * Creates a {@code Kind<CompletableFutureKind.Witness, A>} that represents an already
   * exceptionally completed {@link CompletableFuture} with the given {@code error}.
   *
   * @param <A> The phantom type of the value (since this future is failed).
   * @param error The non-null {@link Throwable} with which the future should fail.
   * @return A non-null {@code Kind<CompletableFutureKind.Witness, A>} representing {@code
   *     CompletableFuture.failedFuture(error)}.
   */
  @Override
  public <A> @NonNull Kind<CompletableFutureKind.Witness, A> raiseError(@NonNull Throwable error) {
    return FUTURE.widen(CompletableFuture.failedFuture(error));
  }

  /**
   * Handles an exceptionally completed {@code CompletableFuture} (represented by {@code ma}) by
   * applying a recovery function {@code handler}.
   *
   * <p>If {@code ma} completes successfully, its result is returned. If {@code ma} completes
   * exceptionally, the {@code handler} function is applied to the {@link Throwable}. The {@code
   * handler} must return a new {@code Kind<CompletableFutureKind.Witness, A>} (another {@code
   * CompletableFuture<A>}), which then determines the outcome of the operation.
   *
   * <p>The {@link Throwable} passed to the handler is typically the cause of the failure, unwrapped
   * from {@link CompletionException} if necessary.
   *
   * @param <A> The type of the value.
   * @param ma The non-null {@code Kind<CompletableFutureKind.Witness, A>} to handle.
   * @param handler The non-null function to apply if {@code ma} completes exceptionally. It takes
   *     the {@link Throwable} and returns a new {@code Kind<CompletableFutureKind.Witness, A>}.
   * @return A non-null {@code Kind<CompletableFutureKind.Witness, A>}, either the original if
   *     successful, or the result from the {@code handler}.
   */
  @Override
  public <A> @NonNull Kind<CompletableFutureKind.Witness, A> handleErrorWith(
      @NonNull Kind<CompletableFutureKind.Witness, A> ma,
      @NonNull Function<Throwable, Kind<CompletableFutureKind.Witness, A>> handler) {
    CompletableFuture<A> futureA = FUTURE.narrow(ma);

    // Optimization: If already successfully completed, no need to attach handler.
    if (futureA.isDone() && !futureA.isCompletedExceptionally()) {
      return ma;
    }

    CompletableFuture<A> recoveredFuture =
        futureA.exceptionallyCompose(
            throwable -> {
              Throwable cause =
                  (throwable instanceof CompletionException && throwable.getCause() != null)
                      ? throwable.getCause()
                      : throwable;
              Kind<CompletableFutureKind.Witness, A> recoveryKind = handler.apply(cause);
              return FUTURE.narrow(recoveryKind);
            });
    return FUTURE.widen(recoveredFuture);
  }
}
