// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link Writer} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Writer} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.writer.WriterAssert.assertThatWriter;
 *
 * Monoid<String> stringMonoid = new StringMonoid();
 * Writer<String, Integer> writer = new Writer<>("Log;", 42);
 *
 * assertThatWriter(writer)
 *     .hasLog("Log;")
 *     .hasValue(42);
 *
 * Writer<String, String> mapped = writer.map(i -> "Value:" + i);
 * assertThatWriter(mapped)
 *     .hasLog("Log;")
 *     .hasValue("Value:42")
 *     .satisfiesValue(v -> assertThat(v).startsWith("Value:"));
 *
 * // Null-safe assertions
 * Writer<String, Integer> nullWriter = new Writer<>("NullLog;", null);
 * assertThatWriter(nullWriter)
 *     .hasLog("NullLog;")
 *     .hasNullValue();
 * }</pre>
 *
 * @param <W> The type of the log
 * @param <A> The type of the value
 */
public class WriterAssert<W, A> extends AbstractAssert<WriterAssert<W, A>, Writer<W, A>> {

  /**
   * Creates a new {@code WriterAssert} instance.
   *
   * <p>This is the entry point for all Writer assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.writer.WriterAssert.assertThatWriter;
   * }</pre>
   *
   * @param <W> The type of the log
   * @param <A> The type of the value
   * @param actual The Writer instance to make assertions on
   * @return A new WriterAssert instance
   */
  public static <W, A> WriterAssert<W, A> assertThatWriter(Writer<W, A> actual) {
    return new WriterAssert<>(actual);
  }

  protected WriterAssert(Writer<W, A> actual) {
    super(actual, WriterAssert.class);
  }

  /**
   * Verifies that the Writer has the expected log.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> writer = new Writer<>("TestLog;", 42);
   * assertThatWriter(writer)
   *     .hasLog("TestLog;");
   * }</pre>
   *
   * @param expectedLog The expected log value
   * @return This assertion object for method chaining
   * @throws AssertionError if the log doesn't match the expected value
   */
  public WriterAssert<W, A> hasLog(W expectedLog) {
    isNotNull();

    W actualLog = actual.log();
    if (!Objects.equals(actualLog, expectedLog)) {
      failWithMessage("Expected Writer to have log <%s> but had log <%s>", expectedLog, actualLog);
    }

    return this;
  }

  /**
   * Verifies that the Writer has the expected value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> writer = Writer.value(stringMonoid, 42);
   * assertThatWriter(writer)
   *     .hasValue(42);
   * }</pre>
   *
   * @param expectedValue The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the value doesn't match the expected value
   */
  public WriterAssert<W, A> hasValue(A expectedValue) {
    isNotNull();

    A actualValue = actual.value();
    if (!Objects.equals(actualValue, expectedValue)) {
      failWithMessage(
          "Expected Writer to have value <%s> but had value <%s>", expectedValue, actualValue);
    }

    return this;
  }

  /**
   * Verifies that the Writer has a null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> nullWriter = new Writer<>("Log;", null);
   * assertThatWriter(nullWriter)
   *     .hasNullValue();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is not null
   */
  public WriterAssert<W, A> hasNullValue() {
    isNotNull();

    A actualValue = actual.value();
    if (actualValue != null) {
      failWithMessage("Expected Writer to have null value but had value <%s>", actualValue);
    }

    return this;
  }

  /**
   * Verifies that the Writer has a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> writer = Writer.value(stringMonoid, 42);
   * assertThatWriter(writer)
   *     .hasNonNullValue();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is null
   */
  public WriterAssert<W, A> hasNonNullValue() {
    isNotNull();

    A actualValue = actual.value();
    if (actualValue == null) {
      failWithMessage("Expected Writer to have non-null value but value was null");
    }

    return this;
  }

  /**
   * Verifies that the Writer has an empty log.
   *
   * <p>Note: This method assumes the log type W has a meaningful isEmpty() method or is a String.
   * For custom log types, use {@link #hasLog(Object)} with the expected empty value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> writer = Writer.value(stringMonoid, 42);
   * assertThatWriter(writer)
   *     .hasEmptyLog();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the log is not empty
   */
  public WriterAssert<W, A> hasEmptyLog() {
    isNotNull();

    W actualLog = actual.log();
    boolean isEmpty;

    if (actualLog instanceof String) {
      isEmpty = ((String) actualLog).isEmpty();
    } else if (actualLog instanceof Collection) {
      isEmpty = ((Collection<?>) actualLog).isEmpty();
    } else {
      failWithMessage(
          "Cannot verify empty log for type <%s>. Use hasLog() with expected empty value instead.",
          actualLog.getClass().getSimpleName());
      return this;
    }

    if (!isEmpty) {
      failWithMessage("Expected Writer to have empty log but had log <%s>", actualLog);
    }

    return this;
  }

  /**
   * Verifies that the Writer's log satisfies the given requirements.
   *
   * <p>This is useful for complex assertions on the log without having to extract it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * assertThatWriter(writer)
   *     .satisfiesLog(log -> {
   *         assertThat(log).contains("Error");
   *         assertThat(log).startsWith("[");
   *     });
   * }</pre>
   *
   * @param requirements The requirements to verify on the log
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   */
  public WriterAssert<W, A> satisfiesLog(Consumer<? super W> requirements) {
    isNotNull();
    requirements.accept(actual.log());
    return this;
  }

  /**
   * Verifies that the Writer's value satisfies the given requirements.
   *
   * <p>This is useful for complex assertions on the value without having to extract it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * assertThatWriter(writer)
   *     .satisfiesValue(value -> {
   *         assertThat(value).isPositive();
   *         assertThat(value).isLessThan(100);
   *     });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   */
  public WriterAssert<W, A> satisfiesValue(Consumer<? super A> requirements) {
    isNotNull();
    requirements.accept(actual.value());
    return this;
  }

  /**
   * Verifies that the Writer's log matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * assertThatWriter(writer)
   *     .logMatches(log -> log.contains("Success"));
   * }</pre>
   *
   * @param predicate The predicate to match
   * @return This assertion object for method chaining
   * @throws AssertionError if the predicate doesn't match
   */
  public WriterAssert<W, A> logMatches(Predicate<? super W> predicate) {
    isNotNull();

    W actualLog = actual.log();
    if (!predicate.test(actualLog)) {
      failWithMessage("Expected Writer log to match predicate but <%s> did not match", actualLog);
    }

    return this;
  }

  /**
   * Verifies that the Writer's log matches the given predicate, with a custom error message.
   *
   * <p>Example:
   *
   * <pre>{@code
   * assertThatWriter(writer)
   *     .logMatches(log -> log.contains("Error"), "Log should contain error message");
   * }</pre>
   *
   * @param predicate The predicate to match
   * @param description Description of what the predicate tests
   * @return This assertion object for method chaining
   * @throws AssertionError if the predicate doesn't match
   */
  public WriterAssert<W, A> logMatches(Predicate<? super W> predicate, String description) {
    isNotNull();

    W actualLog = actual.log();
    if (!predicate.test(actualLog)) {
      failWithMessage("%s but <%s> did not match", description, actualLog);
    }

    return this;
  }

  /**
   * Verifies that the Writer's value matches the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * assertThatWriter(writer)
   *     .valueMatches(value -> value > 0);
   * }</pre>
   *
   * @param predicate The predicate to match
   * @return This assertion object for method chaining
   * @throws AssertionError if the predicate doesn't match
   */
  public WriterAssert<W, A> valueMatches(Predicate<? super A> predicate) {
    isNotNull();

    A actualValue = actual.value();
    if (!predicate.test(actualValue)) {
      failWithMessage(
          "Expected Writer value to match predicate but <%s> did not match", actualValue);
    }

    return this;
  }

  /**
   * Verifies that the Writer's value matches the given predicate, with a custom error message.
   *
   * <p>Example:
   *
   * <pre>{@code
   * assertThatWriter(writer)
   *     .valueMatches(value -> value > 0, "Value should be positive");
   * }</pre>
   *
   * @param predicate The predicate to match
   * @param description Description of what the predicate tests
   * @return This assertion object for method chaining
   * @throws AssertionError if the predicate doesn't match
   */
  public WriterAssert<W, A> valueMatches(Predicate<? super A> predicate, String description) {
    isNotNull();

    A actualValue = actual.value();
    if (!predicate.test(actualValue)) {
      failWithMessage("%s but <%s> did not match", description, actualValue);
    }

    return this;
  }

  /**
   * Verifies that the Writer produces the same result when run multiple times (referential
   * transparency / purity check).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> writer = Writer.value(stringMonoid, 42);
   * assertThatWriter(writer)
   *     .isPure();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Writer produces different results on multiple accesses
   */
  public WriterAssert<W, A> isPure() {
    isNotNull();

    W firstLog = actual.log();
    A firstValue = actual.value();
    W secondLog = actual.log();
    A secondValue = actual.value();

    if (!Objects.equals(firstLog, secondLog)) {
      failWithMessage(
          "Expected Writer to be pure (produce same log on multiple accesses) but got different"
              + " logs: <%s> and <%s>",
          firstLog, secondLog);
    }

    if (!Objects.equals(firstValue, secondValue)) {
      failWithMessage(
          "Expected Writer to be pure (produce same value on multiple accesses) but got different"
              + " values: <%s> and <%s>",
          firstValue, secondValue);
    }

    return this;
  }

  /**
   * Verifies that two Writers have the same log and value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Writer<String, Integer> w1 = new Writer<>("Log;", 42);
   * Writer<String, Integer> w2 = new Writer<>("Log;", 42);
   * assertThatWriter(w1)
   *     .isEqualTo(w2);
   * }</pre>
   *
   * @param expected The expected Writer
   * @return This assertion object for method chaining
   * @throws AssertionError if the Writers are not equal
   */
  public WriterAssert<W, A> isEqualTo(Writer<W, A> expected) {
    isNotNull();

    if (!Objects.equals(actual, expected)) {
      failWithMessage("Expected Writer to be equal to <%s> but was <%s>", expected, actual);
    }

    return this;
  }

  /**
   * Returns the actual log for further assertions using standard AssertJ assertions.
   *
   * <p>Example:
   *
   * <pre>{@code
   * String log = assertThatWriter(writer)
   *     .getLog();
   *
   * assertThat(log).contains("Error");
   * }</pre>
   *
   * @return The actual log from the Writer
   */
  public W getLog() {
    isNotNull();
    return actual.log();
  }

  /**
   * Returns the actual value for further assertions using standard AssertJ assertions.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Integer value = assertThatWriter(writer)
   *     .getValue();
   *
   * assertThat(value).isPositive();
   * }</pre>
   *
   * @return The actual value from the Writer
   */
  public A getValue() {
    isNotNull();
    return actual.value();
  }
}
