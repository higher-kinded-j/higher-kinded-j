// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Custom AssertJ assertions for {@link VTaskContext} instances.
 *
 * <p>Executes the underlying computation once on first use and caches the result for subsequent
 * assertions.
 *
 * @param <T> The type of the value produced by the VTaskContext computation
 */
public class VTaskContextAssert<T> extends AbstractAssert<VTaskContextAssert<T>, VTaskContext<T>> {

  /** Entry point. */
  public static <T> VTaskContextAssert<T> assertThatVTaskContext(VTaskContext<T> actual) {
    return new VTaskContextAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;

  protected VTaskContextAssert(VTaskContext<T> actual) {
    super(actual, VTaskContextAssert.class);
  }

  private VTaskContextAssert<T> ensureExecuted() {
    if (!hasBeenExecuted) {
      long startNanos = System.nanoTime();
      Try<T> result = actual.run();
      executionTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      executedValue = result.foldFailureFirst(e -> null, v -> v);
      executedException = result.foldFailureFirst(e -> e, v -> null);
      hasBeenExecuted = true;
    }
    return this;
  }

  /** Asserts that the VTaskContext computation succeeds. */
  public VTaskContextAssert<T> succeeds() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTaskContext to succeed but it failed with: %s",
            executedException == null ? null : executedException.getMessage())
        .isNull();
    return this;
  }

  /** Asserts that the VTaskContext computation fails. */
  public VTaskContextAssert<T> fails() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTaskContext to fail but it succeeded with value: %s", executedValue)
        .isNotNull();
    return this;
  }

  /** Asserts that the successful value equals the expected value. */
  public VTaskContextAssert<T> hasValue(T expected) {
    succeeds();
    Assertions.assertThat(executedValue)
        .withFailMessage("Expected value <%s> but was <%s>", expected, executedValue)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the successful value satisfies the given requirements. */
  public VTaskContextAssert<T> hasValueSatisfying(Consumer<T> requirements) {
    succeeds();
    requirements.accept(executedValue);
    return this;
  }

  /** Asserts that the failure exception is of the expected type. */
  public VTaskContextAssert<T> withExceptionType(Class<? extends Throwable> expectedType) {
    fails();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected exception of type <%s> but was <%s>",
            expectedType.getName(), executedException.getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /** Asserts that the failure exception has the expected message. */
  public VTaskContextAssert<T> withExceptionMessage(String expectedMessage) {
    fails();
    Assertions.assertThat(executedException.getMessage())
        .withFailMessage(
            "Expected exception message <%s> but was <%s>",
            expectedMessage, executedException.getMessage())
        .isEqualTo(expectedMessage);
    return this;
  }

  /** Asserts that the failure exception message contains the expected substring. */
  public VTaskContextAssert<T> withExceptionMessageContaining(String expectedSubstring) {
    fails();
    String message = executedException.getMessage();
    Assertions.assertThat(message)
        .withFailMessage(
            "Expected exception message to contain <%s> but was <%s>", expectedSubstring, message)
        .contains(expectedSubstring);
    return this;
  }

  /** Asserts that the VTaskContext completes within the specified duration. */
  public VTaskContextAssert<T> completesWithin(Duration timeout) {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executionTimeMillis)
        .withFailMessage(
            "Expected completion within %dms but took %dms",
            timeout.toMillis(), executionTimeMillis)
        .isLessThanOrEqualTo(timeout.toMillis());
    return this;
  }

  /** Asserts that the underlying VTask equals the expected VTask. */
  public VTaskContextAssert<T> hasUnderlying(VTask<T> expectedTask) {
    isNotNull();
    VTask<T> actualTask = actual.toVTask();
    Assertions.assertThat(actualTask)
        .withFailMessage("Expected underlying VTask <%s> but was <%s>", expectedTask, actualTask)
        .isEqualTo(expectedTask);
    return this;
  }

  /** Asserts that the underlying VTaskPath equals the expected VTaskPath. */
  public VTaskContextAssert<T> hasUnderlyingPath(VTaskPath<T> expectedPath) {
    isNotNull();
    VTaskPath<T> actualPath = actual.toPath();
    Assertions.assertThat(actualPath)
        .withFailMessage(
            "Expected underlying VTaskPath <%s> but was <%s>", expectedPath, actualPath)
        .isEqualTo(expectedPath);
    return this;
  }

  /** Asserts that this VTaskContext is semantically equivalent to another VTaskContext. */
  public VTaskContextAssert<T> isEquivalentTo(VTaskContext<T> other) {
    isNotNull();
    ensureExecuted();
    Objects.requireNonNull(other, "other VTaskContext must not be null");

    Try<T> otherResult = other.run();
    boolean otherSucceeded = otherResult.isSuccess();

    if (executedException != null) {
      T otherValue = otherResult.foldFailureFirst(e -> null, v -> v);
      Assertions.assertThat(otherSucceeded)
          .withFailMessage(
              "Expected both VTaskContexts to fail, but other succeeded with: %s", otherValue)
          .isFalse();
    } else {
      String otherFailureMessage = otherResult.foldFailureFirst(Throwable::getMessage, v -> null);
      Assertions.assertThat(otherSucceeded)
          .withFailMessage(
              "Expected both VTaskContexts to succeed, but other failed with: %s",
              otherFailureMessage)
          .isTrue();
      T otherSuccessValue = otherResult.orElse(null);
      Assertions.assertThat(otherSuccessValue)
          .withFailMessage(
              "Expected equivalent values but got <%s> and <%s>", executedValue, otherSuccessValue)
          .isEqualTo(executedValue);
    }
    return this;
  }
}
