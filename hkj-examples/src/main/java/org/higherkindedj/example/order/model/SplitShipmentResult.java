// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Result of creating split shipments from multiple warehouses. Tracks all shipments and provides
 * aggregate information.
 *
 * @param orderId the order identifier
 * @param shipments list of individual shipments
 * @param latestEstimatedDelivery the latest estimated delivery across all shipments
 * @param totalShippingCost combined shipping cost for all shipments
 */
@GenerateLenses
public record SplitShipmentResult(
    OrderId orderId,
    List<ShipmentInfo> shipments,
    Instant latestEstimatedDelivery,
    Money totalShippingCost) {
  /**
   * Returns the number of shipments created.
   *
   * @return the shipment count
   */
  public int shipmentCount() {
    return shipments.size();
  }

  /**
   * Returns all tracking numbers for this order.
   *
   * @return list of tracking numbers
   */
  public List<String> allTrackingNumbers() {
    return shipments.stream().map(ShipmentInfo::trackingNumber).toList();
  }

  /**
   * Checks if this is actually a split shipment (multiple shipments).
   *
   * @return true if more than one shipment exists
   */
  public boolean isSplit() {
    return shipments.size() > 1;
  }

  /**
   * Returns the earliest shipment date.
   *
   * @return the earliest estimated delivery
   */
  public Instant earliestEstimatedDelivery() {
    return shipments.stream()
        .map(ShipmentInfo::estimatedDelivery)
        .min(Instant::compareTo)
        .orElse(Instant.now());
  }

  /**
   * Creates a single-shipment result (not actually split).
   *
   * @param orderId the order ID
   * @param shipment the single shipment
   * @return a SplitShipmentResult with one shipment
   */
  public static SplitShipmentResult single(OrderId orderId, ShipmentInfo shipment) {
    return new SplitShipmentResult(
        orderId, List.of(shipment), shipment.estimatedDelivery(), shipment.shippingCost());
  }

  /**
   * Creates a split shipment result from multiple shipments.
   *
   * @param orderId the order ID
   * @param shipments the list of shipments
   * @return a SplitShipmentResult
   */
  public static SplitShipmentResult fromShipments(OrderId orderId, List<ShipmentInfo> shipments) {
    var latestDelivery =
        shipments.stream()
            .map(ShipmentInfo::estimatedDelivery)
            .max(Instant::compareTo)
            .orElse(Instant.now());

    var totalCost =
        shipments.stream().map(ShipmentInfo::shippingCost).reduce(Money.ZERO_GBP, Money::add);

    return new SplitShipmentResult(orderId, shipments, latestDelivery, totalCost);
  }
}
