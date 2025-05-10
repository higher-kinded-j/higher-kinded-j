package org.higherkindedj.hkt.list;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link ListKind} and {@link Kind} representations of {@link List}.
 * This class provides utility methods to wrap and unwrap lists from their HKT form.
 */
public final class ListKindHelper {

  private ListKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Wraps a standard {@link java.util.List} into its higher-kinded representation, {@code
   * Kind<ListKind.Witness, A>}.
   *
   * <p>This method uses the static {@code ListKind.of(list)} factory method, which returns a {@code
   * ListView<A>} instance (which implements {@code ListKind<A>}, and therefore {@code
   * Kind<ListKind.Witness, A>}).
   *
   * @param list The {@link List} to wrap. Must not be null.
   * @param <A> The element type of the list.
   * @return The higher-kinded representation of the list, specifically an instance of {@link
   *     ListView}. Returns an HKT representation of an empty list if the input list is empty.
   */
  public static <A> @NonNull Kind<ListKind.Witness, A> wrap(@NonNull List<A> list) {
    Objects.requireNonNull(list, "list cannot be null for wrap");
    return ListKind.of(list);
  }

  /**
   * Unwraps a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}.
   *
   * <p>This method uses the {@code ListKind.narrow(kind).unwrap()} pattern.
   *
   * @param kind The higher-kinded representation of the list. Can be null.
   * @param <A> The element type of the list.
   * @return The underlying {@link java.util.List}. Returns an empty list if {@code kind} is null or
   *     represents an empty list. Returns the unwrapped list otherwise.
   * @throws ClassCastException if the provided {@code kind} is not actually a {@code ListKind}.
   */
  public static <A> @NonNull List<A> unwrap(@Nullable Kind<ListKind.Witness, A> kind) {
    if (kind == null) {
      return Collections.emptyList();
    }
    // ListKind.narrow performs the cast to ListKind<A>
    // and then unwrap() is called on the ListKind<A> interface.
    return ListKind.narrow(kind).unwrap();
  }

  /**
   * Unwraps a higher-kinded representation of a list, {@code Kind<ListKind.Witness, A>}, back to a
   * standard {@link java.util.List}, providing a default list if the kind is null.
   *
   * @param kind The higher-kinded representation of the list.
   * @param defaultValue The list to return if {@code kind} is null. Must not be null.
   * @param <A> The element type of the list.
   * @return The unwrapped list, or {@code defaultValue} if {@code kind} is null.
   * @throws ClassCastException if the provided {@code kind} is not actually a {@code ListKind}.
   */
  public static <A> @NonNull List<A> unwrapOr(
      @Nullable Kind<ListKind.Witness, A> kind, @NonNull List<A> defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue cannot be null");
    if (kind == null) {
      return defaultValue;
    }
    return ListKind.narrow(kind).unwrap();
  }
}
