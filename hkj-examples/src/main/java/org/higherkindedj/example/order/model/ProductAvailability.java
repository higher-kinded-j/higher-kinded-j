// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.example.order.model.value.ProductId;

/**
 * Availability status for a product in inventory.
 *
 * @param productId the product identifier
 * @param requestedQty the quantity requested
 * @param availableQty the quantity available
 * @param warehouseId the warehouse where stock is located (if available)
 */
public record ProductAvailability(
    ProductId productId, int requestedQty, int availableQty, String warehouseId) {
  /**
   * Checks if the full requested quantity is available.
   *
   * @return true if available quantity meets or exceeds requested quantity
   */
  public boolean isAvailable() {
    return availableQty >= requestedQty;
  }

  /**
   * Checks if any quantity is available.
   *
   * @return true if at least some stock is available
   */
  public boolean isPartiallyAvailable() {
    return availableQty > 0 && availableQty < requestedQty;
  }

  /**
   * Returns the shortage amount.
   *
   * @return the quantity that cannot be fulfilled, or 0 if fully available
   */
  public int shortageQty() {
    return Math.max(0, requestedQty - availableQty);
  }

  /**
   * Creates an availability record for a fully available product.
   *
   * @param productId the product identifier
   * @param qty the quantity (both requested and available)
   * @param warehouseId the warehouse location
   * @return a ProductAvailability indicating full availability
   */
  public static ProductAvailability available(ProductId productId, int qty, String warehouseId) {
    return new ProductAvailability(productId, qty, qty, warehouseId);
  }

  /**
   * Creates an availability record for an unavailable product.
   *
   * @param productId the product identifier
   * @param requestedQty the quantity requested
   * @return a ProductAvailability indicating unavailability
   */
  public static ProductAvailability unavailable(ProductId productId, int requestedQty) {
    return new ProductAvailability(productId, requestedQty, 0, "NONE");
  }

  /**
   * Creates an availability record for a partially available product.
   *
   * @param productId the product identifier
   * @param requestedQty the quantity requested
   * @param availableQty the quantity available
   * @param warehouseId the warehouse location
   * @return a ProductAvailability indicating partial availability
   */
  public static ProductAvailability partial(
      ProductId productId, int requestedQty, int availableQty, String warehouseId) {
    return new ProductAvailability(productId, requestedQty, availableQty, warehouseId);
  }
}
