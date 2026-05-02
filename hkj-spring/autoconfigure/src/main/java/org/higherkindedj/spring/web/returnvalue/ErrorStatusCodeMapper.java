// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Utility for mapping error types to HTTP status codes.
 *
 * <p>Resolution order applied by {@link #determineStatusCode(Object, int, Map)}:
 *
 * <ol>
 *   <li>Explicit override from {@code errorStatusMappings} keyed by the error's simple class name
 *       (preferred, stable across refactors of the package layout).
 *   <li>Explicit override keyed by the error's fully-qualified class name (useful when two classes
 *       share a simple name across packages).
 *   <li>Built-in token-aware heuristics on the simple class name.
 * </ol>
 *
 * <p>The heuristics are applied to a tokenised form of the simple class name (CamelCase split on
 * upper-case boundaries, then lower-cased and bracketed with separators). This means {@code
 * RevalidationError} no longer matches the {@code validation} rule, while {@code MfaNotFoundError}
 * still matches {@code NotFound}. Adopters who need anything beyond this should configure {@link
 * ErrorStatusCodeStrategy} as a Spring bean.
 *
 * @see ErrorStatusCodeStrategy
 * @see DefaultErrorStatusCodeStrategy
 */
public final class ErrorStatusCodeMapper {

  private ErrorStatusCodeMapper() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Backward-compatible overload. Equivalent to {@link #determineStatusCode(Object, int, Map)} with
   * an empty mapping table.
   *
   * @param error the error object
   * @param defaultStatus the default status code if no rule matches
   * @return the resolved HTTP status code
   */
  public static int determineStatusCode(Object error, int defaultStatus) {
    return determineStatusCode(error, defaultStatus, Map.of());
  }

  /**
   * Resolves the HTTP status code for an error value.
   *
   * @param error the error object (must not be {@code null})
   * @param defaultStatus the default status code if no rule matches
   * @param errorStatusMappings explicit overrides keyed by simple or fully-qualified class name;
   *     {@code null} is treated as an empty map
   * @return the resolved HTTP status code
   */
  public static int determineStatusCode(
      Object error, int defaultStatus, @Nullable Map<String, Integer> errorStatusMappings) {
    Class<?> klass = error.getClass();
    if (errorStatusMappings != null && !errorStatusMappings.isEmpty()) {
      Integer simple = errorStatusMappings.get(klass.getSimpleName());
      if (simple != null) {
        return simple;
      }
      Integer qualified = errorStatusMappings.get(klass.getName());
      if (qualified != null) {
        return qualified;
      }
    }
    return heuristicStatus(klass.getSimpleName(), defaultStatus);
  }

  /**
   * Returns the status code suggested by the built-in heuristics for the given simple class name,
   * or {@code defaultStatus} if no rule matches. Exposed as a static helper so custom {@link
   * ErrorStatusCodeStrategy} implementations can delegate to the same logic.
   *
   * @param simpleName the simple class name of the error
   * @param defaultStatus the default status code if no rule matches
   * @return the resolved HTTP status code
   */
  public static int heuristicStatus(String simpleName, int defaultStatus) {
    String tokens = tokenize(simpleName);
    if (tokens.contains("-not-found-")) {
      return HttpStatus.NOT_FOUND.value();
    }
    if (tokens.contains("-validation-") || tokens.contains("-invalid-")) {
      return HttpStatus.BAD_REQUEST.value();
    }
    if (tokens.contains("-authorization-") || tokens.contains("-forbidden-")) {
      return HttpStatus.FORBIDDEN.value();
    }
    if (tokens.contains("-authentication-") || tokens.contains("-unauthorized-")) {
      return HttpStatus.UNAUTHORIZED.value();
    }
    return defaultStatus;
  }

  /**
   * Tokenises a CamelCase name into {@code -lower-case-token-} form so that callers can perform
   * word-boundary substring matches without having to roll their own scanner.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code "MfaNotFoundError"} → {@code "-mfa-not-found-error-"}
   *   <li>{@code "RevalidationError"} → {@code "-revalidation-error-"}
   *   <li>{@code ""} → {@code "-"}
   * </ul>
   *
   * @param simpleName the CamelCase name to tokenise
   * @return the tokenised form bracketed with separators
   */
  static String tokenize(String simpleName) {
    if (simpleName == null || simpleName.isEmpty()) {
      return "-";
    }
    StringBuilder sb = new StringBuilder(simpleName.length() + 8);
    sb.append('-');
    for (int i = 0; i < simpleName.length(); i++) {
      char c = simpleName.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(simpleName.charAt(i - 1))) {
        sb.append('-');
      }
      sb.append(Character.toLowerCase(c));
    }
    sb.append('-');
    return sb.toString();
  }
}
