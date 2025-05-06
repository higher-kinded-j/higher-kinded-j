package org.higherkindedj.hkt.trymonad;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link TryKind} HKT simulation. Provides static methods for
 * wrapping, unwrapping, and creating {@link Try} instances within the Kind simulation.
 */
public final class TryKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Try";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a TryHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "TryHolder contained null Try instance";

  private TryKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: tryInstance component is NOT marked @NonNull
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> {}

  /**
   * Unwraps a TryKind back to the concrete {@code Try<A>} type. Throws KindUnwrapException if the
   * Kind is null, not a TryHolder, or the holder contains a null Try instance.
   *
   * @param <A> The element type potentially held by the Try.
   * @param kind The TryKind instance. Can be {@code @Nullable}.
   * @return The underlying, non-null {@code Try<A>}. Returns {@code @NonNull}.
   * @throws KindUnwrapException if unwrapping fails due to null input, wrong type, or the holder
   *     containing a null Try instance.
   */
  @SuppressWarnings("unchecked") // For casting tryInstance - safe after checks
  public static <A> @NonNull Try<A> unwrap(@Nullable Kind<TryKind<?>, A> kind) {
    // Use switch expression with pattern matching
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a TryHolder (extract inner 'tryInstance')
      case TryKindHelper.TryHolder<?>(var tryInstance) -> {
        // Check if the extracted Try instance itself is null.
        // Necessary as the record component is not marked @NonNull.
        if (tryInstance == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // Cast is safe after type check and null check
        yield (Try<A>) tryInstance;
      }

      // Case 3: Input Kind is non-null but not a TryHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Try<A>} value into the TryKind simulation type. Requires a non-null Try
   * instance as input.
   *
   * @param <A> The element type.
   * @param tryInstance The concrete {@code Try<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code TryKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if tryInstance is null.
   */
  public static <A> @NonNull TryKind<A> wrap(@NonNull Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for wrap");
    return new TryHolder<>(tryInstance);
  }

  /**
   * Wraps a successful value directly into TryKind. Uses {@link Try#success(Object)}.
   *
   * @param <A> The element type.
   * @param value The successful value. Can be {@code @Nullable}.
   * @return A {@code Kind<TryKind<?>, A>} representing success. Returns {@code @NonNull}.
   */
  public static <A> @NonNull Kind<TryKind<?>, A> success(
      @Nullable A value) { // Allow null success value
    return wrap(Try.success(value));
  }

  /**
   * Wraps a failure directly into TryKind. Uses {@link Try#failure(Throwable)}.
   *
   * @param <A> The phantom element type.
   * @param throwable The exception representing the failure. Must be {@code @NonNull}.
   * @return A {@code Kind<TryKind<?>, A>} representing failure. Returns {@code @NonNull}.
   * @throws NullPointerException if throwable is null.
   */
  public static <A> @NonNull Kind<TryKind<?>, A> failure(@NonNull Throwable throwable) {
    // Try.failure performs null check
    return wrap(Try.failure(throwable));
  }

  /**
   * Executes a supplier and wraps the result or exception in TryKind. Uses {@link
   * Try#of(Supplier)}.
   *
   * @param <A> The element type.
   * @param supplier The {@link Supplier} to execute. Must be {@code @NonNull}.
   * @return A {@code Kind<TryKind<?>, A>} representing the outcome. Returns {@code @NonNull}.
   * @throws NullPointerException if supplier is null.
   */
  public static <A> @NonNull Kind<TryKind<?>, A> tryOf(@NonNull Supplier<? extends A> supplier) {
    // Try.of performs null check
    return wrap(Try.of(supplier));
  }
}
