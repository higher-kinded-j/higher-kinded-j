package org.higherkindedj.hkt.future;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class CompletableFutureKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG =
      "Cannot unwrap null Kind for CompletableFuture";
  public static final String INVALID_KIND_TYPE_MSG =
      "Kind instance is not a CompletableFutureHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "CompletableFutureHolder contained null Future instance";

  private CompletableFutureKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a CompletableFutureKind back to the concrete CompletableFuture<A> type. Throws
   * KindUnwrapException if the Kind is null, not a CompletableFutureHolder, or the holder contains
   * a null CompletableFuture instance.
   *
   * @param kind The CompletableFutureKind instance. (@Nullable allows checking null input)
   * @param <A> The element type.
   * @return The underlying, non-null {@code CompletableFuture<A>}. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked") // For casting holder.future() - safe after checks
  public static <A> @NonNull CompletableFuture<A> unwrap(
      @Nullable Kind<CompletableFutureKind<?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }

    if (kind instanceof CompletableFutureHolder<?>(CompletableFuture<?> future)) {
      if (future == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      return (CompletableFuture<A>) future; // Cast is safe here
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /**
   * Wraps a concrete {@code CompletableFuture<A> {@code value into the CompletableFutureKind type.
   */
  public static <A> @NonNull CompletableFutureKind<A> wrap(@NonNull CompletableFuture<A> future) {
    Objects.requireNonNull(future, "Input CompletableFuture cannot be null for wrap");
    return new CompletableFutureHolder<>(future);
  }

  /** Internal holder record for the HKT higherkindedj of CompletableFuture. */
  record CompletableFutureHolder<A>(CompletableFuture<A> future)
      implements CompletableFutureKind<A> {}

  // --- Convenience methods ---

  /**
   * Gets the result of the CompletableFuture Kind, blocking if necessary. Rethrows exceptions from
   * the future, wrapped in RuntimeException if checked. Rethrows KindUnwrapException if unwrapping
   * fails. Primarily for testing or scenarios where blocking is acceptable.
   */
  public static <A> A join(Kind<CompletableFutureKind<?>, A> kind) {
    // KindUnwrapException from unwrap will propagate directly as it's unchecked
    CompletableFuture<A> future = unwrap(kind);
    try {
      return future.join();
    } catch (CompletionException e) {
      // Rethrow cause if possible (consistent with previous logic)
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) throw re;
      if (cause instanceof Error err) throw err;
      throw e; // Wrap checked exceptions or other throwables
    }
    // Other potential RuntimeExceptions from join (like CancellationException) propagate naturally
  }
}
