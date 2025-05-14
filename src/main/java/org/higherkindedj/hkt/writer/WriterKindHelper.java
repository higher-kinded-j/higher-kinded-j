package org.higherkindedj.hkt.writer;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link WriterKind} in the context of higher-kinded types (HKT).
 * Provides static methods for wrapping a {@link Writer} into its HKT form, unwrapping it back,
 * creating {@link Writer} instances represented as HKTs, and running them.
 *
 * @see Writer
 * @see WriterKind
 * @see WriterKind.Witness
 * @see Kind
 * @see Monoid
 */
public final class WriterKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Writer";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a WriterHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null}
   * Writer instance. This should ideally not occur if {@link #wrap(Writer)} enforces non-null
   * Writer instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "WriterHolder contained null Writer instance";

  private WriterKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link WriterKind WriterKind&lt;W, A&gt;} to hold the concrete
   * {@link Writer Writer&lt;W, A&gt;} instance. Since {@code WriterKind<W, A>} now extends {@code
   * Kind<WriterKind.Witness<W>, A>}, this holder bridges the concrete type and its HKT
   * representation.
   *
   * @param <W> The log type.
   * @param <A> The value type.
   * @param writer The non-null {@link Writer Writer&lt;W, A&gt;} instance.
   */
  record WriterHolder<W, A>(@NonNull Writer<W, A> writer) implements WriterKind<W, A> {}

  /**
   * Unwraps a {@code Kind<WriterKind.Witness<W>, A>} back to its concrete {@link Writer
   * Writer&lt;W, A&gt;} type.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} instance to unwrap. Can be
   *     {@code @Nullable}.
   * @return The unwrapped, non-null {@link Writer Writer&lt;W, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is null or not an instance of {@code
   *     WriterHolder}.
   */
  @SuppressWarnings("unchecked")
  public static <W, A> @NonNull Writer<W, A> unwrap(@Nullable Kind<WriterKind.Witness<W>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case WriterKindHelper.WriterHolder<?, ?> holder -> (Writer<W, A>) holder.writer();
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link Writer Writer&lt;W, A&gt;} instance into its higher-kinded
   * representation, {@link WriterKind WriterKind&lt;W, A&gt;} (which is also a {@code
   * Kind<WriterKind.Witness<W>, A>}).
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param writer The concrete {@link Writer Writer&lt;W, A&gt;} instance to wrap. Must be
   *     {@code @NonNull}.
   * @return A non-null {@link WriterKind WriterKind&lt;W, A&gt;} representing the wrapped {@code
   *     Writer}.
   * @throws NullPointerException if {@code writer} is null.
   */
  public static <W, A> @NonNull WriterKind<W, A> wrap(@NonNull Writer<W, A> writer) {
    Objects.requireNonNull(writer, "Input Writer cannot be null for wrap");
    return new WriterHolder<>(writer);
  }

  /**
   * Creates a {@code Kind<WriterKind.Witness<W>, A>} with an empty log (based on the provided
   * {@link Monoid}) and the given value. This is a common way to lift a pure value into the {@link
   * Writer} context.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param monoidW The {@link Monoid} instance for the log type {@code W}. Must be
   *     {@code @NonNull}.
   * @param value The computed value. Can be {@code @Nullable}.
   * @return A {@code Kind<WriterKind.Witness<W>, A>} representing the value with an empty log.
   *     Never null.
   * @throws NullPointerException if {@code monoidW} is null.
   */
  public static <W, A> @NonNull Kind<WriterKind.Witness<W>, A> value(
      @NonNull Monoid<W> monoidW, @Nullable A value) {
    return wrap(Writer.value(monoidW, value));
  }

  /**
   * Creates a {@code Kind<WriterKind.Witness<W>, Void>} that logs a message but has a {@link Void}
   * (null) value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param monoidW The {@link Monoid} instance for the log type {@code W}. While not directly used
   *     by {@code Writer.tell(log)} itself, it's good practice to ensure its availability if other
   *     helper operations might assume it for consistency. Must be {@code @NonNull}.
   * @param log The log message to accumulate. Must be {@code @NonNull}.
   * @return A {@code Kind<WriterKind.Witness<W>, Void>} representing only the log action. Never
   *     null.
   * @throws NullPointerException if {@code monoidW} or {@code log} is null.
   */
  public static <W> @NonNull Kind<WriterKind.Witness<W>, Void> tell(
      @NonNull Monoid<W> monoidW, @NonNull W log) {
    Objects.requireNonNull(monoidW, "Monoid<W> cannot be null for tell helper");
    Objects.requireNonNull(log, "Log message for tell cannot be null");
    return wrap(Writer.tell(log));
  }

  /**
   * Runs the {@link Writer} computation held within the {@link Kind} wrapper, returning the
   * complete {@link Writer Writer&lt;W, A&gt;} record which contains both the log and the value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} holding the {@code Writer} computation.
   *     Must be {@code @NonNull}.
   * @return The {@link Writer Writer&lt;W, A&gt;} record containing the final value and log. Never
   *     null.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   */
  public static <W, A> @NonNull Writer<W, A> runWriter(
      @NonNull Kind<WriterKind.Witness<W>, A> kind) {
    return unwrap(kind);
  }

  /**
   * Runs the {@link Writer} computation held within the {@link Kind} wrapper, returning only the
   * computed value {@code A} and discarding the log.
   *
   * @param <W> The type of the accumulated log/output (discarded).
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} holding the {@code Writer} computation.
   *     Must be {@code @NonNull}.
   * @return The computed value {@code A}. Can be {@code @Nullable} depending on {@code A}.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   */
  public static <W, A> @Nullable A run(@NonNull Kind<WriterKind.Witness<W>, A> kind) {
    return unwrap(kind).run();
  }

  /**
   * Runs the {@link Writer} computation held within the {@link Kind} wrapper, returning only the
   * accumulated log {@code W} and discarding the value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value (discarded).
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} holding the {@code Writer} computation.
   *     Must be {@code @NonNull}.
   * @return The accumulated log {@code W}. Never null (as {@link Writer}'s log is
   *     {@code @NonNull}).
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   */
  public static <W, A> @NonNull W exec(@NonNull Kind<WriterKind.Witness<W>, A> kind) {
    return unwrap(kind).exec();
  }
}
