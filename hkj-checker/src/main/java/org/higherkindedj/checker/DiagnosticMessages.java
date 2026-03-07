// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import java.util.Optional;

/**
 * Centralised error message formatting for the HKJ checker.
 *
 * <p>This class provides consistent error messages across all check sites. Messages follow the
 * pattern: describe what was expected, what was received, and how to fix it.
 */
public final class DiagnosticMessages {

  private DiagnosticMessages() {}

  /**
   * Formats a Path type mismatch error message.
   *
   * @param methodName the method where the mismatch was detected (e.g., "via", "zipWith")
   * @param expectedType the simple name of the expected Path type (e.g., "MaybePath")
   * @param actualType the simple name of the actual Path type (e.g., "IOPath")
   * @return a formatted error message
   */
  public static String pathTypeMismatch(String methodName, String expectedType, String actualType) {
    String base =
        "Path type mismatch in %s(): expected %s but received %s. "
                .formatted(methodName, expectedType, actualType)
            + "Each Path type can only chain with the same type.";

    Optional<String> conversion = PathTypeRegistry.suggestedConversion(actualType, expectedType);
    if (conversion.isPresent()) {
      return base + " Use conversion methods like " + conversion.get() + " to convert.";
    }
    return base + " Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.";
  }
}
