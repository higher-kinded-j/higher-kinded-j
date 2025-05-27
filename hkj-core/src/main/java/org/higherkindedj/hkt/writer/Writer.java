// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that produces a primary value of type {@code A} along with an
 * accumulated output (log) of type {@code W}. The {@code Writer} monad is useful for computations
 * that need to produce some auxiliary information (like logs, metrics, or other accumulated data)
 * in addition to their main result.
 *
 * <p>A {@link Monoid} for the log type {@code W} is required to combine logs when sequencing {@code
 * Writer} computations (e.g., in {@code flatMap}). This class is implemented as an immutable
 * record, effectively a pair {@code (W, A)}.
 *
 * <p><b>Key Characteristics:</b>
 *
 * <ul>
 *   <li><b>Logging:</b> Allows functions to "write" to a log without using side effects.
 *   <li><b>Purity:</b> Constructing and combining {@code Writer} instances are pure operations.
 *   <li><b>Composability:</b> {@code Writer} operations can be easily chained using methods like
 *       {@link #map(Function)} and {@link #flatMap(Monoid, Function)}.
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * // Assuming a StringMonoid that implements Monoid<String>
 * Monoid<String> stringMonoid = new StringMonoid(); // (Implementation not shown here)
 *
 * // Create a Writer that logs a message and produces a value
 * Writer<String, Integer> writer1 = Writer.create("Initial step done. ", 10);
 *
 * // Map over the value
 * Writer<String, String> writer2 = writer1.map(value -> "Value is: " + value);
 * // writer2 contains log: "Initial step done. ", value: "Value is: 10"
 *
 * // FlatMap to sequence another operation
 * Writer<String, Integer> writer3 = writer2.flatMap(stringMonoid, valueStr ->
 * Writer.create("Processed string '" + valueStr + "'. ", valueStr.length())
 * );
 * // writer3 contains log: "Initial step done. Processed string 'Value is: 10'. ", value: 12
 *
 * System.out.println("Final Log: " + writer3.exec());
 * System.out.println("Final Value: " + writer3.run());
 * }</pre>
 *
 * @param <W> The type of the accumulated output (log). This type must have an associated {@link
 *     Monoid} instance for combining logs.
 * @param <A> The type of the primary computed value.
 * @param log The accumulated log or output value of type {@code W}. This component is guaranteed to
 *     be non-null.
 * @param value The primary computed result value of type {@code A}. This component can be {@code
 *     null} if {@code A} is a nullable type.
 * @see WriterKind
 * @see WriterMonad
 * @see Monoid
 */
public record Writer<W, A>(@NonNull W log, @Nullable A value) {

  /**
   * Canonical constructor for the {@link Writer} record. Ensures that the provided log is non-null.
   *
   * @param log The accumulated log/output value. Must not be null.
   * @param value The computed result value. Can be null.
   * @throws NullPointerException if {@code log} is null.
   */
  public Writer {
    Objects.requireNonNull(log, "Writer log cannot be null");
  }

  /**
   * Static factory method to create a {@link Writer} with the given log and value.
   *
   * @param <W> The type of the log.
   * @param <A> The type of the value.
   * @param log The log to associate with this {@code Writer}. Must not be null.
   * @param value The value to associate with this {@code Writer}. Can be null.
   * @return A new {@link Writer} instance. Never null.
   * @throws NullPointerException if {@code log} is null.
   */
  public static <W, A> @NonNull Writer<W, A> create(@NonNull W log, @Nullable A value) {
    return new Writer<>(log, value);
  }

  /**
   * Creates a {@link Writer} with an empty log (obtained from the provided {@link Monoid}) and the
   * given value. This method is often used as the {@code of} or {@code pure} operation in the
   * context of the Writer monad, lifting a pure value into the {@code Writer} context with no
   * initial log accumulation.
   *
   * @param <W> The type of the log.
   * @param <A> The type of the value.
   * @param monoidW The {@link Monoid} for the log type {@code W}, used to get the empty log value.
   *     Must not be null.
   * @param value The value to wrap in the {@code Writer}. Can be null.
   * @return A new {@link Writer} instance with an empty log and the provided value. Never null.
   * @throws NullPointerException if {@code monoidW} is null.
   */
  public static <W, A> @NonNull Writer<W, A> value(@NonNull Monoid<W> monoidW, @Nullable A value) {
    Objects.requireNonNull(monoidW, "Monoid<W> for Writer.value cannot be null");
    return new Writer<>(monoidW.empty(), value);
  }

  /**
   * Creates a {@link Writer} that produces no primary value (typically represented by {@link Void},
   * resulting in a {@code null} value component) but records the given log message. This is useful
   * for computations that only contribute to the log.
   *
   * @param <W> The type of the log.
   * @param log The log message to record. Must not be null.
   * @return A new {@link Writer} instance with the given log and a {@code null} value. Never null.
   * @throws NullPointerException if {@code log} is null.
   */
  public static <W> @NonNull Writer<W, Void> tell(@NonNull W log) {
    Objects.requireNonNull(log, "Log message for Writer.tell cannot be null");
    return new Writer<>(log, null); // Void actions typically result in a null value.
  }

  /**
   * Transforms the primary value of this {@link Writer} from type {@code A} to type {@code B} using
   * the provided mapping function, while keeping the log {@code W} unchanged. This is the Functor
   * {@code map} operation for {@code Writer}.
   *
   * @param <B> The type of the value produced by the mapping function and thus by the new {@code
   *     Writer}.
   * @param f The non-null function to apply to the primary value of this {@code Writer}. It takes a
   *     value of type {@code A} and returns a value of type {@code B}.
   * @return A new {@link Writer} instance with the original log and the transformed value. Never
   *     null.
   * @throws NullPointerException if {@code f} is null.
   */
  public <B> @NonNull Writer<W, B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "Mapper function for Writer.map cannot be null");
    return new Writer<>(this.log, f.apply(this.value));
  }

  /**
   * Applies a function that takes the primary value {@code A} of this {@link Writer} and returns
   * another {@code Writer<W, B>}. The log of the original {@code Writer} is combined with the log
   * of the {@code Writer} produced by the function, using the provided {@link Monoid} for {@code
   * W}. This is the Monad {@code flatMap} (or {@code bind}) operation for {@code Writer}.
   *
   * @param <B> The type of the value in the {@code Writer} produced by the function {@code f}.
   * @param monoidW The {@link Monoid} for the log type {@code W}, used to combine logs. Must not be
   *     null.
   * @param f The non-null function to apply to the primary value of this {@code Writer}. It takes a
   *     value of type {@code A} and returns a new {@code Writer<W, ? extends B>}. The {@code
   *     Writer} returned by this function must not be null.
   * @return A new {@link Writer} instance with the combined log and the value from the {@code
   *     Writer} produced by {@code f}. Never null.
   * @throws NullPointerException if {@code monoidW}, {@code f}, or the {@code Writer} returned by
   *     {@code f} is null.
   */
  public <B> @NonNull Writer<W, B> flatMap(
      @NonNull Monoid<W> monoidW,
      @NonNull Function<? super A, ? extends Writer<W, ? extends B>> f) {
    Objects.requireNonNull(monoidW, "Monoid<W> for Writer.flatMap cannot be null");
    Objects.requireNonNull(f, "FlatMap mapper function for Writer.flatMap cannot be null");

    Writer<W, ? extends B> nextWriter = f.apply(this.value);
    Objects.requireNonNull(
        nextWriter, "Function f supplied to Writer.flatMap returned a null Writer");

    W combinedLog = monoidW.combine(this.log, nextWriter.log());
    B nextValue = nextWriter.value();

    return new Writer<>(combinedLog, nextValue);
  }

  /**
   * Retrieves the primary computed value of this {@link Writer}, discarding the log.
   *
   * @return The primary value of type {@code A}. Can be {@code null} if {@code A} is nullable or if
   *     the {@code Writer} was constructed to represent a {@link Void} value (e.g., via {@link
   *     #tell(Object)}).
   */
  public @Nullable A run() {
    return this.value;
  }

  /**
   * Retrieves the accumulated log of this {@link Writer}, discarding the primary value.
   *
   * @return The accumulated log of type {@code W}. Never null.
   */
  public @NonNull W exec() {
    return this.log;
  }
}
