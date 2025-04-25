package org.simulation.hkt.lazy;

import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

public final class LazyKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Lazy";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a LazyHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "LazyHolder contained null Lazy instance";

  private LazyKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder
  record LazyHolder<A>(@NonNull Lazy<A> lazyInstance) implements LazyKind<A> {}

  // Wrap
  public static <A> @NonNull LazyKind<A> wrap(@NonNull Lazy<A> lazy) {
    Objects.requireNonNull(lazy, "Input Lazy cannot be null for wrap");
    return new LazyHolder<>(lazy);
  }

  /**
   * Unwraps a LazyKind back to the concrete Lazy<A> type. Throws KindUnwrapException if the Kind is
   * null, not a LazyHolder, or the holder contains a null Lazy instance.
   *
   * <p>Refactored using Java 21+ Pattern Matching for switch.
   *
   * @param kind The LazyKind instance. (@Nullable allows checking null input)
   * @param <A> The element type.
   * @return The underlying, non-null Lazy<A>. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings(
      "unchecked") // Cast inside switch is necessary due to pattern matching on LazyHolder<?>
  public static <A> @NonNull Lazy<A> unwrap(@Nullable Kind<LazyKind<?>, A> kind) {
    // Use switch expression with pattern matching
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a LazyHolder (using record pattern to extract 'lazyInstance')
      case LazyHolder(var lazyInstance) -> {
        // Check if the extracted Lazy instance inside the holder is null
        if (lazyInstance == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // Cast is still necessary because the pattern matched LazyHolder<?>
        // The cast is safe here after the null check.
        yield (Lazy<A>) lazyInstance;
      }
      // Case 3: Input Kind is non-null but not a LazyHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  // Convenience factory using Lazy.defer
  public static <A> @NonNull Kind<LazyKind<?>, A> defer(@NonNull Supplier<A> computation) {
    return wrap(Lazy.defer(computation));
  }

  // Convenience factory using Lazy.now
  public static <A> @NonNull Kind<LazyKind<?>, A> now(@Nullable A value) {
    return wrap(Lazy.now(value));
  }

  // Convenience for forcing evaluation via the Kind
  public static <A> @Nullable A force(@NonNull Kind<LazyKind<?>, A> kind) {
    return unwrap(kind).force();
  }
}
