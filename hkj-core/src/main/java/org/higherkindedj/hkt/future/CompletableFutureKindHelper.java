// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link CompletableFutureConverterOps} for widen/narrow operations, and
 * providing an additional utility instance method for {@link CompletableFuture} types.
 *
 * <p>Access these operations via the singleton {@code FUTURE}. For example: {@code
 * CompletableFutureKindHelper.FUTURE.widen(myFuture);}
 */
public enum CompletableFutureKindHelper implements CompletableFutureConverterOps {
  FUTURE;

  private static final Class<CompletableFuture> COMPLETABLE_FUTURE_CLASS = CompletableFuture.class;

  /**
   * Internal record implementing {@link CompletableFutureKind} to hold the concrete {@link
   * CompletableFuture} instance. Made package-private for potential test access.
   *
   * @param <A> The result type of the CompletableFuture.
   * @param future The actual {@link CompletableFuture} instance. Must not be null.
   */
  record CompletableFutureHolder<A>(CompletableFuture<A> future)
      implements CompletableFutureKind<A> {
    CompletableFutureHolder {
      KindValidator.requireForWiden(future, COMPLETABLE_FUTURE_CLASS);
    }
  }

  /**
   * Widens a concrete {@link CompletableFuture<A>} instance into its higher-kinded representation,
   * {@code Kind<CompletableFutureKind.Witness, A>}. Implements {@link
   * CompletableFutureConverterOps#widen}.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param future The concrete {@link CompletableFuture<A>} instance to widen. Must not be null.
   * @return A {@link Kind<CompletableFutureKind.Witness, A>} representing the wrapped future. Never
   *     null.
   * @throws NullPointerException if {@code future} is {@code null}.
   */
  @Override
  public <A> Kind<CompletableFutureKind.Witness, A> widen(CompletableFuture<A> future) {
    return new CompletableFutureHolder<>(future);
  }

  /**
   * Narrows a {@code Kind<CompletableFutureKind.Witness, A>} back to its concrete {@link
   * CompletableFuture<A>} type. Implements {@link CompletableFutureConverterOps#narrow}.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The {@code Kind<CompletableFutureKind.Witness, A>} instance to narrow. May be
   *     {@code null}.
   * @return The underlying {@link CompletableFuture<A>}. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, not an instance of {@code CompletableFutureHolder}, or if the holder internally
   *     contains a {@code null} future.
   */
  @Override
  public <A> CompletableFuture<A> narrow(@Nullable Kind<CompletableFutureKind.Witness, A> kind) {
    return KindValidator.narrow(kind, COMPLETABLE_FUTURE_CLASS, this::extractCompletableFuture);
  }

  /**
   * Retrieves the result of the {@link CompletableFuture} wrapped within the {@link Kind}, blocking
   * the current thread if necessary until the future completes.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The {@code Kind<CompletableFutureKind.Witness, A>} holding the {@code
   *     CompletableFuture} computation. Must not be null.
   * @return The result of the {@code CompletableFuture} computation. Can be {@code null} if the
   *     future completes with a {@code null} value.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is
   *     invalid (e.g., null or wrong type).
   * @throws CompletionException if the future completed exceptionally.
   * @throws java.util.concurrent.CancellationException if the future was cancelled.
   */
  public <A> A join(Kind<CompletableFutureKind.Witness, A> kind) {
    CompletableFuture<A> future = this.narrow(kind);
    try {
      return future.join();
    } catch (CompletionException e) {
      // Preserve original exception throwing behavior if cause is RuntimeException or Error
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw e; // otherwise, throw the original CompletionException
    }
  }

  private <A> CompletableFuture<A> extractCompletableFuture(
      Kind<CompletableFutureKind.Witness, A> kind) {
    return switch (kind) {
      case CompletableFutureHolder<A> holder -> holder.future();
      default -> throw new ClassCastException(); // Will be caught and wrapped by KindValidator
    };
  }
}
