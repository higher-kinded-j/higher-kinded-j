// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link Reader} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Reader} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;
 *
 * record Config(String url, int timeout) {}
 * Config config = new Config("jdbc:test", 5000);
 *
 * Reader<Config, String> reader = Reader.of(Config::url);
 * assertThatReader(reader)
 *     .whenRunWith(config)
 *     .produces("jdbc:test");
 *
 * Reader<Config, Integer> timeoutReader = Reader.of(Config::timeout);
 * assertThatReader(timeoutReader)
 *     .whenRunWith(config)
 *     .produces(5000)
 *     .satisfies(timeout -> assertThat(timeout).isGreaterThan(0));
 *
 * // Null-safe assertions
 * Reader<Config, String> nullReader = Reader.of(c -> null);
 * assertThatReader(nullReader)
 *     .whenRunWith(config)
 *     .producesNull();
 * }</pre>
 *
 * @param <R> The type of the environment required by the Reader
 * @param <A> The type of the value produced by the Reader
 */
public class ReaderAssert<R, A> extends AbstractAssert<ReaderAssert<R, A>, Reader<R, A>> {

  /**
   * Creates a new {@code ReaderAssert} instance.
   *
   * <p>This is the entry point for all Reader assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.reader.ReaderAssert.assertThatReader;
   * }</pre>
   *
   * @param <R> The type of the environment required by the Reader
   * @param <A> The type of the value produced by the Reader
   * @param actual The Reader instance to make assertions on
   * @return A new ReaderAssert instance
   */
  public static <R, A> ReaderAssert<R, A> assertThatReader(Reader<R, A> actual) {
    return new ReaderAssert<>(actual);
  }

  protected ReaderAssert(Reader<R, A> actual) {
    super(actual, ReaderAssert.class);
  }

  /**
   * Begins a fluent assertion chain by specifying the environment to run the Reader with.
   *
   * <p>This method returns a {@link ReaderResultAssert} which provides assertions on the result of
   * running the Reader.
   *
   * <p>Example:
   *
   * <pre>{@code
   * record Config(String url) {}
   * Reader<Config, String> reader = Reader.of(Config::url);
   *
   * assertThatReader(reader)
   *     .whenRunWith(new Config("jdbc:test"))
   *     .produces("jdbc:test");
   * }</pre>
   *
   * @param environment The environment to run the Reader with
   * @return A ReaderResultAssert for making assertions on the result
   * @throws AssertionError if the Reader is null
   */
  public ReaderResultAssert<R, A> whenRunWith(R environment) {
    isNotNull();
    return new ReaderResultAssert<>(actual, environment);
  }

  /**
   * Verifies that the Reader produces the same result when run multiple times with the same
   * environment (referential transparency / purity check).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Reader<Config, String> reader = Reader.of(Config::url);
   * assertThatReader(reader)
   *     .isPureWhenRunWith(config);
   * }</pre>
   *
   * @param environment The environment to run the Reader with
   * @return This assertion object for method chaining
   * @throws AssertionError if the Reader produces different results on multiple runs
   */
  public ReaderAssert<R, A> isPureWhenRunWith(R environment) {
    isNotNull();

    A firstResult = actual.run(environment);
    A secondResult = actual.run(environment);

    if (!Objects.equals(firstResult, secondResult)) {
      failWithMessage(
          "Expected Reader to be pure (produce same result on multiple runs) but got different"
              + " results: <%s> and <%s>",
          firstResult, secondResult);
    }

    return this;
  }

  /**
   * Verifies that the Reader is a constant Reader (produces the same result regardless of the
   * environment).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Reader<Config, String> constant = Reader.constant("fixed");
   * assertThatReader(constant)
   *     .isConstantFor(config1, config2);
   * }</pre>
   *
   * @param env1 First environment to test with
   * @param env2 Second environment to test with
   * @return This assertion object for method chaining
   * @throws AssertionError if the Reader produces different results with different environments
   */
  public ReaderAssert<R, A> isConstantFor(R env1, R env2) {
    isNotNull();

    A result1 = actual.run(env1);
    A result2 = actual.run(env2);

    if (!Objects.equals(result1, result2)) {
      failWithMessage(
          "Expected Reader to be constant (produce same result for all environments) but got <%s>"
              + " for first environment and <%s> for second environment",
          result1, result2);
    }

    return this;
  }

  /**
   * Inner class providing assertions on the result of running a Reader with a specific environment.
   *
   * @param <R> The type of the environment
   * @param <A> The type of the result
   */
  public static class ReaderResultAssert<R, A> {
    private final Reader<R, A> reader;
    private final R environment;
    private final A result;

    ReaderResultAssert(Reader<R, A> reader, R environment) {
      this.reader = reader;
      this.environment = environment;
      this.result = reader.run(environment);
    }

    /**
     * Verifies that the Reader produces the expected value.
     *
     * <p>Example:
     *
     * <pre>{@code
     * assertThatReader(reader)
     *     .whenRunWith(config)
     *     .produces("expected value");
     * }</pre>
     *
     * @param expected The expected value
     * @return This assertion object for method chaining
     * @throws AssertionError if the actual value doesn't match the expected value
     */
    public ReaderResultAssert<R, A> produces(A expected) {
      if (!Objects.equals(result, expected)) {
        throw new AssertionError(
            String.format("Expected Reader to produce <%s> but produced <%s>", expected, result));
      }
      return this;
    }

    /**
     * Verifies that the Reader produces a null value.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Reader<Config, String> nullReader = Reader.of(c -> null);
     * assertThatReader(nullReader)
     *     .whenRunWith(config)
     *     .producesNull();
     * }</pre>
     *
     * @return This assertion object for method chaining
     * @throws AssertionError if the result is not null
     */
    public ReaderResultAssert<R, A> producesNull() {
      if (result != null) {
        throw new AssertionError(
            String.format("Expected Reader to produce null but produced <%s>", result));
      }
      return this;
    }

    /**
     * Verifies that the Reader produces a non-null value.
     *
     * <p>Example:
     *
     * <pre>{@code
     * assertThatReader(reader)
     *     .whenRunWith(config)
     *     .producesNonNull();
     * }</pre>
     *
     * @return This assertion object for method chaining
     * @throws AssertionError if the result is null
     */
    public ReaderResultAssert<R, A> producesNonNull() {
      if (result == null) {
        throw new AssertionError("Expected Reader to produce non-null value but produced null");
      }
      return this;
    }

    /**
     * Verifies that the Reader's result satisfies the given requirements.
     *
     * <p>This is useful for complex assertions on the result without having to extract it first.
     *
     * <p>Example:
     *
     * <pre>{@code
     * assertThatReader(reader)
     *     .whenRunWith(config)
     *     .satisfies(result -> {
     *         assertThat(result).isNotEmpty();
     *         assertThat(result).startsWith("jdbc:");
     *     });
     * }</pre>
     *
     * @param requirements The requirements to verify on the result
     * @return This assertion object for method chaining
     * @throws AssertionError if the requirements are not satisfied
     */
    public ReaderResultAssert<R, A> satisfies(Consumer<? super A> requirements) {
      requirements.accept(result);
      return this;
    }

    /**
     * Verifies that the Reader's result matches the given predicate.
     *
     * <p>Example:
     *
     * <pre>{@code
     * assertThatReader(reader)
     *     .whenRunWith(config)
     *     .matches(url -> url.startsWith("jdbc:"));
     * }</pre>
     *
     * @param predicate The predicate to match
     * @return This assertion object for method chaining
     * @throws AssertionError if the predicate doesn't match
     */
    public ReaderResultAssert<R, A> matches(Predicate<? super A> predicate) {
      if (!predicate.test(result)) {
        throw new AssertionError(
            String.format(
                "Expected Reader result to match predicate but <%s> did not match", result));
      }
      return this;
    }

    /**
     * Verifies that the Reader's result matches the given predicate, with a custom error message.
     *
     * <p>Example:
     *
     * <pre>{@code
     * assertThatReader(reader)
     *     .whenRunWith(config)
     *     .matches(url -> url.startsWith("jdbc:"), "URL should start with jdbc:");
     * }</pre>
     *
     * @param predicate The predicate to match
     * @param description Description of what the predicate tests
     * @return This assertion object for method chaining
     * @throws AssertionError if the predicate doesn't match
     */
    public ReaderResultAssert<R, A> matches(Predicate<? super A> predicate, String description) {
      if (!predicate.test(result)) {
        throw new AssertionError(String.format("%s but <%s> did not match", description, result));
      }
      return this;
    }

    /**
     * Returns the actual result for further assertions using standard AssertJ assertions.
     *
     * <p>Example:
     *
     * <pre>{@code
     * String url = assertThatReader(reader)
     *     .whenRunWith(config)
     *     .getResult();
     *
     * assertThat(url).startsWith("jdbc:");
     * }</pre>
     *
     * @return The actual result produced by the Reader
     */
    public A getResult() {
      return result;
    }
  }
}
