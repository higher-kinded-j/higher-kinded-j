// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.jspecify.annotations.Nullable;

/**
 * Information about a created shipment.
 *
 * @param shipmentId unique identifier for this shipment
 * @param trackingNumber carrier tracking number
 * @param carrier the shipping carrier (e.g., "ROYAL_MAIL", "DHL")
 * @param estimatedDelivery estimated delivery date and time
 * @param shippingCost the cost of shipping
 * @param warehouseId the warehouse this shipment originates from (for split shipments)
 */
@GenerateLenses
public record ShipmentInfo(
    String shipmentId,
    String trackingNumber,
    String carrier,
    Instant estimatedDelivery,
    Money shippingCost,
    @Nullable String warehouseId) {
  /**
   * Creates a ShipmentInfo without warehouse information.
   *
   * @param shipmentId the shipment ID
   * @param trackingNumber the tracking number
   * @param carrier the carrier
   * @param estimatedDelivery the estimated delivery
   * @param shippingCost the shipping cost
   * @return a ShipmentInfo
   */
  public static ShipmentInfo create(
      String shipmentId,
      String trackingNumber,
      String carrier,
      Instant estimatedDelivery,
      Money shippingCost) {
    return new ShipmentInfo(
        shipmentId, trackingNumber, carrier, estimatedDelivery, shippingCost, null);
  }

  /**
   * Creates a ShipmentInfo from a specific warehouse.
   *
   * @param shipmentId the shipment ID
   * @param trackingNumber the tracking number
   * @param carrier the carrier
   * @param estimatedDelivery the estimated delivery
   * @param shippingCost the shipping cost
   * @param warehouseId the warehouse ID
   * @return a ShipmentInfo
   */
  public static ShipmentInfo fromWarehouse(
      String shipmentId,
      String trackingNumber,
      String carrier,
      Instant estimatedDelivery,
      Money shippingCost,
      String warehouseId) {
    return new ShipmentInfo(
        shipmentId, trackingNumber, carrier, estimatedDelivery, shippingCost, warehouseId);
  }

  /**
   * Returns a copy with updated shipping cost.
   *
   * @param cost the new shipping cost
   * @return a new ShipmentInfo with the updated cost
   */
  public ShipmentInfo withShippingCost(Money cost) {
    return new ShipmentInfo(
        shipmentId, trackingNumber, carrier, estimatedDelivery, cost, warehouseId);
  }
}
