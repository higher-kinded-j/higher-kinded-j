package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing static helper methods for working with {@link ReaderT} instances in the
 * context of higher-kinded types (HKT). It facilitates the conversion between the concrete {@link
 * ReaderT ReaderT&lt;F, R, A&gt;} type and its HKT representation, {@code Kind<ReaderTKind<F, R,
 * ?>, A>}.
 *
 * <p>This class is essential for bridging the gap between the concrete {@code ReaderT} monad
 * transformer and generic functional programming abstractions that operate over {@link Kind}
 * instances. It provides methods for:
 *
 * <ul>
 *   <li>Wrapping a {@link ReaderT} into its {@link ReaderTKind} form ({@link #wrap(ReaderT)}).
 *   <li>Unwrapping a {@link ReaderTKind} (represented as a {@link Kind}) back to a {@link ReaderT}
 *       ({@link #unwrap(Kind)}).
 * </ul>
 *
 * <p>The unwrapping mechanism relies on an internal private record, {@link ReaderTHolder}, which
 * implements {@link ReaderTKind} to encapsulate the actual {@code ReaderT} instance.
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
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderTHolder: ";

  /**
   * Error message for when the internal {@link ReaderTHolder} in {@link #unwrap(Kind)} contains a
   * {@code null} {@link ReaderT} instance. This should ideally not occur if {@link #wrap(ReaderT)}
   * enforces non-null {@link ReaderT} instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "ReaderTHolder contained null ReaderT instance";

  private ReaderTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a {@code Kind<ReaderTKind<F, R, ?>, A>} back to its concrete {@link ReaderT
   * ReaderT&lt;F, R, A&gt;} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents a {@link ReaderT} computation. The type parameter {@code ReaderTKind<F, R,
   * ?>} acts as the witness for the higher-kinded type {@code ReaderT<F, R, _>}.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT} within its outer monad.
   * @param kind The {@code Kind<ReaderTKind<F, R, ?>, A>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@link ReaderT ReaderT&lt;F, R, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@link ReaderTHolder}, or if (theoretically) the holder contains a null {@code ReaderT}
   *     instance.
   */
  @SuppressWarnings("unchecked")
  public static <F, R, A> @NonNull ReaderT<F, R, A> unwrap(
      @Nullable Kind<ReaderTKind<F, R, ?>, A> kind) {

    return switch (kind) {
      case null -> throw new KindUnwrapException(ReaderTKindHelper.INVALID_KIND_NULL_MSG);
      case ReaderTKindHelper.ReaderTHolder<?, ?, ?> holder -> (ReaderT<F, R, A>) holder.readerT();
      default ->
          throw new KindUnwrapException(
              ReaderTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link ReaderT ReaderT&lt;F, R, A&gt;} instance into its higher-kinded
   * representation, {@link ReaderTKind ReaderTKind&lt;F, R, A&gt;}.
   *
   * <p>Since {@code ReaderTKind<F, R, A>} implements {@code Kind<ReaderTKind<F, R, ?>, A>}, the
   * result can be used where such a {@link Kind} is expected.
   *
   * @param <F> The witness type of the outer monad in {@code ReaderT}.
   * @param <R> The type of the environment required by the {@code ReaderT}.
   * @param <A> The type of the value produced by the {@code ReaderT}.
   * @param readerT The concrete {@link ReaderT ReaderT&lt;F, R, A&gt;} instance to wrap. Must be
   *     {@code @NonNull}.
   * @return A non-null {@link ReaderTKind ReaderTKind&lt;F, R, A&gt;} representing the wrapped
   *     {@code ReaderT}.
   * @throws NullPointerException if {@code readerT} is {@code null}.
   */
  public static <F, R, A> @NonNull ReaderTKind<F, R, A> wrap(@NonNull ReaderT<F, R, A> readerT) {
    Objects.requireNonNull(readerT, "Input ReaderT cannot be null for wrap");
    return new ReaderTHolder<>(readerT);
  }

  /**
   * Internal record implementing {@link ReaderTKind ReaderTKind&lt;F, R, A&gt;} to hold the
   * concrete {@link ReaderT ReaderT&lt;F, R, A&gt;} instance. This is the mechanism enabling the
   * HKT simulation for {@code ReaderT}.
   *
   * <p>The {@link ReaderT} itself, and thus its {@code run} function ({@link Function
   * Function&lt;R, Kind&lt;F, A&gt;&gt;}), is guaranteed to be non-null if this holder is correctly
   * constructed via {@link #wrap(ReaderT)}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R> The environment type of the {@code ReaderT}.
   * @param <A> The value type of the {@code ReaderT}.
   * @param readerT The non-null, actual {@link ReaderT ReaderT&lt;F, R, A&gt;} instance.
   */
  record ReaderTHolder<F, R, A>(@NonNull ReaderT<F, R, A> readerT)
      implements ReaderTKind<F, R, A> {}
}
