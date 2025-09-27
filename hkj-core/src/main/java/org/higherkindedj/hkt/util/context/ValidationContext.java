// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

/**
 * Base interface for validation contexts that provide standardized error messaging. Contexts carry
 * semantic information about what is being validated.
 */
public interface ValidationContext {
  /** Creates a standardized error message for null parameter validation. */
  String nullParameterMessage();

  /** Creates a standardized error message for null input validation. */
  String nullInputMessage();

  /** Creates a standardized error message with custom details. */
  String customMessage(String template, Object... args);
}
