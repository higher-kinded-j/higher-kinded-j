package org.higherkindedj.hkt.trans.optional_t;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for simulating higher-kinded types (HKT) with {@link OptionalT}. It provides
 * methods to safely wrap a concrete {@link OptionalT} instance into its {@link Kind} representation
 * ({@link OptionalTKind}) and to unwrap it back.
 *
 * <p>This helper is essential for using {@code OptionalT} with generic HKT abstractions like {@code
 * Monad<OptionalTKind<F, ?>, A>}. It encapsulates the necessary type casting and provides runtime
 * safety checks.
 *
 * <p>This class is final and cannot be instantiated.
 */
public final class OptionalTKindHelper {

  // Error Messages
  /** Error message for attempting to unwrap a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for OptionalT";

  /** Error message for attempting to unwrap a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalTHolder: ";

  /**
   * Error message for an invalid state where the internal holder contains a null OptionalT. This
   * should ideally not be reachable if wrap ensures non-null inputs.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "OptionalTHolder contained null OptionalT instance";

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException always.
   */
  private OptionalTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@link Kind} representation of {@code OptionalT} (i.e., an {@link OptionalTKind})
   * back to its concrete {@link OptionalT}{@code <F, A>} type.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link Optional}.
   * @param kind The {@code Kind<OptionalTKind<F, ?>, A>} to unwrap. Can be null.
   * @return The unwrapped, non-null {@link OptionalT}{@code <F, A>} instance.
   * @throws KindUnwrapException if {@code kind} is null, not an instance of the internal {@code
   *     OptionalTHolder}, or if (theoretically) the holder contained a null {@code OptionalT}.
   */
  @SuppressWarnings("unchecked")
  public static <F, A> @NonNull OptionalT<F, A> unwrap(
      @Nullable Kind<OptionalTKind<F, ?>, A> kind) {

    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(OptionalTKindHelper.INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is an OptionalTHolder.
      // The @NonNull contract on OptionalTHolder.optionalT guarantees optionalT is not null here.
      case OptionalTKindHelper.OptionalTHolder<?, ?>(var optionalTVal) ->
          // Cast is safe because the pattern matched and optionalTVal is known to be non-null.
          (OptionalT<F, A>) optionalTVal;

      // Case 3: Input Kind is non-null but not an OptionalTHolder
      default ->
          throw new KindUnwrapException(
              OptionalTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link OptionalT}{@code <F, A>} instance into its {@link Kind} representation
   * ({@link OptionalTKind}{@code <F, A>}).
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link Optional}.
   * @param optionalT The concrete {@link OptionalT} instance to wrap. Must not be null.
   * @return The {@link Kind} representation (specifically, an instance of {@code OptionalTHolder}).
   * @throws NullPointerException if {@code optionalT} is null.
   */
  public static <F, A> @NonNull OptionalTKind<F, A> wrap(@NonNull OptionalT<F, A> optionalT) {
    Objects.requireNonNull(optionalT, "Input OptionalT cannot be null for wrap");
    return new OptionalTHolder<>(optionalT);
  }

  /**
   * Internal record implementing the {@link OptionalTKind} interface. This class acts as the actual
   * holder of the concrete {@link OptionalT} instance when it's treated as a {@link Kind}. It is
   * not intended for direct public use.
   *
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the {@code OptionalT}.
   * @param optionalT The concrete {@link OptionalT} instance. Must not be null, as {@link
   *     OptionalT} itself requires a non-null underlying value.
   */
  record OptionalTHolder<F, A>(@NonNull OptionalT<F, A> optionalT) implements OptionalTKind<F, A> {}
}
