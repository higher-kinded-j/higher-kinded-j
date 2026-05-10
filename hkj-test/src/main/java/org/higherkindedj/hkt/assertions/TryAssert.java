// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.trymonad.Try;

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
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Try is null or is Failure
   */
  public TryAssert<T> isSuccess() {
    isNotNull();
    Assertions.assertThat(actual.isSuccess())
        .withFailMessage("Expected Try to be Success but was Failure")
        .isTrue();
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a {@link Try.Failure} instance.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Try is null or is Success
   */
  public TryAssert<T> isFailure() {
    isNotNull();
    Assertions.assertThat(actual.isFailure())
        .withFailMessage(
            () -> "Expected Try to be Failure but was Success with value: <" + successValue() + ">")
        .isTrue();
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Success and contains the expected value.
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Failure or the value doesn't match
   */
  public TryAssert<T> hasValue(T expected) {
    isSuccess();
    T actualValue = successValue();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Try.Success to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Success and the contained value satisfies the given
   * requirements.
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Failure or the requirements are not satisfied
   */
  public TryAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    isSuccess();
    requirements.accept(successValue());
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Success containing a non-null value.
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Failure or the value is null
   */
  public TryAssert<T> hasValueNonNull() {
    isSuccess();
    Assertions.assertThat(successValue()).as("Try.Success value").isNotNull();
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Failure containing the expected exception.
   *
   * @param expected The expected exception (same instance)
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Success or the exception doesn't match
   */
  public TryAssert<T> hasException(Throwable expected) {
    isFailure();
    Throwable actualException = failureCause();
    Assertions.assertThat(actualException)
        .withFailMessage(
            "Expected Try.Failure to contain exception <%s> but contained <%s>",
            expected, actualException)
        .isSameAs(expected);
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Failure containing an exception of the expected type.
   *
   * @param expectedType The expected exception type
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Success or the exception type doesn't match
   */
  public TryAssert<T> hasExceptionOfType(Class<? extends Throwable> expectedType) {
    isFailure();
    Throwable actualException = failureCause();
    Assertions.assertThat(actualException)
        .withFailMessage(
            "Expected Try.Failure to contain exception of type <%s> but contained <%s>",
            expectedType.getName(), actualException.getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /**
   * Verifies that the actual {@code Try} is a Failure and the exception satisfies the given
   * requirements.
   *
   * @param requirements The requirements to verify on the exception
   * @return This assertion object for method chaining
   * @throws AssertionError if the Try is Success or the requirements are not satisfied
   */
  public TryAssert<T> hasExceptionSatisfying(Consumer<? super Throwable> requirements) {
    isFailure();
    requirements.accept(failureCause());
    return this;
  }

  /**
   * Direct accessor that bypasses Success.get()'s exception-throwing behaviour. Caller must have
   * verified the Try is a Success.
   */
  private T successValue() {
    return ((Try.Success<T>) actual).value();
  }

  /**
   * Direct accessor for the underlying cause of a Failure. Caller must have verified the Try is a
   * Failure.
   */
  private Throwable failureCause() {
    return ((Try.Failure<T>) actual).cause();
  }
}
