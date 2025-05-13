package org.higherkindedj.hkt.io;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing helper methods for working with {@link IO} in the context of
 * higher-kinded types (HKT), using {@link IOKind} as the HKT marker.
 *
 * <p>This class offers static methods for:
 *
 * <ul>
 *   <li>Safely converting between an {@link IO} instance and its {@link Kind} representation
 *       ({@link #wrap(IO)} and {@link #unwrap(Kind)}).
 *   <li>Creating {@link IO} instances, wrapped as {@link Kind}, from suppliers ({@link
 *       #delay(Supplier)}).
 *   <li>Executing an {@code IO} computation represented as a {@link Kind} ({@link
 *       #unsafeRunSync(Kind)}).
 * </ul>
 *
 * <p>It acts as a bridge between the concrete {@link IO} type, which encapsulates a potentially
 * side-effecting computation, and the abstract {@link Kind} interface used in generic functional
 * programming patterns.
 *
 * <p>The {@link #unwrap(Kind)} method uses an internal private record ({@code IOHolder}) that
 * implements {@link IOKind} to hold the actual {@code IO} instance.
 *
 * @see IO
 * @see IOKind
 * @see Kind
 * @see KindUnwrapException
 */
public final class IOKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for IO";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an IOHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null} IO
   * instance. This should ideally not occur if {@link #wrap(IO)} enforces non-null IO instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "IOHolder contained null IO instance";

  /** Private constructor to prevent instantiation of this utility class. All methods are static. */
  private IOKindHelper() {
    // prevent instantiation via reflection
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link IOKind} to hold the concrete {@link IO} instance. This is
   * used by {@link #wrap(IO)} and {@link #unwrap(Kind)}.
   *
   * @param <A> The result type of the IO computation.
   * @param ioInstance The non-null, actual {@link IO} instance.
   */
  record IOHolder<A>(@NonNull IO<A> ioInstance) implements IOKind<A> {}

  /**
   * Unwraps a {@code Kind<IOKind.Witness, A>} back to its concrete {@link IO<A>} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents an {@link IO} computation.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param kind The {@code Kind<IOKind.Witness, A>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@link IO IO<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code IOHolder}, or if the holder's internal {@code IO} instance is {@code null} (which
   *     would indicate an internal issue with {@link #wrap(IO)}).
   */
  @SuppressWarnings("unchecked")
  public static <A> @NonNull IO<A> unwrap(@Nullable Kind<IOKind.Witness, A> kind) {
    return switch (kind) {
      case null ->
          throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case IOKindHelper.IOHolder<?> holder -> {
        yield (IO<A>) holder.ioInstance();
      }
      default ->
          // If the Kind is non-null but not the expected IOHolder type.
          throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link IO<A>} instance into its higher-kinded representation, {@code
   * Kind<IOKind.Witness, A>}.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param io The non-null, concrete {@link IO IO<A>} instance to wrap.
   * @return A non-null {@link IOKind<A>} (which is also a {@code Kind<IOKind.Witness, A>})
   *     representing the wrapped {@code IO} computation.
   * @throws NullPointerException if {@code io} is {@code null}.
   */
  public static <A> @NonNull IOKind<A> wrap(@NonNull IO<A> io) {
    Objects.requireNonNull(io, "Input IO cannot be null for wrap");
    return new IOHolder<>(io);
  }

  /**
   * Creates an {@link IOKind<A>} that wraps an {@link IO} computation produced by delaying the
   * execution of a {@link Supplier}.
   *
   * <p>This is a convenience factory method that delegates to {@link IO#delay(Supplier)} and then
   * wraps the result using {@link #wrap(IO)}.
   *
   * @param <A> The type of the value produced by the {@code IO} computation.
   * @param thunk The non-null {@link Supplier} representing the deferred computation. The supplier
   *     itself may return {@code null} if {@code A} is a nullable type.
   * @return A new, non-null {@code Kind<IOKind.Witness, A>} representing the delayed {@code IO}
   *     computation.
   * @throws NullPointerException if {@code thunk} is {@code null}.
   */
  public static <A> @NonNull Kind<IOKind.Witness, A> delay(@NonNull Supplier<A> thunk) {
    // IO.delay will perform its own null check on the thunk.
    return wrap(IO.delay(thunk));
  }

  /**
   * Executes the {@link IO} computation held within the {@link Kind} wrapper and retrieves its
   * result.
   *
   * <p>This method synchronously runs the {@code IO} action. It should be used with caution,
   * typically at the "end of the world" in an application, as it exits the {@code IO} context and
   * can produce side effects.
   *
   * <p>This is a convenience method that delegates to {@link #unwrap(Kind)} and then calls {@link
   * IO#unsafeRunSync()}.
   *
   * @param <A> The type of the result produced by the {@code IO} computation.
   * @param kind The non-null {@code Kind<IOKind.Witness, A>} holding the {@code IO} computation.
   * @return The result of the {@code IO} computation. Can be {@code null} if the computation
   *     defined within the {@code IO} produces a {@code null} value.
   * @throws KindUnwrapException if the input {@code kind} is invalid (e.g., null or wrong type).
   *     Any exceptions thrown by the {@code IO} computation itself during {@code unsafeRunSync}
   *     will propagate.
   */
  public static <A> @Nullable A unsafeRunSync(@NonNull Kind<IOKind.Witness, A> kind) {
    // unwrap will throw KindUnwrapException if kind is invalid.
    return unwrap(kind).unsafeRunSync();
  }
}
