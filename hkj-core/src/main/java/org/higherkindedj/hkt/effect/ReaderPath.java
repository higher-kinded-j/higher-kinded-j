// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.reader.Reader;

/**
 * A fluent path wrapper for {@link Reader} computations.
 *
 * <p>{@code ReaderPath} represents computations that depend on some environment {@code R} to
 * produce a value {@code A}. This is the functional programming approach to dependency injection,
 * allowing computations to "read" from a shared environment without explicitly passing it through
 * all layers of function calls.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Dependency injection without frameworks
 *   <li>Configuration access throughout computation
 *   <li>Database connection passing
 *   <li>Logger or context propagation
 * </ul>
 *
 * <h2>Creating ReaderPath instances</h2>
 *
 * <p>Use the {@link Path} factory class or static factory methods:
 *
 * <pre>{@code
 * // From a Reader
 * ReaderPath<AppConfig, String> path = Path.reader(Reader.ask().map(AppConfig::dbUrl));
 *
 * // Pure value (ignores environment)
 * ReaderPath<AppConfig, Integer> pure = ReaderPath.pure(42);
 *
 * // Ask for environment
 * ReaderPath<AppConfig, AppConfig> ask = ReaderPath.ask();
 *
 * // Extract from environment
 * ReaderPath<AppConfig, String> dbUrl = ReaderPath.asks(AppConfig::dbUrl);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * record AppConfig(String dbUrl, int timeout) {}
 *
 * ReaderPath<AppConfig, Connection> connect = ReaderPath.asks(AppConfig::dbUrl)
 *     .via(url -> ReaderPath.asks(cfg ->
 *         DriverManager.getConnection(url, cfg.timeout())));
 *
 * // Run with environment
 * Connection conn = connect.run(new AppConfig("jdbc:...", 30));
 * }</pre>
 *
 * @param <R> the environment type
 * @param <A> the type of the computed value
 */
public final class ReaderPath<R, A> implements Chainable<A> {

  private final Reader<R, A> reader;

  /**
   * Creates a new ReaderPath wrapping the given Reader.
   *
   * @param reader the Reader to wrap; must not be null
   */
  ReaderPath(Reader<R, A> reader) {
    this.reader = Objects.requireNonNull(reader, "reader must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a ReaderPath that ignores the environment and always returns the given value.
   *
   * @param value the value to return
   * @param <R> the environment type (ignored)
   * @param <A> the type of the value
   * @return a ReaderPath that always returns the given value
   */
  public static <R, A> ReaderPath<R, A> pure(A value) {
    return new ReaderPath<>(Reader.constant(value));
  }

  /**
   * Creates a ReaderPath that returns the entire environment as its value.
   *
   * @param <R> the environment type
   * @return a ReaderPath that returns the environment
   */
  public static <R> ReaderPath<R, R> ask() {
    return new ReaderPath<>(Reader.ask());
  }

  /**
   * Creates a ReaderPath that extracts a value from the environment using the given function.
   *
   * <p>This is a convenience method equivalent to {@code ask().map(f)}.
   *
   * @param f the function to apply to the environment; must not be null
   * @param <R> the environment type
   * @param <A> the type of the extracted value
   * @return a ReaderPath that extracts from the environment
   * @throws NullPointerException if f is null
   */
  public static <R, A> ReaderPath<R, A> asks(Function<? super R, ? extends A> f) {
    Objects.requireNonNull(f, "f must not be null");
    // Create Reader directly to avoid type inference issues with Reader.ask().map()
    return new ReaderPath<>(r -> f.apply(r));
  }

  // ===== Terminal Operations =====

  /**
   * Runs this computation with the given environment.
   *
   * @param environment the environment to provide; must not be null
   * @return the computed value
   * @throws NullPointerException if environment is null
   */
  public A run(R environment) {
    Objects.requireNonNull(environment, "environment must not be null");
    return reader.run(environment);
  }

  /**
   * Returns the underlying Reader.
   *
   * @return the wrapped Reader
   */
  public Reader<R, A> toReader() {
    return reader;
  }

  // ===== Composable implementation =====

  @Override
  public <B> ReaderPath<R, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new ReaderPath<>(reader.map(mapper));
  }

  @Override
  public ReaderPath<R, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new ReaderPath<>(
        Reader.of(
            env -> {
              A value = reader.run(env);
              consumer.accept(value);
              return value;
            }));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> ReaderPath<R, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof ReaderPath<?, ?> otherReader)) {
      throw new IllegalArgumentException("Cannot zipWith non-ReaderPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    ReaderPath<R, B> typedOther = (ReaderPath<R, B>) otherReader;

    return new ReaderPath<>(
        Reader.of(
            env -> {
              A a = this.reader.run(env);
              B b = typedOther.reader.run(env);
              return combiner.apply(a, b);
            }));
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
  public <B, C, D> ReaderPath<R, D> zipWith3(
      ReaderPath<R, B> second,
      ReaderPath<R, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new ReaderPath<>(
        Reader.of(
            env -> {
              A a = this.reader.run(env);
              B b = second.reader.run(env);
              C c = third.reader.run(env);
              return combiner.apply(a, b, c);
            }));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> ReaderPath<R, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    return new ReaderPath<>(
        reader.flatMap(
            a -> {
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof ReaderPath<?, ?> readerPath)) {
                throw new IllegalArgumentException(
                    "via mapper must return ReaderPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              ReaderPath<R, B> typedResult = (ReaderPath<R, B>) readerPath;
              return typedResult.reader;
            }));
  }

  @Override
  public <B> ReaderPath<R, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    return new ReaderPath<>(
        Reader.of(
            env -> {
              // Run this reader for its effect (though Reader has no side effects, this maintains
              // consistency)
              this.reader.run(env);

              Chainable<B> result = supplier.get();
              Objects.requireNonNull(result, "supplier must not return null");

              if (!(result instanceof ReaderPath<?, ?> readerPath)) {
                throw new IllegalArgumentException(
                    "then supplier must return ReaderPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              ReaderPath<R, B> typedResult = (ReaderPath<R, B>) readerPath;
              return typedResult.reader.run(env);
            }));
  }

  // ===== Reader-Specific Operations =====

  /**
   * Modifies the environment before running this computation.
   *
   * <p>This allows adapting a {@code ReaderPath<R, A>} to work with a different environment type
   * {@code R2} by providing a function that transforms {@code R2} into {@code R}.
   *
   * <pre>{@code
   * ReaderPath<DbConfig, User> loadUser = ...;
   *
   * // Adapt to work with a larger AppConfig that contains DbConfig
   * ReaderPath<AppConfig, User> adapted = loadUser.local(AppConfig::dbConfig);
   * }</pre>
   *
   * @param f the function to transform the new environment into the original environment; must not
   *     be null
   * @param <R2> the new environment type
   * @return a ReaderPath that works with the new environment type
   * @throws NullPointerException if f is null
   */
  public <R2> ReaderPath<R2, A> local(Function<? super R2, ? extends R> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new ReaderPath<>(Reader.of(r2 -> reader.run(f.apply(r2))));
  }

  // ===== Conversions =====

  /**
   * Converts to an IOPath by providing the environment.
   *
   * <p>The resulting IOPath, when run, will execute this Reader with the given environment.
   *
   * @param environment the environment to use; must not be null
   * @return an IOPath that produces the same result
   * @throws NullPointerException if environment is null
   */
  public IOPath<A> toIOPath(R environment) {
    Objects.requireNonNull(environment, "environment must not be null");
    return new IOPath<>(() -> run(environment));
  }

  /**
   * Converts to an IdPath by providing the environment.
   *
   * @param environment the environment to use; must not be null
   * @return an IdPath containing the result
   * @throws NullPointerException if environment is null
   */
  public IdPath<A> toIdPath(R environment) {
    Objects.requireNonNull(environment, "environment must not be null");
    return new IdPath<>(Id.of(run(environment)));
  }

  /**
   * Converts to a MaybePath by providing the environment.
   *
   * <p>If the result is null, returns an empty MaybePath.
   *
   * @param environment the environment to use; must not be null
   * @return a MaybePath containing the result if non-null
   * @throws NullPointerException if environment is null
   */
  public MaybePath<A> toMaybePath(R environment) {
    Objects.requireNonNull(environment, "environment must not be null");
    A result = run(environment);
    return result != null ? new MaybePath<>(Maybe.just(result)) : new MaybePath<>(Maybe.nothing());
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof ReaderPath<?, ?> other)) return false;
    return reader.equals(other.reader);
  }

  @Override
  public int hashCode() {
    return reader.hashCode();
  }

  @Override
  public String toString() {
    return "ReaderPath(" + reader + ")";
  }
}
