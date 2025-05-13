package org.higherkindedj.hkt.trans.maybe_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for simulating higher-kinded types (HKT) with {@link MaybeT}. It provides methods
 * to safely wrap a concrete {@link MaybeT} instance into its {@link Kind} representation ({@code
 * Kind<MaybeTKind.Witness<F>, A>}) and to unwrap it back.
 *
 * <p>This helper is essential for using {@code MaybeT} with generic HKT abstractions like {@code
 * Monad<MaybeTKind.Witness<F>, A>}. Since {@link MaybeT} now directly implements {@link
 * MaybeTKind}, this helper primarily facilitates type casting and provides runtime safety checks.
 *
 * <p>This class is final and cannot be instantiated.
 */
public final class MaybeTKindHelper {

  // Error Messages
  /** Error message for attempting to unwrap a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for MaybeT";

  /** Error message for attempting to unwrap a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeT: ";

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException always.
   */
  private MaybeTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@code Kind<MaybeTKind.Witness<F>, A>} back to its concrete {@link MaybeT
   * MaybeT&lt;F, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param kind The {@code Kind<MaybeTKind.Witness<F>, A>} to unwrap. Can be null.
   * @return The unwrapped, non-null {@link MaybeT MaybeT&lt;F, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@code MaybeT} instance.
   */
  @SuppressWarnings("unchecked")
  public static <F, A> @NonNull MaybeT<F, A> unwrap(@Nullable Kind<MaybeTKind.Witness<F>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(MaybeTKindHelper.INVALID_KIND_NULL_MSG);
      // Since MaybeT<F,A> implements MaybeTKind<F,A>, which extends Kind<MaybeTKind.Witness<F>,A>
      case MaybeT<F, A> directMaybeT -> directMaybeT;
      default ->
          throw new KindUnwrapException(
              MaybeTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link MaybeT MaybeT&lt;F, A&gt;} instance into its {@link Kind}
   * representation, {@code Kind<MaybeTKind.Witness<F>, A>}.
   *
   * <p>Since {@link MaybeT} directly implements {@link MaybeTKind} (which extends {@code
   * Kind<MaybeTKind.Witness<F>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param maybeT The concrete {@link MaybeT} instance to wrap. Must not be null.
   * @return The {@code Kind} representation of the {@code maybeT}.
   * @throws NullPointerException if {@code maybeT} is null.
   */
  @SuppressWarnings("unchecked") // Safe due to MaybeT implementing MaybeTKind
  public static <F, A> @NonNull Kind<MaybeTKind.Witness<F>, A> wrap(@NonNull MaybeT<F, A> maybeT) {
    Objects.requireNonNull(maybeT, "Input MaybeT cannot be null for wrap");
    // maybeT is already a MaybeTKind<F, A>, which is a Kind<MaybeTKind.Witness<F>, A>.
    // Explicit cast to Kind for type safety at the call site if needed,
    // though just returning maybeT would also work due to assignability.
    return (Kind<MaybeTKind.Witness<F>, A>) (MaybeTKind<F, A>) maybeT;
  }
}
