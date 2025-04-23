package org.simulation.hkt.optional;

import org.simulation.hkt.Kind;

import java.util.Objects;
import java.util.Optional;

public class OptionalKindHelper {

  /**
   * Unwraps an OptionalKind back to the concrete Optional<A> type.
   * Handles null Kind, unknown Kind types, and holders containing null Optionals
   * by returning Optional.empty().
   */
  public static <A> Optional<A> unwrap(Kind<OptionalKind<?>, A> kind){
    return switch(kind) {
      // Check if the holder's optional is null, return empty() if it is
      case OptionalHolder<A> holder -> holder.optional() != null ? holder.optional() : Optional.empty();
      case null, default -> Optional.empty(); // Return default for null or unknown types
    };
  }

  /**
   * Wraps a concrete Optional<A> value into the OptionalKind simulation type.
   * Requires a non-null Optional as input.
   */
  public static <A> OptionalKind<A> wrap(Optional<A> optional){
    // Prevent wrapping null Optionals directly
    Objects.requireNonNull(optional, "Input Optional cannot be null for wrap");
    return new OptionalHolder<>(optional);
  }


  record OptionalHolder<A>(Optional<A> optional) implements OptionalKind<A> {
  }
}
