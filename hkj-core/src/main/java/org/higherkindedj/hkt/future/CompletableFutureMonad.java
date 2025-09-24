// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.util.validation.CoreTypeValidator;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} type class for {@link java.util.concurrent.CompletableFuture},
 * using {@link CompletableFutureKind.Witness} as the higher-kinded type witness and {@link
 * Throwable} as the error type. This class is a stateless singleton, accessible via {@link
 * #INSTANCE}.
 *
 * <p>This class extends {@link CompletableFutureApplicative} and adds error handling capabilities:
 *
 * <ul>
 *   <li>{@link #raiseError(Throwable)}: Creates a {@code CompletableFuture} that is already
 *       completed exceptionally.
 *   <li>{@link #handleErrorWith(Kind, Function)}: Allows recovery from an exceptionally completed
 *       {@code CompletableFuture} by providing a new {@code CompletableFuture}.
 * </ul>
 *
 * @see MonadError
 * @see CompletableFutureApplicative
 * @see CompletableFuture
 * @see CompletableFutureKind.Witness
 */
public class CompletableFutureMonad extends CompletableFutureApplicative
    implements MonadError<CompletableFutureKind.Witness, Throwable> {

  public static Class<CompletableFutureMonad> COMPLETABLE_FUTURE_MONAD_CLASS =
      CompletableFutureMonad.class;

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
   * @param f A function that takes a value of type {@code A} (the result of {@code ma}) and returns
   *     a {@code Kind<CompletableFutureKind.Witness, B>}, representing the next asynchronous
   *     computation. Must not be null.
   * @param ma A {@code Kind<CompletableFutureKind.Witness, A>} representing the first asynchronous
   *     computation {@code CompletableFuture<A>}. Must not be null.
   * @return A {@code Kind<CompletableFutureKind.Witness, B>} representing a new {@code
   *     CompletableFuture<B>} that will complete with the result of the composed asynchronous
   *     operations. Never null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<CompletableFutureKind.Witness, B> flatMap(
      Function<? super @Nullable A, ? extends Kind<CompletableFutureKind.Witness, B>> f,
      Kind<CompletableFutureKind.Witness, A> ma) {

    FunctionValidator.requireFlatMapper(f, COMPLETABLE_FUTURE_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(ma, COMPLETABLE_FUTURE_MONAD_CLASS, FLAT_MAP);

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
   * @param error The {@link Throwable} with which the future should fail. Must not be null.
   * @return A {@code Kind<CompletableFutureKind.Witness, A>} representing {@code
   *     CompletableFuture.failedFuture(error)}. Never null.
   * @throws NullPointerException if {@code error} is null.
   */
  @Override
  public <A> Kind<CompletableFutureKind.Witness, A> raiseError(Throwable error) {
    // Validate that error (Throwable) is not null
    CoreTypeValidator.requireError(error, COMPLETABLE_FUTURE_MONAD_CLASS);
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
   * @param ma The {@code Kind<CompletableFutureKind.Witness, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} completes exceptionally. It takes the {@link
   *     Throwable} and returns a new {@code Kind<CompletableFutureKind.Witness, A>}. Must not be
   *     null.
   * @return A {@code Kind<CompletableFutureKind.Witness, A>}, either the original if successful, or
   *     the result from the {@code handler}. Never null.
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code handler} cannot be unwrapped.
   */
  @Override
  public <A> Kind<CompletableFutureKind.Witness, A> handleErrorWith(
      Kind<CompletableFutureKind.Witness, A> ma,
      Function<? super Throwable, ? extends Kind<CompletableFutureKind.Witness, A>> handler) {

    // Enhanced validation with descriptive parameter
    KindValidator.requireNonNull(ma, COMPLETABLE_FUTURE_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    FunctionValidator.requireFunction(
        handler, "handler", COMPLETABLE_FUTURE_MONAD_CLASS, HANDLE_ERROR_WITH);

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
