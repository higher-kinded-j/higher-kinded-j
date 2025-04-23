package org.simulation.hkt.either;

import org.simulation.hkt.Kind;


public final class EitherKindHelper {


  private EitherKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }


  private static final String INVALID_KIND_STATE_ERROR = "Invalid Kind state (null or unexpected type)";


  /**
   * Unwraps an EitherKind back to the concrete Either<L, R> type.
   * Requires knowledge of the specific holder implementation (EitherHolder).
   * Now returns a Left value for null or invalid Kind types.
   *
   * @param kind The EitherKind instance.
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return The underlying Either<L, R>, or a Left containing an error message if unwrapping fails.
   */
  public static <L, R> Either<L, R> unwrap(Kind<EitherKind<L, ?>, R> kind) {
    // Now returns Left(error) for null or unknown types instead of throwing
    return switch (kind) {
      case EitherHolder<?, ?> holder when holder.either() != null -> {
        // Unsafe cast, but necessary due to type erasure and HKT simulation limits
        @SuppressWarnings("unchecked")
        Either<L, R> typedEither = (Either<L, R>) holder.either();
        yield typedEither;
      }
      // @TODO:
      // Return a specific Left for null or unknown/invalid Kind types
      // Cast L is unsafe, assumes L can hold String or user handles it. Consider a dedicated Error type for L.
      //I think A better approach in a real system would be to have L be a type that can represent this error state,
      // or use a more sophisticated error handling
      case null, default -> Either.left((L) INVALID_KIND_STATE_ERROR);
    };

  }

/**
 * Wraps a concrete Either<L, R> value into the EitherKind simulation type.
 * Uses the specific holder implementation (EitherHolder).
 *
 * @param either The concrete Either<L, R> instance.
 * @param <L> The Left type.
 * @param <R> The Right type.
 * @return The EitherKind<L, R> representation.
 */
public static <L, R> Kind<EitherKind<L, ?>, R> wrap(Either<L, R> either) {
  // Create a new instance of the holder record, wrapping the Either
  return new EitherHolder<>(either);
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
