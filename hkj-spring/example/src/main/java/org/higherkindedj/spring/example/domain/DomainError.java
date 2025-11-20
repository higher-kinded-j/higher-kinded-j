package org.higherkindedj.spring.example.domain;

/**
 * Base interface for domain errors.
 * Errors are modeled as sealed types for exhaustive pattern matching.
 */
public sealed interface DomainError
    permits UserNotFoundError, ValidationError {
  String message();
}
