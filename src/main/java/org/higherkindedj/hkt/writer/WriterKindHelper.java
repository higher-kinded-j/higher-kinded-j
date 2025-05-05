package org.higherkindedj.hkt.writer;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.typeclass.Monoid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link WriterKind} HKT simulation. Provides static methods for
 * wrapping, unwrapping, creating, and running {@link Writer} instances within the Kind simulation.
 */
public final class WriterKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Writer";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a WriterHolder: ";
  // This message is technically redundant now due to @NonNull, but kept for consistency/history
  public static final String INVALID_HOLDER_STATE_MSG =
      "WriterHolder contained null Writer instance";

  private WriterKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder record - Note: writer component IS marked @NonNull
  record WriterHolder<W, A>(@NonNull Writer<W, A> writer) implements WriterKind<W, A> {}

  /**
   * Unwraps a WriterKind back to the concrete {@code Writer<W, A>} type. Throws KindUnwrapException
   * if the Kind is null or not a valid WriterHolder.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind<W, ?>, A>} instance to unwrap. Can be {@code @Nullable}.
   * @return The unwrapped, non-null {@code Writer<W, A>} instance. Returns {@code @NonNull}.
   * @throws KindUnwrapException if the input {@code kind} is null or not an instance of {@code
   *     WriterHolder}.
   */
  @SuppressWarnings("unchecked") // For casting writer - safe after pattern match
  public static <W, A> @NonNull Writer<W, A> unwrap(@Nullable Kind<WriterKind<W, ?>, A> kind) {
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a WriterHolder (record pattern extracts non-null writer)
      // The @NonNull contract on WriterHolder.writer guarantees writer is not null here.
      case WriterKindHelper.WriterHolder<?, ?>(var writer) ->
          // Cast is safe because pattern matched and writer is known non-null.
          (Writer<W, A>) writer;

      // Case 3: Input Kind is non-null but not a WriterHolder
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Writer<W, A>} value into the WriterKind Higher-Kinded-J type.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param writer The concrete {@code Writer<W, A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code WriterKind<W, A>} representation. Returns {@code @NonNull}.
   * @throws NullPointerException if writer is null.
   */
  public static <W, A> @NonNull WriterKind<W, A> wrap(@NonNull Writer<W, A> writer) {
    Objects.requireNonNull(writer, "Input Writer cannot be null for wrap");
    return new WriterHolder<>(writer);
  }

  /**
   * Creates a WriterKind with an empty log (based on the provided Monoid) and the given value.
   * Wraps {@link Writer#value(Monoid, Object)}.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param monoidW The Monoid instance for the log type W. Must be {@code @NonNull}.
   * @param value The computed value. Can be {@code @Nullable}.
   * @return A {@code Kind<WriterKind<W, ?>, A>} representing the value with an empty log. Returns
   *     {@code @NonNull}.
   * @throws NullPointerException if monoidW is null.
   */
  public static <W, A> @NonNull WriterKind<W, A> value(
      @NonNull Monoid<W> monoidW, @Nullable A value) {
    // Writer.value performs null check on monoidW
    return wrap(Writer.value(monoidW, value));
  }

  /**
   * Creates a WriterKind that logs a message but has a Void (null) value. Wraps {@link
   * Writer#tell(Object)}.
   *
   * @param <W> The type of the accumulated log/output.
   * @param monoidW The Monoid instance for the log type W (needed by some internal logic, though
   *     not directly used by Writer.tell). Must be {@code @NonNull}.
   * @param log The log message to accumulate. Must be {@code @NonNull}.
   * @return A {@code Kind<WriterKind<W, ?>, Void>} representing only the log action. Returns
   *     {@code @NonNull}.
   * @throws NullPointerException if monoidW or log is null.
   */
  public static <W> @NonNull WriterKind<W, Void> tell(@NonNull Monoid<W> monoidW, @NonNull W log) {
    // Perform null checks before calling wrap/tell
    Objects.requireNonNull(monoidW, "Monoid<W> cannot be null for tell helper");
    Objects.requireNonNull(log, "Log message for tell cannot be null");
    return wrap(Writer.tell(log));
  }

  /**
   * Runs the WriterKind computation, returning the computed value and accumulated log as a {@link
   * Writer} record. Essentially unwraps the Kind.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind<W, ?>, A>} holding the Writer computation. Must be
   *     {@code @NonNull}.
   * @return The {@link Writer} record containing the final value and log. Returns {@code @NonNull}.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   */
  public static <W, A> @NonNull Writer<W, A> runWriter(@NonNull Kind<WriterKind<W, ?>, A> kind) {
    // unwrap throws KindUnwrapException if kind is invalid
    return unwrap(kind);
  }

  /**
   * Runs the WriterKind computation, returning only the computed value A. Calls {@link
   * #unwrap(Kind)} and then {@link Writer#run()}.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind<W, ?>, A>} holding the Writer computation. Must be
   *     {@code @NonNull}.
   * @return The computed value A. Can be {@code @Nullable} depending on A.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   */
  public static <W, A> @Nullable A run(@NonNull Kind<WriterKind<W, ?>, A> kind) {
    // unwrap throws KindUnwrapException if kind is invalid
    return unwrap(kind).run();
  }

  /**
   * Runs the WriterKind computation, returning only the accumulated log W. Calls {@link
   * #unwrap(Kind)} and then {@link Writer#exec()}.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind<W, ?>, A>} holding the Writer computation. Must be
   *     {@code @NonNull}.
   * @return The accumulated log W. Returns {@code @NonNull} (as Writer log is NonNull).
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   */
  public static <W, A> @NonNull W exec(@NonNull Kind<WriterKind<W, ?>, A> kind) {
    // unwrap throws KindUnwrapException if kind is invalid
    return unwrap(kind).exec();
  }
}
