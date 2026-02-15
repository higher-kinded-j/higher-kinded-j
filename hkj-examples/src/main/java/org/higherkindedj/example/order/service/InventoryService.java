// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import java.util.List;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.ProductAvailability;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/** Service for inventory operations. */
@GeneratePathBridge
public interface InventoryService {

  /**
   * Checks stock availability for order lines.
   *
   * @param lines the order lines to check
   * @return either an error or the availability check result
   */
  @PathVia(doc = "Checks stock availability for all order lines")
  Either<OrderError, InventoryCheckResult> checkAvailability(List<ValidatedOrderLine> lines);

  /**
   * Reserves inventory for an order.
   *
   * @param orderId the order identifier
   * @param lines the order lines to reserve
   * @return either an error or the reservation details
   */
  @PathVia(doc = "Reserves inventory for order lines, returning reservation details")
  Either<OrderError, InventoryReservation> reserve(OrderId orderId, List<ValidatedOrderLine> lines);

  /**
   * Confirms a reservation, making it permanent.
   *
   * @param reservationId the reservation to confirm
   * @return either an error or success
   */
  @PathVia(doc = "Confirms inventory reservation after successful payment")
  Either<OrderError, Void> confirmReservation(String reservationId);

  /**
   * Releases a reservation, returning stock to available pool.
   *
   * @param reservationId the reservation to release
   * @return either an error or success
   */
  @PathVia(doc = "Releases inventory reservation, returning stock to pool")
  Either<OrderError, Void> releaseReservation(String reservationId);

  /**
   * Gets detailed availability for order lines including warehouse information. Used for partial
   * fulfilment and split shipments.
   *
   * @param lines the order lines to check
   * @return list of product availability details
   */
  @PathVia(doc = "Gets detailed availability with warehouse info for partial fulfilment")
  Either<OrderError, List<ProductAvailability>> getDetailedAvailability(
      List<ValidatedOrderLine> lines);

  /**
   * Reserves only available items from a list. Used for partial fulfilment where some items may be
   * unavailable.
   *
   * @param orderId the order identifier
   * @param availability the availability information to reserve
   * @return reservation for available items only
   */
  @PathVia(doc = "Reserves only available items for partial fulfilment")
  Either<OrderError, InventoryReservation> reserveAvailable(
      OrderId orderId, List<ProductAvailability> availability);

  /**
   * Result of an inventory availability check.
   *
   * @param allAvailable true if all items are fully available
   * @param unavailableProducts list of product IDs that are unavailable
   * @param partialAvailability products with partial stock (requested vs available)
   */
  record InventoryCheckResult(
      boolean allAvailable,
      List<String> unavailableProducts,
      List<PartialStock> partialAvailability) {
    public static InventoryCheckResult fullyAvailable() {
      return new InventoryCheckResult(true, List.of(), List.of());
    }

    public boolean hasUnavailableItems() {
      return !unavailableProducts.isEmpty();
    }

    public boolean hasPartialStock() {
      return !partialAvailability.isEmpty();
    }
  }

  /**
   * Represents partial stock availability.
   *
   * @param productId the product
   * @param requested the requested quantity
   * @param available the available quantity
   */
  record PartialStock(String productId, int requested, int available) {}
}
