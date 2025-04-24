package org.simulation.hkt.maybe;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;

import java.util.Objects;

public final class MaybeKindHelper {

  private MaybeKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a MaybeKind back to the concrete Maybe<A> type.
   * Handles null Kind, unknown Kind types, and holders containing null Maybes
   * by returning Maybe.nothing().
   * @param kind The Kind instance (Nullable).
   * @return The underlying Maybe or Maybe.nothing() (NonNull).
   */
  @SuppressWarnings("unchecked") // For casting MaybeHolder
  public static <A> @NonNull Maybe<A> unwrap(@Nullable Kind<MaybeKind<?>, A> kind) {
    return switch(kind) {
      // Check if the holder's maybe is null, return nothing() if it is
      case MaybeHolder<?> holder -> {
        Maybe<?> heldMaybe = holder.maybe();
        yield heldMaybe != null ? (Maybe<A>)heldMaybe : Maybe.nothing();
      }
      case null, default -> Maybe.nothing(); // Return default for null or unknown types
    };
  }

  /**
   * Wraps a concrete Maybe<A> value into the MaybeKind simulation type.
   * Requires a non-null Maybe instance as input.
   * @param maybe The Maybe instance to wrap (NonNull).
   * @return The wrapped MaybeKind (NonNull).
   */
  public static <A> @NonNull MaybeKind<A> wrap(@NonNull Maybe<A> maybe) {
    // Prevent wrapping null Maybe instances directly
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for wrap");
    return new MaybeHolder<>(maybe);
  }

  /**
   * Helper to wrap a Just value directly into MaybeKind.
   * Throws NullPointerException if value is null.
   * @param value The value (NonNull).
   * @return The wrapped MaybeKind (NonNull).
   */
  public static <A> @NonNull Kind<MaybeKind<?>, A> just(@NonNull A value) {
    // Maybe.just throws if value is null, wrap handles the rest
    return wrap(Maybe.just(value));
  }

  /**
   * Helper to get the Nothing Kind directly.
   * @return The wrapped MaybeKind for Nothing (NonNull).
   */
  public static <A> @NonNull Kind<MaybeKind<?>, A> nothing() {
    return wrap(Maybe.nothing());
  }

  // Internal holder record - field assumed NonNull based on wrap check
  record MaybeHolder<A>(@NonNull Maybe<A> maybe) implements MaybeKind<A> { }
}