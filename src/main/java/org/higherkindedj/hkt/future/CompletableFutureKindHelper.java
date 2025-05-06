package org.higherkindedj.hkt.future;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link CompletableFutureKind} HKT simulation. Provides static
 * methods for wrapping and unwrapping {@link CompletableFuture} instances.
 */
public final class CompletableFutureKindHelper {

  // Error Messages (ensure these constants are defined within this class)
  public static final String INVALID_KIND_NULL_MSG =
      "Cannot unwrap null Kind for CompletableFuture";
  public static final String INVALID_KIND_TYPE_MSG =
      "Kind instance is not a CompletableFutureHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "CompletableFutureHolder contained null Future instance";

  private CompletableFutureKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: 'future' component is NOT marked @NonNull
  record CompletableFutureHolder<A>(CompletableFuture<A> future)
      implements CompletableFutureKind<A> {}

  /**
   * Unwraps a CompletableFutureKind back to the concrete {@code CompletableFuture<A>} type. Throws
   * KindUnwrapException if the Kind is null, not a CompletableFutureHolder, or the holder contains
   * a null CompletableFuture instance.
   *
   * @param <A> The element type of the CompletableFuture.
   * @param kind The CompletableFutureKind instance. Can be {@code @Nullable}.
   * @return The underlying, non-null {@code CompletableFuture<A>}. Returns {@code @NonNull}.
   * @throws KindUnwrapException if unwrapping fails due to null input, wrong type, or the holder
   *     containing a null Future.
   */
  @SuppressWarnings("unchecked") // For casting future - safe after checks
  public static <A> @NonNull CompletableFuture<A> unwrap(
      @Nullable Kind<CompletableFutureKind<?>, A> kind) {
    // Use switch expression with pattern matching
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a CompletableFutureHolder (extract inner 'future')
      case CompletableFutureKindHelper.CompletableFutureHolder<?>(var future) -> {
        // Check if the extracted Future instance itself is null.
        // Necessary as the record component is not marked @NonNull.
        if (future == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // Cast is safe after type check and null check
        yield (CompletableFuture<A>) future;
      }

      // Case 3: Input Kind is non-null but not a CompletableFutureHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code CompletableFuture<A>} value into the CompletableFutureKind type.
   *
   * @param <A> The element type.
   * @param future The concrete {@code CompletableFuture<A>} instance to wrap. Must be
   *     {@code @NonNull}.
   * @return The {@code CompletableFutureKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if future is null.
   */
  public static <A> @NonNull CompletableFutureKind<A> wrap(@NonNull CompletableFuture<A> future) {
    Objects.requireNonNull(future, "Input CompletableFuture cannot be null for wrap");
    return new CompletableFutureHolder<>(future);
  }

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
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) throw re;
      if (cause instanceof Error err) throw err;
      throw e;
    }
  }
}
