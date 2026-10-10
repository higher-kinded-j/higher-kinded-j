// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/**
 * A courier's SDK enum, simulated here: a guest from outside the order service. The courier owns
 * it, so this package imports its optics rather than annotating it, and its constants are the
 * courier's tracking states rather than the order service's {@code OrderStatus}.
 */
public enum TrackingStatus {
  PENDING,
  IN_TRANSIT,
  DELIVERED,
  RETURNED
}
