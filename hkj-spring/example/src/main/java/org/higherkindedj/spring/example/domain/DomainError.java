// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.domain;

/**
 * Base interface for domain errors. Errors are modeled as sealed types for exhaustive pattern
 * matching.
 */
public sealed interface DomainError permits UserNotFoundError, ValidationError {
  String message();
}
