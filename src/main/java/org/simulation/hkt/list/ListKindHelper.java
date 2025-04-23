package org.simulation.hkt.list;

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
   * @param kind
   * @return The underlying list or an empty list.
   * @param <A>
   */
  public static <A> List<A> unwrap(Kind<ListKind<?>, A> kind) {
    return switch(kind) {
      // Check if the holder's list is null, return emptyList if it is
      case ListHolder<A> holder -> holder.list() != null ? holder.list() : Collections.emptyList();
      case null, default -> Collections.emptyList(); // Return default for null or unknown types
    };
  }

  public static <A> ListKind<A> wrap(List<A> list) {
    // It's good practice to prevent wrapping null lists directly if unintended
    Objects.requireNonNull(list, "Input list cannot be null for wrap");
    return new ListHolder<>(list);
  }

  record ListHolder<A>(List<A> list) implements ListKind<A> {
  }
}