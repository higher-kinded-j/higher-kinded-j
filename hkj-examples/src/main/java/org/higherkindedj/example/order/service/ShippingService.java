// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import java.time.Instant;
import java.util.List;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/** Service for shipping operations. */
@GeneratePathBridge
public interface ShippingService {

  /**
   * Validates a shipping address.
   *
   * @param address the address to validate
   * @return either an error or the validated address with shipping zone
   */
  @PathVia(doc = "Validates shipping address and determines shipping zone")
  Either<OrderError, ValidatedShippingAddress> validateAddress(ShippingAddress address);

  /**
   * Creates a shipment for an order.
   *
   * @param orderId the order identifier
   * @param address the validated shipping address
   * @param lines the order lines to ship
   * @return either an error or the shipment information
   */
  @PathVia(doc = "Creates shipment and generates tracking number")
  Either<OrderError, ShipmentInfo> createShipment(
      OrderId orderId, ValidatedShippingAddress address, List<ValidatedOrderLine> lines);

  /**
   * Cancels a shipment.
   *
   * @param shipmentId the shipment to cancel
   * @return either an error or success
   */
  @PathVia(doc = "Cancels a shipment before dispatch")
  Either<OrderError, Void> cancelShipment(String shipmentId);

  /**
   * Gets the current status of a shipment.
   *
   * @param trackingNumber the tracking number
   * @return either an error or the shipment status
   */
  @PathVia(doc = "Retrieves current shipment status")
  Either<OrderError, ShipmentStatus> getShipmentStatus(String trackingNumber);

  /**
   * Creates a shipment from a specific warehouse for split shipment support.
   *
   * @param orderId the order ID
   * @param warehouse the warehouse shipping from
   * @param items the items to ship
   * @param address the shipping address
   * @return either an error or the created shipment info
   */
  @PathVia(doc = "Creates shipment from a specific warehouse")
  Either<OrderError, ShipmentInfo> createShipmentFromWarehouse(
      OrderId orderId,
      WarehouseInfo warehouse,
      List<InventoryReservation.ReservedItem> items,
      ValidatedShippingAddress address);

  /**
   * Shipment status.
   *
   * @param status the current status
   * @param location current location, if known
   * @param estimatedDelivery estimated delivery time
   */
  record ShipmentStatus(Status status, String location, Instant estimatedDelivery) {
    public enum Status {
      PENDING,
      PICKED,
      IN_TRANSIT,
      OUT_FOR_DELIVERY,
      DELIVERED,
      RETURNED,
      CANCELLED
    }
  }
}
