// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.domain;

/**
 * Error indicating a validation failure. The error class name contains "Validation" so the
 * EitherReturnValueHandler will automatically return HTTP 400.
 *
 * @param field the field that failed validation
 * @param message the validation error message
 */
public record ValidationError(String field, String message) implements DomainError {
  @Override
  public String message() {
    return "Validation error on field '" + field + "': " + message;
  }
}
