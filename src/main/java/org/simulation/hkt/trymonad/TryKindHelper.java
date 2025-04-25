package org.simulation.hkt.trymonad;

import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

public final class TryKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Try";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a TryHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "TryHolder contained null Try instance";

  private TryKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a TryKind back to the concrete Try<A> type. Throws KindUnwrapException if the Kind is
   * null, not a TryHolder, or the holder contains a null Try instance.
   *
   * @param kind The TryKind instance. (@Nullable allows checking null input)
   * @param <A> The element type.
   * @return The underlying, non-null Try<A>. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked") // For casting holder.tryInstance() - safe after checks
  public static <A> @NonNull Try<A> unwrap(@Nullable Kind<TryKind<?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }

    if (kind instanceof TryHolder<?>(Try<?> tryInstance)) {
      if (tryInstance == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      return (Try<A>) tryInstance; // Cast is safe here
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /**
   * Wraps a concrete Try<A> value into the TryKind simulation type. Requires a non-null Try
   * instance as input.
   */
  public static <A> @NonNull TryKind<A> wrap(@NonNull Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for wrap");
    return new TryHolder<>(tryInstance);
  }

  /** Wraps a successful value directly into TryKind. */
  public static <A> @NonNull Kind<TryKind<?>, A> success(
      @Nullable A value) { // Allow null success value
    return wrap(Try.success(value));
  }

  /** Wraps a failure directly into TryKind. */
  public static <A> @NonNull Kind<TryKind<?>, A> failure(@NonNull Throwable throwable) {
    return wrap(Try.failure(throwable));
  }

  /** Executes a supplier and wraps the result or exception in TryKind. */
  public static <A> @NonNull Kind<TryKind<?>, A> tryOf(@NonNull Supplier<? extends A> supplier) {
    return wrap(Try.of(supplier));
  }

  // Internal holder record
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> {}
}
