package org.higherkindedj.hkt.io;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link IOKind} HKT simulation. Provides static methods for
 * wrapping, unwrapping, creating, and running {@link IO} instances within the Kind simulation.
 */
public final class IOKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for IO";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an IOHolder: ";
  // This message is technically redundant now due to @NonNull, but kept for consistency/history
  public static final String INVALID_HOLDER_STATE_MSG = "IOHolder contained null IO instance";

  private IOKindHelper() {
    // prevent instantiation via reflection
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: ioInstance component IS marked @NonNull
  record IOHolder<A>(@NonNull IO<A> ioInstance) implements IOKind<A> {}

  /**
   * Unwraps an IOKind back to the concrete {@code IO<A>} type. Throws KindUnwrapException if the
   * Kind is null or not a valid IOHolder.
   *
   * @param <A> The type of the value produced by the IO computation.
   * @param kind The {@code Kind<IOKind<?>, A>} instance to unwrap. Can be {@code @Nullable}.
   * @return The unwrapped, non-null {@code IO<A>} instance. Returns {@code @NonNull}.
   * @throws KindUnwrapException if the input {@code kind} is null or not an instance of {@code
   *     IOHolder}.
   */
  @SuppressWarnings("unchecked") // For casting ioInstance - safe after pattern match
  public static <A> @NonNull IO<A> unwrap(@Nullable Kind<IOKind<?>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      // Record pattern extracts non-null ioInstance due to @NonNull contract
      case IOKindHelper.IOHolder<?>(var ioInstance) -> (IO<A>) ioInstance;
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code IO<A>} value into the IOKind Higher-Kinded-J type.
   *
   * @param <A> The type of the value produced by the IO computation.
   * @param io The concrete {@code IO<A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code IOKind<A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if io is null.
   */
  public static <A> @NonNull IOKind<A> wrap(@NonNull IO<A> io) {
    Objects.requireNonNull(io, "Input IO cannot be null for wrap");
    return new IOHolder<>(io);
  }

  /**
   * Convenience factory to create an {@code IOKind<A>} by delaying the execution of a {@code
   * Supplier}. Wraps {@link IO#delay(Supplier)}.
   *
   * @param <A> The type of the value produced.
   * @param thunk The {@link Supplier} representing the deferred computation. Must be
   *     {@code @NonNull}.
   * @return A new {@code Kind<IOKind<?>, A>} wrapping the delayed computation. Returns
   *     {@code @NonNull}.
   * @throws NullPointerException if thunk is null.
   */
  public static <A> @NonNull Kind<IOKind<?>, A> delay(@NonNull Supplier<A> thunk) {
    return wrap(IO.delay(thunk));
  }

  /**
   * Convenience method for running the IO computation held within the Kind wrapper and getting the
   * result. Calls {@link #unwrap(Kind)} and then {@link IO#unsafeRunSync()}.
   *
   * @param <A> The type of the result.
   * @param kind The {@code Kind<IOKind<?>, A>} holding the IO computation. Must be
   *     {@code @NonNull}.
   * @return The result of the IO computation. Can be {@code @Nullable} depending on A.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   * @throws Exception if the underlying IO computation throws an exception.
   */
  public static <A> @Nullable A unsafeRunSync(@NonNull Kind<IOKind<?>, A> kind) {
    return unwrap(kind).unsafeRunSync();
  }
}
