// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;

/**
 * Base class for Either type class tests.
 *
 * <p>Provides common fixture creation, standardised test constants, and helper methods for all
 * Either type class tests, eliminating duplication across Functor, Applicative, Monad, MonadError,
 * Foldable, and Traverse tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standardised test values are provided to ensure consistency across all Either tests:
 *
 * <ul>
 *   <li>{@link #DEFAULT_RIGHT_VALUE} - The primary test value (42)
 *   <li>{@link #ALTERNATIVE_RIGHT_VALUE} - A secondary test value (24)
 *   <li>{@link #DEFAULT_LEFT_VALUE} - The primary error value ("TEST_ERROR")
 *   <li>{@link TestErrorType} - Standardised error types for different test scenarios
 * </ul>
 */
abstract class EitherTestBase
    extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

  // ============================================================================
  // Test Constants - Standardised Values
  // ============================================================================

  /** Default value for Right instances in tests. */
  protected static final Integer DEFAULT_RIGHT_VALUE = 42;

  /** Alternative value for Right instances when testing with multiple values. */
  protected static final Integer ALTERNATIVE_RIGHT_VALUE = 24;

  /** Default value for Left instances in tests. */
  protected static final String DEFAULT_LEFT_VALUE = "TEST_ERROR";

  /**
   * Standardised error types for different test scenarios.
   *
   * <p>Using an enum ensures type safety and provides clear documentation of error categories used
   * in tests.
   */
  protected enum TestErrorType {
    /** Generic test error - use for basic error scenarios. */
    DEFAULT("TEST_ERROR"),

    /** First error in a sequence - use for testing error propagation. */
    ERROR_1("E1"),

    /** Second error in a sequence - use for testing error propagation. */
    ERROR_2("E2"),

    /** Error that can be recovered from - use for error handling tests. */
    RECOVERABLE("RECOVERABLE_ERROR"),

    /** Error that cannot be recovered from - use for error handling tests. */
    UNRECOVERABLE("UNRECOVERABLE_ERROR"),

    /** Validation failure error - use for validation pipeline tests. */
    VALIDATION("VALIDATION_ERROR"),

    /** Resource unavailable error - use for resource management tests. */
    RESOURCE_UNAVAILABLE("RESOURCE_UNAVAILABLE"),

    /** Function application error - use for function application tests. */
    FUNCTION_ERROR("FUNCTION_ERROR");

    private final String message;

    TestErrorType(String message) {
      this.message = message;
    }

    /**
     * Returns the error message for this error type.
     *
     * @return The error message
     */
    public String message() {
      return message;
    }

    @Override
    public String toString() {
      return message;
    }
  }

  /**
   * Test record for complex error scenarios requiring structured error information.
   *
   * <p>Use this when testing with errors that need more than just a message string.
   */
  protected record ComplexTestError(String code, int severity, String message) {

    /** Creates a low severity error. */
    public static ComplexTestError low(String code, String message) {
      return new ComplexTestError(code, 1, message);
    }

    /** Creates a medium severity error. */
    public static ComplexTestError medium(String code, String message) {
      return new ComplexTestError(code, 5, message);
    }

    /** Creates a high severity error. */
    public static ComplexTestError high(String code, String message) {
      return new ComplexTestError(code, 10, message);
    }
  }

  /**
   * Creates a Right Kind with the specified value.
   *
   * @param value The value to wrap in a Right
   * @return A Right Kind containing the specified value
   */
  protected <R> Kind<EitherKind.Witness<String>, R> rightKind(R value) {
    return EITHER.widen(Either.right(value));
  }

  /**
   * Creates a Left Kind with the default test error.
   *
   * @return A Left Kind containing {@link #DEFAULT_LEFT_VALUE}
   */
  protected <R> Kind<EitherKind.Witness<String>, R> leftKind() {
    return EITHER.widen(Either.left(DEFAULT_LEFT_VALUE));
  }

  /**
   * Creates a Left Kind with the specified error type.
   *
   * @param errorType The error type
   * @return A Left Kind containing the error type's message
   */
  protected <R> Kind<EitherKind.Witness<String>, R> leftKind(TestErrorType errorType) {
    return EITHER.widen(Either.left(errorType.message()));
  }

  /**
   * Converts a Kind to an Either instance.
   *
   * <p>This is a convenience method to make test code more readable by avoiding repeated
   * EITHER.narrow() calls.
   *
   * @param <L> The type of the Left value
   * @param <R> The type of the Right value
   * @param kind The Kind to convert
   * @return The underlying Either instance
   */
  protected <L, R> Either<L, R> narrowToEither(Kind<EitherKind.Witness<L>, R> kind) {
    return EITHER.narrow(kind);
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind() {
    return EITHER.widen(Either.right(DEFAULT_RIGHT_VALUE));
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind2() {
    return rightKind(ALTERNATIVE_RIGHT_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createValidFlatMapper() {
    return i -> EITHER.widen(Either.right("flat:" + i));
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
    return EITHER.widen(Either.right(TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_RIGHT_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "Transformed:" + s;
  }

  @Override
  protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createTestFunction() {
    return i -> EITHER.widen(Either.right("test:" + i));
  }

  @Override
  protected Function<String, Kind<EitherKind.Witness<String>, String>> createChainFunction() {
    return s -> EITHER.widen(Either.right(s + "!"));
  }

  @Override
  protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }
}
