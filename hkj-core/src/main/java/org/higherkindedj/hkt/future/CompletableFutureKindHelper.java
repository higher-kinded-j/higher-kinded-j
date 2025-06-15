// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
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

  /** Error message for when a null {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG =
      "Cannot narrow null Kind for CompletableFuture";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG =
      "Kind instance is not a CompletableFutureHolder: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG =
      "Input CompletableFuture cannot be null for widen";

  /** Error message for when the internal holder in {@link #narrow(Kind)} contains a null future. */
  public static final String INVALID_HOLDER_STATE_MSG =
      "CompletableFutureHolder contained null Future instance";

  /**
   * Internal record implementing {@link CompletableFutureKind} to hold the concrete {@link
   * CompletableFuture} instance. Made package-private for potential test access.
   *
   * @param <A> The result type of the CompletableFuture.
   * @param future The actual {@link CompletableFuture} instance.
   */
  record CompletableFutureHolder<A>(CompletableFuture<A> future)
      implements CompletableFutureKind<A> {}

  /**
   * Widens a concrete {@link CompletableFuture<A>} instance into its higher-kinded representation,
   * {@code Kind<CompletableFutureKind.Witness, A>}. Implements {@link
   * CompletableFutureConverterOps#widen}.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param future The non-null, concrete {@link CompletableFuture<A>} instance to widen.
   * @return A non-null {@link Kind<CompletableFutureKind.Witness, A>} representing the wrapped
   *     future.
   * @throws NullPointerException if {@code future} is {@code null}.
   */
  @Override
  public <A> @NonNull Kind<CompletableFutureKind.Witness, A> widen(
      @NonNull CompletableFuture<A> future) {
    Objects.requireNonNull(future, INVALID_KIND_TYPE_NULL_MSG);
    return new CompletableFutureHolder<>(future);
  }

  /**
   * Narrows a {@code Kind<CompletableFutureKind.Witness, A>} back to its concrete {@link
   * CompletableFuture<A>} type. Implements {@link CompletableFutureConverterOps#narrow}.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The {@code Kind<CompletableFutureKind.Witness, A>} instance to narrow. May be
   *     {@code null}.
   * @return The underlying, non-null {@link CompletableFuture<A>}.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code CompletableFutureHolder}, or if the holder internally contains a {@code null}
   *     future.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> @NonNull CompletableFuture<A> narrow(
      @Nullable Kind<CompletableFutureKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case CompletableFutureKindHelper.CompletableFutureHolder<?> holder -> {
        CompletableFuture<?> rawFuture = holder.future();
        if (rawFuture == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        yield (CompletableFuture<A>) rawFuture;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Retrieves the result of the {@link CompletableFuture} wrapped within the {@link Kind}, blocking
   * the current thread if necessary until the future completes.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The non-null {@code Kind<CompletableFutureKind.Witness, A>} holding the {@code
   *     CompletableFuture} computation.
   * @return The result of the {@code CompletableFuture} computation. Can be {@code null} if the
   *     future completes with a {@code null} value.
   * @throws KindUnwrapException if the input {@code kind} is invalid (e.g., null or wrong type).
   * @throws CompletionException if the future completed exceptionally.
   * @throws java.util.concurrent.CancellationException if the future was cancelled.
   */
  public <A> @NonNull A join(@NonNull Kind<CompletableFutureKind.Witness, A> kind) {
    CompletableFuture<A> future = this.narrow(kind); // Uses instance method narrow
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
}
