package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ReaderTKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for ReaderT";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderTHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "ReaderTHolder contained null ReaderT instance";

  private ReaderTKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a ReaderTKind back to the concrete {@code ReaderT<F, R, A>} type. Throws
   * KindUnwrapException if the Kind is null, not a ReaderTHolder, or the holder contains a null
   * ReaderT instance.
   */
  @SuppressWarnings("unchecked")
  public static <F, R, A> @NonNull ReaderT<F, R, A> unwrap(
      @Nullable Kind<ReaderTKind<F, R, ?>, A> kind) {

    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(ReaderTKindHelper.INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a ReaderTHolder (record pattern extracts non-null readerT)
      // The @NonNull contract on ReaderTHolder.readerT guarantees readerT is not null here.
      case ReaderTKindHelper.ReaderTHolder<?, ?, ?>(var readerT) ->
          // Cast is safe because pattern matched and readerT is known non-null.
          (ReaderT<F, R, A>) readerT;

      // Case 3: Input Kind is non-null but not a ReaderTHolder
      default ->
          throw new KindUnwrapException(
              ReaderTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code ReaderT<F, R, A>} value into the ReaderTKind simulation type. Requires
   * a non-null ReaderT instance as input.
   */
  public static <F, R, A> @NonNull ReaderTKind<F, R, A> wrap(@NonNull ReaderT<F, R, A> readerT) {
    Objects.requireNonNull(readerT, "Input ReaderT cannot be null for wrap");
    return new ReaderTHolder<>(readerT);
  }

  // Internal holder record implementing the Kind interface
  // Ensure @NonNull on the payload as ReaderT itself requires a non-null function
  record ReaderTHolder<F, R, A>(@NonNull ReaderT<F, R, A> readerT)
      implements ReaderTKind<F, R, A> {}
}
