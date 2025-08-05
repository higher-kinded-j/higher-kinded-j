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
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} type class for {@link java.util.concurrent.CompletableFuture},
 * using {@link CompletableFutureKind.Witness} as the higher-kinded type witness and {@link
 * Throwable} as the error type. This class is a stateless singleton, accessible via {@link
 * #INSTANCE}.
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
public class CompletableFutureMonad extends CompletableFutureApplicative
    implements MonadError<CompletableFutureKind.Witness, Throwable> {

  /** Singleton instance of {@code CompletableFutureMonad}. */
  public static final CompletableFutureMonad INSTANCE = new CompletableFutureMonad();

  /** Private constructor to enforce the singleton pattern. */
  private CompletableFutureMonad() {
    // Default constructor
  }

  /**
   * Sequentially composes two asynchronous computations, where the second computation (produced by
   * function {@code f}) depends on the result of the first computation ({@code ma}).
   *
   * <p>If the first {@code CompletableFuture} ({@code ma}) completes successfully with a value
   * {@code a}, the function {@code f} is applied to {@code a}. {@code f} must return a {@code
   * Kind<CompletableFutureKind.Witness, B>}, which represents another {@code CompletableFuture<B>}.
   * The result of this {@code flatMap} operation is this new {@code CompletableFuture<B>}.
   *
   * <p>If {@code ma} completes exceptionally, or if the application of {@code f} throws an
   * exception, or if {@code f} returns a {@code Kind} that unwraps to a {@code CompletableFuture}
   * that later completes exceptionally, then the resulting {@code CompletableFuture} will also
   * complete exceptionally.
   *
   * <p>This operation is analogous to {@code bind} or {@code >>=} in other monadic contexts and is
   * implemented using {@link CompletableFuture#thenCompose(Function)}.
   *
   * @param <A> The type of the result of the first computation {@code ma}.
   * @param <B> The type of the result of the second computation returned by function {@code f}.
   * @param f A non-null function that takes a value of type {@code A} (the result of {@code ma})
   *     and returns a {@code Kind<CompletableFutureKind.Witness, B>}, representing the next
   *     asynchronous computation. The value {@code a} passed to this function can be {@code null}
   *     if the preceding {@code CompletableFuture<A>} completed with {@code null}.
   * @param ma A non-null {@code Kind<CompletableFutureKind.Witness, A>} representing the first
   *     asynchronous computation {@code CompletableFuture<A>}.
   * @return A non-null {@code Kind<CompletableFutureKind.Witness, B>} representing a new {@code
   *     CompletableFuture<B>} that will complete with the result of the composed asynchronous
   *     operations.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   * @throws NullPointerException if {@code f} is null, or if {@code f} returns a null {@code Kind}.
   */
  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind.Witness, B> flatMap(
      @NonNull
          Function<? super @Nullable A, ? extends @NonNull Kind<CompletableFutureKind.Witness, B>>
          f,
      @NonNull Kind<CompletableFutureKind.Witness, A> ma) {
    CompletableFuture<A> futureA = FUTURE.narrow(ma);
    CompletableFuture<B> futureB =
        futureA.thenCompose(
            a -> {
              Kind<CompletableFutureKind.Witness, B> kindB = f.apply(a);
              return FUTURE.narrow(kindB);
            });
    return FUTURE.widen(futureB);
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
      @NonNull Function<? super Throwable, ? extends Kind<CompletableFutureKind.Witness, A>>
          handler) {
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
