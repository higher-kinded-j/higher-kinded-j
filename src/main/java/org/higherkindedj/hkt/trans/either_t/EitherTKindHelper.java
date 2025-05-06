package org.higherkindedj.hkt.trans.either_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for simulating higher-kinded types (HKT) with {@link EitherT}. It provides
 * methods to safely wrap a concrete {@link EitherT} instance into its {@link Kind} representation
 * ({@link EitherTKind}) and to unwrap it back.
 *
 * <p>This helper is essential for using {@code EitherT} with generic HKT abstractions like {@code
 * Monad<EitherTKind<F, L, ?>, R>}. It encapsulates the necessary type casting and provides runtime
 * safety checks.
 *
 * <p>This class is final and cannot be instantiated.
 *
 * @param <F> The witness type of the outer monad.
 * @param <L> The type of the 'left' value in the {@code EitherT}.
 */
public final class EitherTKindHelper<F, L> {

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
   * Unwraps a {@link Kind} representation of {@code EitherT} (i.e., a {@link EitherTKind}) back to
   * its concrete {@link EitherT}{@code <F, L, R>} type.
   *
   * @param <F_IN> The witness type of the outer monad in {@code EitherT} (input Kind).
   * @param <L_IN> The type of the 'left' value in {@code EitherT} (input Kind).
   * @param <R> The type of the 'right' value in the {@link org.higherkindedj.hkt.either.Either}.
   * @param kind The {@code Kind<EitherTKind<F_IN, L_IN, ?>, R>} to unwrap. Can be null.
   * @return The unwrapped, non-null {@link EitherT}{@code <F_IN, L_IN, R>} instance.
   * @throws KindUnwrapException if {@code kind} is null, not an instance of the internal {@code
   *     EitherTHolder}, or if (theoretically) the holder contained a null {@code EitherT}.
   */
  @SuppressWarnings("unchecked")
  public static <F_IN, L_IN, R> @NonNull EitherT<F_IN, L_IN, R> unwrap(
      @Nullable Kind<EitherTKind<F_IN, L_IN, ?>, R> kind) {

    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(EitherTKindHelper.INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is an EitherTHolder.
      // The @NonNull contract on EitherTHolder.eitherT guarantees eitherT is not null here.
      case EitherTKindHelper.EitherTHolder<?, ?, ?>(var eitherTVal) ->
          // Cast is safe because the pattern matched and eitherTVal is known to be non-null.
          (EitherT<F_IN, L_IN, R>) eitherTVal;

      // Case 3: Input Kind is non-null but not an EitherTHolder
      default ->
          throw new KindUnwrapException(
              EitherTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link EitherT}{@code <F_IN, L_IN, R>} instance into its {@link Kind}
   * representation ({@link EitherTKind}{@code <F_IN, L_IN, R>}).
   *
   * @param <F_IN> The witness type of the outer monad in {@code EitherT}.
   * @param <L_IN> The type of the 'left' value in {@code EitherT}.
   * @param <R> The type of the 'right' value in the {@link org.higherkindedj.hkt.either.Either}.
   * @param eitherT The concrete {@link EitherT} instance to wrap. Must not be null.
   * @return The {@link Kind} representation (specifically, an instance of {@code EitherTHolder}).
   * @throws NullPointerException if {@code eitherT} is null.
   */
  public static <F_IN, L_IN, R> @NonNull EitherTKind<F_IN, L_IN, R> wrap(
      @NonNull EitherT<F_IN, L_IN, R> eitherT) {
    Objects.requireNonNull(eitherT, "Input EitherT cannot be null for wrap");
    return new EitherTHolder<>(eitherT);
  }

  /**
   * Internal record implementing the {@link EitherTKind} interface. This class acts as the actual
   * holder of the concrete {@link EitherT} instance when it's treated as a {@link Kind}. It is not
   * intended for direct public use.
   *
   * @param <F_HOLDER> The witness type of the outer monad.
   * @param <L_HOLDER> The type of the 'left' value.
   * @param <R_HOLDER> The type of the 'right' value.
   * @param eitherT The concrete {@link EitherT} instance. Must not be null, as {@link EitherT}
   *     itself requires a non-null underlying value.
   */
  record EitherTHolder<F_HOLDER, L_HOLDER, R_HOLDER>(
      @NonNull EitherT<F_HOLDER, L_HOLDER, R_HOLDER> eitherT)
      implements EitherTKind<F_HOLDER, L_HOLDER, R_HOLDER> {}
}
