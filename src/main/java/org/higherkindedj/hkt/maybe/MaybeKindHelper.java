package org.higherkindedj.hkt.maybe;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class MaybeKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Maybe";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "MaybeHolder contained null Maybe instance";

  private MaybeKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a MaybeKind back to the concrete {@code Maybe<A>} type. Throws KindUnwrapException if
   * the Kind is null, not a MaybeHolder, or the holder contains a null Maybe instance.
   *
   * @param kind The MaybeKind instance. (@Nullable allows checking null input)
   * @param <A> The element type.
   * @return The underlying, non-null {@code Maybe<A>}. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked") // For casting holder.maybe() - safe after checks
  public static <A> @NonNull Maybe<A> unwrap(@Nullable Kind<MaybeKind<?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }

    if (kind instanceof MaybeHolder<?>(Maybe<?> maybe)) {
      if (maybe == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      return (Maybe<A>) maybe; // Cast is safe here
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /**
   * Wraps a concrete {@code Maybe<A>} value into the MaybeKind type. Requires a non-null Maybe
   * instance as input.
   */
  public static <A> @NonNull MaybeKind<A> wrap(@NonNull Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for wrap");
    return new MaybeHolder<>(maybe);
  }

  /**
   * Helper to wrap a Just value directly into MaybeKind. Throws NullPointerException if value is
   * null.
   */
  public static <A> @NonNull Kind<MaybeKind<?>, A> just(@NonNull A value) {
    return wrap(Maybe.just(value));
  }

  /** Helper to get the Nothing Kind directly. */
  public static <A> @NonNull Kind<MaybeKind<?>, A> nothing() {
    return wrap(Maybe.nothing());
  }

  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> {}
}
