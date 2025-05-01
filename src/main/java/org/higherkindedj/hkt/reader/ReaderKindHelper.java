package org.higherkindedj.hkt.reader;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ReaderKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Reader";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "ReaderHolder contained null Reader instance";

  private ReaderKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a ReaderKind back to the concrete Reader<R, A> type. Throws KindUnwrapException if the
   * Kind is null, not a ReaderHolder, or the holder contains a null Reader instance.
   */
  @SuppressWarnings("unchecked")
  public static <R, A> @NonNull Reader<R, A> unwrap(@Nullable Kind<ReaderKind<R, ?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }
    if (kind instanceof ReaderHolder<?, ?> holder) { // Use pattern matching
      if (holder.reader() == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      // Cast is safe here due to type structure ReaderHolder<R, A> implements ReaderKind<R, A>
      return (Reader<R, A>) holder.reader();
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  /**
   * Wraps a concrete Reader<R, A> value into the ReaderKind higherkindedj type. Requires a non-null
   * Reader as input.
   */
  public static <R, A> @NonNull ReaderKind<R, A> wrap(@NonNull Reader<R, A> reader) {
    Objects.requireNonNull(reader, "Input Reader cannot be null for wrap");
    return new ReaderHolder<>(reader);
  }

  /** Creates a ReaderKind directly from a function R -> A. */
  public static <R, A> @NonNull ReaderKind<R, A> reader(@NonNull Function<R, A> runFunction) {
    return wrap(Reader.of(runFunction));
  }

  /** Creates a ReaderKind that ignores the environment and always returns the given value. */
  public static <R, A> @NonNull ReaderKind<R, A> constant(@Nullable A value) {
    return wrap(Reader.constant(value));
  }

  /** Creates a ReaderKind that simply returns the environment itself. */
  public static <R> @NonNull ReaderKind<R, R> ask() {
    return wrap(Reader.ask());
  }

  /** Runs the Reader computation within the Kind with the given environment. */
  public static <R, A> @Nullable A runReader(
      @NonNull Kind<ReaderKind<R, ?>, A> kind, @NonNull R environment) {
    return unwrap(kind).run(environment);
  }

  // Internal holder record
  record ReaderHolder<R, A>(@NonNull Reader<R, A> reader) implements ReaderKind<R, A> {}
}
