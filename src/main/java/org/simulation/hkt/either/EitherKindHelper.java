package org.simulation.hkt.either;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
   * @param kind The EitherKind instance. (Nullable)
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return The underlying Either<L, R>, or a Left containing an error message if unwrapping fails. (NonNull)
   */
  @SuppressWarnings({"unchecked", "rawtypes"}) // Suppress warnings for casting and raw types in switch
  public static <L, R> @NonNull Either<L, R> unwrap(@Nullable Kind<EitherKind<L, ?>, R> kind) {
    // Now returns Left(error) for null or unknown types instead of throwing
    return switch (kind) {
      case EitherHolder holder when holder.either() != null -> {
        // Unsafe cast, but necessary due to type erasure and HKT simulation limits
        Either<L, R> typedEither = (Either<L, R>) holder.either();
        yield typedEither;
      }
      // Handle null Either within holder or null/default Kind
      case null, default -> {
        // Return a specific Left for null or unknown/invalid Kind types
        // Cast L is unsafe, assumes L can hold String or user handles it.
        // A better approach might involve a dedicated error type for L.
        // Using Object to avoid direct cast to L, assuming L can be Object or user expects String error.
        yield Either.left((L) INVALID_KIND_STATE_ERROR); // Cast to L is inherently problematic
      }
    };
  }


  /**
   * Wraps a concrete Either<L, R> value into the EitherKind simulation type.
   * Uses the specific holder implementation (EitherHolder).
   *
   * @param either The concrete Either<L, R> instance. (NonNull)
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return The EitherKind<L, R> representation. (NonNull)
   */
  public static <L, R> @NonNull Kind<EitherKind<L, ?>, R> wrap(@NonNull Either<L, R> either) {
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
// Field 'either' assumed NonNull based on wrap requiring NonNull input
  record EitherHolder<L, R>(@NonNull Either<L, R> either) implements EitherKind<L, R> { }

}