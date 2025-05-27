// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing helper methods for working with {@link Lazy} in the context of
 * higher-kinded types (HKT), using {@link LazyKind.Witness} as the HKT marker.
 *
 * <p>This class offers static methods for:
 *
 * <ul>
 *   <li>Safely converting between a {@link Lazy} instance and its {@link Kind} representation
 *       ({@link #wrap(Lazy)} and {@link #unwrap(Kind)}).
 *   <li>Creating {@link Lazy} instances, wrapped as {@link Kind}, from suppliers or immediate
 *       values ({@link #defer(ThrowableSupplier)}, {@link #now(Object)}).
 *   <li>Forcing the evaluation of a {@code Lazy} computation represented as a {@link Kind} ({@link
 *       #force(Kind)}).
 * </ul>
 *
 * <p>It acts as a bridge between the concrete {@link Lazy} type, which encapsulates a deferred
 * computation, and the abstract {@link Kind} interface used in generic functional programming
 * patterns.
 *
 * <p>The {@link #unwrap(Kind)} method uses an internal private record ({@code LazyHolder}) that
 * implements {@link LazyKind} to hold the actual {@code Lazy} instance.
 *
 * @see Lazy
 * @see LazyKind
 * @see LazyKind.Witness
 * @see ThrowableSupplier
 * @see Kind
 * @see KindUnwrapException
 */
public final class LazyKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Lazy";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a LazyHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null}
   * Lazy instance. This should ideally not occur if {@link #wrap(Lazy)} enforces non-null Lazy
   * instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "LazyHolder contained null Lazy instance";

  /** Private constructor to prevent instantiation of this utility class. All methods are static. */
  private LazyKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link LazyKind} to hold the concrete {@link Lazy} instance. This
   * is used by {@link #wrap(Lazy)} and {@link #unwrap(Kind)}. By implementing {@code LazyKind<A>},
   * it implicitly becomes {@code Kind<LazyKind.Witness, A>}.
   *
   * @param <A> The result type of the Lazy computation.
   * @param lazyInstance The non-null, actual {@link Lazy} instance.
   */
  record LazyHolder<A>(@NonNull Lazy<A> lazyInstance) implements LazyKind<A> {}

  /**
   * Unwraps a {@code Kind<LazyKind.Witness, A>} back to its concrete {@link Lazy Lazy<A>} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents a {@link Lazy} computation.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param kind The {@code Kind<LazyKind.Witness, A>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@link Lazy Lazy<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code LazyHolder}, or if the holder's internal {@code Lazy} instance is {@code null}
   *     (which would indicate an internal issue with {@link #wrap(Lazy)}).
   */
  @SuppressWarnings("unchecked") // For casting lazyInstance - safe after pattern match.
  public static <A> @NonNull Lazy<A> unwrap(@Nullable Kind<LazyKind.Witness, A> kind) {
    return switch (kind) {
      case null ->
          // If the input Kind itself is null.
          throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case LazyKindHelper.LazyHolder<?> holder -> {
        // The LazyHolder record's 'lazyInstance' component is @NonNull.
        // So, holder.lazyInstance() is guaranteed non-null if the holder itself is valid.
        // The cast is safe due to the pattern match.
        yield (Lazy<A>) holder.lazyInstance();
      }
      default ->
          // If the Kind is non-null but not the expected LazyHolder type.
          throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link Lazy<A>} instance into its higher-kinded representation, {@code
   * Kind<LazyKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param lazy The non-null, concrete {@link Lazy Lazy<A>} instance to wrap.
   * @return A non-null {@link LazyKind<A>} (which is also a {@code Kind<LazyKind.Witness, A>})
   *     representing the wrapped {@code Lazy} computation.
   * @throws NullPointerException if {@code Lazy} is {@code null}.
   */
  public static <A> @NonNull LazyKind<A> wrap(@NonNull Lazy<A> lazy) {
    Objects.requireNonNull(lazy, "Input Lazy cannot be null for wrap");
    return new LazyHolder<>(lazy);
  }

  /**
   * Creates a {@link Kind<LazyKind.Witness,A>} by deferring the execution of a {@link
   * ThrowableSupplier}. The actual computation is performed only when {@link Lazy#force()} (or
   * {@link #force(Kind)}) is called.
   *
   * <p>This is a convenience factory method that delegates to {@link Lazy#defer(ThrowableSupplier)}
   * and then wraps the result using {@link #wrap(Lazy)}.
   *
   * @param <A> The type of the value that will be produced by the computation.
   * @param computation The non-null {@link ThrowableSupplier} representing the deferred
   *     computation. The supplier itself may return {@code null} or throw a {@link Throwable}.
   * @return A new, non-null {@code Kind<LazyKind.Witness, A>} representing the deferred {@code
   *     Lazy} computation.
   * @throws NullPointerException if {@code computation} is {@code null}.
   */
  public static <A> @NonNull Kind<LazyKind.Witness, A> defer(
      @NonNull ThrowableSupplier<A> computation) {
    // Lazy.defer will perform its own null check on the computation.
    return wrap(Lazy.defer(computation));
  }

  /**
   * Creates an already evaluated {@link Kind<LazyKind.Witness,A>} that holds a known value. The
   * {@link Lazy#force()} (or {@link #force(Kind)}) method on the resulting {@code Kind} will
   * immediately return this value without further computation.
   *
   * <p>This is a convenience factory method that delegates to {@link Lazy#now(Object)} and then
   * wraps the result using {@link #wrap(Lazy)}.
   *
   * @param <A> The type of the value.
   * @param value The pre-computed value to be wrapped. Can be {@code null}.
   * @return A new, non-null {@code Kind<LazyKind.Witness, A>} representing an already evaluated
   *     {@code Lazy} computation.
   */
  public static <A> @NonNull Kind<LazyKind.Witness, A> now(@Nullable A value) {
    return wrap(Lazy.now(value));
  }

  /**
   * Forces the evaluation of the {@link Lazy} computation held within the {@link Kind} wrapper and
   * retrieves its result.
   *
   * <p>If the computation has already been forced, this method returns the cached result. If it's
   * the first time, the underlying {@link ThrowableSupplier} is executed.
   *
   * <p>This is a convenience method that delegates to {@link #unwrap(Kind)} and then calls {@link
   * Lazy#force()}.
   *
   * @param <A> The type of the result produced by the {@code Lazy} computation.
   * @param kind The non-null {@code Kind<LazyKind.Witness, A>} holding the {@code Lazy}
   *     computation.
   * @return The result of the {@code Lazy} computation. Can be {@code null} if the computation
   *     produces a {@code null} value.
   * @throws KindUnwrapException if the input {@code kind} is invalid (e.g., null or wrong type).
   * @throws Throwable if the underlying {@link ThrowableSupplier} throws an exception during its
   *     first evaluation. Subsequent calls on the same instance will re-throw the same cached
   *     exception.
   */
  public static <A> @Nullable A force(@NonNull Kind<LazyKind.Witness, A> kind) throws Throwable {
    // unwrap will throw KindUnwrapException if kind is invalid.
    return unwrap(kind).force();
  }
}
