package org.simulation.hkt.either;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException; // Assuming this was added

public final class EitherKindHelper {

  // Error Messages (Ensure these are defined and match the constants used in unwrap)
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Either";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an EitherHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "EitherHolder contained null Either instance";

  private EitherKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Wraps a concrete Either<L, R> value into the EitherKind simulation type. Uses the specific
   * holder implementation (EitherHolder).
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
   * Unwraps an EitherKind back to the concrete Either<L, R> type. Requires knowledge of the
   * specific holder implementation (EitherHolder). Throws KindUnwrapException if the Kind is null,
   * not an EitherHolder, or the holder contains a null Either instance.
   *
   * <p>Refactored using Java 21+ Pattern Matching for switch.
   *
   * @param kind The EitherKind instance. (@Nullable allows checking null input)
   * @param <L> The Left type.
   * @param <R> The Right type.
   * @return The underlying, non-null Either<L, R>. (@NonNull assumes success)
   * @throws KindUnwrapException if unwrapping fails.
   */
  @SuppressWarnings("unchecked") //  needed for the final cast due to EitherHolder<?, ?> pattern
  public static <L, R> @NonNull Either<L, R> unwrap(@Nullable Kind<EitherKind<L, ?>, R> kind) {
    // Use switch expression with pattern matching
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is an EitherHolder (using record pattern to extract 'internalEither')
      case EitherHolder(var internalEither) -> {
        // Check if the extracted Either inside the holder is null
        if (internalEither == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // Cast is  necessary because the pattern matched EitherHolder<?,?>
        // before confirming the internal Either's specific L/R types.
        // But it's safer now after the null check.
        yield (Either<L, R>) internalEither;
      }
      // Case 3: Input Kind is non-null but not an EitherHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Holder record for the HKT simulation of Either. Implements EitherKind<L, R> and holds the
   * actual Either<L, R> value. This is the concrete implementation used by wrap/unwrap helpers.
   *
   * @param <L> The Left type.
   * @param <R> The Right type.
   */
  record EitherHolder<L, R>(Either<L, R> either) implements EitherKind<L, R> {}
}
