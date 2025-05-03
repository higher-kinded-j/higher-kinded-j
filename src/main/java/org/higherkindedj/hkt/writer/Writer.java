package org.higherkindedj.hkt.writer;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.typeclass.Monoid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that produces a value A along with an accumulated output W (log).
 * Requires a Monoid for W. Implemented as an immutable pair (W, A).
 *
 * @param <W> The type of the accumulated output (must have a Monoid).
 * @param <A> The type of the computed value.
 * @param log The accumulated log/output value. (@NonNull, uses {@code Monoid<W>})
 * @param value The computed result value. (@Nullable depends on A)
 */
public record Writer<W, A>(@NonNull W log, @Nullable A value) {

  /** Canonical constructor. Ensures log is non-null. */
  public Writer {
    Objects.requireNonNull(log, "Writer log cannot be null");
  }

  /** Creates a Writer with the given log and value. */
  public static <W, A> @NonNull Writer<W, A> create(@NonNull W log, @Nullable A value) {
    return new Writer<>(log, value);
  }

  /**
   * Creates a Writer with an empty log (using the Monoid's empty) and the given value. This is
   * often used as the 'of' or 'pure' operation for the Writer monad.
   */
  public static <W, A> @NonNull Writer<W, A> value(@NonNull Monoid<W> monoidW, @Nullable A value) {
    // Add explicit null check for monoidW before using it
    Objects.requireNonNull(monoidW, "monoidW cannot be null");
    return new Writer<>(monoidW.empty(), value);
  }

  /** Creates a Writer that produces no value (Unit/Void) but records the given log message. */
  public static <W> @NonNull Writer<W, Void> tell(@NonNull W log) {
    // Ensure the log being told is not the empty value, otherwise it's redundant
    // Although the Writer itself handles null log, tell implies adding something.
    Objects.requireNonNull(log, "log cannot be null"); // Changed message for clarity
    // Here, we assume Void is represented by null for the value type A
    return new Writer<>(log, null);
  }

  /** Maps the value A to B while keeping the log W unchanged. Functor map operation. */
  public <B> @NonNull Writer<W, B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    return new Writer<>(this.log, f.apply(this.value));
  }

  /**
   * Applies a function A -> {@code Writer<W, B>} to the value A, combining the logs. Monadic bind (flatMap)
   * operation.
   */
  public <B> @NonNull Writer<W, B> flatMap(
      @NonNull Monoid<W> monoidW,
      @NonNull Function<? super A, ? extends Writer<W, ? extends B>> f) {
    Objects.requireNonNull(monoidW, "Monoid<W> cannot be null"); // Keep this check
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");

    // Apply f to the current value to get the next Writer
    Writer<W, ? extends B> nextWriter = f.apply(this.value);
    Objects.requireNonNull(nextWriter, "flatMap function returned null Writer");

    // Combine the log from the original writer and the next writer
    @NonNull W combinedLog = monoidW.combine(this.log, nextWriter.log());

    // The new value is the value from the next writer
    @Nullable B nextValue = nextWriter.value();

    // Return the new writer with combined log and next value
    return new Writer<>(combinedLog, nextValue);
  }

  /** Runs the Writer, returning the computed value and discarding the log. */
  public @Nullable A run() {
    return this.value;
  }

  /** Runs the Writer, returning the accumulated log and discarding the value. */
  public @NonNull W exec() {
    return this.log;
  }
}
