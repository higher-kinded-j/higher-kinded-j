// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.test.HKTTestAssertions.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * Simplified testing helpers that avoid complex inheritance hierarchies and interface conflicts
 * while still providing the benefits of standardized testing.
 *
 * <p>This class provides static utility methods for common testing scenarios:
 *
 * <ul>
 *   <li>Complete test suites that validate all standard operations in one call
 *   <li>Individual test components for specific validation needs
 *   <li>Common test data generators and utility functions
 *   <li>Exception propagation testing utilities
 *   <li>Monad law testing with built-in error validation
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * // Complete monad test in one line
 * runCompleteMonadTestSuite(monad, "TestMonad", validKind, testValue,
 *     validMapper, validFlatMapper, validFunctionKind, testFunction,
 *     chainFunction, equalityChecker, typeName);
 *
 * // Or use individual components
 * testAllMonadNullValidations(monad, validKind, validMapper, validFlatMapper, validFunctionKind);
 * testMonadExceptionPropagation(monad, validInput, testException);
 * }</pre>
 */
public final class HKTTestHelpers {

  private HKTTestHelpers() {
    throw new AssertionError("HKTTestHelpers is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Simple Test Methods for Common Scenarios
  // =============================================================================

  /**
   * Test all standard monad null parameter validations in a single call.
   *
   * <p>This method validates that all monad operations (map, flatMap, ap) properly throw
   * NullPointerException with standardized error messages when passed null function or Kind
   * parameters.
   *
   * @param monad The monad instance to test
   * @param validKind A valid Kind instance for testing
   * @param validMapper A valid mapping function (Integer -> String)
   * @param validFlatMapper A valid flatMapping function (Integer -> Kind<F, String>)
   * @param validFunctionKind A valid function wrapped in the monad context
   * @param <F> The monad witness type
   */
  public static <F> void testAllMonadNullValidations(
      Monad<F> monad,
      Kind<F, Integer> validKind,
      Function<Integer, String> validMapper,
      Function<Integer, Kind<F, String>> validFlatMapper,
      Kind<F, Function<Integer, String>> validFunctionKind) {

    ValidationTestBuilder.create()
        // Map validations
        .assertNullFunction(() -> monad.map(null, validKind), "function f for map")
        .assertNullKind(() -> monad.map(validMapper, null), "source Kind for map")
        // FlatMap validations
        .assertNullFunction(() -> monad.flatMap(null, validKind), "function f for flatMap")
        .assertNullKind(() -> monad.flatMap(validFlatMapper, null), "source Kind for flatMap")
        // Ap validations
        .assertNullKind(() -> monad.ap(null, validKind), "function Kind for ap")
        .assertNullKind(() -> monad.ap(validFunctionKind, null), "argument Kind for ap")
        .execute();
  }

  /**
   * Test basic monad operations work correctly (without null parameter testing).
   *
   * <p>This method verifies that the basic monad operations execute successfully and return
   * non-null results when given valid inputs.
   *
   * @param monad The monad instance to test
   * @param validInput A valid input Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMapping function
   * @param validFunctionKind A valid function Kind for ap operation
   * @param <F> The monad witness type
   */
  public static <F> void testBasicMonadOperations(
      Monad<F> monad,
      Kind<F, Integer> validInput,
      Function<Integer, String> validMapper,
      Function<Integer, Kind<F, String>> validFlatMapper,
      Kind<F, Function<Integer, String>> validFunctionKind) {

    // Test that operations return non-null results
    org.assertj.core.api.Assertions.assertThat(monad.map(validMapper, validInput))
        .as("map should return non-null result")
        .isNotNull();
    org.assertj.core.api.Assertions.assertThat(monad.flatMap(validFlatMapper, validInput))
        .as("flatMap should return non-null result")
        .isNotNull();
    org.assertj.core.api.Assertions.assertThat(monad.ap(validFunctionKind, validInput))
        .as("ap should return non-null result")
        .isNotNull();
  }

  /**
   * Test standard KindHelper operations (widen/narrow) with error handling.
   *
   * <p>This method tests that KindHelper widen/narrow operations work correctly for valid inputs
   * and throw appropriate exceptions for invalid inputs.
   *
   * @param validInstance A valid instance of the concrete type
   * @param typeName The name of the type for error messages
   * @param invalidKind An invalid Kind instance for testing type errors
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testStandardKindHelperOperations(
      T validInstance,
      String typeName,
      Kind<F, A> invalidKind,
      java.util.function.Function<T, Kind<F, A>> widenFunc,
      java.util.function.Function<Kind<F, A>, T> narrowFunc) {

    // Test successful round-trip
    Kind<F, A> widened = widenFunc.apply(validInstance);
    T narrowed = narrowFunc.apply(widened);
    org.assertj.core.api.Assertions.assertThat(narrowed)
        .as("Round-trip widen/narrow should preserve identity")
        .isSameAs(validInstance);

    // Test error conditions
    ValidationTestBuilder.create()
        .assertNullWidenInput(() -> widenFunc.apply(null), typeName)
        .assertNullKindNarrow(() -> narrowFunc.apply(null), typeName)
        .assertInvalidKindType(() -> narrowFunc.apply(invalidKind), typeName, invalidKind)
        .execute();
  }

  /**
   * Test exception propagation for monad operations.
   *
   * <p>This method verifies that exceptions thrown by mapper functions are properly propagated
   * through monad operations (map and flatMap).
   *
   * @param monad The monad instance to test
   * @param validInput A valid input Kind for testing
   * @param testException The exception to test propagation with
   * @param <F> The monad witness type
   */
  public static <F> void testMonadExceptionPropagation(
      Monad<F> monad, Kind<F, Integer> validInput, RuntimeException testException) {

    // Test map exception propagation
    Function<Integer, String> throwingMapper =
        i -> {
          throw testException;
        };
    assertThatThrownBy(() -> monad.map(throwingMapper, validInput))
        .as("map should propagate function exceptions")
        .isInstanceOf(RuntimeException.class)
        .isSameAs(testException);

    // Test flatMap exception propagation
    Function<Integer, Kind<F, String>> throwingFlatMapper =
        i -> {
          throw testException;
        };
    assertThatThrownBy(() -> monad.flatMap(throwingFlatMapper, validInput))
        .as("flatMap should propagate function exceptions")
        .isInstanceOf(RuntimeException.class)
        .isSameAs(testException);
  }

  /**
   * Test that flatMap properly validates that functions don't return null Kinds.
   *
   * <p>This method verifies that when a flatMap function returns null, the appropriate
   * KindUnwrapException is thrown with standardized error message.
   *
   * @param monad The monad instance to test
   * @param validInput A valid input Kind for testing
   * @param typeName The name of the concrete type for error messages
   * @param <F> The monad witness type
   */
  public static <F> void testFlatMapNullKindValidation(
      Monad<F> monad, Kind<F, Integer> validInput, String typeName) {

    Function<Integer, Kind<F, String>> nullReturningMapper = i -> null;
    assertNullKindNarrowThrows(() -> monad.flatMap(nullReturningMapper, validInput), typeName);
  }

  // =============================================================================
  // Monad Law Testing Helpers
  // =============================================================================

  /**
   * Test monad left identity law with error validation.
   *
   * <p>Tests the law: flatMap(of(a), f) == f(a)
   *
   * <p>Also validates that null parameters are properly rejected.
   *
   * @param monad The monad instance to test
   * @param testValue The test value for the law
   * @param testFunction The test function for the law
   * @param equalityChecker Function to check equality of Kind instances
   * @param <F> The monad witness type
   */
  public static <F> void testLeftIdentityLaw(
      Monad<F> monad,
      Integer testValue,
      Function<Integer, Kind<F, String>> testFunction,
      java.util.function.BiPredicate<Kind<F, String>, Kind<F, String>> equalityChecker) {

    // Test the actual law
    Kind<F, Integer> ofValue = monad.of(testValue);
    Kind<F, String> leftSide = monad.flatMap(testFunction, ofValue);
    Kind<F, String> rightSide = testFunction.apply(testValue);

    org.assertj.core.api.Assertions.assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Left identity law should hold: flatMap(of(a), f) == f(a)")
        .isTrue();

    // Test error conditions
    ValidationTestBuilder.create()
        .assertNullFunction(() -> monad.flatMap(null, ofValue), "function f for flatMap")
        .assertNullKind(() -> monad.flatMap(testFunction, null), "source Kind for flatMap")
        .execute();
  }

  /**
   * Test monad right identity law with error validation.
   *
   * <p>Tests the law: flatMap(m, of) == m
   *
   * <p>Also validates that null parameters are properly rejected.
   *
   * @param monad The monad instance to test
   * @param testKind The test Kind for the law
   * @param equalityChecker Function to check equality of Kind instances
   * @param <F> The monad witness type
   */
  public static <F> void testRightIdentityLaw(
      Monad<F> monad,
      Kind<F, Integer> testKind,
      java.util.function.BiPredicate<Kind<F, Integer>, Kind<F, Integer>> equalityChecker) {

    Function<Integer, Kind<F, Integer>> ofFunc = monad::of;

    // Test the actual law
    Kind<F, Integer> leftSide = monad.flatMap(ofFunc, testKind);

    org.assertj.core.api.Assertions.assertThat(equalityChecker.test(leftSide, testKind))
        .as("Right identity law should hold: flatMap(m, of) == m")
        .isTrue();

    // Test error conditions
    ValidationTestBuilder.create()
        .assertNullFunction(() -> monad.flatMap(null, testKind), "function f for flatMap")
        .assertNullKind(() -> monad.flatMap(ofFunc, null), "source Kind for flatMap")
        .execute();
  }

  /**
   * Test monad associativity law with error validation.
   *
   * <p>Tests the law: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))
   *
   * <p>Also validates that null parameters are properly rejected.
   *
   * @param monad The monad instance to test
   * @param testKind The test Kind for the law
   * @param f The first function for the law
   * @param g The second function for the law
   * @param equalityChecker Function to check equality of Kind instances
   * @param <F> The monad witness type
   */
  public static <F> void testAssociativityLaw(
      Monad<F> monad,
      Kind<F, Integer> testKind,
      Function<Integer, Kind<F, String>> f,
      Function<String, Kind<F, String>> g,
      java.util.function.BiPredicate<Kind<F, String>, Kind<F, String>> equalityChecker) {

    // Test the actual law: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))
    Kind<F, String> innerFlatMap = monad.flatMap(f, testKind);
    Kind<F, String> leftSide = monad.flatMap(g, innerFlatMap);

    Function<Integer, Kind<F, String>> rightSideFunc = a -> monad.flatMap(g, f.apply(a));
    Kind<F, String> rightSide = monad.flatMap(rightSideFunc, testKind);

    org.assertj.core.api.Assertions.assertThat(equalityChecker.test(leftSide, rightSide))
        .as(
            "Associativity law should hold: flatMap(flatMap(m, f), g) == flatMap(m, a ->"
                + " flatMap(f(a), g))")
        .isTrue();

    // Test error conditions - NOTE: All flatMap calls use "function f" in error messages
    // This is because that's the parameter name in the flatMap method signature
    // The test was incorrectly expecting "function g" but that's not what the actual implementation
    // reports
    ValidationTestBuilder.create()
        .assertNullFunction(() -> monad.flatMap(null, testKind), "function f for flatMap")
        .assertNullKind(() -> monad.flatMap(f, null), "source Kind for flatMap")
        .assertNullKind(() -> monad.flatMap(g, null), "source Kind for flatMap")
        // When testing the null function parameter, flatMap always reports "function f" regardless
        // of which function variable we pass, because "f" is the parameter name in the method
        // signature
        .execute();
  }

  // =============================================================================
  // Convenience Methods for Creating Test Data
  // =============================================================================

  /**
   * Creates a dummy Kind implementation for testing invalid type errors.
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
   * Creates a RuntimeException for testing error propagation.
   *
   * @param message The exception message
   * @return A RuntimeException with the given message
   */
  public static RuntimeException createTestException(String message) {
    return new RuntimeException("Test exception: " + message);
  }

  /**
   * Common test functions for use in monad testing.
   *
   * <p>Provides standard functions that are commonly needed in tests, reducing boilerplate and
   * ensuring consistency across test suites.
   */
  public static class CommonTestFunctions {

    /** Converts Integer to String using toString() */
    public static final Function<Integer, String> INT_TO_STRING = Object::toString;

    /** Appends "_test" suffix to strings */
    public static final Function<String, String> APPEND_SUFFIX = s -> s + "_test";

    /** Multiplies integers by 2 */
    public static final Function<Integer, Integer> MULTIPLY_BY_2 = i -> i * 2;

    /**
     * Creates a function that throws the given exception.
     *
     * @param exception The exception to throw
     * @param <A> The input type
     * @param <B> The output type
     * @return A function that always throws the given exception
     */
    public static <A, B> Function<A, B> throwingFunction(RuntimeException exception) {
      return a -> {
        throw exception;
      };
    }

    /**
     * Creates a function that returns null (for testing null handling).
     *
     * @param <A> The input type
     * @param <B> The output type
     * @return A function that always returns null
     */
    public static <A, B> Function<A, B> nullReturningFunction() {
      return a -> null;
    }
  }

  // =============================================================================
  // Complete Test Patterns
  // =============================================================================

  /**
   * Runs a comprehensive test suite for a Monad implementation.
   *
   * <p>This method executes a complete test suite including:
   *
   * <ul>
   *   <li>Basic operation validation
   *   <li>Null parameter validation
   *   <li>Exception propagation testing
   *   <li>FlatMap null Kind validation
   *   <li>All three monad laws with error validation
   * </ul>
   *
   * @param monad The monad instance to test
   * @param monadName The name of the monad for documentation
   * @param validKind A valid Kind instance for testing
   * @param testValue A test value for law testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMapping function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param testFunction A test function for law testing
   * @param chainFunction A chaining function for associativity law
   * @param equalityChecker Function to check equality of Kind instances
   * @param typeName The name of the concrete type for error messages
   * @param <F> The monad witness type
   */
  public static <F> void runCompleteMonadTestSuite(
      Monad<F> monad,
      String monadName,
      Kind<F, Integer> validKind,
      Integer testValue,
      Function<Integer, String> validMapper,
      Function<Integer, Kind<F, String>> validFlatMapper,
      Kind<F, Function<Integer, String>> validFunctionKind,
      Function<Integer, Kind<F, String>> testFunction,
      Function<String, Kind<F, String>> chainFunction,
      java.util.function.BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker,
      String typeName) {

    // Test basic operations
    testBasicMonadOperations(monad, validKind, validMapper, validFlatMapper, validFunctionKind);

    // Test null parameter validations
    testAllMonadNullValidations(monad, validKind, validMapper, validFlatMapper, validFunctionKind);

    // Test exception propagation
    RuntimeException testException = createTestException(monadName + " test");
    testMonadExceptionPropagation(monad, validKind, testException);

    // Test flatMap null Kind validation
    testFlatMapNullKindValidation(monad, validKind, typeName);

    // Test monad laws
    testLeftIdentityLaw(monad, testValue, testFunction, equalityChecker::test);
    testRightIdentityLaw(monad, validKind, equalityChecker::test);
    testAssociativityLaw(monad, validKind, testFunction, chainFunction, equalityChecker::test);
  }

  /**
   * Runs a comprehensive test suite for a KindHelper implementation.
   *
   * <p>This method executes a complete test suite including:
   *
   * <ul>
   *   <li>Standard widen/narrow operations
   *   <li>Error condition validation
   *   <li>Round-trip idempotency testing
   * </ul>
   *
   * @param validInstance A valid instance of the concrete type
   * @param typeName The name of the type for error messages
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void runCompleteKindHelperTestSuite(
      T validInstance,
      String typeName,
      java.util.function.Function<T, Kind<F, A>> widenFunc,
      java.util.function.Function<Kind<F, A>, T> narrowFunc) {

    Kind<F, A> invalidKind = createDummyKind("invalid_" + typeName);

    // Test standard operations with error handling
    testStandardKindHelperOperations(validInstance, typeName, invalidKind, widenFunc, narrowFunc);

    // Test multiple round-trips for idempotency
    T current = validInstance;
    for (int i = 0; i < 3; i++) {
      Kind<F, A> widened = widenFunc.apply(current);
      current = narrowFunc.apply(widened);
    }
    org.assertj.core.api.Assertions.assertThat(current)
        .as("Multiple round-trips should preserve identity")
        .isSameAs(validInstance);
  }
}
