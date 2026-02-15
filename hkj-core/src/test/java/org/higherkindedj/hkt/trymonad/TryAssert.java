// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link Try} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Try} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.trymonad.TryAssert.assertThatTry;
 *
 * Try<Integer> success = Try.success(42);
 * assertThatTry(success)
 *     .isSuccess()
 *     .hasValue(42);
 *
 * Try<Integer> failure = Try.failure(new RuntimeException("Error"));
 * assertThatTry(failure).isFailure();
 *
 * // Chaining with null-safe assertions
 * assertThatTry(success)
 *     .isSuccess()
 *     .hasValueSatisfying(value -> {
 *         assertThat(value).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <T> The type of the value held by the Try
 */
public class TryAssert<T> extends AbstractAssert<TryAssert<T>, Try<T>> {

  /**
   * Creates a new {@code TryAssert} instance.
   *
   * <p>This is the entry point for all Try assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.trymonad.TryAssert.assertThatTry;
   * }</pre>
   *
   * @param <T> The type of the value held by the Try
   * @param actual The Try instance to make assertions on
   * @return A new TryAssert instance
   */
  public static <T> TryAssert<T> assertThatTry(Try<T> actual) {
    return new TryAssert<>(actual);
  }

  protected TryAssert(Try<T> actual) {
    super(actual, TryAssert.class);
  }

  /**
   * Verifies that the actual {@code Try} is a {@link Try.Success} instance.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.success(42);
   * assertThatTry(tryResult).isSuccess();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Try is null or is Failure
   */
  public TryAssert<T> isSuccess() {
    isNotNull();
    if (!actual.isSuccess()) {
      failWithMessage("Expected Try to be Success but was Failure");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a {@link Try.Failure} instance.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.failure(new RuntimeException("Error"));
   * assertThatTry(tryResult).isFailure();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Try is null or is Success
   */
  public TryAssert<T> isFailure() {
    isNotNull();
    if (!actual.isFailure()) {
      try {
        failWithMessage(
            "Expected Try to be Failure but was Success with value: <%s>", actual.get());
      } catch (Throwable t) {
        failWithMessage(
            "Expected Try to be Failure but was Success (could not retrieve value: %s)",
            t.getMessage());
      }
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Success and contains the expected value.
   *
   * <p>This method first verifies that the Try is a Success, then checks the contained value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.success(42);
   * assertThatTry(tryResult).hasValue(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Failure or the value doesn't match
   */
  public TryAssert<T> hasValue(T expected) {
    isSuccess();
    T actualValue;
    try {
      actualValue = actual.get();
    } catch (Throwable t) {
      failWithMessage(
          "Expected Try.Success to contain <%s> but could not retrieve value: %s",
          expected, t.getMessage());
      return this; // unreachable, but needed for compilation
    }
    if (!Objects.equals(actualValue, expected)) {
      failWithMessage(
          "Expected Try.Success to contain <%s> but contained <%s>", expected, actualValue);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Success and the contained value satisfies the given
   * requirements.
   *
   * <p>This is useful for complex assertions on the value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.success(42);
   * assertThatTry(tryResult).hasValueSatisfying(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Failure or the requirements are not satisfied
   */
  public TryAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    isSuccess();
    T actualValue;
    try {
      actualValue = actual.get();
    } catch (Throwable t) {
      failWithMessage(
          "Expected Try.Success to have value satisfying requirements but could not retrieve"
              + " value: %s",
          t.getMessage());
      return this; // unreachable, but needed for compilation
    }
    requirements.accept(actualValue);
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Success containing a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.success(42);
   * assertThatTry(tryResult).hasValueNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Failure or the value is null
   */
  public TryAssert<T> hasValueNonNull() {
    isSuccess();
    T actualValue;
    try {
      actualValue = actual.get();
    } catch (Throwable t) {
      failWithMessage(
          "Expected Try.Success to contain non-null value but could not retrieve value: %s",
          t.getMessage());
      return this; // unreachable, but needed for compilation
    }
    if (actualValue == null) {
      failWithMessage("Expected Try.Success to contain non-null value but was null");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Failure containing the expected exception.
   *
   * <p>Example:
   *
   * <pre>{@code
   * RuntimeException exception = new RuntimeException("Error");
   * Try<Integer> tryResult = Try.failure(exception);
   * assertThatTry(tryResult).hasException(exception);
   * }</pre>
   *
   * @param expected The expected exception (same instance)
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Success or the exception doesn't match
   */
  public TryAssert<T> hasException(Throwable expected) {
    isFailure();
    Throwable actualException;
    try {
      actual.get();
      failWithMessage("Expected Try.Failure but was Success");
      return this; // unreachable, but needed for compilation
    } catch (Throwable t) {
      actualException = t;
    }
    if (actualException != expected) {
      failWithMessage(
          "Expected Try.Failure to contain exception <%s> but contained <%s>",
          expected, actualException);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Failure containing an exception of the expected type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.failure(new RuntimeException("Error"));
   * assertThatTry(tryResult).hasExceptionOfType(RuntimeException.class);
   * }</pre>
   *
   * @param expectedType The expected exception type
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Success or the exception type doesn't match
   */
  public TryAssert<T> hasExceptionOfType(Class<? extends Throwable> expectedType) {
    isFailure();
    Throwable actualException;
    try {
      actual.get();
      failWithMessage("Expected Try.Failure but was Success");
      return this; // unreachable, but needed for compilation
    } catch (Throwable t) {
      actualException = t;
    }
    if (!expectedType.isInstance(actualException)) {
      failWithMessage(
          "Expected Try.Failure to contain exception of type <%s> but contained <%s>",
          expectedType.getName(), actualException.getClass().getName());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Failure and the exception satisfies the given
   * requirements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> tryResult = Try.failure(new RuntimeException("Error message"));
   * assertThatTry(tryResult).hasExceptionSatisfying(exception -> {
   *     assertThat(exception).hasMessageContaining("Error");
   *     assertThat(exception).isInstanceOf(RuntimeException.class);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the exception
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Success or the requirements are not satisfied
   */
  public TryAssert<T> hasExceptionSatisfying(Consumer<? super Throwable> requirements) {
    isFailure();
    Throwable actualException;
    try {
      actual.get();
      failWithMessage("Expected Try.Failure but was Success");
      return this; // unreachable, but needed for compilation
    } catch (Throwable t) {
      actualException = t;
    }
    requirements.accept(actualException);
    return this;
  }
}
