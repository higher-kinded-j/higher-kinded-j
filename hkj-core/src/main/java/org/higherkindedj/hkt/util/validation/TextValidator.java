// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;

/**
 * Handles string and text-based validations.
 *
 * <p>This validator provides common string validation operations with consistent error messaging
 * and support for various text-based constraints.
 */
public final class TextValidator {

  private TextValidator() {
    throw new AssertionError("TextValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates that a string is non-null and non-blank.
   *
   * @param text The string to validate
   * @param parameterName The parameter name for error messaging
   * @return The validated string
   * @throws NullPointerException if text is null
   * @throws IllegalArgumentException if text is blank
   */
  public static String requireNonBlank(String text, String parameterName) {
    Objects.requireNonNull(text, parameterName + " cannot be null");

    if (text.isBlank()) {
      throw new IllegalArgumentException(parameterName + " cannot be blank");
    }

    return text;
  }

  /**
   * Validates that a string length is within bounds.
   *
   * @param text The string to validate
   * @param minLength The minimum length (inclusive)
   * @param maxLength The maximum length (inclusive)
   * @param parameterName The parameter name for error messaging
   * @return The validated string
   * @throws NullPointerException if text is null
   * @throws IllegalArgumentException if length is out of bounds
   */
  public static String requireLengthInRange(
      String text, int minLength, int maxLength, String parameterName) {

    Objects.requireNonNull(text, parameterName + " cannot be null");

    int length = text.length();
    if (length < minLength || length > maxLength) {
      throw new IllegalArgumentException(
          "%s length must be between %d and %d, got %d"
              .formatted(parameterName, minLength, maxLength, length));
    }

    return text;
  }

  /**
   * Validates that a string matches a pattern.
   *
   * @param text The string to validate
   * @param pattern The regex pattern to match
   * @param parameterName The parameter name for error messaging
   * @return The validated string
   * @throws NullPointerException if text or pattern is null
   * @throws IllegalArgumentException if text doesn't match the pattern
   */
  public static String requireMatches(String text, String pattern, String parameterName) {
    Objects.requireNonNull(text, parameterName + " cannot be null");
    Objects.requireNonNull(pattern, "pattern cannot be null");

    if (!text.matches(pattern)) {
      throw new IllegalArgumentException(
          "%s must match pattern '%s', got: %s".formatted(parameterName, pattern, text));
    }

    return text;
  }

  /**
   * Validates that a string contains only allowed characters.
   *
   * @param text The string to validate
   * @param allowedChars The set of allowed characters
   * @param parameterName The parameter name for error messaging
   * @return The validated string
   * @throws NullPointerException if text or allowedChars is null
   * @throws IllegalArgumentException if text contains disallowed characters
   */
  public static String requireOnlyAllowedChars(
      String text, java.util.Set<Character> allowedChars, String parameterName) {

    Objects.requireNonNull(text, parameterName + " cannot be null");
    Objects.requireNonNull(allowedChars, "allowedChars cannot be null");

    for (char c : text.toCharArray()) {
      if (!allowedChars.contains(c)) {
        throw new IllegalArgumentException(
            "%s contains disallowed character '%c'".formatted(parameterName, c));
      }
    }

    return text;
  }
}
