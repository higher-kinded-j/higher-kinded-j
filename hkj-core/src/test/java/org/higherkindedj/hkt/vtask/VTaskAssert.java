// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Custom AssertJ assertions for {@link VTask} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code VTask} instances,
 * making test code more readable and providing better error messages. Since VTask represents lazy
 * computations that execute on virtual threads, these assertions properly handle the execution
 * semantics.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;
 *
 * VTask<Integer> task = VTask.succeed(42);
 * assertThatVTask(task).succeeds().hasValue(42);
 *
 * VTask<String> failing = VTask.fail(new RuntimeException("Error"));
 * assertThatVTask(failing).fails().withExceptionType(RuntimeException.class);
 *
 * VTask<Integer> slow = VTask.of(() -> { Thread.sleep(100); return 42; });
 * assertThatVTask(slow).completesWithin(Duration.ofSeconds(1));
 * }</pre>
 *
 * @param <T> The type of the value produced by the VTask computation
 */
public class VTaskAssert<T> extends AbstractAssert<VTaskAssert<T>, VTask<T>> {

  /**
   * Creates a new {@code VTaskAssert} instance.
   *
   * <p>This is the entry point for all VTask assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;
   * }</pre>
   *
   * @param <T> The type of the value produced by the VTask computation
   * @param actual The VTask instance to make assertions on
   * @return A new VTaskAssert instance
   */
  public static <T> VTaskAssert<T> assertThatVTask(VTask<T> actual) {
    return new VTaskAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;
  private boolean wasExecutedOnVirtualThread = false;

  protected VTaskAssert(VTask<T> actual) {
    super(actual, VTaskAssert.class);
  }

  /**
   * Executes the VTask and prepares for assertions on the result.
   *
   * <p>This method triggers the {@code run()} call and captures either the resulting value or any
   * exception thrown during execution. Subsequent assertions will operate on the captured result.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual VTask is null
   */
  public VTaskAssert<T> whenRun() {
    isNotNull();
    if (!hasBeenExecuted) {
      long startTime = System.currentTimeMillis();
      // Wrap to capture virtual thread status
      VTask<T> wrappedTask =
          actual.peek(ignored -> wasExecutedOnVirtualThread = Thread.currentThread().isVirtual());
      try {
        executedValue = wrappedTask.run();
        executedException = null;
      } catch (Throwable t) {
        executedValue = null;
        executedException = t;
      }
      executionTimeMillis = System.currentTimeMillis() - startTime;
      hasBeenExecuted = true;
    }
    return this;
  }

  /**
   * Verifies that the VTask completes successfully (does not throw).
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask throws an exception
   */
  public VTaskAssert<T> succeeds() {
    whenRun();
    if (executedException != null) {
      failWithMessage(
          "Expected VTask to succeed but it threw: %s",
          executedException.getClass().getName() + ": " + executedException.getMessage());
    }
    return this;
  }

  /**
   * Verifies that the VTask fails (throws an exception).
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask completes successfully
   */
  public VTaskAssert<T> fails() {
    whenRun();
    if (executedException == null) {
      failWithMessage("Expected VTask to fail but it succeeded with value: <%s>", executedValue);
    }
    return this;
  }

  /**
   * Verifies that the VTask, when run, produces the expected value.
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask throws an exception or the value doesn't match
   */
  public VTaskAssert<T> hasValue(T expected) {
    succeeds();
    if (!Objects.equals(executedValue, expected)) {
      failWithMessage(
          "Expected VTask to produce value <%s> but produced <%s>", expected, executedValue);
    }
    return this;
  }

  /**
   * Verifies that the VTask, when run, produces a null value.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask throws an exception or the value is not null
   */
  public VTaskAssert<T> hasNullValue() {
    succeeds();
    if (executedValue != null) {
      failWithMessage("Expected VTask to produce null but produced <%s>", executedValue);
    }
    return this;
  }

  /**
   * Verifies that the VTask, when run, produces a value satisfying the given requirements.
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask throws an exception or the requirements are not satisfied
   */
  public VTaskAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    succeeds();
    requirements.accept(executedValue);
    return this;
  }

  /**
   * Verifies that the VTask throws an exception of the expected type.
   *
   * @param expectedType The expected exception type
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask succeeds or throws a different exception type
   */
  public VTaskAssert<T> withExceptionType(Class<? extends Throwable> expectedType) {
    fails();
    if (!expectedType.isInstance(executedException)) {
      failWithMessage(
          "Expected VTask to throw <%s> but threw <%s>",
          expectedType.getName(), executedException.getClass().getName());
    }
    return this;
  }

  /**
   * Verifies that the thrown exception has the expected message.
   *
   * @param expectedMessage The expected exception message
   * @return This assertion object for method chaining
   * @throws AssertionError if no exception was thrown or the message doesn't match
   */
  public VTaskAssert<T> withMessage(String expectedMessage) {
    if (executedException == null) {
      failWithMessage(
          "Expected VTask to have thrown an exception with message <%s> but no exception was thrown",
          expectedMessage);
    }
    if (!Objects.equals(executedException.getMessage(), expectedMessage)) {
      failWithMessage(
          "Expected exception message to be <%s> but was <%s>",
          expectedMessage, executedException.getMessage());
    }
    return this;
  }

  /**
   * Verifies that the thrown exception's message contains the expected substring.
   *
   * @param substring The expected substring in the exception message
   * @return This assertion object for method chaining
   * @throws AssertionError if no exception was thrown or the message doesn't contain the substring
   */
  public VTaskAssert<T> withMessageContaining(String substring) {
    if (executedException == null) {
      failWithMessage(
          "Expected VTask to have thrown an exception with message containing <%s> but no exception"
              + " was thrown",
          substring);
    }
    String message = executedException.getMessage();
    if (message == null || !message.contains(substring)) {
      failWithMessage(
          "Expected exception message to contain <%s> but was <%s>", substring, message);
    }
    return this;
  }

  /**
   * Verifies that the VTask completes within the specified duration.
   *
   * @param maxDuration The maximum duration allowed
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask takes longer than the specified duration
   */
  public VTaskAssert<T> completesWithin(Duration maxDuration) {
    whenRun();
    if (executionTimeMillis > maxDuration.toMillis()) {
      failWithMessage(
          "Expected VTask to complete within %dms but took %dms",
          maxDuration.toMillis(), executionTimeMillis);
    }
    return this;
  }

  /**
   * Verifies that the VTask was executed on a virtual thread.
   *
   * <p>This assertion is useful for confirming that the VTask execution model properly leverages
   * Java's virtual threads. Note that this checks if the task's peek observer ran on a virtual
   * thread during execution.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the VTask was not executed on a virtual thread
   */
  public VTaskAssert<T> runsOnVirtualThread() {
    whenRun();
    if (!wasExecutedOnVirtualThread) {
      failWithMessage("Expected VTask to run on a virtual thread but it ran on a platform thread");
    }
    return this;
  }

  /**
   * Verifies that calling runSafe() returns a Success.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if runSafe returns a Failure
   */
  public VTaskAssert<T> runSafeSucceeds() {
    isNotNull();
    Try<T> result = actual.runSafe();
    if (result.isFailure()) {
      try {
        result.get();
      } catch (Throwable t) {
        failWithMessage("Expected runSafe() to succeed but it failed with: %s", t.getMessage());
      }
    }
    return this;
  }

  /**
   * Verifies that calling runSafe() returns a Failure.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if runSafe returns a Success
   */
  public VTaskAssert<T> runSafeFails() {
    isNotNull();
    Try<T> result = actual.runSafe();
    if (result.isSuccess()) {
      try {
        failWithMessage("Expected runSafe() to fail but it succeeded with: %s", result.get());
      } catch (Throwable t) {
        // Should not happen for Success
      }
    }
    return this;
  }

  /**
   * Returns the exception that was thrown during execution, if any.
   *
   * @return The exception that was thrown, or null if no exception was thrown
   */
  public Throwable getException() {
    if (!hasBeenExecuted) {
      whenRun();
    }
    return executedException;
  }

  /**
   * Returns the value that was produced during execution, if any.
   *
   * @return The value that was produced, or null if an exception was thrown or the value is null
   */
  public T getValue() {
    if (!hasBeenExecuted) {
      whenRun();
    }
    return executedValue;
  }

  /**
   * Returns the execution time in milliseconds.
   *
   * @return The execution time, or -1 if not yet executed
   */
  public long getExecutionTimeMillis() {
    return executionTimeMillis;
  }
}
