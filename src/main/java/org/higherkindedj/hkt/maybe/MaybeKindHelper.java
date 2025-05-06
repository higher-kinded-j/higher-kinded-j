package org.higherkindedj.hkt.maybe;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link MaybeKind} HKT simulation. Provides static methods for
 * wrapping, unwrapping, and creating {@link Maybe} instances within the Kind simulation.
 */
public final class MaybeKindHelper {

  // Error Messages (ensure these constants are defined within this class)
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Maybe";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "MaybeHolder contained null Maybe instance";

  private MaybeKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: 'maybe' component is NOT marked @NonNull
  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> {}

  /**
   * Unwraps a MaybeKind back to the concrete {@code Maybe<A>} type. Throws KindUnwrapException if
   * the Kind is null, not a MaybeHolder, or the holder contains a null Maybe instance.
   *
   * @param <A> The element type.
   * @param kind The MaybeKind instance. (@Nullable allows checking null input)
   * @return The underlying, non-null {@code Maybe<A>}. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails due to null input, wrong type, or the holder
   *     containing a null Maybe.
   */
  @SuppressWarnings("unchecked") // For casting maybe - safe after checks
  public static <A> @NonNull Maybe<A> unwrap(@Nullable Kind<MaybeKind<?>, A> kind) {
    // Use switch expression with pattern matching
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a MaybeHolder (extract inner 'maybe')
      case MaybeKindHelper.MaybeHolder<?>(var maybe) -> {
        // Check if the extracted Maybe instance itself is null.
        // This check is necessary because the record component is not @NonNull.
        if (maybe == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // Cast is safe after type check and null check
        yield (Maybe<A>) maybe;
      }

      // Case 3: Input Kind is non-null but not a MaybeHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Maybe<A>} value into the MaybeKind type. Requires a non-null Maybe
   * instance as input.
   *
   * @param <A> The element type.
   * @param maybe The concrete {@code Maybe<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code MaybeKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if maybe is null.
   */
  public static <A> @NonNull MaybeKind<A> wrap(@NonNull Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for wrap");
    return new MaybeHolder<>(maybe);
  }

  /**
   * Helper factory to wrap a Just value directly into MaybeKind. Throws NullPointerException if
   * value is null, consistent with {@link Maybe#just(Object)}.
   *
   * @param <A> The element type (must be NonNull).
   * @param value The non-null value to wrap in Just. Must be {@code @NonNull}.
   * @return The {@code Kind<MaybeKind<?>, A>} representing Just(value). Returns {@code @NonNull}.
   * @throws NullPointerException if value is null.
   */
  public static <A> @NonNull Kind<MaybeKind<?>, A> just(@NonNull A value) {
    return wrap(Maybe.just(value));
  }

  /**
   * Helper factory to get the Nothing Kind directly. Wraps the singleton {@link Maybe#nothing()}.
   *
   * @param <A> The phantom element type.
   * @return The {@code Kind<MaybeKind<?>, A>} representing Nothing. Returns {@code @NonNull}.
   */
  public static <A> @NonNull Kind<MaybeKind<?>, A> nothing() {
    return wrap(Maybe.nothing());
  }
}
