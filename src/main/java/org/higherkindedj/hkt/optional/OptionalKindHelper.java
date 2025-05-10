package org.higherkindedj.hkt.optional;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for simulating higher-kinded types (HKT) with {@link java.util.Optional}. It
 * provides methods to safely wrap a concrete {@link Optional} instance into its {@link Kind}
 * representation ({@link OptionalKind}) and to unwrap it back.
 *
 * <p>This helper is essential for using {@code Optional} with generic HKT abstractions like {@code
 * Functor<OptionalKind.Witness, A>} or {@code MonadError<OptionalKind.Witness, Void, A>}. It
 * encapsulates the necessary type casting and provides runtime safety checks.
 *
 * <p>This class is final and cannot be instantiated.
 *
 * @see Optional
 * @see OptionalKind
 * @see Kind
 * @see KindUnwrapException
 */
public final class OptionalKindHelper {

  /** Error message for attempting to unwrap a {@code null} {@link Kind}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Optional";

  /** Error message for attempting to unwrap a {@link Kind} of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalHolder: ";

  /**
   * Error message for an invalid state where the internal holder contains a {@code null} Optional.
   * This should ideally not be reachable if {@link #wrap(Optional)} ensures non-null inputs for the
   * {@code Optional} instance itself (not its content).
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "OptionalHolder contained null Optional instance";

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException always.
   */
  private OptionalKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing the {@link OptionalKind} interface. This class acts as the actual
   * holder of the concrete {@link Optional} instance when it's treated as a {@link Kind}. It is not
   * intended for direct public use.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param optional The concrete {@link Optional} instance. Must not be {@code null} itself, though
   *     it can be {@code Optional.empty()} or {@code Optional.ofNullable(null)}.
   */
  record OptionalHolder<A>(@NonNull Optional<A> optional) implements OptionalKind<A> {
    /**
     * Constructs an {@code OptionalHolder}.
     *
     * @param optional The {@link Optional} to hold. Must not be null.
     * @throws NullPointerException if the provided {@code optional} instance is null.
     */
    OptionalHolder {
      requireNonNull(optional, "Wrapped Optional instance cannot be null in OptionalHolder");
    }
  }

  /**
   * Unwraps a {@code Kind<OptionalKind.Witness, A>} back to its concrete {@link Optional}{@code
   * <A>} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents an {@link Optional}.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param kind The {@code Kind<OptionalKind.Witness, A>} instance to unwrap. May be {@code null},
   *     in which case a {@link KindUnwrapException} is thrown.
   * @return The underlying, non-null {@link Optional}{@code <A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code OptionalHolder}, or (theoretically) if the holder's internal {@code Optional}
   *     instance were {@code null}.
   */
  @SuppressWarnings("unchecked")
  public static <A> @NonNull Optional<A> unwrap(@Nullable Kind<OptionalKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      // The pattern match ensures 'optional' (the record component) is not null
      // because OptionalHolder's constructor requires a @NonNull Optional.
      case OptionalKindHelper.OptionalHolder<?> holder -> (Optional<A>) holder.optional();
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link Optional}{@code <A>} instance into its higher-kinded representation,
   * {@code Kind<OptionalKind.Witness, A>}.
   *
   * @param <A> The type of the value potentially held by the {@code Optional}.
   * @param optional The concrete {@link Optional}{@code <A>} instance to wrap. Must not be {@code
   *     null} (though it can be {@code Optional.empty()}).
   * @return A non-null {@code OptionalKind<A>} (which is also a {@code Kind<OptionalKind.Witness,
   *     A>}) representing the wrapped {@code Optional}.
   * @throws NullPointerException if {@code optional} is {@code null}.
   */
  public static <A> @NonNull OptionalKind<A> wrap(@NonNull Optional<A> optional) {
    requireNonNull(optional, "Input Optional cannot be null for wrap");
    return new OptionalHolder<>(optional);
  }
}
