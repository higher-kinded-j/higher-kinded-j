package org.simulation.hkt.maybe;

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
   */
  public static <A> Maybe<A> unwrap(Kind<MaybeKind<?>, A> kind) {
    return switch(kind) {
      // Check if the holder's maybe is null, return nothing() if it is
      case MaybeHolder<A> holder -> holder.maybe() != null ? holder.maybe() : Maybe.nothing();
      case null, default -> Maybe.nothing(); // Return default for null or unknown types
    };
  }

  /**
   * Wraps a concrete Maybe<A> value into the MaybeKind simulation type.
   * Requires a non-null Maybe instance as input.
   */
  public static <A> MaybeKind<A> wrap(Maybe<A> maybe) {
    // Prevent wrapping null Maybe instances directly
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for wrap");
    return new MaybeHolder<>(maybe);
  }

  /**
   * Helper to wrap a Just value directly into MaybeKind.
   * Throws NullPointerException if value is null.
   */
  public static <A> Kind<MaybeKind<?>, A> just(A value) {
    // Maybe.just throws if value is null, wrap handles the rest
    return wrap(Maybe.just(value));
  }

  /**
   * Helper to get the Nothing Kind directly.
   */
  public static <A> Kind<MaybeKind<?>, A> nothing() {
    return wrap(Maybe.nothing());
  }

  // Internal holder record
  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> { }
}
