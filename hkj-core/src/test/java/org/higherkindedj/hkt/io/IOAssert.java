// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link IO} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code IO} instances,
 * making test code more readable and providing better error messages. Since IO represents lazy
 * computations, these assertions properly handle the execution semantics.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
 *
 * IO<Integer> io = IO.delay(() -> 42);
 * assertThatIO(io)
 *     .whenExecuted()
 *     .hasValue(42);
 *
 * IO<String> failing = IO.delay(() -> { throw new RuntimeException("Error"); });
 * assertThatIO(failing)
 *     .whenExecuted()
 *     .throwsException(RuntimeException.class)
 *     .withMessage("Error");
 *
 * // Verify laziness
 * assertThatIO(io).isNotExecutedYet();
 * }</pre>
 *
 * @param <T> The type of the value produced by the IO computation
 */
public class IOAssert<T> extends AbstractAssert<IOAssert<T>, IO<T>> {

  /**
   * Creates a new {@code IOAssert} instance.
   *
   * <p>This is the entry point for all IO assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
   * }</pre>
   *
   * @param <T> The type of the value produced by the IO computation
   * @param actual The IO instance to make assertions on
   * @return A new IOAssert instance
   */
  public static <T> IOAssert<T> assertThatIO(IO<T> actual) {
    return new IOAssert<>(actual);
  }

  private T executedValue;
  private Throwable executedException;
  private boolean hasBeenExecuted = false;

  protected IOAssert(IO<T> actual) {
    super(actual, IOAssert.class);
  }

  /**
   * Executes the IO computation and prepares for assertions on the result.
   *
   * <p>This method triggers the {@code unsafeRunSync()} call and captures either the resulting
   * value or any exception thrown during execution. Subsequent assertions will operate on the
   * captured result.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io)
   *     .whenExecuted()
   *     .hasValue(42);
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual IO is null
   */
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

  /**
   * Verifies that the IO computation, when executed, produces the expected value.
   *
   * <p>This method automatically executes the IO if it hasn't been executed yet.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io).hasValue(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the IO throws an exception or the value doesn't match
   */
  public IOAssert<T> hasValue(T expected) {
    whenExecuted();
    if (executedException != null) {
      failWithMessage(
          "Expected IO to produce value <%s> but it threw an exception: %s",
          expected, executedException);
    }
    if (!Objects.equals(executedValue, expected)) {
      failWithMessage(
          "Expected IO to produce value <%s> but produced <%s>", expected, executedValue);
    }
    return this;
  }

  /**
   * Verifies that the IO computation, when executed, produces a value satisfying the given
   * requirements.
   *
   * <p>This is useful for complex assertions on the value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io).hasValueSatisfying(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the IO throws an exception or the requirements are not satisfied
   */
  public IOAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    whenExecuted();
    if (executedException != null) {
      failWithMessage(
          "Expected IO to produce a value satisfying requirements but it threw an exception: %s",
          executedException);
    }
    requirements.accept(executedValue);
    return this;
  }

  /**
   * Verifies that the IO computation, when executed, produces a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<String> io = IO.delay(() -> "result");
   * assertThatIO(io).hasValueNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the IO throws an exception or the value is null
   */
  public IOAssert<T> hasValueNonNull() {
    whenExecuted();
    if (executedException != null) {
      failWithMessage(
          "Expected IO to produce a non-null value but it threw an exception: %s",
          executedException);
    }
    if (executedValue == null) {
      failWithMessage("Expected IO to produce a non-null value but it produced null");
    }
    return this;
  }

  /**
   * Verifies that the IO computation, when executed, produces a null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<String> io = IO.delay(() -> null);
   * assertThatIO(io).hasValueNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the IO throws an exception or the value is not null
   */
  public IOAssert<T> hasValueNull() {
    whenExecuted();
    if (executedException != null) {
      failWithMessage(
          "Expected IO to produce null but it threw an exception: %s", executedException);
    }
    if (executedValue != null) {
      failWithMessage("Expected IO to produce null but it produced <%s>", executedValue);
    }
    return this;
  }

  /**
   * Verifies that the IO computation, when executed, throws an exception of the expected type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> { throw new RuntimeException("Error"); });
   * assertThatIO(io).throwsException(RuntimeException.class);
   * }</pre>
   *
   * @param expectedType The expected exception type
   * @return This assertion object for method chaining
   * @throws AssertionError if the IO completes successfully or throws a different exception type
   */
  public IOAssert<T> throwsException(Class<? extends Throwable> expectedType) {
    whenExecuted();
    if (executedException == null) {
      failWithMessage(
          "Expected IO to throw exception of type <%s> but it completed with value <%s>",
          expectedType.getName(), executedValue);
    }
    if (!expectedType.isInstance(executedException)) {
      failWithMessage(
          "Expected IO to throw exception of type <%s> but it threw <%s>",
          expectedType.getName(), executedException.getClass().getName());
    }
    return this;
  }

  /**
   * Verifies that the thrown exception has the expected message.
   *
   * <p>This method should be called after {@link #throwsException(Class)} or {@link
   * #whenExecuted()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> { throw new RuntimeException("Error message"); });
   * assertThatIO(io)
   *     .throwsException(RuntimeException.class)
   *     .withMessage("Error message");
   * }</pre>
   *
   * @param expectedMessage The expected exception message
   * @return This assertion object for method chaining
   * @throws AssertionError if no exception was thrown or the message doesn't match
   */
  public IOAssert<T> withMessage(String expectedMessage) {
    if (executedException == null) {
      failWithMessage(
          "Expected IO to have thrown an exception with message <%s> but no exception was thrown",
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
   * <p>This method should be called after {@link #throwsException(Class)} or {@link
   * #whenExecuted()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> { throw new RuntimeException("Error: failed"); });
   * assertThatIO(io)
   *     .throwsException(RuntimeException.class)
   *     .withMessageContaining("failed");
   * }</pre>
   *
   * @param substring The expected substring in the exception message
   * @return This assertion object for method chaining
   * @throws AssertionError if no exception was thrown or the message doesn't contain the substring
   */
  public IOAssert<T> withMessageContaining(String substring) {
    if (executedException == null) {
      failWithMessage(
          "Expected IO to have thrown an exception with message containing <%s> but no exception"
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
   * Verifies that the IO computation completes successfully without throwing an exception.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io).completesSuccessfully();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the IO throws an exception
   */
  public IOAssert<T> completesSuccessfully() {
    whenExecuted();
    if (executedException != null) {
      failWithMessage(
          "Expected IO to complete successfully but it threw an exception: %s", executedException);
    }
    return this;
  }

  /**
   * Verifies that the IO computation, when executed multiple times, produces the same result.
   *
   * <p>This is useful for verifying referential transparency and proper lazy evaluation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io).isRepeatable();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if multiple executions produce different results
   */
  public IOAssert<T> isRepeatable() {
    whenExecuted();

    // Execute a second time
    T secondValue = null;
    Throwable secondException = null;
    try {
      secondValue = actual.unsafeRunSync();
    } catch (Throwable t) {
      secondException = t;
    }

    // Compare results
    if (executedException != null && secondException != null) {
      // Both threw exceptions - check if they're the same type and message
      if (!executedException.getClass().equals(secondException.getClass())
          || !Objects.equals(executedException.getMessage(), secondException.getMessage())) {
        failWithMessage(
            "Expected IO to be repeatable but first execution threw <%s> and second execution"
                + " threw <%s>",
            executedException, secondException);
      }
    } else if (executedException != null || secondException != null) {
      failWithMessage(
          "Expected IO to be repeatable but first execution %s and second execution %s",
          executedException != null ? "threw " + executedException : "returned " + executedValue,
          secondException != null ? "threw " + secondException : "returned " + secondValue);
    } else if (!Objects.equals(executedValue, secondValue)) {
      failWithMessage(
          "Expected IO to be repeatable but first execution returned <%s> and second execution"
              + " returned <%s>",
          executedValue, secondValue);
    }

    return this;
  }

  /**
   * Verifies that the IO has not been executed yet.
   *
   * <p>This is a conceptual assertion since IO's lazy nature means we can't actually verify if it
   * has been executed elsewhere. This primarily serves as documentation in tests.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io).isNotExecutedYet(); // Documents that we haven't called unsafeRunSync yet
   * }</pre>
   *
   * @return This assertion object for method chaining
   */
  public IOAssert<T> isNotExecutedYet() {
    isNotNull();
    if (hasBeenExecuted) {
      failWithMessage(
          "Expected IO to not have been executed yet, but it has been executed by this assertion"
              + " object");
    }
    return this;
  }

  /**
   * Returns the exception that was thrown during execution, if any.
   *
   * <p>This allows for further assertions on the exception using standard AssertJ methods.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> { throw new RuntimeException("Error"); });
   * assertThatIO(io).whenExecuted();
   * Throwable exception = assertThatIO(io).getException();
   * assertThat(exception).isInstanceOf(RuntimeException.class);
   * }</pre>
   *
   * @return The exception that was thrown, or null if no exception was thrown
   */
  public Throwable getException() {
    if (!hasBeenExecuted) {
      whenExecuted();
    }
    return executedException;
  }

  /**
   * Returns the value that was produced during execution, if any.
   *
   * <p>This allows for further assertions on the value using standard AssertJ methods.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IO<Integer> io = IO.delay(() -> 42);
   * assertThatIO(io).whenExecuted();
   * Integer value = assertThatIO(io).getValue();
   * assertThat(value).isGreaterThan(40);
   * }</pre>
   *
   * @return The value that was produced, or null if an exception was thrown or the value is null
   */
  public T getValue() {
    if (!hasBeenExecuted) {
      whenExecuted();
    }
    return executedValue;
  }
}
