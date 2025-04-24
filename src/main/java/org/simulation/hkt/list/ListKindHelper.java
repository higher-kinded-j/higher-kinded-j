package org.simulation.hkt.list;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ListKindHelper {

  private ListKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * returns empty list for null or unknown types, or if the holder contains a null list.
   * @param kind The Kind instance (Nullable).
   * @return The underlying list or an empty list (NonNull).
   * @param <A> The element type.
   */
  @SuppressWarnings("unchecked") // For casting ListHolder
  public static <A> @NonNull List<A> unwrap(@Nullable Kind<ListKind<?>, A> kind) {
    return switch(kind) {
      // Check if the holder's list is null, return emptyList if it is
      case ListHolder<?> holder -> {
        List<?> heldList = holder.list();
        yield heldList != null ? (List<A>) heldList : Collections.emptyList();
      }
      case null, default -> Collections.emptyList(); // Return default for null or unknown types
    };
  }

  /**
   * Wraps a concrete List<A> value into the ListKind simulation type.
   * Requires a non-null List as input.
   * @param list The List instance to wrap (NonNull).
   * @return The wrapped ListKind (NonNull).
   */
  public static <A> @NonNull ListKind<A> wrap(@NonNull List<A> list) {
    // It's good practice to prevent wrapping null lists directly if unintended
    Objects.requireNonNull(list, "Input list cannot be null for wrap");
    return new ListHolder<>(list);
  }

  // Internal holder record - field assumed NonNull based on wrap check
  record ListHolder<A>(@NonNull List<A> list) implements ListKind<A> {
  }
}