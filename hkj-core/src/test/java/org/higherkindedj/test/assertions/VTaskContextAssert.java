// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.test.assertions;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Custom AssertJ assertions for {@link VTaskContext} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code VTaskContext}
 * instances, making test code more readable and providing better error messages. Since VTaskContext
 * represents lazy computations that execute on virtual threads, these assertions properly handle
 * the execution semantics.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.test.assertions.VTaskContextAssert.assertThatVTaskContext;
 *
 * VTaskContext<Integer> context = VTaskContext.pure(42);
 * assertThatVTaskContext(context).succeeds().hasValue(42);
 *
 * VTaskContext<String> failing = VTaskContext.fail(new RuntimeException("Error"));
 * assertThatVTaskContext(failing).fails().withExceptionType(RuntimeException.class);
 *
 * VTaskContext<Integer> slow = VTaskContext.of(() -> { Thread.sleep(100); return 42; });
 * assertThatVTaskContext(slow).completesWithin(Duration.ofSeconds(1));
 * }</pre>
 *
 * @param <T> The type of the value produced by the VTaskContext computation
 */
public class VTaskContextAssert<T> extends AbstractAssert<VTaskContextAssert<T>, VTaskContext<T>> {

  /**
   * Creates a new {@code VTaskContextAssert} instance.
   *
   * <p>This is the entry point for all VTaskContext assertions. Import statically for best
   * readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.test.assertions.VTaskContextAssert.assertThatVTaskContext;
   * }</pre>
   *
   * @param <T> The type of the value produced by the VTaskContext computation
   * @param actual The VTaskContext instance to make assertions on
   * @return A new VTaskContextAssert instance
   */
  public static <T> VTaskContextAssert<T> assertThatVTaskContext(VTaskContext<T> actual) {
    return new VTaskContextAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;

  /**
   * Creates a new VTaskContextAssert.
   *
   * @param actual the VTaskContext to make assertions on
   */
  protected VTaskContextAssert(VTaskContext<T> actual) {
    super(actual, VTaskContextAssert.class);
  }

  /**
   * Executes the VTaskContext if not already executed and caches the result.
   *
   * @return this assertion for chaining
   */
  private VTaskContextAssert<T> ensureExecuted() {
    if (!hasBeenExecuted) {
      long startTime = System.currentTimeMillis();
      Try<T> result = actual.run();
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
   * Asserts that the VTaskContext computation succeeds.
   *
   * @return this assertion for chaining
   */
  public VTaskContextAssert<T> succeeds() {
    isNotNull();
    ensureExecuted();

    if (executedException != null) {
      failWithMessage(
          "Expected VTaskContext to succeed but it failed with: %s",
          executedException.getMessage());
    }
    return this;
  }

  /**
   * Asserts that the VTaskContext computation fails.
   *
   * @return this assertion for chaining
   */
  public VTaskContextAssert<T> fails() {
    isNotNull();
    ensureExecuted();

    if (executedException == null) {
      failWithMessage(
          "Expected VTaskContext to fail but it succeeded with value: %s", executedValue);
    }
    return this;
  }

  /**
   * Asserts that the successful value equals the expected value.
   *
   * @param expected the expected value
   * @return this assertion for chaining
   */
  public VTaskContextAssert<T> hasValue(T expected) {
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
  public VTaskContextAssert<T> hasValueSatisfying(Consumer<T> requirements) {
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
  public VTaskContextAssert<T> withExceptionType(Class<? extends Throwable> expectedType) {
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
  public VTaskContextAssert<T> withExceptionMessage(String expectedMessage) {
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
  public VTaskContextAssert<T> withExceptionMessageContaining(String expectedSubstring) {
    fails();

    String message = executedException.getMessage();
    if (message == null || !message.contains(expectedSubstring)) {
      failWithMessage(
          "Expected exception message to contain <%s> but was <%s>", expectedSubstring, message);
    }
    return this;
  }

  /**
   * Asserts that the VTaskContext completes within the specified duration.
   *
   * @param timeout the maximum duration
   * @return this assertion for chaining
   */
  public VTaskContextAssert<T> completesWithin(Duration timeout) {
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
  public VTaskContextAssert<T> hasUnderlying(VTask<T> expectedTask) {
    isNotNull();

    VTask<T> actualTask = actual.toVTask();
    if (!Objects.equals(actualTask, expectedTask)) {
      failWithMessage("Expected underlying VTask <%s> but was <%s>", expectedTask, actualTask);
    }
    return this;
  }

  /**
   * Asserts that the underlying VTaskPath equals the expected VTaskPath.
   *
   * @param expectedPath the expected VTaskPath
   * @return this assertion for chaining
   */
  public VTaskContextAssert<T> hasUnderlyingPath(VTaskPath<T> expectedPath) {
    isNotNull();

    VTaskPath<T> actualPath = actual.toPath();
    if (!Objects.equals(actualPath, expectedPath)) {
      failWithMessage("Expected underlying VTaskPath <%s> but was <%s>", expectedPath, actualPath);
    }
    return this;
  }

  /**
   * Asserts that this VTaskContext is semantically equivalent to another VTaskContext.
   *
   * <p>Two VTaskContexts are equivalent if they produce the same result when executed.
   *
   * @param other the other VTaskContext to compare with
   * @return this assertion for chaining
   */
  public VTaskContextAssert<T> isEquivalentTo(VTaskContext<T> other) {
    isNotNull();
    ensureExecuted();

    Objects.requireNonNull(other, "other VTaskContext must not be null");

    Try<T> otherResult = other.run();

    if (executedException != null) {
      if (otherResult.isSuccess()) {
        @SuppressWarnings("unchecked")
        T otherValue = (T) otherResult.fold(v -> v, e -> null);
        failWithMessage(
            "Expected both VTaskContexts to fail, but other succeeded with: %s", otherValue);
      }
      // Both failed - consider equivalent
    } else {
      if (otherResult.isFailure()) {
        failWithMessage(
            "Expected both VTaskContexts to succeed, but other failed with: %s",
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
