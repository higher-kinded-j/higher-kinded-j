package org.higherkindedj.hkt.future;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing helper methods for working with {@link CompletableFuture} in the
 * context of higher-kinded types (HKT), using {@link CompletableFutureKind} as the HKT marker.
 *
 * <p>This class offers static methods for:
 *
 * <ul>
 *   <li>Safely converting between a {@link CompletableFuture} and its {@link Kind} representation
 *       ({@link #wrap(CompletableFuture)} and {@link #unwrap(Kind)}).
 *   <li>Executing a {@code Kind<CompletableFutureKind<?>, A>} and retrieving its result
 *       synchronously ({@link #join(Kind)}), primarily for testing or specific scenarios where
 *       blocking is acceptable.
 * </ul>
 *
 * <p>It bridges the concrete {@link CompletableFuture} type with the abstract {@link Kind}
 * interface, enabling {@code CompletableFuture} to be used with generic HKT abstractions like
 * {@link org.higherkindedj.hkt.Functor}, {@link org.higherkindedj.hkt.Applicative}, and {@link
 * org.higherkindedj.hkt.Monad}.
 *
 * <p>The {@link #unwrap(Kind)} method uses an internal private record ({@code
 * CompletableFutureHolder}) that implements {@link CompletableFutureKind} to hold the actual {@code
 * CompletableFuture} instance.
 *
 * @see CompletableFuture
 * @see CompletableFutureKind
 * @see Kind
 * @see KindUnwrapException
 */
public final class CompletableFutureKindHelper {

  /** Error message for when a null {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG =
      "Cannot unwrap null Kind for CompletableFuture";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG =
      "Kind instance is not a CompletableFutureHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a null future,
   * which should ideally not happen if {@link #wrap(CompletableFuture)} enforces non-null futures.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "CompletableFutureHolder contained null Future instance";

  /** Private constructor to prevent instantiation of this utility class. */
  private CompletableFutureKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link CompletableFutureKind} to hold the concrete {@link
   * CompletableFuture} instance. This is used by {@link #wrap(CompletableFuture)} and {@link
   * #unwrap(Kind)}.
   *
   * @param <A> The result type of the CompletableFuture.
   * @param future The actual {@link CompletableFuture} instance. Note: While the `future` field
   *     itself is not annotated `@NonNull` here, {@link #wrap(CompletableFuture)} requires a
   *     non-null future, and {@link #unwrap(Kind)} checks for nullity of this field.
   */
  record CompletableFutureHolder<A>(CompletableFuture<A> future)
      implements CompletableFutureKind<A> {}

  /**
   * Unwraps a {@code Kind<CompletableFutureKind.Witness, A>} back to its concrete {@link
   * CompletableFuture<A>} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents a CompletableFuture.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The {@code Kind<CompletableFutureKind.Witness, A>} instance to unwrap. May be
   *     {@code null}.
   * @return The underlying, non-null {@link CompletableFuture CompletableFuture<A>}.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code CompletableFutureHolder}, or if the holder internally contains a {@code null}
   *     future.
   */
  @SuppressWarnings("unchecked")
  public static <A> @NonNull CompletableFuture<A> unwrap(
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
   * Wraps a concrete {@link CompletableFuture<A>} instance into its higher-kinded
   * representation, {@code Kind<CompletableFutureKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param future The non-null, concrete {@link CompletableFuture CompletableFuture<A>} instance to
   *     wrap.
   * @return A non-null {@code CompletableFutureKind<A>} (which is also a {@code
   *     Kind<CompletableFutureKind.Witness, A>}) representing the wrapped future.
   * @throws NullPointerException if {@code future} is {@code null}.
   */
  public static <A> @NonNull CompletableFutureKind<A> wrap(@NonNull CompletableFuture<A> future) {
    Objects.requireNonNull(future, "Input CompletableFuture cannot be null for wrap");
    // CompletableFutureHolder now correctly implements the updated CompletableFutureKind<A>
    return new CompletableFutureHolder<>(future);
  }

  /**
   * Retrieves the result of the {@link CompletableFuture} wrapped within the {@link Kind}, blocking
   * the current thread if necessary until the future completes. // ... (rest of Javadoc)
   *
   * @param <A> The result type of the {@code CompletableFuture}.
   * @param kind The non-null {@code Kind<CompletableFutureKind.Witness, A>} holding the {@code
   *     CompletableFuture} computation.
   * @return The result of the {@code CompletableFuture} computation. Can be {@code null} if the
   *     future completes with a {@code null} value. // ... (throws clauses)
   */
  public static <A> @Nullable A join(@NonNull Kind<CompletableFutureKind.Witness, A> kind) {
    CompletableFuture<A> future = unwrap(kind);
    try {
      return future.join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw e;
    }
  }
}
