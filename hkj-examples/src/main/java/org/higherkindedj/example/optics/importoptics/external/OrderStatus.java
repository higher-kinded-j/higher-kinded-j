// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/**
 * External enum used to demonstrate {@code @ImportOptics} with enums.
 *
 * <p>This simulates an external library enum that cannot be annotated directly.
 */
public enum OrderStatus {
  PENDING,
  CONFIRMED,
  PROCESSING,
  SHIPPED,
  DELIVERED,
  CANCELLED
}
