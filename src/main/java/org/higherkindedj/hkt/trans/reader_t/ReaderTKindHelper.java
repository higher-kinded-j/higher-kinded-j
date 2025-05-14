package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing static helper methods for working with {@link ReaderT} instances in the
 * context of higher-kinded types (HKT). It facilitates the conversion between the concrete {@link
 * ReaderT ReaderT&lt;F, R, A&gt;} type and its HKT representation, {@code
 * Kind<ReaderTKind.Witness<F, R>, A>}.
 *
 * <p>Since {@link ReaderT} now directly implements {@link ReaderTKind}, this helper primarily
 * facilitates type casting and provides runtime safety checks.
 *
 * @see ReaderT
 * @see ReaderTKind
 * @see Kind
 * @see KindUnwrapException
 */
public final class ReaderTKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for ReaderT";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderT: ";

  private ReaderTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} back to its concrete {@link ReaderT
   * ReaderT&lt;F, R_ENV, A&gt;} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents a {@link ReaderT} computation. The type parameter {@code
   * ReaderTKind.Witness<F, R_ENV>} acts as the witness for the higher-kinded type {@code ReaderT<F,
   * R_ENV, _>}.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT} within its outer monad.
   * @param kind The {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} instance to unwrap. May be
   *     {@code null}.
   * @return The underlying, non-null {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not an instance of
   *     {@link ReaderT}.
   */
  @SuppressWarnings("unchecked")
  public static <F, R_ENV, A> @NonNull ReaderT<F, R_ENV, A> unwrap(
      @Nullable Kind<ReaderTKind.Witness<F, R_ENV>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(ReaderTKindHelper.INVALID_KIND_NULL_MSG);
      case ReaderT<F, R_ENV, A> directReaderT -> directReaderT;
      default ->
          throw new KindUnwrapException(
              ReaderTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>}.
   *
   * <p>Since {@link ReaderT} directly implements {@link ReaderTKind} (which extends {@code
   * Kind<ReaderTKind.Witness<F, R_ENV>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R_ENV> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param readerT The concrete {@link ReaderT ReaderT&lt;F, R_ENV, A&gt;} instance to wrap. Must
   *     be {@code @NonNull}.
   * @return A non-null {@code Kind<ReaderTKind.Witness<F, R_ENV>, A>} representing the wrapped
   *     {@code readerT}.
   * @throws NullPointerException if {@code readerT} is {@code null}.
   */
  public static <F, R_ENV, A> @NonNull Kind<ReaderTKind.Witness<F, R_ENV>, A> wrap(
      @NonNull ReaderT<F, R_ENV, A> readerT) {
    Objects.requireNonNull(readerT, "Input ReaderT cannot be null for wrap");
    return (Kind<ReaderTKind.Witness<F, R_ENV>, A>) (ReaderTKind<F, R_ENV, A>) readerT;
  }
}
