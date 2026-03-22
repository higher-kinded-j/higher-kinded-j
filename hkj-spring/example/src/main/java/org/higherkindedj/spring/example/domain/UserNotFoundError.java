// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.domain;

/**
 * Error indicating that a user was not found. The error class name contains "NotFound" so the
 * EitherReturnValueHandler will automatically return HTTP 404.
 *
 * @param userId the ID of the user that was not found
 */
public record UserNotFoundError(String userId) implements DomainError {
  @Override
  public String message() {
    return "User not found: " + userId;
  }
}
