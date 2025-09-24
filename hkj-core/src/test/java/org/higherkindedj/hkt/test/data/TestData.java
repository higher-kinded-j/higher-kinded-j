// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.data;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;

/**
 * Test data generators and utilities.
 *
 * <p>Provides common test data generation functions to reduce boilerplate in test code and ensure
 * consistency across test suites.
 *
 * <h2>Categories:</h2>
 *
 * <ul>
 *   <li>Dummy Kind generators (for invalid type testing)
 *   <li>Exception generators
 *   <li>Test value generators
 *   <li>Collection generators
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * // Create dummy Kind for testing invalid types
 * Kind<F, A> invalidKind = TestData.createDummyKind("test");
 *
 * // Create test exception
 * RuntimeException ex = TestData.createTestException("test scenario");
 *
 * // Generate test collections
 * List<Integer> numbers = TestData.generateIntegerList(1, 100);
 * }</pre>
 */
public final class TestData {

  private TestData() {
    throw new AssertionError("TestData is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Dummy Kind Generators
  // =============================================================================

  /**
   * Creates a dummy Kind implementation for testing invalid type errors.
   *
   * <p>This is useful for testing that narrow operations properly validate Kind types and throw
   * appropriate exceptions.
   *
   * @param identifier A string identifier for the dummy Kind (used in toString)
   * @param <F> The witness type
   * @param <A> The value type
   * @return A dummy Kind implementation
   */
  public static <F, A> Kind<F, A> createDummyKind(String identifier) {
    return new Kind<F, A>() {
      @Override
      public String toString() {
        return "DummyKind{" + identifier + "}";
      }
    };
  }

  /**
   * Creates a named dummy Kind with specific type information.
   *
   * @param name The name for this dummy Kind
   * @param witnessType The witness type class name
   * @param valueType The value type class name
   * @param <F> The witness type
   * @param <A> The value type
   * @return A dummy Kind with detailed toString
   */
  public static <F, A> Kind<F, A> createNamedDummyKind(
      String name, String witnessType, String valueType) {
    return new Kind<F, A>() {
      @Override
      public String toString() {
        return String.format(
            "DummyKind{name='%s', witness='%s', value='%s'}", name, witnessType, valueType);
      }
    };
  }

  // =============================================================================
  // Exception Generators
  // =============================================================================

  /**
   * Creates a RuntimeException for testing error propagation.
   *
   * <p>All test exceptions have the prefix "Test exception: " to make them easily identifiable in
   * test output.
   *
   * @param message The exception message
   * @return A RuntimeException with the given message
   */
  public static RuntimeException createTestException(String message) {
    return new RuntimeException("Test exception: " + message);
  }

  /**
   * Creates a test exception with cause.
   *
   * @param message The exception message
   * @param cause The underlying cause
   * @return A RuntimeException with message and cause
   */
  public static RuntimeException createTestException(String message, Throwable cause) {
    return new RuntimeException("Test exception: " + message, cause);
  }

  /**
   * Creates an IllegalArgumentException for testing.
   *
   * @param message The exception message
   * @return An IllegalArgumentException
   */
  public static IllegalArgumentException createIllegalArgumentException(String message) {
    return new IllegalArgumentException("Test validation error: " + message);
  }

  /**
   * Creates a NullPointerException for testing.
   *
   * @param message The exception message
   * @return A NullPointerException
   */
  public static NullPointerException createNullPointerException(String message) {
    return new NullPointerException("Test null pointer: " + message);
  }

  // =============================================================================
  // Test Value Generators
  // =============================================================================

  /**
   * Generates a list of integers in the specified range.
   *
   * @param start The start value (inclusive)
   * @param end The end value (inclusive)
   * @return A list of integers from start to end
   */
  public static List<Integer> generateIntegerList(int start, int end) {
    return IntStream.rangeClosed(start, end).boxed().collect(Collectors.toList());
  }

  /**
   * Generates a list of strings with a common prefix.
   *
   * @param prefix The prefix for each string
   * @param count The number of strings to generate
   * @return A list of prefixed strings
   */
  public static List<String> generateStringList(String prefix, int count) {
    return IntStream.range(0, count).mapToObj(i -> prefix + i).collect(Collectors.toList());
  }

  /**
   * Generates a list of test values with a transformation.
   *
   * @param count The number of values to generate
   * @param generator Function to generate each value from its index
   * @param <T> The value type
   * @return A list of generated values
   */
  public static <T> List<T> generateList(int count, java.util.function.IntFunction<T> generator) {
    return IntStream.range(0, count).mapToObj(generator).collect(Collectors.toList());
  }

  // =============================================================================
  // Standard Test Values
  // =============================================================================

  /** Standard test integer value */
  public static final Integer TEST_INT = 42;

  /** Standard test string value */
  public static final String TEST_STRING = "test";

  /** Standard test boolean value */
  public static final Boolean TEST_BOOLEAN = true;

  /** Standard test double value */
  public static final Double TEST_DOUBLE = 3.14;

  /** Standard negative test integer */
  public static final Integer TEST_NEGATIVE_INT = -1;

  /** Standard zero test integer */
  public static final Integer TEST_ZERO = 0;

  /** Standard empty test string */
  public static final String TEST_EMPTY_STRING = "";

  /** Standard large test integer */
  public static final Integer TEST_LARGE_INT = 999999;

  // =============================================================================
  // Error Message Constants
  // =============================================================================

  /** Standard test error message */
  public static final String TEST_ERROR_MESSAGE = "Test error occurred";

  /** Standard validation error message */
  public static final String VALIDATION_ERROR_MESSAGE = "Validation failed";

  /** Standard null pointer error message */
  public static final String NULL_ERROR_MESSAGE = "Unexpected null value";

  // =============================================================================
  // Collection Utilities
  // =============================================================================

  /**
   * Creates a list of pairs (tuples).
   *
   * @param count The number of pairs to create
   * @param firstGenerator Generator for first element
   * @param secondGenerator Generator for second element
   * @param <A> The first element type
   * @param <B> The second element type
   * @return A list of pairs
   */
  public static <A, B> List<Pair<A, B>> generatePairs(
      int count, IntFunction<A> firstGenerator, IntFunction<B> secondGenerator) {
    return IntStream.range(0, count)
        .mapToObj(i -> new Pair<>(firstGenerator.apply(i), secondGenerator.apply(i)))
        .collect(Collectors.toList());
  }

  /**
   * Simple pair class for test data.
   *
   * @param <A> The first element type
   * @param <B> The second element type
   */
  public record Pair<A, B>(A first, B second) {}

  /**
   * Creates a list with repeated values.
   *
   * @param value The value to repeat
   * @param count The number of repetitions
   * @param <T> The value type
   * @return A list with repeated values
   */
  public static <T> List<T> repeat(T value, int count) {
    return IntStream.range(0, count).mapToObj(i -> value).collect(Collectors.toList());
  }

  // =============================================================================
  // Boundary Value Generators
  // =============================================================================

  /**
   * Gets standard boundary test integers.
   *
   * @return A list containing common boundary values
   */
  public static List<Integer> getBoundaryIntegers() {
    return List.of(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE);
  }

  /**
   * Gets standard test strings including edge cases.
   *
   * @return A list of test strings
   */
  public static List<String> getTestStrings() {
    return List.of(
        "", // empty
        " ", // single space
        "a", // single char
        "test", // normal
        "Hello, World!", // with punctuation
        "123", // numeric
        "   padded   " // with padding
        );
  }

  /**
   * Gets standard test booleans.
   *
   * @return A list containing true and false
   */
  public static List<Boolean> getTestBooleans() {
    return List.of(true, false);
  }
}
