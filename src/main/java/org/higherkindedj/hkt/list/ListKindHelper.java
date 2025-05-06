package org.higherkindedj.hkt.list;

import java.util.List;
import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link ListKind} HKT simulation. Provides static methods for
 * wrapping and unwrapping {@link List} instances.
 */
public final class ListKindHelper {

  // Error Messages (ensure these constants are defined within this class)
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for List";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ListHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "ListHolder contained null List instance";

  private ListKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: 'list' component is NOT marked @NonNull
  record ListHolder<A>(List<A> list) implements ListKind<A> {}

  /**
   * Unwraps a ListKind back to the concrete {@code List<A>} type. Throws KindUnwrapException if the
   * Kind is null, not a ListHolder, or the holder contains a null List instance.
   *
   * @param <A> The element type of the List.
   * @param kind The {@code Kind<ListKind<?>, A>} instance to unwrap. Can be {@code @Nullable}.
   * @return The underlying, non-null {@code List<A>}. Returns {@code @NonNull}.
   * @throws KindUnwrapException if unwrapping fails due to null input, wrong type, or the holder
   *     containing a null List.
   */
  public static <A> @NonNull List<A> unwrap(@Nullable Kind<ListKind<?>, A> kind) {
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a ListHolder (extract inner 'list')
      // We need the <A> type parameter here for the pattern matching
      case ListKindHelper.ListHolder<A>(var list) -> {
        // Check if the extracted List instance itself is null.
        // Necessary as the record component is not marked @NonNull.
        if (list == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // No cast needed as the pattern already provides List<A>
        yield list;
      }

      // Case 3: Input Kind is non-null but not a ListHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code List<A>} value into the ListKind Higher-Kinded-J type.
   *
   * @param <A> The element type.
   * @param list The concrete {@code List<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code ListKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if list is null.
   */
  public static <A> @NonNull ListKind<A> wrap(@NonNull List<A> list) {
    Objects.requireNonNull(list, "Input list cannot be null for wrap");
    return new ListHolder<>(list);
  }
}
