package org.simulation.hkt.either;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException; // Assuming this was added


public final class EitherKindHelper {

  // Error Messages (Ensure these are defined and match the constants used in unwrap)
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Either";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an EitherHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "EitherHolder contained null Either instance";


  private EitherKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }


  /**
   * Wraps a concrete Either<L, R> value into the EitherKind simulation type.
   * Uses the specific holder implementation (EitherHolder).
   *
   * @param either The concrete Either<L, R> instance. (@NonNull assumed)
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return The EitherKind<L, R> representation. (@NonNull)
   */
  public static <L, R> @NonNull Kind<EitherKind<L, ?>, R> wrap(@NonNull Either<L, R> either) {
    // Create a new instance of the holder record, wrapping the Either
    return new EitherHolder<>(either);
  }

  /**
   * Unwraps an EitherKind back to the concrete Either<L, R> type.
   * Requires knowledge of the specific holder implementation (EitherHolder).
   * Throws KindUnwrapException if the Kind is null, not an EitherHolder,
   * or the holder contains a null Either instance.
   *
   * @param kind The EitherKind instance. (@Nullable allows checking null input)
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return The underlying, non-null Either<L, R>. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked") // Suppress warning for casting holder.either()
  public static <L, R> @NonNull Either<L, R> unwrap(@Nullable Kind<EitherKind<L, ?>, R> kind) {
    if (kind == null) {
      // Make sure KindUnwrapException is defined and imported
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }

    if (kind instanceof EitherHolder<?, ?> holder) {
      Either<?, ?> internalEither = holder.either();
      if (internalEither == null) {
        // Make sure KindUnwrapException is defined and imported
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      // Cast is now safer because we've checked the holder and internal non-nullity
      return (Either<L, R>) internalEither;
    } else {
      // Make sure KindUnwrapException is defined and imported
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }


  /**
   * Holder record for the HKT simulation of Either.
   * Implements EitherKind<L, R> and holds the actual Either<L, R> value.
   * This is the concrete implementation used by wrap/unwrap helpers.
   *
   * @param <L> The Left type.
   * @param <R> The Right type.
   */
  record EitherHolder<L, R>(Either<L, R> either) implements EitherKind<L, R> { }

}