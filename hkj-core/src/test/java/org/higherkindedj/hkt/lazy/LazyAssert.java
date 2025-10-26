// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link Lazy} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Lazy} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.lazy.LazyAssert.assertThatLazy;
 *
 * Lazy<Integer> deferred = Lazy.defer(() -> 42);
 * assertThatLazy(deferred)
 *     .isNotEvaluated()
 *     .whenForcedHasValue(42);
 *
 * Lazy<Integer> now = Lazy.now(42);
 * assertThatLazy(now)
 *     .isEvaluated()
 *     .hasValue(42);
 *
 * // Chaining with value assertions
 * assertThatLazy(deferred)
 *     .whenForcedHasValueSatisfying(value -> {
 *         assertThat(value).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <T> The type of the value held by the Lazy
 */
public class LazyAssert<T> extends AbstractAssert<LazyAssert<T>, Lazy<T>> {

  /**
   * Creates a new {@code LazyAssert} instance.
   *
   * <p>This is the entry point for all Lazy assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.lazy.LazyAssert.assertThatLazy;
   * }</pre>
   *
   * @param <T> The type of the value held by the Lazy
   * @param actual The Lazy instance to make assertions on
   * @return A new LazyAssert instance
   */
  public static <T> LazyAssert<T> assertThatLazy(Lazy<T> actual) {
    return new LazyAssert<>(actual);
  }

  protected LazyAssert(Lazy<T> actual) {
    super(actual, LazyAssert.class);
  }

  /**
   * Verifies that the actual {@code Lazy} is already evaluated.
   *
   * <p>This checks the toString representation to determine evaluation state without forcing
   * evaluation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.now(42);
   * assertThatLazy(lazy).isEvaluated();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Lazy is null or is not evaluated
   */
  public LazyAssert<T> isEvaluated() {
    isNotNull();
    String toString = actual.toString();
    if (toString.equals("Lazy[unevaluated...]")) {
      failWithMessage("Expected Lazy to be evaluated but was unevaluated");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Lazy} is not yet evaluated.
   *
   * <p>This checks the toString representation to determine evaluation state without forcing
   * evaluation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> 42);
   * assertThatLazy(lazy).isNotEvaluated();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Lazy is null or is already evaluated
   */
  public LazyAssert<T> isNotEvaluated() {
    isNotNull();
    String toString = actual.toString();
    if (!toString.equals("Lazy[unevaluated...]")) {
      failWithMessage("Expected Lazy to be unevaluated but was: <%s>", toString);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Lazy} is evaluated and contains the expected value.
   *
   * <p>This method assumes the Lazy is already evaluated and checks its value without forcing.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.now(42);
   * assertThatLazy(lazy).hasValue(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Lazy is not evaluated or the value doesn't match
   * @throws Throwable if forcing the lazy computation fails
   */
  public LazyAssert<T> hasValue(T expected) throws Throwable {
    isEvaluated();
    T actualValue = actual.force();
    if (actualValue == null && expected == null) {
      return this;
    }
    if (actualValue == null || !actualValue.equals(expected)) {
      failWithMessage("Expected Lazy to contain <%s> but contained <%s>", expected, actualValue);
    }
    return this;
  }

  /**
   * Forces evaluation of the actual {@code Lazy} and verifies it contains the expected value.
   *
   * <p>This method forces evaluation if necessary before checking the value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> 42);
   * assertThatLazy(lazy).whenForcedHasValue(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the value doesn't match
   * @throws Throwable if forcing the lazy computation fails
   */
  public LazyAssert<T> whenForcedHasValue(T expected) throws Throwable {
    isNotNull();
    T actualValue = actual.force();
    if (actualValue == null && expected == null) {
      return this;
    }
    if (actualValue == null || !actualValue.equals(expected)) {
      failWithMessage("Expected Lazy to contain <%s> but contained <%s>", expected, actualValue);
    }
    return this;
  }

  /**
   * Forces evaluation of the actual {@code Lazy} and verifies the contained value satisfies the
   * given requirements.
   *
   * <p>This is useful for complex assertions on the value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> 42);
   * assertThatLazy(lazy).whenForcedHasValueSatisfying(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   * @throws Throwable if forcing the lazy computation fails
   */
  public LazyAssert<T> whenForcedHasValueSatisfying(Consumer<? super T> requirements)
      throws Throwable {
    isNotNull();
    T actualValue = actual.force();
    requirements.accept(actualValue);
    return this;
  }

  /**
   * Forces evaluation of the actual {@code Lazy} and verifies it contains a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> 42);
   * assertThatLazy(lazy).whenForcedHasValueNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is null
   * @throws Throwable if forcing the lazy computation fails
   */
  public LazyAssert<T> whenForcedHasValueNonNull() throws Throwable {
    isNotNull();
    T actualValue = actual.force();
    if (actualValue == null) {
      failWithMessage("Expected Lazy to contain non-null value but was null");
    }
    return this;
  }

  /**
   * Verifies that forcing the actual {@code Lazy} throws an exception of the expected type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> { throw new IllegalStateException("Failed"); });
   * assertThatLazy(lazy).whenForcedThrows(IllegalStateException.class);
   * }</pre>
   *
   * @param expectedExceptionType The expected exception type
   * @return This assertion object for method chaining
   * @throws AssertionError if no exception is thrown or the exception type doesn't match
   */
  public LazyAssert<T> whenForcedThrows(Class<? extends Throwable> expectedExceptionType) {
    isNotNull();
    try {
      actual.force();
      failWithMessage(
          "Expected Lazy.force() to throw <%s> but no exception was thrown",
          expectedExceptionType.getSimpleName());
    } catch (Throwable thrown) {
      if (!expectedExceptionType.isInstance(thrown)) {
        failWithMessage(
            "Expected Lazy.force() to throw <%s> but threw <%s>",
            expectedExceptionType.getSimpleName(), thrown.getClass().getSimpleName());
      }
    }
    return this;
  }

  /**
   * Verifies that forcing the actual {@code Lazy} throws an exception with the expected message.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> { throw new IllegalStateException("Failed"); });
   * assertThatLazy(lazy).whenForcedThrowsWithMessage("Failed");
   * }</pre>
   *
   * @param expectedMessage The expected exception message
   * @return This assertion object for method chaining
   * @throws AssertionError if no exception is thrown or the message doesn't match
   */
  public LazyAssert<T> whenForcedThrowsWithMessage(String expectedMessage) {
    isNotNull();
    try {
      actual.force();
      failWithMessage("Expected Lazy.force() to throw exception but no exception was thrown");
    } catch (Throwable thrown) {
      if (!expectedMessage.equals(thrown.getMessage())) {
        failWithMessage(
            "Expected Lazy.force() to throw exception with message <%s> but was <%s>",
            expectedMessage, thrown.getMessage());
      }
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Lazy} is in a failed state (contains a cached exception).
   *
   * <p>This checks the toString representation to determine if a failure is cached without forcing
   * evaluation again.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.defer(() -> { throw new IllegalStateException("Failed"); });
   * try { lazy.force(); } catch (Throwable ignored) {}
   * assertThatLazy(lazy).hasFailed();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Lazy is null or is not in a failed state
   */
  public LazyAssert<T> hasFailed() {
    isNotNull();
    String toString = actual.toString();
    if (!toString.startsWith("Lazy[failed:")) {
      failWithMessage("Expected Lazy to have failed but was: <%s>", toString);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Lazy} has not failed (is either unevaluated or successfully
   * evaluated).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Lazy<Integer> lazy = Lazy.now(42);
   * assertThatLazy(lazy).hasNotFailed();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Lazy is null or is in a failed state
   */
  public LazyAssert<T> hasNotFailed() {
    isNotNull();
    String toString = actual.toString();
    if (toString.startsWith("Lazy[failed:")) {
      failWithMessage("Expected Lazy to not have failed but was: <%s>", toString);
    }
    return this;
  }
}
