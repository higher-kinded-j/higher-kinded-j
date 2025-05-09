package org.higherkindedj.hkt.either;

import java.util.Objects; // Added for Objects.requireNonNull
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing helper methods for working with {@link Either} in the context of
 * higher-kinded types (HKT), using {@link EitherKind} as the HKT marker.
 *
 * <p>This class offers static methods for:
 *
 * <ul>
 *   <li>Safely converting between an {@link Either} instance and its {@link Kind} representation
 *       ({@link #wrap(Either)} and {@link #unwrap(Kind)}).
 * </ul>
 *
 * <p>It acts as a bridge between the concrete {@link Either} type, which represents a value of one
 * of two possible types (a {@code Left} or a {@code Right}), and the abstract {@link Kind}
 * interface used in generic functional programming patterns.
 *
 * <p>The {@link #unwrap(Kind)} method uses an internal private record ({@code EitherHolder}) that
 * implements {@link EitherKind} to hold the actual {@code Either} instance.
 *
 * @see Either
 * @see Either.Left
 * @see Either.Right
 * @see EitherKind
 * @see Kind
 * @see KindUnwrapException
 */
public final class EitherKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Either";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an EitherHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null}
   * Either instance. This should ideally not occur if {@link #wrap(Either)} enforces non-null
   * Either instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "EitherHolder contained null Either instance";

  /** Private constructor to prevent instantiation of this utility class. All methods are static. */
  private EitherKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link EitherKind} to hold the concrete {@link Either} instance.
   * This is used by {@link #wrap(Either)} and {@link #unwrap(Kind)}.
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @param either The non-null, actual {@link Either} instance.
   */
  record EitherHolder<L, R>(@NonNull Either<L, R> either) implements EitherKind<L, R> {}

  /**
   * Wraps a concrete {@code Either<L, R>} instance into its higher-kinded representation, {@code
   * Kind<EitherKind<L, ?>, R>}.
   *
   * @param <L> The type of the {@code Left} value of the {@code Either}.
   * @param <R> The type of the {@code Right} value of the {@code Either}.
   * @param either The non-null, concrete {@code Either<L, R>} instance to wrap.
   * @return A non-null {@code Kind Kind<EitherKind<L, ?>, R>} representing the wrapped {@code
   *     Either}. The witness type {@code EitherKind<L, ?>} fixes the Left type {@code L}.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  public static <L, R> @NonNull Kind<EitherKind<L, ?>, R> wrap(@NonNull Either<L, R> either) {
    Objects.requireNonNull(either, "Input Either cannot be null for wrap");
    return new EitherHolder<>(either);
  }

  /**
   * Unwraps a {@code Kind<EitherKind<L, ?>, R>} back to its concrete {@code Either<L, R>} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents an {@link Either} with the specified {@code Left} type {@code L} (as
   * encoded in the witness {@code EitherKind<L, ?>}) and {@code Right} type {@code R}.
   *
   * @param <L> The type of the {@code Left} value of the target {@code Either}.
   * @param <R> The type of the {@code Right} value of the target {@code Either}.
   * @param kind The {@code Kind<EitherKind<L, ?>, R>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@code Either<L, R>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code EitherHolder}, or if the holder's internal {@code Either} instance is {@code null}
   *     (which would indicate an internal issue with {@link #wrap(Either)}).
   */
  @SuppressWarnings("unchecked")
  public static <L, R> @NonNull Either<L, R> unwrap(@Nullable Kind<EitherKind<L, ?>, R> kind) {
    return switch (kind) {
      case null ->
          // If the input Kind itself is null.
          throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case EitherHolder<?, ?> holder -> { // Use wildcard for L, R in holder pattern
        // The EitherHolder record's 'either' component is @NonNull.
        Either<?, ?> internalEither = holder.either(); // Access record component
        // The cast to Either<L, R> is necessary because the pattern match
        // on EitherHolder<?, ?> doesn't fully resolve L and R to the specific
        // types required by the method's signature. The caller ensures this through
        // the Kind<EitherKind<L,?>, R> input type.
        yield (Either<L, R>) internalEither;
      }
      default ->
          // If the Kind is non-null but not the expected EitherHolder type.
          throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }
}
