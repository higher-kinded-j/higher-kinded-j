package org.higherkindedj.hkt.trymonad;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link Try} in the context of higher-kinded types (HKT), using
 * {@link TryKind.Witness} as the HKT marker. Provides static methods for wrapping, unwrapping, and
 * creating {@link Try} instances within the Kind simulation.
 *
 * @see Try
 * @see TryKind
 * @see TryKind.Witness
 */
public final class TryKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Try";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a TryHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "TryHolder contained null Try instance";

  private TryKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link TryKind} to hold the concrete {@link Try} instance. By
   * implementing {@code TryKind<A>}, it implicitly becomes {@code Kind<TryKind.Witness, A>}.
   *
   * @param <A> The result type of the Try computation.
   * @param tryInstance The actual {@link Try} instance. Note: This can be null internally if not
   *     careful, though {@link #wrap(Try)} enforces non-null.
   */
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> {}

  /**
   * Unwraps a {@code Kind<TryKind.Witness, A>} back to the concrete {@code Try<A>} type. Throws
   * {@link KindUnwrapException} if the Kind is null, not a {@code TryHolder}, or the holder
   * contains a null {@code Try} instance.
   *
   * @param <A> The element type potentially held by the Try.
   * @param kind The {@code Kind<TryKind.Witness, A>} instance. Can be {@code @Nullable}.
   * @return The underlying, non-null {@code Try<A>}.
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked") // For casting tryInstance - safe after checks
  public static <A> @NonNull Try<A> unwrap(@Nullable Kind<TryKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case TryKindHelper.TryHolder<?> holder -> {
        // Access the tryInstance directly from the pattern-matched holder
        Try<A> tryInstance = (Try<A>) holder.tryInstance();
        if (tryInstance == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        yield tryInstance;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Try<A>} value into its {@code Kind<TryKind.Witness, A>} representation.
   * Requires a non-null {@code Try} instance as input.
   *
   * @param <A> The element type.
   * @param tryInstance The concrete {@code Try<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code TryKind<A>} representation (which is a {@code Kind<TryKind.Witness, A>}).
   * @throws NullPointerException if tryInstance is null.
   */
  public static <A> @NonNull TryKind<A> wrap(@NonNull Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for wrap");
    return new TryHolder<>(tryInstance);
  }

  /**
   * Wraps a successful value directly into {@code Kind<TryKind.Witness, A>}. Uses {@link
   * Try#success(Object)}.
   *
   * @param <A> The element type.
   * @param value The successful value. Can be {@code @Nullable}.
   * @return A {@code Kind<TryKind.Witness, A>} representing success.
   */
  public static <A> @NonNull Kind<TryKind.Witness, A> success(@Nullable A value) {
    return wrap(Try.success(value));
  }

  /**
   * Wraps a failure directly into {@code Kind<TryKind.Witness, A>}. Uses {@link
   * Try#failure(Throwable)}.
   *
   * @param <A> The phantom element type.
   * @param throwable The exception representing the failure. Must be {@code @NonNull}.
   * @return A {@code Kind<TryKind.Witness, A>} representing failure.
   * @throws NullPointerException if throwable is null (delegated to Try.failure).
   */
  public static <A> @NonNull Kind<TryKind.Witness, A> failure(@NonNull Throwable throwable) {
    return wrap(Try.failure(throwable));
  }

  /**
   * Executes a supplier and wraps the result or exception in {@code Kind<TryKind.Witness, A>}. Uses
   * {@link Try#of(Supplier)}.
   *
   * @param <A> The element type.
   * @param supplier The {@link Supplier} to execute. Must be {@code @NonNull}.
   * @return A {@code Kind<TryKind.Witness, A>} representing the outcome.
   * @throws NullPointerException if supplier is null (delegated to Try.of).
   */
  public static <A> @NonNull Kind<TryKind.Witness, A> tryOf(
      @NonNull Supplier<? extends A> supplier) {
    return wrap(Try.of(supplier));
  }
}
