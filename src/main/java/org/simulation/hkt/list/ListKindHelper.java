package org.simulation.hkt.list;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

public final class ListKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for List";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ListHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "ListHolder contained null List instance";

  private ListKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a ListKind back to the concrete List<A> type. Throws KindUnwrapException if the Kind is
   * null, not a ListHolder, or the holder contains a null List instance.
   *
   * @param kind The ListKind instance. (@Nullable allows checking null input)
   * @param <A> The element type.
   * @return The underlying, non-null List<A>. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  public static <A> @NonNull List<A> unwrap(@Nullable Kind<ListKind<?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }

    if (kind instanceof ListHolder<A>(List<A> list)) {
      if (list == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      return list;
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /**
   * Wraps a concrete List<A> value into the ListKind simulation type. Requires a non-null List
   * instance as input.
   */
  public static <A> @NonNull ListKind<A> wrap(@NonNull List<A> list) {
    Objects.requireNonNull(list, "Input list cannot be null for wrap");
    return new ListHolder<>(list);
  }

  record ListHolder<A>(List<A> list) implements ListKind<A> {}
}
