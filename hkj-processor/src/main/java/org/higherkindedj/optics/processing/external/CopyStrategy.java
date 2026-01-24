// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

/**
 * Describes the strategy used to create modified copies of a type.
 *
 * <p>Different types require different approaches to creating updated copies:
 *
 * <ul>
 *   <li>Records use their canonical constructor
 *   <li>Wither-based classes use {@code withX()} methods
 *   <li>Builder-based classes use a builder pattern (Phase 2)
 * </ul>
 */
public enum CopyStrategy {
  /**
   * Use the record's canonical constructor to create copies.
   *
   * <p>This is the default strategy for Java records. All components are passed to the constructor
   * with the modified value substituted.
   */
  CANONICAL_CONSTRUCTOR,

  /**
   * Use wither methods ({@code withX()}) to create copies.
   *
   * <p>Each lens will call the corresponding wither method on the source object. This is suitable
   * for immutable classes like {@link java.time.LocalDate}.
   */
  WITHER,

  /**
   * Use a builder pattern to create copies (Phase 2).
   *
   * <p>This strategy requires a {@code toBuilder()} method or similar mechanism to create a builder
   * pre-populated with the current values.
   */
  BUILDER
}
