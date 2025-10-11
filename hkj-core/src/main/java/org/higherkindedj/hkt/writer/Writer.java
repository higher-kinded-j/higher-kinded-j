// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.CoreTypeValidator;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that, in addition to producing a value of type {@code A}, also
 * accumulates a log or output of type {@code W}. The log type {@code W} must have a {@link Monoid}
 * instance, which defines how logs are combined (typically through an append operation) and what
 * constitutes an empty log.
 *
 * <p>A {@code Writer<W, A>} instance holds a pair: {@code (log: W, value: A)}.
 *
 * <ul>
 *   <li>The {@code log} component is guaranteed to be non-null.
 *   <li>The {@code value} component can be {@code null} if {@code A} is a nullable type. If {@code
 *       A} is {@link Unit}, the value will be {@link Unit#INSTANCE}.
 * </ul>
 *
 * <p>Common operations include:
 *
 * <ul>
 *   <li>{@link #value(Monoid, Object)}: Creates a {@code Writer} with a pure value and an empty
 *       log.
 *   <li>{@link #tell(Object)}: Creates a {@code Writer} that only produces a log entry, with {@link
 *       Unit#INSTANCE} as its value.
 *   <li>{@link #map(Function)}: Transforms the computed value without affecting the log.
 *   <li>{@link #flatMap(Monoid, Function)}: Sequences computations, combining their logs and
 *       threading the value of the first into the second.
 * </ul>
 *
 * @param <W> The type of the log, which must form a {@link Monoid}. This log is guaranteed to be
 *     non-null.
 * @param <A> The type of the computed value. This can be {@code null} if the type {@code A} permits
 *     it (e.g., {@code String}). If {@code A} is {@link Unit}, this will be {@link Unit#INSTANCE}.
 * @param log The accumulated log of type {@code W}. Must not be {@code null}.
 * @param value The computed value of type {@code A}. May be {@code null} (if {@code A} is a
 *     nullable type like {@code String}); if {@code A} is {@link Unit}, this will be {@link
 *     Unit#INSTANCE}.
 * @see Monoid
 * @see Unit
 * @see WriterMonad
 * @see WriterKindHelper
 */
public record Writer<W, A>(W log, @Nullable A value) {

  private static Class<Writer> WRITER_CLASS = Writer.class;

  /**
   * Compact constructor for {@link Writer}. Ensures that the log component {@code W} is never
   * {@code null}.
   *
   * @param log The log value. Must not be {@code null}.
   * @param value The computed value. Can be {@code null} if {@code A} is a nullable type. If {@code
   *     A} is {@link Unit}, this should be {@link Unit#INSTANCE}.
   * @throws NullPointerException if {@code log} is {@code null}.
   */
  public Writer {
    CoreTypeValidator.requireValue(log, WRITER_CLASS, CONSTRUCTION);
  }

  /**
   * Creates a {@code Writer} with a pure value and an empty log.
   *
   * @param <W> The type of the log.
   * @param <A> The type of the value.
   * @param monoidW The {@link Monoid} for the log type {@code W}, used to get the empty log. Must
   *     not be {@code null}.
   * @param value The pure value to wrap. Can be {@code null} if {@code A} is a nullable type. If
   *     {@code A} is {@link Unit}, pass {@link Unit#INSTANCE}.
   * @return A new {@code Writer<W, A>} with the given value and an empty log.
   * @throws NullPointerException if {@code monoidW} is {@code null}.
   */
  public static <W, A> Writer<W, A> value(Monoid<W> monoidW, @Nullable A value) {
    FunctionValidator.requireMonoid(monoidW, "monoidW", WRITER_CLASS, VALUE);
    return new Writer<>(monoidW.empty(), value);
  }

  /**
   * Creates a {@code Writer} that records the given {@code log} message and has {@link
   * Unit#INSTANCE} as its value. This is useful for computations that only produce output/log
   * entries without a significant return value.
   *
   * @param <W> The type of the log.
   * @param log The log message to record. Must not be {@code null}.
   * @return A new {@code Writer<W, Unit>} with the specified log and {@link Unit#INSTANCE} as the
   *     value.
   * @throws NullPointerException if {@code log} is {@code null}.
   */
  public static <W> Writer<W, Unit> tell(W log) {
    CoreTypeValidator.requireValue(log, "log", WRITER_CLASS, TELL);
    return new Writer<>(log, Unit.INSTANCE);
  }

  /**
   * Transforms the computed value of this {@code Writer} from type {@code A} to type {@code B}
   * using the provided mapping function {@code f}, while leaving the log unchanged.
   *
   * @param <B> The type of the new computed value.
   * @param f The non-null function to apply to the current computed value. This function takes the
   *     current value of type {@code A} (which could be {@link Unit#INSTANCE} if {@code A} is
   *     {@link Unit}) and returns a value of type {@code B}.
   * @return A new {@code Writer<W, B>} with the original log and the transformed value.
   * @throws NullPointerException if {@code f} is {@code null}.
   */
  public <B> Writer<W, B> map(Function<? super A, ? extends B> f) {
    FunctionValidator.requireMapper(f, WRITER_CLASS, MAP);
    return new Writer<>(this.log, f.apply(this.value));
  }

  /**
   * Composes this {@code Writer} computation with a function {@code f} that takes the current value
   * {@code A} and returns a new {@code Writer<W, B>} computation. The logs from both computations
   * are combined using the {@link Monoid} for {@code W}.
   *
   * <p>This is the monadic bind operation for {@code Writer}. It allows sequencing of operations
   * where the next operation depends on the result of the current one, and logs are accumulated
   * throughout.
   *
   * @param <B> The type of the value produced by the {@code Writer} returned by function {@code f}.
   * @param monoidW The {@link Monoid} for the log type {@code W}, used to combine logs. Must not be
   *     {@code null}.
   * @param f The non-null function that takes the current value of type {@code A} (which could be
   *     {@link Unit#INSTANCE} if {@code A} is {@link Unit}) and returns a new {@code Writer<W, ?
   *     extends B>}. The {@code Writer} returned by this function must not be {@code null}.
   * @return A new {@code Writer<W, B>} where the value is the result of the composed computation,
   *     and the log is the combination of the original log and the log from the {@code Writer}
   *     produced by {@code f}.
   * @throws NullPointerException if {@code monoidW} or {@code f} is {@code null}, or if {@code f}
   *     returns a {@code null} {@code Writer}.
   */
  public <B> Writer<W, B> flatMap(
      Monoid<W> monoidW, Function<? super A, ? extends Writer<W, ? extends B>> f) {

    FunctionValidator.requireMonoid(monoidW, "monoidW", WRITER_CLASS, FLAT_MAP);
    FunctionValidator.requireFlatMapper(f, WRITER_CLASS, FLAT_MAP);

    Writer<W, ? extends B> nextWriter = f.apply(this.value);
    FunctionValidator.requireNonNullResult(nextWriter, FLAT_MAP, Writer.class);

    W combinedLog = monoidW.combine(this.log, nextWriter.log());
    // The cast to B is safe due to the ? extends B in the function's return type.
    return new Writer<>(combinedLog, (B) nextWriter.value());
  }

  /**
   * Runs the {@code Writer} computation, returning the computed value {@code A} and discarding the
   * log {@code W}.
   *
   * @return The computed value {@code A}. This can be {@code null} if {@code A} is a nullable type
   *     (e.g. {@code String}); if {@code A} is {@link Unit}, this will be {@link Unit#INSTANCE}.
   */
  public @Nullable A run() {
    return value;
  }

  /**
   * Runs the {@code Writer} computation, returning the accumulated log {@code W} and discarding the
   * value {@code A}.
   *
   * @return The non-null accumulated log {@code W}.
   */
  public W exec() {
    return log;
  }
}
