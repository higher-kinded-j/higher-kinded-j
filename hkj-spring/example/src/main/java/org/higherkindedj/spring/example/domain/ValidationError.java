// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.domain;

/**
 * Error indicating a validation failure. The error class name contains "Validation" so the
 * EitherReturnValueHandler will automatically return HTTP 400.
 */
public record ValidationError(String field, String message) implements DomainError {
  @Override
  public String message() {
    return "Validation error on field '" + field + "': " + message;
  }
}
