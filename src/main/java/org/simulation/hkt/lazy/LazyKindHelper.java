package org.simulation.hkt.lazy;

import java.util.Objects;
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

  // Unwrap
  @SuppressWarnings("unchecked")
  public static <A> @NonNull Lazy<A> unwrap(@Nullable Kind<LazyKind<?>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case LazyHolder(var lazyInstance) -> {
        if (lazyInstance == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        yield (Lazy<A>) lazyInstance;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Convenience factory using Lazy.defer, accepting a ThrowableSupplier.
   *
   * @param computation The ThrowableSupplier computation. (NonNull)
   * @param <A> The value type.
   * @return A new LazyKind wrapping the deferred computation. (NonNull)
   */
  public static <A> @NonNull Kind<LazyKind<?>, A> defer(@NonNull ThrowableSupplier<A> computation) {
    return wrap(Lazy.defer(computation));
  }

  // Convenience factory using Lazy.now
  public static <A> @NonNull Kind<LazyKind<?>, A> now(@Nullable A value) {
    return wrap(Lazy.now(value));
  }

  /**
   * Convenience method for forcing evaluation via the Kind. Note: This method catches Throwable
   * from Lazy.force() and re-throws it. Callers might need to handle Throwable.
   *
   * @param kind The LazyKind to force. (NonNull)
   * @param <A> The value type.
   * @return The evaluated value. (Nullable)
   * @throws Throwable if the underlying Lazy computation fails.
   */
  public static <A> @Nullable A force(@NonNull Kind<LazyKind<?>, A> kind) throws Throwable {
    return unwrap(kind).force();
  }
}
