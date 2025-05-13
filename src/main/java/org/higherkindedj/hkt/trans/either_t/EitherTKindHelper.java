package org.higherkindedj.hkt.trans.either_t;

import static java.util.Objects.requireNonNull;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for HKT simulation with {@link EitherT}. Provides methods to wrap a concrete
 * {@link EitherT} instance into its {@link Kind} representation (using {@link EitherTKind.Witness})
 * and to unwrap it back.
 *
 * <p>This helper is essential for using {@code EitherT} with generic HKT abstractions like {@code
 * Monad<EitherTKind.Witness<F, L>, R>}.
 *
 * @see EitherT
 * @see EitherTKind
 * @see EitherTKind.Witness
 * @see Kind
 */
public final class EitherTKindHelper {

  // Error Messages
  /** Error message for attempting to unwrap a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for EitherT";

  /** Error message for attempting to unwrap a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an EitherTHolder: ";

  /**
   * Error message for an invalid state where the internal holder contains a null EitherT. This
   * should ideally not be reachable if wrap ensures non-null inputs.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "EitherTHolder contained null EitherT instance";

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException always.
   */
  private EitherTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@code Kind<EitherTKind.Witness<F, L>, R>} back to its concrete {@link EitherT
   * EitherT&lt;F, L, R&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code EitherT}.
   * @param <L> The type of the 'left' value in the inner {@link Either} of {@code EitherT}.
   * @param <R> The type of the 'right' value in the inner {@link Either}.
   * @param kind The {@code Kind<EitherTKind.Witness<F, L>, R>} to unwrap. Can be null.
   * @return The unwrapped, non-null {@link EitherT EitherT&lt;F, L, R&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@code EitherTHolder}.
   */
  @SuppressWarnings("unchecked")
  public static <F, L, R> @NonNull EitherT<F, L, R> unwrap(
      @Nullable Kind<EitherTKind.Witness<F, L>, R> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(EitherTKindHelper.INVALID_KIND_NULL_MSG);
      case EitherTKindHelper.EitherTHolder<F, L, R> holder -> holder.eitherT();
      // If EitherT itself implements EitherTKind directly (as modified):
      case EitherT<F, L, R> directEitherT -> directEitherT;
      default ->
          throw new KindUnwrapException(
              EitherTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link EitherT EitherT&lt;F, L, R&gt;} instance into its {@link Kind}
   * representation, {@code Kind<EitherTKind.Witness<F, L>, R>}.
   *
   * @param <F> The witness type of the outer monad in {@code EitherT}.
   * @param <L> The type of the 'left' value in the inner {@link Either} of {@code EitherT}.
   * @param <R> The type of the 'right' value in the inner {@link Either}.
   * @param eitherT The concrete {@link EitherT} instance to wrap. Must not be null.
   * @return The {@code Kind} representation (an instance of {@link EitherTKind}).
   * @throws NullPointerException if {@code eitherT} is null.
   */
  @SuppressWarnings("unchecked") // Safe due to EitherT implementing EitherTKind
  public static <F, L, R> @NonNull Kind<EitherTKind.Witness<F, L>, R> wrap(
      @NonNull EitherT<F, L, R> eitherT) {
    requireNonNull(eitherT, "Input EitherT cannot be null for wrap");
    return (EitherTKind<F, L, R>) eitherT;
  }

  /**
   * Internal record implementing {@link EitherTKind}. This can be used if {@link EitherT} itself
   * does not directly implement {@link EitherTKind} or if a separate holder is preferred for the
   * HKT simulation. Given that {@code EitherT} now implements {@code EitherTKind}, this holder
   * becomes optional and {@link #wrap(EitherT)} can perform a direct cast.
   *
   * @param <F_HOLDER> Witness type of the outer monad.
   * @param <L_HOLDER> Type of the 'left' value.
   * @param <R_HOLDER> Type of the 'right' value.
   * @param eitherT The concrete {@link EitherT} instance.
   */
  record EitherTHolder<F_HOLDER, L_HOLDER, R_HOLDER>(
      @NonNull EitherT<F_HOLDER, L_HOLDER, R_HOLDER> eitherT)
      implements EitherTKind<F_HOLDER, L_HOLDER, R_HOLDER> {}
}
