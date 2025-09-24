// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.data;

import java.util.function.Supplier;

/**
 * Exception utilities for testing error handling and propagation.
 *
 * <p>Provides utilities for creating test exceptions, exception suppliers, and for testing
 * exception propagation patterns in monad operations.
 *
 * <h2>Categories:</h2>
 *
 * <ul>
 *   <li>Exception factory methods
 *   <li>Exception suppliers (lazy evaluation)
 *   <li>Exception matchers and validators
 *   <li>Exception propagation testers
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * // Create test exception
 * RuntimeException ex = TestExceptions.runtime("test scenario");
 *
 * // Create lazy exception supplier
 * Supplier<RuntimeException> exSupplier = TestExceptions.runtimeSupplier("deferred");
 *
 * // Test exception propagation
 * assertThatThrownBy(() -> operation())
 *     .matches(TestExceptions.isTestException());
 * }</pre>
 */
public final class TestExceptions {

  private TestExceptions() {
    throw new AssertionError("TestExceptions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Exception Factory Methods
  // =============================================================================

  /**
   * Creates a RuntimeException for testing.
   *
   * <p>All test exceptions include "Test exception: " prefix for easy identification.
   *
   * @param message The exception message
   * @return A RuntimeException
   */
  public static RuntimeException runtime(String message) {
    return new RuntimeException("Test exception: " + message);
  }

  /**
   * Creates a RuntimeException with cause.
   *
   * @param message The exception message
   * @param cause The underlying cause
   * @return A RuntimeException with cause
   */
  public static RuntimeException runtime(String message, Throwable cause) {
    return new RuntimeException("Test exception: " + message, cause);
  }

  /**
   * Creates an IllegalArgumentException for testing.
   *
   * @param message The exception message
   * @return An IllegalArgumentException
   */
  public static IllegalArgumentException illegalArgument(String message) {
    return new IllegalArgumentException("Test validation error: " + message);
  }

  /**
   * Creates an IllegalStateException for testing.
   *
   * @param message The exception message
   * @return An IllegalStateException
   */
  public static IllegalStateException illegalState(String message) {
    return new IllegalStateException("Test state error: " + message);
  }

  /**
   * Creates a NullPointerException for testing.
   *
   * @param message The exception message
   * @return A NullPointerException
   */
  public static NullPointerException nullPointer(String message) {
    return new NullPointerException("Test null pointer: " + message);
  }

  /**
   * Creates an UnsupportedOperationException for testing.
   *
   * @param message The exception message
   * @return An UnsupportedOperationException
   */
  public static UnsupportedOperationException unsupportedOperation(String message) {
    return new UnsupportedOperationException("Test unsupported: " + message);
  }

  // =============================================================================
  // Exception Suppliers (Lazy Evaluation)
  // =============================================================================

  /**
   * Creates a supplier that produces RuntimeExceptions.
   *
   * <p>Useful for deferred exception creation in tests.
   *
   * @param message The exception message
   * @return A supplier that creates RuntimeExceptions
   */
  public static Supplier<RuntimeException> runtimeSupplier(String message) {
    return () -> runtime(message);
  }

  /**
   * Creates a supplier that produces IllegalArgumentExceptions.
   *
   * @param message The exception message
   * @return A supplier that creates IllegalArgumentExceptions
   */
  public static Supplier<IllegalArgumentException> illegalArgumentSupplier(String message) {
    return () -> illegalArgument(message);
  }

  /**
   * Creates a supplier that throws on get().
   *
   * <p>Useful for testing lazy evaluation scenarios.
   *
   * @param exception The exception to throw
   * @param <T> The expected return type (never returned)
   * @return A supplier that throws when invoked
   */
  public static <T> Supplier<T> throwingSupplier(RuntimeException exception) {
    return () -> {
      throw exception;
    };
  }

  // =============================================================================
  // Exception Message Builders
  // =============================================================================

  /**
   * Builds a detailed test exception message.
   *
   * @param operation The operation that failed
   * @param reason The reason for failure
   * @return A formatted exception message
   */
  public static String buildMessage(String operation, String reason) {
    return "Test exception in %s: %s".formatted(operation, reason);
  }

  /**
   * Builds a parameterized test exception message.
   *
   * @param operation The operation that failed
   * @param parameter The parameter that caused the failure
   * @param reason The reason for failure
   * @return A formatted exception message
   */
  public static String buildParameterMessage(String operation, String parameter, String reason) {
    return "Test exception in %s (parameter: %s): %s".formatted(operation, parameter, reason);
  }

  /**
   * Builds a message for testing validation errors.
   *
   * @param field The field that failed validation
   * @param constraint The constraint that was violated
   * @return A formatted validation message
   */
  public static String buildValidationMessage(String field, String constraint) {
    return "Test validation error - %s: %s".formatted(field, constraint);
  }

  // =============================================================================
  // Exception Predicates (for AssertJ matching)
  // =============================================================================

  /**
   * Creates a predicate that matches test exceptions.
   *
   * <p>Use with AssertJ: {@code .matches(isTestException())}
   *
   * @return A predicate that matches exceptions with "Test exception:" prefix
   */
  public static java.util.function.Predicate<Throwable> isTestException() {
    return throwable ->
        throwable != null
            && throwable.getMessage() != null
            && throwable.getMessage().startsWith("Test exception:");
  }

  /**
   * Creates a predicate that matches exceptions with specific message.
   *
   * @param expectedMessage The expected message (exact match)
   * @return A predicate that matches the message
   */
  public static java.util.function.Predicate<Throwable> hasMessage(String expectedMessage) {
    return throwable -> throwable != null && expectedMessage.equals(throwable.getMessage());
  }

  /**
   * Creates a predicate that matches exceptions containing message substring.
   *
   * @param messageSubstring The substring to match
   * @return A predicate that matches messages containing the substring
   */
  public static java.util.function.Predicate<Throwable> hasMessageContaining(
      String messageSubstring) {
    return throwable ->
        throwable != null
            && throwable.getMessage() != null
            && throwable.getMessage().contains(messageSubstring);
  }

  /**
   * Creates a predicate that matches specific exception types.
   *
   * @param exceptionClass The expected exception class
   * @return A predicate that matches the exception type
   */
  public static java.util.function.Predicate<Throwable> isInstanceOf(
      Class<? extends Throwable> exceptionClass) {
    return exceptionClass::isInstance;
  }

  /**
   * Creates a predicate that matches exceptions with a specific cause.
   *
   * @param causeClass The expected cause class
   * @return A predicate that matches exceptions with the specified cause
   */
  public static java.util.function.Predicate<Throwable> hasCause(
      Class<? extends Throwable> causeClass) {
    return throwable ->
        throwable != null
            && throwable.getCause() != null
            && causeClass.isInstance(throwable.getCause());
  }

  // =============================================================================
  // Exception Chain Utilities
  // =============================================================================

  /**
   * Creates a chained exception for testing exception wrapping.
   *
   * @param message The outer exception message
   * @param cause The cause exception
   * @return A RuntimeException wrapping the cause
   */
  public static RuntimeException chained(String message, Throwable cause) {
    return new RuntimeException("Test exception: " + message, cause);
  }

  /**
   * Creates a multi-level exception chain.
   *
   * @param messages Messages for each level (innermost first)
   * @return The outermost exception with full chain
   */
  public static RuntimeException chainedMultiple(String... messages) {
    if (messages.length == 0) {
      throw new IllegalArgumentException("At least one message required");
    }

    RuntimeException current = runtime(messages[0]);
    for (int i = 1; i < messages.length; i++) {
      current = chained(messages[i], current);
    }
    return current;
  }

  /**
   * Extracts the root cause from an exception chain.
   *
   * @param throwable The exception to analyze
   * @return The root cause (innermost exception)
   */
  public static Throwable getRootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * Gets the depth of an exception chain.
   *
   * @param throwable The exception to analyze
   * @return The number of exceptions in the chain
   */
  public static int getChainDepth(Throwable throwable) {
    int depth = 1;
    Throwable current = throwable;
    while (current.getCause() != null) {
      depth++;
      current = current.getCause();
    }
    return depth;
  }

  // =============================================================================
  // Specific Test Scenarios
  // =============================================================================

  /**
   * Creates an exception for testing null parameter validation.
   *
   * @param parameterName The name of the null parameter
   * @return A NullPointerException with descriptive message
   */
  public static NullPointerException nullParameter(String parameterName) {
    return nullPointer(parameterName + " cannot be null");
  }

  /**
   * Creates an exception for testing invalid state scenarios.
   *
   * @param currentState The current invalid state
   * @param expectedState The expected state
   * @return An IllegalStateException with descriptive message
   */
  public static IllegalStateException invalidState(String currentState, String expectedState) {
    return illegalState(
        "Invalid state: current=%s, expected=%s".formatted(currentState, expectedState));
  }

  /**
   * Creates an exception for testing range validation.
   *
   * @param value The invalid value
   * @param min The minimum allowed value
   * @param max The maximum allowed value
   * @return An IllegalArgumentException with descriptive message
   */
  public static IllegalArgumentException outOfRange(Object value, Object min, Object max) {
    return illegalArgument("Value %s out of range [%s, %s]".formatted(value, min, max));
  }

  /**
   * Creates an exception for testing type mismatch scenarios.
   *
   * @param expected The expected type
   * @param actual The actual type
   * @return An IllegalArgumentException with descriptive message
   */
  public static IllegalArgumentException typeMismatch(Class<?> expected, Class<?> actual) {
    return illegalArgument(
        "Type mismatch: expected %s, got %s"
            .formatted(expected.getSimpleName(), actual.getSimpleName()));
  }

  // =============================================================================
  // Exception Assertion Helpers
  // =============================================================================

  /**
   * Verifies that an exception has expected properties.
   *
   * @param exception The exception to verify
   * @param expectedMessage The expected message (null to skip check)
   * @param expectedType The expected type (null to skip check)
   * @throws AssertionError if verification fails
   */
  public static void verifyException(
      Throwable exception, String expectedMessage, Class<? extends Throwable> expectedType) {

    if (exception == null) {
      throw new AssertionError("Expected exception but none was thrown");
    }

    if (expectedType != null && !expectedType.isInstance(exception)) {
      throw new AssertionError(
          "Expected exception of type %s but got %s"
              .formatted(expectedType.getName(), exception.getClass().getName()));
    }

    if (expectedMessage != null && !expectedMessage.equals(exception.getMessage())) {
      throw new AssertionError(
          "Expected message '%s' but got '%s'".formatted(expectedMessage, exception.getMessage()));
    }
  }

  /**
   * Verifies exception chain structure.
   *
   * @param exception The exception to verify
   * @param expectedChain The expected exception types (outermost first)
   * @throws AssertionError if chain doesn't match
   */
  @SafeVarargs
  public static void verifyExceptionChain(
      Throwable exception, Class<? extends Throwable>... expectedChain) {

    Throwable current = exception;
    for (int i = 0; i < expectedChain.length; i++) {
      if (current == null) {
        throw new AssertionError(
            "Exception chain too short: expected %d levels, got %d"
                .formatted(expectedChain.length, i));
      }

      if (!expectedChain[i].isInstance(current)) {
        throw new AssertionError(
            "Exception at level %d: expected %s, got %s"
                .formatted(i, expectedChain[i].getName(), current.getClass().getName()));
      }

      current = current.getCause();
    }

    if (current != null) {
      throw new AssertionError(
          "Exception chain too long: expected %d levels, has more".formatted(expectedChain.length));
    }
  }
}
