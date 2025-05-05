package org.higherkindedj.hkt.optional;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Helper class for working with {@link OptionalKind} HKT simulation. */
public final class OptionalKindHelper {

  // Error Messages (ensure these constants are defined within this class)
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Optional";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "OptionalHolder contained null Optional instance";

  private OptionalKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: 'optional' component is NOT marked @NonNull
  record OptionalHolder<A>(Optional<A> optional) implements OptionalKind<A> {}

  /**
   * Unwraps an OptionalKind back to the concrete {@code Optional<A>} type. Throws
   * KindUnwrapException if the Kind is null, not an OptionalHolder, or the holder contains a null
   * Optional instance.
   *
   * @param <A> The element type.
   * @param kind The OptionalKind instance. (@Nullable allows checking null input)
   * @return The underlying, non-null {@code Optional<A>}. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails due to null input, wrong type, or the holder
   *     containing a null Optional.
   */
  @SuppressWarnings("unchecked")
  public static <A> @NonNull Optional<A> unwrap(@Nullable Kind<OptionalKind<?>, A> kind) {
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      // Case 2: Input Kind is an OptionalHolder (extract inner 'optional')
      case OptionalKindHelper.OptionalHolder<?>(var optional) -> {
        // Check if the extracted Optional instance itself is null.
        // This check remains necessary because the record component is not @NonNull.
        if (optional == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        yield (Optional<A>) optional;
      }
      // Case 3: Input Kind is non-null but not an OptionalHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Optional<A>} value into the OptionalKind type. Requires a non-null
   * Optional as input.
   *
   * @param <A> The element type.
   * @param optional The concrete {@code Optional<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code OptionalKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if optional is null.
   */
  public static <A> @NonNull OptionalKind<A> wrap(@NonNull Optional<A> optional) {
    Objects.requireNonNull(optional, "Input Optional cannot be null for wrap");
    return new OptionalHolder<>(optional);
  }
}
