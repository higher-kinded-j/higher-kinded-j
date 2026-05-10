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
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Custom AssertJ assertions for {@link VTaskPath} instances.
 *
 * <p>Since VTaskPath represents a lazy computation that executes on virtual threads, these
 * assertions execute the path once on first use and cache the result for subsequent assertions.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.VTaskPathAssert.assertThatVTaskPath;
 *
 * assertThatVTaskPath(Path.vtaskPure(42)).succeeds().hasValue(42);
 * }</pre>
 *
 * @param <T> The type of the value produced by the VTaskPath computation
 */
public class VTaskPathAssert<T> extends AbstractAssert<VTaskPathAssert<T>, VTaskPath<T>> {

  /** Entry point. */
  public static <T> VTaskPathAssert<T> assertThatVTaskPath(VTaskPath<T> actual) {
    return new VTaskPathAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;

  protected VTaskPathAssert(VTaskPath<T> actual) {
    super(actual, VTaskPathAssert.class);
  }

  private VTaskPathAssert<T> ensureExecuted() {
    if (!hasBeenExecuted) {
      long startNanos = System.nanoTime();
      Try<T> result = actual.runSafe();
      executionTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      executedValue = result.fold(v -> v, e -> null);
      executedException = result.fold(v -> null, e -> e);
      hasBeenExecuted = true;
    }
    return this;
  }

  /** Asserts that the VTaskPath computation succeeds. */
  public VTaskPathAssert<T> succeeds() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTaskPath to succeed but it failed with: %s",
            executedException == null ? null : executedException.getMessage())
        .isNull();
    return this;
  }

  /** Asserts that the VTaskPath computation fails. */
  public VTaskPathAssert<T> fails() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTaskPath to fail but it succeeded with value: %s", executedValue)
        .isNotNull();
    return this;
  }

  /** Asserts that the successful value equals the expected value. */
  public VTaskPathAssert<T> hasValue(T expected) {
    succeeds();
    Assertions.assertThat(executedValue)
        .withFailMessage("Expected value <%s> but was <%s>", expected, executedValue)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the successful value satisfies the given requirements. */
  public VTaskPathAssert<T> hasValueSatisfying(Consumer<T> requirements) {
    succeeds();
    requirements.accept(executedValue);
    return this;
  }

  /** Asserts that the failure exception is of the expected type. */
  public VTaskPathAssert<T> withExceptionType(Class<? extends Throwable> expectedType) {
    fails();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected exception of type <%s> but was <%s>",
            expectedType.getName(), executedException.getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /** Asserts that the failure exception has the expected message. */
  public VTaskPathAssert<T> withExceptionMessage(String expectedMessage) {
    fails();
    Assertions.assertThat(executedException.getMessage())
        .withFailMessage(
            "Expected exception message <%s> but was <%s>",
            expectedMessage, executedException.getMessage())
        .isEqualTo(expectedMessage);
    return this;
  }

  /** Asserts that the failure exception message contains the expected substring. */
  public VTaskPathAssert<T> withExceptionMessageContaining(String expectedSubstring) {
    fails();
    String message = executedException.getMessage();
    Assertions.assertThat(message)
        .withFailMessage(
            "Expected exception message to contain <%s> but was <%s>", expectedSubstring, message)
        .contains(expectedSubstring);
    return this;
  }

  /** Asserts that the VTaskPath completes within the specified duration. */
  public VTaskPathAssert<T> completesWithin(Duration timeout) {
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
  public VTaskPathAssert<T> hasUnderlyingTask(VTask<T> expectedTask) {
    isNotNull();
    VTask<T> actualTask = actual.run();
    Assertions.assertThat(actualTask)
        .withFailMessage("Expected underlying VTask <%s> but was <%s>", expectedTask, actualTask)
        .isEqualTo(expectedTask);
    return this;
  }

  /** Asserts that this VTaskPath is semantically equivalent to another VTaskPath. */
  public VTaskPathAssert<T> isEquivalentTo(VTaskPath<T> other) {
    isNotNull();
    ensureExecuted();
    Objects.requireNonNull(other, "other VTaskPath must not be null");

    Try<T> otherResult = other.runSafe();
    boolean otherSucceeded = otherResult.isSuccess();

    if (executedException != null) {
      T otherValue = otherResult.fold(v -> v, e -> null);
      Assertions.assertThat(otherSucceeded)
          .withFailMessage(
              "Expected both VTaskPaths to fail, but other succeeded with: %s", otherValue)
          .isFalse();
    } else {
      String otherFailureMessage = otherResult.fold(v -> null, Throwable::getMessage);
      Assertions.assertThat(otherSucceeded)
          .withFailMessage(
              "Expected both VTaskPaths to succeed, but other failed with: %s", otherFailureMessage)
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
