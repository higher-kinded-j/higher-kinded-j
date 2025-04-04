package org.simulation.hkt.either;

import org.simulation.hkt.Kind;


public class EitherKindHelper {
/**
 * Unwraps an EitherKind back to the concrete Either<L, R> type.
 * Requires knowledge of the specific holder implementation (EitherHolder).
 *
 * @param kind The EitherKind instance.
 * @param <L> The Left type.
 * @param <R> The Right type.
 * @return The underlying Either<L, R>.
 * @throws IllegalArgumentException if the kind is not an EitherHolder instance.
 */
public static <L, R> Either<L, R> unwrap(Kind<EitherKind<L, ?>, R> kind) {
  return switch (kind) {
    case EitherHolder<?, ?> holder when holder.either() != null -> {
      // Unsafe cast, but necessary due to type erasure and HKT simulation limits
      @SuppressWarnings("unchecked")
      Either<L, R> typedEither = (Either<L, R>) holder.either();
      yield typedEither;
    }
    case null ->
        throw new NullPointerException("Cannot unwrap null Kind"); // assume Left(null) is not a valid representation here.
    default ->
        throw new IllegalArgumentException("Kind instance is not an EitherHolder or has incompatible types: " + kind.getClass().getName());
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
