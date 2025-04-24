package org.simulation.hkt.optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

import java.util.Objects;
import java.util.Optional;

public final class OptionalKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Optional";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "OptionalHolder contained null Optional instance";


  private OptionalKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps an OptionalKind back to the concrete Optional<A> type.
   * Throws KindUnwrapException if the Kind is null, not an OptionalHolder,
   * or the holder contains a null Optional instance.
   *
   * @param kind The OptionalKind instance. (@Nullable allows checking null input)
   * @param <A>  The element type.
   * @return The underlying, non-null Optional<A>. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked")
  public static <A> @NonNull Optional<A> unwrap(@Nullable Kind<OptionalKind<?>, A> kind){
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }

    if (kind instanceof OptionalHolder<?>(Optional<?> optional)) {
      if (optional == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      return (Optional<A>) optional; // Cast is safe here
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /**
   * Wraps a concrete Optional<A> value into the OptionalKind simulation type.
   * Requires a non-null Optional as input.
   */
  public static <A> @NonNull OptionalKind<A> wrap(@NonNull Optional<A> optional){
    Objects.requireNonNull(optional, "Input Optional cannot be null for wrap");
    return new OptionalHolder<>(optional);
  }

  record OptionalHolder<A>(Optional<A> optional) implements OptionalKind<A> {
  }
}