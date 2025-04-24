package org.simulation.hkt.optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;

import java.util.Objects;
import java.util.Optional;

public final class OptionalKindHelper {

  private OptionalKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps an OptionalKind back to the concrete Optional<A> type.
   * Handles null Kind, unknown Kind types, and holders containing null Optionals
   * by returning Optional.empty().
   * @param kind The Kind instance (Nullable).
   * @return The underlying Optional or Optional.empty() (NonNull).
   */
  @SuppressWarnings("unchecked") // For casting OptionalHolder
  public static <A> @NonNull Optional<A> unwrap(@Nullable Kind<OptionalKind<?>, A> kind){
    return switch(kind) {
      // Check if the holder's optional is null, return empty() if it is
      case OptionalHolder<?> holder -> {
        Optional<?> heldOptional = holder.optional();
        yield heldOptional != null ? (Optional<A>) heldOptional : Optional.empty();
      }
      case null, default -> Optional.empty(); // Return default for null or unknown types
    };
  }

  /**
   * Wraps a concrete Optional<A> value into the OptionalKind simulation type.
   * Requires a non-null Optional as input.
   * @param optional The Optional instance to wrap (NonNull).
   * @return The wrapped OptionalKind (NonNull).
   */
  public static <A> @NonNull OptionalKind<A> wrap(@NonNull Optional<A> optional){
    // Prevent wrapping null Optionals directly
    Objects.requireNonNull(optional, "Input Optional cannot be null for wrap");
    return new OptionalHolder<>(optional);
  }


  // Internal holder record - field assumed NonNull based on wrap check
  record OptionalHolder<A>(@NonNull Optional<A> optional) implements OptionalKind<A> {
  }
}