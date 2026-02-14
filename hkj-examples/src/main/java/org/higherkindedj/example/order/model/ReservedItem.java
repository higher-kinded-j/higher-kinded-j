// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.ProductId;

/**
 * An item that has been reserved in inventory at a specific warehouse. Used for tracking which
 * items come from which warehouse for split shipments.
 *
 * @param productId the product identifier
 * @param quantity the quantity reserved
 * @param warehouseId the warehouse where the item is reserved
 * @param unitPrice the price per unit
 */
public record ReservedItem(ProductId productId, int quantity, String warehouseId, Money unitPrice) {
  /**
   * Creates a reserved item from availability information.
   *
   * @param availability the product availability
   * @param unitPrice the unit price
   * @return a ReservedItem for the available quantity
   */
  public static ReservedItem fromAvailability(ProductAvailability availability, Money unitPrice) {
    return new ReservedItem(
        availability.productId(),
        Math.min(availability.requestedQty(), availability.availableQty()),
        availability.warehouseId(),
        unitPrice);
  }

  /**
   * Calculates the total value of this reserved item.
   *
   * @return quantity multiplied by unit price
   */
  public Money totalValue() {
    return unitPrice.multiply(quantity);
  }
}
