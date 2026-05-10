// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.writer.Writer;

/**
 * Custom AssertJ assertions for {@link Writer} instances.
 *
 * @param <W> The type of the log
 * @param <A> The type of the value
 */
public class WriterAssert<W, A> extends AbstractAssert<WriterAssert<W, A>, Writer<W, A>> {

  /** Entry point. */
  public static <W, A> WriterAssert<W, A> assertThatWriter(Writer<W, A> actual) {
    return new WriterAssert<>(actual);
  }

  protected WriterAssert(Writer<W, A> actual) {
    super(actual, WriterAssert.class);
  }

  /** Verifies that the Writer has the expected log. */
  public WriterAssert<W, A> hasLog(W expectedLog) {
    isNotNull();
    W actualLog = actual.log();
    Assertions.assertThat(actualLog)
        .withFailMessage(
            "Expected Writer to have log <%s> but had log <%s>", expectedLog, actualLog)
        .isEqualTo(expectedLog);
    return this;
  }

  /** Verifies that the Writer has the expected value. */
  public WriterAssert<W, A> hasValue(A expectedValue) {
    isNotNull();
    A actualValue = actual.value();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Writer to have value <%s> but had value <%s>", expectedValue, actualValue)
        .isEqualTo(expectedValue);
    return this;
  }

  /** Verifies that the Writer has a null value. */
  public WriterAssert<W, A> hasNullValue() {
    isNotNull();
    Assertions.assertThat(actual.value()).as("Writer value").isNull();
    return this;
  }

  /** Verifies that the Writer has a non-null value. */
  public WriterAssert<W, A> hasNonNullValue() {
    isNotNull();
    Assertions.assertThat(actual.value()).as("Writer value").isNotNull();
    return this;
  }

  /**
   * Verifies that the Writer has an empty log. Supports {@link String} and {@link Collection} log
   * types; for other log types, use {@link #hasLog(Object)} with an explicit empty value.
   */
  public WriterAssert<W, A> hasEmptyLog() {
    isNotNull();
    W actualLog = actual.log();
    boolean isEmpty;
    if (actualLog instanceof String s) {
      isEmpty = s.isEmpty();
    } else if (actualLog instanceof Collection<?> c) {
      isEmpty = c.isEmpty();
    } else {
      throw failure(
          "Cannot verify empty log for type <%s>. Use hasLog() with expected empty value instead.",
          actualLog.getClass().getSimpleName());
    }
    Assertions.assertThat(isEmpty)
        .withFailMessage("Expected Writer to have empty log but had log <%s>", actualLog)
        .isTrue();
    return this;
  }

  /** Verifies that the log satisfies the given requirements. */
  public WriterAssert<W, A> satisfiesLog(Consumer<? super W> requirements) {
    isNotNull();
    requirements.accept(actual.log());
    return this;
  }

  /** Verifies that the value satisfies the given requirements. */
  public WriterAssert<W, A> satisfiesValue(Consumer<? super A> requirements) {
    isNotNull();
    requirements.accept(actual.value());
    return this;
  }

  /** Verifies that the log matches the given predicate. */
  public WriterAssert<W, A> logMatches(Predicate<? super W> predicate) {
    isNotNull();
    W actualLog = actual.log();
    Assertions.assertThat(predicate.test(actualLog))
        .withFailMessage("Expected Writer log to match predicate but <%s> did not match", actualLog)
        .isTrue();
    return this;
  }

  /** Verifies that the log matches the given predicate, with a custom error message. */
  public WriterAssert<W, A> logMatches(Predicate<? super W> predicate, String description) {
    isNotNull();
    W actualLog = actual.log();
    Assertions.assertThat(predicate.test(actualLog))
        .withFailMessage("%s but <%s> did not match", description, actualLog)
        .isTrue();
    return this;
  }

  /** Verifies that the value matches the given predicate. */
  public WriterAssert<W, A> valueMatches(Predicate<? super A> predicate) {
    isNotNull();
    A actualValue = actual.value();
    Assertions.assertThat(predicate.test(actualValue))
        .withFailMessage(
            "Expected Writer value to match predicate but <%s> did not match", actualValue)
        .isTrue();
    return this;
  }

  /** Verifies that the value matches the given predicate, with a custom error message. */
  public WriterAssert<W, A> valueMatches(Predicate<? super A> predicate, String description) {
    isNotNull();
    A actualValue = actual.value();
    Assertions.assertThat(predicate.test(actualValue))
        .withFailMessage("%s but <%s> did not match", description, actualValue)
        .isTrue();
    return this;
  }

  /**
   * Verifies that the Writer produces the same result when accessed multiple times (referential
   * transparency check).
   */
  public WriterAssert<W, A> isPure() {
    isNotNull();
    W firstLog = actual.log();
    A firstValue = actual.value();
    W secondLog = actual.log();
    A secondValue = actual.value();
    Assertions.assertThat(secondLog)
        .withFailMessage(
            "Expected Writer to be pure (produce same log on multiple accesses) but got different"
                + " logs: <%s> and <%s>",
            firstLog, secondLog)
        .isEqualTo(firstLog);
    Assertions.assertThat(secondValue)
        .withFailMessage(
            "Expected Writer to be pure (produce same value on multiple accesses) but got different"
                + " values: <%s> and <%s>",
            firstValue, secondValue)
        .isEqualTo(firstValue);
    return this;
  }

  /** Verifies that two Writers have the same log and value. */
  public WriterAssert<W, A> isEqualTo(Writer<W, A> expected) {
    isNotNull();
    Assertions.assertThat(actual)
        .withFailMessage("Expected Writer to be equal to <%s> but was <%s>", expected, actual)
        .isEqualTo(expected);
    return this;
  }

  /** Returns the log for further assertions. */
  public W getLog() {
    isNotNull();
    return actual.log();
  }

  /** Returns the value for further assertions. */
  public A getValue() {
    isNotNull();
    return actual.value();
  }
}
