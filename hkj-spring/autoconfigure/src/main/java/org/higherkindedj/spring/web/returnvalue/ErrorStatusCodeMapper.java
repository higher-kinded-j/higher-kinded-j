// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import org.springframework.http.HttpStatus;

/**
 * Utility class for mapping error types to HTTP status codes.
 *
 * <p>Uses simple heuristics based on error class names to determine appropriate HTTP status codes.
 * This provides a sensible default mapping for common error types without requiring explicit
 * configuration.
 */
public final class ErrorStatusCodeMapper {

  private ErrorStatusCodeMapper() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Determines the appropriate HTTP status code based on the error type.
   *
   * <p>Uses simple heuristics based on the error class name:
   *
   * <ul>
   *   <li>*NotFound* → 404
   *   <li>*Validation*, *Invalid* → 400
   *   <li>*Authorization*, *Forbidden* → 403
   *   <li>*Authentication*, *Unauthorized* → 401
   *   <li>default → provided default status
   * </ul>
   *
   * @param error the error object
   * @param defaultStatus the default status code to use if no pattern matches
   * @return the HTTP status code
   */
  public static int determineStatusCode(Object error, int defaultStatus) {
    String errorClassName = error.getClass().getSimpleName().toLowerCase();

    if (errorClassName.contains("notfound")) {
      return HttpStatus.NOT_FOUND.value();
    } else if (errorClassName.contains("validation") || errorClassName.contains("invalid")) {
      return HttpStatus.BAD_REQUEST.value();
    } else if (errorClassName.contains("authorization") || errorClassName.contains("forbidden")) {
      return HttpStatus.FORBIDDEN.value();
    } else if (errorClassName.contains("authentication")
        || errorClassName.contains("unauthorized")) {
      return HttpStatus.UNAUTHORIZED.value();
    }

    return defaultStatus;
  }
}
