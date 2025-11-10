// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

/**
 * Centralized error handling utility for Higher-Kinded-J operations.
 *
 * <p>This utility provides standardised error handling patterns across the library, particularly
 * for Kind unwrapping operations, null validation, and error message consistency. It helps reduce
 * code duplication and ensures consistent error reporting throughout the library.
 *
 * <p>The utility follows these design principles:
 *
 * <ul>
 *   <li>Fail-fast with clear, descriptive error messages
 *   <li>Consistent exception types for similar error conditions
 *   <li>Null-safe operations with appropriate annotations
 *   <li>Lazy error message evaluation for performance
 * </ul>
 */
@Deprecated
public final class ErrorHandling {

  // Prevent instantiation
  private ErrorHandling() {
    throw new AssertionError("ErrorHandling is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Standard Error Messages
  // =============================================================================

  /** Standard error message template for null Kind instances. */
  public static final String NULL_KIND_TEMPLATE = "Cannot narrow null Kind for %s";

  /** Standard error message template for null inputs to widen operations. */
  public static final String NULL_WIDEN_INPUT_TEMPLATE = "Input %s cannot be null for widen";
}
