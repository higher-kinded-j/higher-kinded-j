package org.higherkindedj.hkt.list;

import java.util.List;
import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing helper methods for working with {@link java.util.List} in the context
 * of higher-kinded types (HKT), using {@link ListKind} as the HKT marker.
 *
 * <p>This class offers static methods for:
 *
 * <ul>
 *   <li>Safely converting between a {@link List} instance and its {@link Kind} representation
 *       ({@link #wrap(List)} and {@link #unwrap(Kind)}).
 * </ul>
 *
 * <p>It acts as a bridge between the concrete {@link java.util.List} type and the abstract {@link
 * Kind} interface, enabling {@code List} to be used with generic HKT abstractions like {@link
 * org.higherkindedj.hkt.Functor}, {@link org.higherkindedj.hkt.Applicative}, and {@link
 * org.higherkindedj.hkt.Monad}.
 *
 * <p>The {@link #unwrap(Kind)} method uses an internal private record ({@code ListHolder}) that
 * implements {@link ListKind} to hold the actual {@code List} instance.
 *
 * @see java.util.List
 * @see ListKind
 * @see Kind
 * @see KindUnwrapException
 */
public final class ListKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for List";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ListHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null}
   * List instance. This should ideally not occur if {@link #wrap(List)} enforces non-null List
   * instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "ListHolder contained null List instance";

  /** Private constructor to prevent instantiation of this utility class. All methods are static. */
  private ListKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link ListKind} to hold the concrete {@link List} instance. This
   * is used by {@link #wrap(List)} and {@link #unwrap(Kind)}.
   *
   * @param <A> The element type of the List.
   * @param list The actual {@link List} instance. Note: While the `list` field itself is not
   *     annotated `@NonNull` here, {@link #wrap(List)} requires a non-null list, and {@link
   *     #unwrap(Kind)} checks for nullity of this field.
   */
  record ListHolder<A>(List<A> list) implements ListKind<A> {}

  /**
   * Unwraps a {@code Kind<ListKind<?>, A>} back to its concrete {@link List List<A>} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents a {@link List}.
   *
   * @param <A> The element type of the {@code List}.
   * @param kind The {@code Kind<ListKind<?>, A>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@link List List<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code ListHolder}, or if the holder's internal {@code List} instance is {@code null}
   *     (which would indicate an internal issue with {@link #wrap(List)}).
   */
  public static <A> @NonNull List<A> unwrap(@Nullable Kind<ListKind<?>, A> kind) {
    return switch (kind) {
      case null ->
          // If the input Kind itself is null.
          throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case ListKindHelper.ListHolder<A> holder -> { // Explicit type <A> for holder pattern
        // If it's a ListHolder, extract the 'list' component.
        List<A> list = holder.list(); // Access record component
        if (list == null) {
          // This case should ideally not be reached if wrap() enforces non-null lists.
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // No further cast needed as the pattern match provides List<A>.
        yield list;
      }
      default ->
          // If the Kind is non-null but not the expected ListHolder type.
          throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link List List<A>} instance into its higher-kinded representation, {@code
   * Kind<ListKind<?>, A>}.
   *
   * @param <A> The element type of the {@code List}.
   * @param list The non-null, concrete {@link List List<A>} instance to wrap. The list itself must
   *     not be null, though it can be an empty list.
   * @return A non-null {@link ListKind ListKind<A>} (which is also a {@code Kind<ListKind<?>, A>})
   *     representing the wrapped {@code List}.
   * @throws NullPointerException if {@code list} is {@code null}.
   */
  public static <A> @NonNull ListKind<A> wrap(@NonNull List<A> list) {
    Objects.requireNonNull(list, "Input list cannot be null for wrap");
    return new ListHolder<>(list);
  }
}
