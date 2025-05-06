package org.higherkindedj.hkt.trans.maybe_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for simulating higher-kinded types (HKT) with {@link MaybeT}. It provides methods
 * to safely wrap a concrete {@link MaybeT} instance into its {@link Kind} representation ({@link
 * MaybeTKind}) and to unwrap it back.
 *
 * <p>This helper is essential for using {@code MaybeT} with generic HKT abstractions like {@code
 * Monad<MaybeTKind<F, ?>, A>}. It encapsulates the necessary type casting and provides runtime
 * safety checks.
 *
 * <p>This class is final and cannot be instantiated.
 */
public final class MaybeTKindHelper {

  // Error Messages
  /** Error message for attempting to unwrap a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for MaybeT";

  /** Error message for attempting to unwrap a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeTHolder: ";

  /**
   * Error message for an invalid state where the internal holder contains a null MaybeT. This
   * should ideally not be reachable if wrap ensures non-null inputs.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "MaybeTHolder contained null MaybeT instance";

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException always.
   */
  private MaybeTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@link Kind} representation of {@code MaybeT} (i.e., a {@link MaybeTKind}) back to
   * its concrete {@link MaybeT}{@code <F, A>} type.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param kind The {@code Kind<MaybeTKind<F, ?>, A>} to unwrap. Can be null.
   * @return The unwrapped, non-null {@link MaybeT}{@code <F, A>} instance.
   * @throws KindUnwrapException if {@code kind} is null, not an instance of the internal {@code
   *     MaybeTHolder}, or if (theoretically) the holder contained a null {@code MaybeT}.
   */
  @SuppressWarnings("unchecked")
  public static <F, A> @NonNull MaybeT<F, A> unwrap(@Nullable Kind<MaybeTKind<F, ?>, A> kind) {

    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(MaybeTKindHelper.INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a MaybeTHolder.
      // The @NonNull contract on MaybeTHolder.maybeT guarantees maybeT is not null here.
      case MaybeTKindHelper.MaybeTHolder<?, ?>(var maybeTVal) ->
          // Cast is safe because the pattern matched and maybeTVal is known to be non-null.
          (MaybeT<F, A>) maybeTVal;

      // Case 3: Input Kind is non-null but not a MaybeTHolder
      default ->
          throw new KindUnwrapException(
              MaybeTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link MaybeT}{@code <F, A>} instance into its {@link Kind} representation
   * ({@link MaybeTKind}{@code <F, A>}).
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param maybeT The concrete {@link MaybeT} instance to wrap. Must not be null.
   * @return The {@link Kind} representation (specifically, an instance of {@code MaybeTHolder}).
   * @throws NullPointerException if {@code maybeT} is null.
   */
  public static <F, A> @NonNull MaybeTKind<F, A> wrap(@NonNull MaybeT<F, A> maybeT) {
    Objects.requireNonNull(maybeT, "Input MaybeT cannot be null for wrap");
    return new MaybeTHolder<>(maybeT);
  }

  /**
   * Internal record implementing the {@link MaybeTKind} interface. This class acts as the actual
   * holder of the concrete {@link MaybeT} instance when it's treated as a {@link Kind}. It is not
   * intended for direct public use.
   *
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the {@code MaybeT}.
   * @param maybeT The concrete {@link MaybeT} instance. Must not be null, as {@link MaybeT} itself
   *     requires a non-null underlying value.
   */
  record MaybeTHolder<F, A>(@NonNull MaybeT<F, A> maybeT) implements MaybeTKind<F, A> {}
}
