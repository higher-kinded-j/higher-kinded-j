package org.higherkindedj.hkt.either;

import java.util.Objects;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing helper methods for working with {@link Either} in the context of
 * higher-kinded types (HKT).
 *
 * <p>This class facilitates the conversion between a concrete {@link Either Either&lt;L, R&gt;}
 * instance and its HKT representation, {@code Kind<EitherKind.Witness<L>, R>}. The "Left" type
 * {@code L} is fixed for the HKT witness {@code EitherKind.Witness<L>}.
 *
 * <p>The primary methods offered are {@link #wrap(Either)} and {@link #unwrap(Kind)}. If {@link
 * Either.Left} and {@link Either.Right} directly implement {@link EitherKind}, this helper might
 * primarily be used for type guidance or if a holder pattern is strictly followed. Assuming a
 * holder pattern or a need for explicit conversion:
 *
 * @see Either
 * @see Either.Left
 * @see Either.Right
 * @see EitherKind
 * @see EitherKind.Witness
 * @see Kind
 * @see KindUnwrapException
 */
public final class EitherKindHelper {

  /**
   * Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Either";
  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG =
      "Kind instance is not an EitherHolder or direct EitherKind implementor: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null}
   * Either instance. This should ideally not occur if {@link #wrap(Either)} enforces non-null
   * Either instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "EitherHolder contained null Either instance";

  /**
   * Private constructor to prevent instantiation of this utility class. All methods are static.
   */
  private EitherKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Wraps a concrete {@code Either<L, R>} instance into its higher-kinded representation, {@code
   * Kind<EitherKind.Witness<L>, R>}.
   *
   * <p>If {@code Either<L,R>} (or its subtypes {@code Left<L,R>}, {@code Right<L,R>}) directly
   * implements {@code EitherKind<L,R>}, this method can simply perform a cast. Otherwise, it uses
   * {@link EitherHolder}.
   *
   * @param <L>    The type of the "Left" value of the {@code Either}.
   * @param <R>    The type of the "Right" value of the {@code Either}.
   * @param either The non-null, concrete {@code Either<L, R>} instance to wrap.
   * @return A non-null {@code Kind<EitherKind.Witness<L>, R>} representing the wrapped {@code
   * Either}.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  public static <L, R> @NonNull Kind<EitherKind.Witness<L>, R> wrap(@NonNull Either<L, R> either) {
    Objects.requireNonNull(either, "Input Either cannot be null for wrap");
    return new EitherHolder<>(either); // Consistently use EitherHolder
  }

  /**
   * Unwraps a {@code Kind<EitherKind.Witness<L>, R>} back to its concrete {@code Either<L, R>}
   * type.
   *
   * @param <L>  The type of the "Left" value of the target {@code Either}.
   * @param <R>  The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind<EitherKind.Witness<L>, R>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@code Either<L, R>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not a representation
   *                             of an {@code Either<L,R>}.
   */
  public static <L, R> @NonNull Either<L, R> unwrap(@Nullable Kind<EitherKind.Witness<L>, R> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case EitherHolder<L, R> holder -> holder.either(); // Expect EitherHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Internal record implementing {@link EitherKind} to hold the concrete {@link Either} instance.
   * This can be used if {@link Either.Left} and {@link Either.Right} do not directly implement
   * {@link EitherKind}, or if a consistent holder pattern across all HKTs is desired.
   *
   * @param <L>    The type of the {@code Left} value.
   * @param <R>    The type of the {@code Right} value.
   * @param either The non-null, actual {@link Either} instance.
   */
  record EitherHolder<L, R>(@NonNull Either<L, R> either) implements EitherKind<L, R> {
  }
}
