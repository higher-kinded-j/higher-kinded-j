// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;

/**
 * Handles numeric validations beyond basic range checking.
 *
 * <p>This validator provides specialized numeric validations with appropriate error messaging for
 * various numeric constraints.
 */
public final class NumericValidator {

  private NumericValidator() {
    throw new AssertionError("NumericValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates that a number is finite (not infinite or NaN).
   *
   * @param value The double value to validate
   * @param parameterName The parameter name for error messaging
   * @return The validated value
   * @throws NullPointerException if value is null
   * @throws IllegalArgumentException if value is not finite
   */
  public static Double requireFinite(Double value, String parameterName) {
    Objects.requireNonNull(value, parameterName + " cannot be null");

    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(
          "%s must be finite, got %s".formatted(parameterName, value));
    }

    return value;
  }

  /**
   * Validates that a number is a multiple of another number.
   *
   * @param value The value to validate
   * @param multiple The number that value should be a multiple of
   * @param parameterName The parameter name for error messaging
   * @return The validated value
   * @throws NullPointerException if value or multiple is null
   * @throws IllegalArgumentException if value is not a multiple
   */
  public static Integer requireMultipleOf(Integer value, Integer multiple, String parameterName) {
    Objects.requireNonNull(value, parameterName + " cannot be null");
    Objects.requireNonNull(multiple, "multiple cannot be null");

    if (multiple == 0) {
      throw new IllegalArgumentException("multiple cannot be zero");
    }

    if (value % multiple != 0) {
      throw new IllegalArgumentException(
          "%s must be a multiple of %d, got %d".formatted(parameterName, multiple, value));
    }

    return value;
  }

  /**
   * Validates that a floating-point number is within tolerance of an expected value.
   *
   * @param actual The actual value
   * @param expected The expected value
   * @param tolerance The allowed tolerance
   * @param parameterName The parameter name for error messaging
   * @return The validated actual value
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if actual is not within tolerance of expected
   */
  public static Double requireWithinTolerance(
      Double actual, Double expected, Double tolerance, String parameterName) {

    Objects.requireNonNull(actual, parameterName + " cannot be null");
    Objects.requireNonNull(expected, "expected cannot be null");
    Objects.requireNonNull(tolerance, "tolerance cannot be null");

    double diff = Math.abs(actual - expected);
    if (diff > tolerance) {
      throw new IllegalArgumentException(
          "%s must be within %f of %f, got %f (difference: %f)"
              .formatted(parameterName, tolerance, expected, actual, diff));
    }

    return actual;
  }

  /**
   * Validates that a number is a power of two.
   *
   * @param value The value to validate
   * @param parameterName The parameter name for error messaging
   * @return The validated value
   * @throws NullPointerException if value is null
   * @throws IllegalArgumentException if value is not a power of two
   */
  public static Integer requirePowerOfTwo(Integer value, String parameterName) {
    Objects.requireNonNull(value, parameterName + " cannot be null");

    if (value <= 0 || (value & (value - 1)) != 0) {
      throw new IllegalArgumentException(
          "%s must be a power of two, got %d".formatted(parameterName, value));
    }

    return value;
  }
}
