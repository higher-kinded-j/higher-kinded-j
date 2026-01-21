// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.Locale;

/**
 * Shared utility methods for annotation processors in the optics module.
 *
 * <p>This class provides common string manipulation utilities used across multiple processors.
 */
public final class ProcessorUtils {

  private ProcessorUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts a string to camelCase.
   *
   * <p>Handles various input formats:
   *
   * <ul>
   *   <li>SNAKE_CASE: "MY_CONSTANT" → "myConstant"
   *   <li>ALL_CAPS: "MONDAY" → "monday"
   *   <li>PascalCase: "MyClass" → "myClass"
   *   <li>Already camelCase: "myMethod" → "myMethod"
   * </ul>
   *
   * @param s the string to convert
   * @return the camelCase version of the string
   */
  public static String toCamelCase(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }

    // Handle SNAKE_CASE (with underscores)
    if (s.contains("_")) {
      String[] parts = s.split("_");
      StringBuilder camelCaseString = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
      for (int i = 1; i < parts.length; i++) {
        if (!parts[i].isEmpty()) {
          camelCaseString
              .append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT))
              .append(parts[i].substring(1).toLowerCase(Locale.ROOT));
        }
      }
      return camelCaseString.toString();
    }

    // Handle ALL_CAPS (no underscores but all uppercase letters)
    if (isAllUpperCase(s)) {
      return s.toLowerCase(Locale.ROOT);
    }

    // Handle PascalCase
    if (Character.isUpperCase(s.charAt(0))) {
      return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    return s;
  }

  /**
   * Checks if a string contains only uppercase letters.
   *
   * <p>Non-letter characters are ignored in the check.
   *
   * @param s the string to check
   * @return true if all letter characters are uppercase, false otherwise
   */
  public static boolean isAllUpperCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isLetter(c) && !Character.isUpperCase(c)) {
        return false;
      }
    }
    return true;
  }
}
