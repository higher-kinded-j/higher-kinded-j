package org.higherkindedj.spring.example.domain;

/**
 * Error indicating that a user was not found.
 * The error class name contains "NotFound" so the EitherReturnValueHandler
 * will automatically return HTTP 404.
 */
public record UserNotFoundError(String userId) implements DomainError {
  @Override
  public String message() {
    return "User not found: " + userId;
  }
}
