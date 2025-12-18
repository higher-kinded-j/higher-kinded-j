// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.writer.Writer;

/**
 * A fluent path wrapper for {@link Writer} computations.
 *
 * <p>{@code WriterPath} represents computations that produce a value along with accumulated output.
 * The output type must have a {@link Monoid} instance for combining. This is useful for logging,
 * audit trails, or any scenario where you want to accumulate information alongside your
 * computation.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Logging during computation
 *   <li>Audit trail generation
 *   <li>Collecting metrics
 *   <li>Building output alongside computation
 * </ul>
 *
 * <h2>Creating WriterPath instances</h2>
 *
 * <pre>{@code
 * // Using List<String> for log output
 * Monoid<List<String>> logMonoid = Monoids.list();
 *
 * // Pure value with empty log
 * WriterPath<List<String>, Integer> pure = WriterPath.pure(42, logMonoid);
 *
 * // Tell (log only, return Unit)
 * WriterPath<List<String>, Unit> log = WriterPath.tell(List.of("Starting..."), logMonoid);
 *
 * // Writer with both value and log
 * WriterPath<List<String>, Integer> result = WriterPath.writer(42, List.of("Got 42"), logMonoid);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * Monoid<List<String>> logMonoid = Monoids.list();
 *
 * WriterPath<List<String>, Integer> computation =
 *     WriterPath.tell(List.of("Starting"), logMonoid)
 *         .then(() -> WriterPath.pure(42, logMonoid))
 *         .via(n -> WriterPath.tell(List.of("Got " + n), logMonoid)
 *             .map(_ -> n * 2));
 *
 * Writer<List<String>, Integer> result = computation.run();
 * // result.log() = ["Starting", "Got 42"]
 * // result.value() = 84
 * }</pre>
 *
 * @param <W> the output/log type (must have Monoid)
 * @param <A> the type of the computed value
 */
public final class WriterPath<W, A> implements Chainable<A> {

  private final Writer<W, A> writer;
  private final Monoid<W> monoid;

  /**
   * Creates a new WriterPath wrapping the given Writer.
   *
   * @param writer the Writer to wrap; must not be null
   * @param monoid the Monoid for combining logs; must not be null
   */
  WriterPath(Writer<W, A> writer, Monoid<W> monoid) {
    this.writer = Objects.requireNonNull(writer, "writer must not be null");
    this.monoid = Objects.requireNonNull(monoid, "monoid must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a WriterPath with a value and empty log.
   *
   * @param value the value to return
   * @param monoid the Monoid for combining logs; must not be null
   * @param <W> the log type
   * @param <A> the value type
   * @return a WriterPath with the given value and empty log
   * @throws NullPointerException if monoid is null
   */
  public static <W, A> WriterPath<W, A> pure(A value, Monoid<W> monoid) {
    Objects.requireNonNull(monoid, "monoid must not be null");
    return new WriterPath<>(Writer.value(monoid, value), monoid);
  }

  /**
   * Creates a WriterPath that only produces output/log, with {@link Unit} as its value.
   *
   * @param log the log to produce; must not be null
   * @param monoid the Monoid for combining logs; must not be null
   * @param <W> the log type
   * @return a WriterPath that produces the given log
   * @throws NullPointerException if log or monoid is null
   */
  public static <W> WriterPath<W, Unit> tell(W log, Monoid<W> monoid) {
    Objects.requireNonNull(log, "log must not be null");
    Objects.requireNonNull(monoid, "monoid must not be null");
    return new WriterPath<>(Writer.tell(log), monoid);
  }

  /**
   * Creates a WriterPath from a value and output.
   *
   * @param value the value to return
   * @param log the log to produce; must not be null
   * @param monoid the Monoid for combining logs; must not be null
   * @param <W> the log type
   * @param <A> the value type
   * @return a WriterPath with the given value and log
   * @throws NullPointerException if log or monoid is null
   */
  public static <W, A> WriterPath<W, A> writer(A value, W log, Monoid<W> monoid) {
    Objects.requireNonNull(log, "log must not be null");
    Objects.requireNonNull(monoid, "monoid must not be null");
    return new WriterPath<>(new Writer<>(log, value), monoid);
  }

  // ===== Terminal Operations =====

  /**
   * Returns the underlying Writer containing both value and log.
   *
   * @return the wrapped Writer
   */
  public Writer<W, A> run() {
    return writer;
  }

  /**
   * Returns only the computed value, discarding the log.
   *
   * @return the computed value
   */
  public A value() {
    return writer.value();
  }

  /**
   * Returns only the accumulated log, discarding the value.
   *
   * @return the accumulated log
   */
  public W written() {
    return writer.log();
  }

  /**
   * Returns the Monoid used for combining logs.
   *
   * @return the log Monoid
   */
  public Monoid<W> monoid() {
    return monoid;
  }

  // ===== Composable implementation =====

  @Override
  public <B> WriterPath<W, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new WriterPath<>(writer.map(mapper), monoid);
  }

  @Override
  public WriterPath<W, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    consumer.accept(writer.value());
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> WriterPath<W, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof WriterPath<?, ?> otherWriter)) {
      throw new IllegalArgumentException("Cannot zipWith non-WriterPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    WriterPath<W, B> typedOther = (WriterPath<W, B>) otherWriter;

    W combinedLog = monoid.combine(this.writer.log(), typedOther.writer.log());
    C combinedValue = combiner.apply(this.writer.value(), typedOther.writer.value());

    return new WriterPath<>(new Writer<>(combinedLog, combinedValue), monoid);
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result
   */
  public <B, C, D> WriterPath<W, D> zipWith3(
      WriterPath<W, B> second,
      WriterPath<W, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    W combinedLog =
        monoid.combine(monoid.combine(this.writer.log(), second.writer.log()), third.writer.log());
    D combinedValue =
        combiner.apply(this.writer.value(), second.writer.value(), third.writer.value());

    return new WriterPath<>(new Writer<>(combinedLog, combinedValue), monoid);
  }

  // ===== Chainable implementation =====

  @Override
  public <B> WriterPath<W, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    Chainable<B> result = mapper.apply(writer.value());
    Objects.requireNonNull(result, "mapper must not return null");

    if (!(result instanceof WriterPath<?, ?> writerPath)) {
      throw new IllegalArgumentException(
          "via mapper must return WriterPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    WriterPath<W, B> typedResult = (WriterPath<W, B>) writerPath;

    W combinedLog = monoid.combine(this.writer.log(), typedResult.writer.log());
    return new WriterPath<>(new Writer<>(combinedLog, typedResult.writer.value()), monoid);
  }

  @Override
  public <B> WriterPath<W, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    Chainable<B> result = supplier.get();
    Objects.requireNonNull(result, "supplier must not return null");

    if (!(result instanceof WriterPath<?, ?> writerPath)) {
      throw new IllegalArgumentException(
          "then supplier must return WriterPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    WriterPath<W, B> typedResult = (WriterPath<W, B>) writerPath;

    W combinedLog = monoid.combine(this.writer.log(), typedResult.writer.log());
    return new WriterPath<>(new Writer<>(combinedLog, typedResult.writer.value()), monoid);
  }

  // ===== Writer-Specific Operations =====

  /**
   * Transforms the log using the given function.
   *
   * <p>Note: The function must produce a value that is compatible with the existing Monoid. If you
   * need to change the Monoid, you should create a new WriterPath.
   *
   * @param f the function to transform the log; must not be null
   * @return a new WriterPath with the transformed log
   * @throws NullPointerException if f is null
   */
  public WriterPath<W, A> censor(Function<? super W, ? extends W> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new WriterPath<>(new Writer<>(f.apply(writer.log()), writer.value()), monoid);
  }

  /**
   * Adds additional output to the current log.
   *
   * @param additionalLog the additional log to append; must not be null
   * @return a new WriterPath with the combined log
   * @throws NullPointerException if additionalLog is null
   */
  public WriterPath<W, A> listen(W additionalLog) {
    Objects.requireNonNull(additionalLog, "additionalLog must not be null");
    W combinedLog = monoid.combine(writer.log(), additionalLog);
    return new WriterPath<>(new Writer<>(combinedLog, writer.value()), monoid);
  }

  // ===== Conversions =====

  /**
   * Converts to an IOPath, discarding the log.
   *
   * @return an IOPath that produces only the value
   */
  public IOPath<A> toIOPath() {
    return new IOPath<>(this::value);
  }

  /**
   * Converts to an IdPath, discarding the log.
   *
   * @return an IdPath containing only the value
   */
  public IdPath<A> toIdPath() {
    return new IdPath<>(Id.of(value()));
  }

  /**
   * Converts to a MaybePath, discarding the log.
   *
   * <p>If the value is null, returns an empty MaybePath.
   *
   * @return a MaybePath containing the value if non-null
   */
  public MaybePath<A> toMaybePath() {
    A val = value();
    return val != null ? new MaybePath<>(Maybe.just(val)) : new MaybePath<>(Maybe.nothing());
  }

  /**
   * Converts to an EitherPath, discarding the log.
   *
   * @param <E> the error type
   * @return an EitherPath containing the value as Right
   */
  public <E> EitherPath<E, A> toEitherPath() {
    return new EitherPath<>(Either.right(value()));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof WriterPath<?, ?> other)) return false;
    return writer.equals(other.writer) && monoid.equals(other.monoid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(writer, monoid);
  }

  @Override
  public String toString() {
    return "WriterPath(log=" + writer.log() + ", value=" + writer.value() + ")";
  }
}
