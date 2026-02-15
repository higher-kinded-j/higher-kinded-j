// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.test.assertions;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Custom AssertJ assertions for {@link VTaskPath} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code VTaskPath}
 * instances, making test code more readable and providing better error messages. Since VTaskPath
 * represents lazy computations that execute on virtual threads, these assertions properly handle
 * the execution semantics.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.test.assertions.VTaskPathAssert.assertThatVTaskPath;
 *
 * VTaskPath<Integer> path = Path.vtaskPure(42);
 * assertThatVTaskPath(path).succeeds().hasValue(42);
 *
 * VTaskPath<String> failing = Path.vtaskFail(new RuntimeException("Error"));
 * assertThatVTaskPath(failing).fails().withExceptionType(RuntimeException.class);
 *
 * VTaskPath<Integer> slow = Path.vtask(() -> { Thread.sleep(100); return 42; });
 * assertThatVTaskPath(slow).completesWithin(Duration.ofSeconds(1));
 * }</pre>
 *
 * @param <T> The type of the value produced by the VTaskPath computation
 */
public class VTaskPathAssert<T> extends AbstractAssert<VTaskPathAssert<T>, VTaskPath<T>> {

  /**
   * Creates a new {@code VTaskPathAssert} instance.
   *
   * <p>This is the entry point for all VTaskPath assertions. Import statically for best
   * readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.test.assertions.VTaskPathAssert.assertThatVTaskPath;
   * }</pre>
   *
   * @param <T> The type of the value produced by the VTaskPath computation
   * @param actual The VTaskPath instance to make assertions on
   * @return A new VTaskPathAssert instance
   */
  public static <T> VTaskPathAssert<T> assertThatVTaskPath(VTaskPath<T> actual) {
    return new VTaskPathAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;

  /**
   * Creates a new VTaskPathAssert.
   *
   * @param actual the VTaskPath to make assertions on
   */
  protected VTaskPathAssert(VTaskPath<T> actual) {
    super(actual, VTaskPathAssert.class);
  }

  /**
   * Executes the VTaskPath if not already executed and caches the result.
   *
   * @return this assertion for chaining
   */
  private VTaskPathAssert<T> ensureExecuted() {
    if (!hasBeenExecuted) {
      long startTime = System.currentTimeMillis();
      Try<T> result = actual.runSafe();
      executionTimeMillis = System.currentTimeMillis() - startTime;

      if (result.isSuccess()) {
        executedValue = result.fold(v -> v, e -> null);
      } else {
        executedException = result.fold(v -> null, e -> e);
      }
      hasBeenExecuted = true;
    }
    return this;
  }

  /**
   * Asserts that the VTaskPath computation succeeds.
   *
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> succeeds() {
    isNotNull();
    ensureExecuted();

    if (executedException != null) {
      failWithMessage(
          "Expected VTaskPath to succeed but it failed with: %s", executedException.getMessage());
    }
    return this;
  }

  /**
   * Asserts that the VTaskPath computation fails.
   *
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> fails() {
    isNotNull();
    ensureExecuted();

    if (executedException == null) {
      failWithMessage("Expected VTaskPath to fail but it succeeded with value: %s", executedValue);
    }
    return this;
  }

  /**
   * Asserts that the successful value equals the expected value.
   *
   * @param expected the expected value
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> hasValue(T expected) {
    succeeds();

    if (!Objects.equals(executedValue, expected)) {
      failWithMessage("Expected value <%s> but was <%s>", expected, executedValue);
    }
    return this;
  }

  /**
   * Asserts that the successful value satisfies the given requirements.
   *
   * @param requirements the requirements for the value
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> hasValueSatisfying(Consumer<T> requirements) {
    succeeds();
    requirements.accept(executedValue);
    return this;
  }

  /**
   * Asserts that the failure exception is of the expected type.
   *
   * @param expectedType the expected exception type
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> withExceptionType(Class<? extends Throwable> expectedType) {
    fails();

    if (!expectedType.isInstance(executedException)) {
      failWithMessage(
          "Expected exception of type <%s> but was <%s>",
          expectedType.getName(), executedException.getClass().getName());
    }
    return this;
  }

  /**
   * Asserts that the failure exception has the expected message.
   *
   * @param expectedMessage the expected message
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> withExceptionMessage(String expectedMessage) {
    fails();

    if (!Objects.equals(executedException.getMessage(), expectedMessage)) {
      failWithMessage(
          "Expected exception message <%s> but was <%s>",
          expectedMessage, executedException.getMessage());
    }
    return this;
  }

  /**
   * Asserts that the failure exception message contains the expected substring.
   *
   * @param expectedSubstring the expected substring
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> withExceptionMessageContaining(String expectedSubstring) {
    fails();

    String message = executedException.getMessage();
    if (message == null || !message.contains(expectedSubstring)) {
      failWithMessage(
          "Expected exception message to contain <%s> but was <%s>", expectedSubstring, message);
    }
    return this;
  }

  /**
   * Asserts that the VTaskPath completes within the specified duration.
   *
   * @param timeout the maximum duration
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> completesWithin(Duration timeout) {
    isNotNull();
    ensureExecuted();

    if (executionTimeMillis > timeout.toMillis()) {
      failWithMessage(
          "Expected completion within %dms but took %dms", timeout.toMillis(), executionTimeMillis);
    }
    return this;
  }

  /**
   * Asserts that the underlying VTask equals the expected VTask.
   *
   * @param expectedTask the expected VTask
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> hasUnderlyingTask(VTask<T> expectedTask) {
    isNotNull();

    VTask<T> actualTask = actual.run();
    if (!Objects.equals(actualTask, expectedTask)) {
      failWithMessage("Expected underlying VTask <%s> but was <%s>", expectedTask, actualTask);
    }
    return this;
  }

  /**
   * Asserts that this VTaskPath is semantically equivalent to another VTaskPath.
   *
   * <p>Two VTaskPaths are equivalent if they produce the same result when executed.
   *
   * @param other the other VTaskPath to compare with
   * @return this assertion for chaining
   */
  public VTaskPathAssert<T> isEquivalentTo(VTaskPath<T> other) {
    isNotNull();
    ensureExecuted();

    Objects.requireNonNull(other, "other VTaskPath must not be null");

    Try<T> otherResult = other.runSafe();

    if (executedException != null) {
      if (otherResult.isSuccess()) {
        @SuppressWarnings("unchecked")
        T otherValue = (T) otherResult.fold(v -> v, e -> null);
        failWithMessage(
            "Expected both VTaskPaths to fail, but other succeeded with: %s", otherValue);
      }
      // Both failed - consider equivalent (could compare exception types if needed)
    } else {
      if (otherResult.isFailure()) {
        failWithMessage(
            "Expected both VTaskPaths to succeed, but other failed with: %s",
            otherResult.fold(v -> null, Throwable::getMessage));
      }
      @SuppressWarnings("unchecked")
      T otherValue = (T) otherResult.fold(v -> v, e -> null);
      if (!Objects.equals(executedValue, otherValue)) {
        failWithMessage(
            "Expected equivalent values but got <%s> and <%s>", executedValue, otherValue);
      }
    }
    return this;
  }
}
