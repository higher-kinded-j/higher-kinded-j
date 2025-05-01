package org.higherkindedj.hkt.writer;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.typeclass.Monoid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class WriterKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Writer";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a WriterHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "WriterHolder contained null Writer instance";

  private WriterKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /** Unwraps a WriterKind back to the concrete Writer<W, A> type. */
  @SuppressWarnings("unchecked")
  public static <W, A> @NonNull Writer<W, A> unwrap(@Nullable Kind<WriterKind<W, ?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }
    if (kind instanceof WriterHolder<?, ?> holder) {
      if (holder.writer() == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      // Cast is safe here due to type structure WriterHolder<W, A> implements WriterKind<W, A>
      return (Writer<W, A>) holder.writer();
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /** Wraps a concrete Writer<W, A> value into the WriterKind higherkindedj type. */
  public static <W, A> @NonNull WriterKind<W, A> wrap(@NonNull Writer<W, A> writer) {
    Objects.requireNonNull(writer, "Input Writer cannot be null for wrap");
    return new WriterHolder<>(writer);
  }

  /** Creates a WriterKind with an empty log and the given value. */
  public static <W, A> @NonNull WriterKind<W, A> value(
      @NonNull Monoid<W> monoidW, @Nullable A value) {
    return wrap(Writer.value(monoidW, value));
  }

  /** Creates a WriterKind that logs a message but has a Void value. */
  public static <W> @NonNull WriterKind<W, Void> tell(@NonNull Monoid<W> monoidW, @NonNull W log) {
    // Ensure the log being told is not the empty value, otherwise it's redundant
    // Although the Writer itself handles null log, tell implies adding something.
    Objects.requireNonNull(log, "Log message for tell cannot be null");
    return wrap(Writer.tell(log));
  }

  /** Runs the WriterKind computation, returning the value and log as a Writer record. */
  public static <W, A> @NonNull Writer<W, A> runWriter(@NonNull Kind<WriterKind<W, ?>, A> kind) {
    return unwrap(kind);
  }

  /** Runs the WriterKind computation, returning only the computed value A. */
  public static <W, A> @Nullable A run(@NonNull Kind<WriterKind<W, ?>, A> kind) {
    return unwrap(kind).run();
  }

  /** Runs the WriterKind computation, returning only the accumulated log W. */
  public static <W, A> @NonNull W exec(@NonNull Kind<WriterKind<W, ?>, A> kind) {
    return unwrap(kind).exec();
  }

  // Internal holder record
  record WriterHolder<W, A>(@NonNull Writer<W, A> writer) implements WriterKind<W, A> {}
}
