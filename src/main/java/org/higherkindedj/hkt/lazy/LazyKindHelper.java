package org.higherkindedj.hkt.lazy;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link LazyKind} HKT simulation. Provides static methods for
 * wrapping, unwrapping, creating, and forcing {@link Lazy} instances within the Kind simulation.
 */
public final class LazyKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Lazy";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a LazyHolder: ";
  // This message is technically redundant now due to @NonNull, but kept for consistency/history
  public static final String INVALID_HOLDER_STATE_MSG = "LazyHolder contained null Lazy instance";

  private LazyKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: lazyInstance component IS marked @NonNull
  record LazyHolder<A>(@NonNull Lazy<A> lazyInstance) implements LazyKind<A> {}

  /**
   * Unwraps a LazyKind back to the concrete {@code Lazy<A>} type. Throws KindUnwrapException if the
   * Kind is null or not a valid LazyHolder.
   *
   * @param <A> The type of the value produced by the Lazy computation.
   * @param kind The {@code Kind<LazyKind<?>, A>} instance to unwrap. Can be {@code @Nullable}.
   * @return The unwrapped, non-null {@code Lazy<A>} instance. Returns {@code @NonNull}.
   * @throws KindUnwrapException if the input {@code kind} is null or not an instance of {@code
   *     LazyHolder}.
   */
  @SuppressWarnings("unchecked") // For casting lazyInstance - safe after pattern match
  public static <A> @NonNull Lazy<A> unwrap(@Nullable Kind<LazyKind<?>, A> kind) {
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a LazyHolder (record pattern extracts non-null lazyInstance)
      // The @NonNull contract on LazyHolder.lazyInstance guarantees it's not null here.
      case LazyKindHelper.LazyHolder<?>(var lazyInstance) ->
          // Cast is safe because pattern matched and lazyInstance is known non-null.
          (Lazy<A>) lazyInstance;

      // Case 3: Input Kind is non-null but not a LazyHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Lazy<A>} value into the LazyKind Higher-Kinded-J type.
   *
   * @param <A> The type of the value produced by the Lazy computation.
   * @param lazy The concrete {@code Lazy<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code LazyKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if lazy is null.
   */
  public static <A> @NonNull LazyKind<A> wrap(@NonNull Lazy<A> lazy) {
    Objects.requireNonNull(lazy, "Input Lazy cannot be null for wrap");
    return new LazyHolder<>(lazy);
  }

  /**
   * Convenience factory to create a {@code LazyKind<A>} by deferring the execution of a {@code
   * ThrowableSupplier}. Wraps {@link Lazy#defer(ThrowableSupplier)}.
   *
   * @param <A> The type of the value produced.
   * @param computation The {@link ThrowableSupplier} representing the deferred computation. Must be
   *     {@code @NonNull}.
   * @return A new {@code Kind<LazyKind<?>, A>} wrapping the deferred computation. Returns
   *     {@code @NonNull}.
   * @throws NullPointerException if computation is null.
   */
  public static <A> @NonNull Kind<LazyKind<?>, A> defer(@NonNull ThrowableSupplier<A> computation) {
    // Lazy.defer performs null check on computation
    return wrap(Lazy.defer(computation));
  }

  /**
   * Convenience factory to create an already evaluated {@code LazyKind<A>} holding a known value.
   * Wraps {@link Lazy#now(Object)}.
   *
   * @param <A> The type of the value.
   * @param value The pre-computed value. Can be {@code @Nullable}.
   * @return A new {@code Kind<LazyKind<?>, A>} wrapping the evaluated value. Returns
   *     {@code @NonNull}.
   */
  public static <A> @NonNull Kind<LazyKind<?>, A> now(@Nullable A value) {
    return wrap(Lazy.now(value));
  }

  /**
   * Convenience method for forcing the evaluation of the Lazy computation held within the Kind
   * wrapper and getting the result. Calls {@link #unwrap(Kind)} and then {@link Lazy#force()}.
   *
   * @param <A> The type of the result.
   * @param kind The {@code Kind<LazyKind<?>, A>} holding the Lazy computation. Must be
   *     {@code @NonNull}.
   * @return The result of the Lazy computation. Can be {@code @Nullable} depending on A.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   * @throws Throwable if the underlying Lazy computation throws an exception during evaluation.
   */
  public static <A> @Nullable A force(@NonNull Kind<LazyKind<?>, A> kind) throws Throwable {
    // `unwrap` throws KindUnwrapException if kind is invalid
    return unwrap(kind).force();
  }
}
