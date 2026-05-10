// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Duration;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;

/**
 * Custom AssertJ assertions for {@link VTask} instances.
 *
 * @param <T> The type of the value produced by the VTask computation
 */
public class VTaskAssert<T> extends AbstractAssert<VTaskAssert<T>, VTask<T>> {

  /** Entry point. */
  public static <T> VTaskAssert<T> assertThatVTask(VTask<T> actual) {
    return new VTaskAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;
  private Try<T> runSafeResult;
  private boolean hasRunSafe = false;
  private boolean wasExecutedOnVirtualThread = false;

  protected VTaskAssert(VTask<T> actual) {
    super(actual, VTaskAssert.class);
  }

  /** Executes the VTask and prepares for assertions on the result. */
  public VTaskAssert<T> whenRun() {
    isNotNull();
    if (!hasBeenExecuted) {
      long startTime = System.currentTimeMillis();
      VTask<T> wrappedTask =
          actual.peek(ignored -> wasExecutedOnVirtualThread = Thread.currentThread().isVirtual());
      try {
        executedValue = wrappedTask.run();
        executedException = null;
      } catch (VTaskExecutionException e) {
        executedValue = null;
        executedException = e.getCause();
      } catch (Throwable t) {
        executedValue = null;
        executedException = t;
      }
      executionTimeMillis = System.currentTimeMillis() - startTime;
      hasBeenExecuted = true;
    }
    return this;
  }

  /** Verifies that the VTask completes successfully. */
  public VTaskAssert<T> succeeds() {
    whenRun();
    Assertions.assertThat(executedException)
        .withFailMessage(
            () ->
                "Expected VTask to succeed but it threw: "
                    + executedException.getClass().getName()
                    + ": "
                    + executedException.getMessage())
        .isNull();
    return this;
  }

  /** Verifies that the VTask fails (throws an exception). */
  public VTaskAssert<T> fails() {
    whenRun();
    Assertions.assertThat(executedException)
        .withFailMessage(
            () -> "Expected VTask to fail but it succeeded with value: <" + executedValue + ">")
        .isNotNull();
    return this;
  }

  /** Verifies that the VTask, when run, produces the expected value. */
  public VTaskAssert<T> hasValue(T expected) {
    succeeds();
    Assertions.assertThat(executedValue)
        .withFailMessage(
            "Expected VTask to produce value <%s> but produced <%s>", expected, executedValue)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the VTask, when run, produces a null value. */
  public VTaskAssert<T> hasNullValue() {
    succeeds();
    Assertions.assertThat(executedValue).as("VTask value").isNull();
    return this;
  }

  /** Verifies that the VTask, when run, produces a value satisfying the given requirements. */
  public VTaskAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    succeeds();
    requirements.accept(executedValue);
    return this;
  }

  /** Verifies that the VTask throws an exception of the expected type. */
  public VTaskAssert<T> withExceptionType(Class<? extends Throwable> expectedType) {
    fails();
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTask to throw <%s> but threw <%s>",
            expectedType.getName(), executedException.getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /** Verifies that the thrown exception has the expected message. */
  public VTaskAssert<T> withMessage(String expectedMessage) {
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTask to have thrown an exception with message <%s> but no exception was"
                + " thrown",
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
  public VTaskAssert<T> withMessageContaining(String substring) {
    Assertions.assertThat(executedException)
        .withFailMessage(
            "Expected VTask to have thrown an exception with message containing <%s> but no"
                + " exception was thrown",
            substring)
        .isNotNull();
    Assertions.assertThat(executedException.getMessage())
        .withFailMessage(
            "Expected exception message to contain <%s> but was <%s>",
            substring, executedException.getMessage())
        .contains(substring);
    return this;
  }

  /** Verifies that the VTask completes within the specified duration. */
  public VTaskAssert<T> completesWithin(Duration maxDuration) {
    whenRun();
    Assertions.assertThat(executionTimeMillis)
        .withFailMessage(
            "Expected VTask to complete within %dms but took %dms",
            maxDuration.toMillis(), executionTimeMillis)
        .isLessThanOrEqualTo(maxDuration.toMillis());
    return this;
  }

  /** Verifies that the VTask was executed on a virtual thread. */
  public VTaskAssert<T> runsOnVirtualThread() {
    whenRun();
    Assertions.assertThat(wasExecutedOnVirtualThread)
        .withFailMessage(
            "Expected VTask to run on a virtual thread but it ran on a platform thread")
        .isTrue();
    return this;
  }

  /** Verifies that calling {@code runSafe()} returns a {@link Try.Success}. */
  public VTaskAssert<T> runSafeSucceeds() {
    runSafeOnce();
    TryAssert.assertThatTry(runSafeResult).isSuccess();
    return this;
  }

  /** Verifies that calling {@code runSafe()} returns a {@link Try.Failure}. */
  public VTaskAssert<T> runSafeFails() {
    runSafeOnce();
    TryAssert.assertThatTry(runSafeResult).isFailure();
    return this;
  }

  /**
   * Invokes {@code actual.runSafe()} at most once across the lifetime of this assert. Subsequent
   * calls reuse the cached result so chaining {@code runSafeSucceeds().runSafeFails()...} does not
   * re-execute the underlying VTask.
   */
  private void runSafeOnce() {
    isNotNull();
    if (!hasRunSafe) {
      runSafeResult = actual.runSafe();
      hasRunSafe = true;
    }
  }

  /** Returns the exception thrown during execution, if any. */
  public Throwable getException() {
    whenRun();
    return executedException;
  }

  /** Returns the value produced during execution, if any. */
  public T getValue() {
    whenRun();
    return executedValue;
  }

  /** Returns the execution time in milliseconds, or -1 if not yet executed. */
  public long getExecutionTimeMillis() {
    return executionTimeMillis;
  }
}
