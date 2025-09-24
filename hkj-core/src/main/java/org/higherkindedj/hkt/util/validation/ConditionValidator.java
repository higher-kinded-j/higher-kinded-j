// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import org.higherkindedj.hkt.util.context.ConditionContext;

/**
 * Handles condition and range validations.
 *
 * <p>This validator provides flexible condition checking with rich error messaging and supports
 * various comparable types for range validation.
 */
public final class ConditionValidator {

  private ConditionValidator() {
    throw new AssertionError(
        "ConditionValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates a boolean condition with formatted message.
   *
   * @param condition The condition to check
   * @param message The error message template
   * @param args The arguments for message formatting
   * @throws IllegalArgumentException if condition is false
   */
  public static void require(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  /**
   * Validates that a value is within the specified range.
   *
   * @param value The value to validate
   * @param min The minimum value (inclusive)
   * @param max The maximum value (inclusive)
   * @param parameterName The parameter name for error messaging
   * @param <T> The comparable type
   * @return The validated value
   * @throws NullPointerException if value, min, or max is null
   * @throws IllegalArgumentException if value is out of range
   */
  public static <T extends Comparable<T>> T requireInRange(
      T value, T min, T max, String parameterName) {

    var context = ConditionContext.range(parameterName);

    Objects.requireNonNull(value, context.nullParameterMessage());
    Objects.requireNonNull(min, "min value cannot be null");
    Objects.requireNonNull(max, "max value cannot be null");

    if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      throw new IllegalArgumentException(context.rangeMessage(value, min, max));
    }

    return value;
  }

  /**
   * Validates that a value is greater than a threshold.
   *
   * @param value The value to validate
   * @param threshold The threshold (exclusive)
   * @param parameterName The parameter name for error messaging
   * @param <T> The comparable type
   * @return The validated value
   * @throws NullPointerException if value or threshold is null
   * @throws IllegalArgumentException if value is not greater than threshold
   */
  public static <T extends Comparable<T>> T requireGreaterThan(
      T value, T threshold, String parameterName) {

    var context = ConditionContext.custom(parameterName, "greater than validation");

    Objects.requireNonNull(value, context.nullParameterMessage());
    Objects.requireNonNull(threshold, "threshold cannot be null");

    if (value.compareTo(threshold) <= 0) {
      throw new IllegalArgumentException(
          context.customMessage(
              "%s must be greater than %s, got %s", parameterName, threshold, value));
    }

    return value;
  }

  /**
   * Validates that a value is non-negative.
   *
   * @param value The numeric value to validate
   * @param parameterName The parameter name for error messaging
   * @return The validated value
   * @throws NullPointerException if value is null
   * @throws IllegalArgumentException if value is negative
   */
  public static Integer requireNonNegative(Integer value, String parameterName) {
    var context = ConditionContext.custom(parameterName, "non-negative validation");

    Objects.requireNonNull(value, context.nullParameterMessage());

    if (value < 0) {
      throw new IllegalArgumentException(
          context.customMessage("%s must be non-negative, got %d", parameterName, value));
    }

    return value;
  }

  /**
   * Validates that a value is positive.
   *
   * @param value The numeric value to validate
   * @param parameterName The parameter name for error messaging
   * @return The validated value
   * @throws NullPointerException if value is null
   * @throws IllegalArgumentException if value is not positive
   */
  public static Integer requirePositive(Integer value, String parameterName) {
    var context = ConditionContext.custom(parameterName, "positive validation");

    Objects.requireNonNull(value, context.nullParameterMessage());

    if (value <= 0) {
      throw new IllegalArgumentException(
          context.customMessage("%s must be positive, got %d", parameterName, value));
    }

    return value;
  }
}
