// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Result of reserving inventory for an order.
 *
 * @param reservationId unique identifier for this reservation
 * @param items the reserved items with quantities and locations
 * @param expiresAt when this reservation expires if not confirmed
 */
@GenerateLenses
public record InventoryReservation(
    String reservationId, List<ReservedItem> items, Instant expiresAt) {
  /**
   * An individual item reservation.
   *
   * @param productId the product reserved
   * @param quantity the quantity reserved
   * @param warehouseId the warehouse holding the stock
   */
  public record ReservedItem(ProductId productId, int quantity, String warehouseId) {}
}
