// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.io.IO;

/**
 * Custom AssertJ assertions for {@link IO} instances.
 *
 * @param <T> The type of the value produced by the IO computation
 */
public class IOAssert<T> extends AbstractAssert<IOAssert<T>, IO<T>> {

  /** Entry point. */
  public static <T> IOAssert<T> assertThatIO(IO<T> actual) {
    return new IOAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;

  protected IOAssert(IO<T> actual) {
    super(actual, IOAssert.class);
  }

  /** Executes the IO computation and prepares for assertions on the result. */
  public IOAssert<T> whenExecuted() {
    isNotNull();
    if (!hasBeenExecuted) {
      try {
        executedValue = actual.unsafeRunSync();
        executedException = null;
      } catch (Throwable t) {
        executedValue = null;
        executedException = t;
      }
      hasBeenExecuted = true;
    }
    return this;
  }

  /** Verifies that the IO produces the expected value. */
  public IOAssert<T> hasValue(T expected) {
    whenExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to produce value <%s> but it threw an exception: %s",
            expected, executedException)
        .isNull();
    Assertions.assertThat(executedValue)
        .withFailMessage(
            "Expected IO to produce value <%s> but produced <%s>", expected, executedValue)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the IO produces a value satisfying the given requirements. */
  public IOAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    whenExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to produce a value satisfying requirements but it threw an exception: %s",
            executedException)
        .isNull();
    requirements.accept(executedValue);
    return this;
  }

  /** Verifies that the IO produces a non-null value. */
  public IOAssert<T> hasValueNonNull() {
    whenExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to produce a non-null value but it threw an exception: %s",
            executedException)
        .isNull();
    Assertions.assertThat(executedValue).as("IO value").isNotNull();
    return this;
  }

  /** Verifies that the IO produces a null value. */
  public IOAssert<T> hasValueNull() {
    whenExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to produce null but it threw an exception: %s", executedException)
        .isNull();
    Assertions.assertThat(executedValue).as("IO value").isNull();
    return this;
  }

  /** Verifies that the IO throws an exception of the expected type. */
  public IOAssert<T> throwsException(Class<? extends Throwable> expectedType) {
    whenExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to throw exception of type <%s> but it completed with value <%s>",
            expectedType.getName(), executedValue)
        .isNotNull();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to throw exception of type <%s> but it threw <%s>",
            expectedType.getName(), executedException.getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /** Verifies that the thrown exception has the expected message. */
  public IOAssert<T> withMessage(String expectedMessage) {
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to have thrown an exception with message <%s> but no exception was thrown",
            expectedMessage)
        .isNotNull();
    Assertions.assertThat(executedException.getMessage())
        .withFailMessage(
            "Expected exception message to be <%s> but was <%s>",
            expectedMessage, executedException.getMessage())
        .isEqualTo(expectedMessage);
    return this;
  }

  /** Verifies that the thrown exception's message contains the expected substring. */
  public IOAssert<T> withMessageContaining(String substring) {
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to have thrown an exception with message containing <%s> but no exception"
                + " was thrown",
            substring)
        .isNotNull();
    Assertions.assertThat(executedException.getMessage())
        .withFailMessage(
            "Expected exception message to contain <%s> but was <%s>",
            substring, executedException.getMessage())
        .contains(substring);
    return this;
  }

  /** Verifies that the IO computation completes successfully without throwing an exception. */
  public IOAssert<T> completesSuccessfully() {
    whenExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected IO to complete successfully but it threw an exception: %s", executedException)
        .isNull();
    return this;
  }

  /** Verifies that the IO produces the same result when executed multiple times. */
  public IOAssert<T> isRepeatable() {
    whenExecuted();

    T secondValue = null;
    Throwable secondException = null;
    try {
      secondValue = actual.unsafeRunSync();
    } catch (Throwable t) {
      secondException = t;
    }

    // Compare results uniformly using two boolean checks against AssertJ.
    boolean exceptionsConsistent;
    if (executedException == null && secondException == null) {
      exceptionsConsistent = true;
    } else if (executedException != null && secondException != null) {
      exceptionsConsistent =
          executedException.getClass().equals(secondException.getClass())
              && Objects.equals(executedException.getMessage(), secondException.getMessage());
    } else {
      exceptionsConsistent = false;
    }
    final Throwable firstEx = executedException;
    final T firstVal = executedValue;
    final Throwable secondEx = secondException;
    final T secondVal = secondValue;
    Assertions.assertThat(exceptionsConsistent)
        .withFailMessage(
            () -> {
              String first = firstEx != null ? "threw " + firstEx : "returned " + firstVal;
              String second = secondEx != null ? "threw " + secondEx : "returned " + secondVal;
              return "Expected IO to be repeatable but first execution "
                  + first
                  + " and second execution "
                  + second;
            })
        .isTrue();
    if (executedException == null) {
      Assertions.assertThat(secondValue)
          .withFailMessage(
              "Expected IO to be repeatable but first execution returned <%s> and second execution"
                  + " returned <%s>",
              executedValue, secondValue)
          .isEqualTo(executedValue);
    }
    return this;
  }

  /** Verifies that the IO has not been executed yet by this assertion object. */
  public IOAssert<T> isNotExecutedYet() {
    isNotNull();
    Assertions.assertThat(hasBeenExecuted)
        .withFailMessage(
            "Expected IO to not have been executed yet, but it has been executed by this assertion"
                + " object")
        .isFalse();
    return this;
  }

  /** Returns the exception thrown during execution, if any. */
  public Throwable getException() {
    whenExecuted();
    return executedException;
  }

  /** Returns the value produced during execution, if any. */
  public T getValue() {
    whenExecuted();
    return executedValue;
  }
}
