// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.ProductAvailability;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.hkt.either.Either;

/**
 * In-memory implementation of InventoryService for testing and examples.
 *
 * <p><strong>Thread Safety Note:</strong> While this implementation uses ConcurrentHashMap for
 * storage, the {@link #reserve} method is not fully atomic. It checks availability first, then
 * reserves stock in a separate operation. In a concurrent environment, this could lead to race
 * conditions (e.g., overselling). This is acceptable for testing and demonstration purposes but
 * would require proper synchronisation or database transactions in production.
 */
public class InMemoryInventoryService implements InventoryService {

  private final Map<String, Integer> stock = new ConcurrentHashMap<>();
  private final Map<String, String> productWarehouses = new ConcurrentHashMap<>();
  private final Map<String, InventoryReservation> reservations = new ConcurrentHashMap<>();

  public InMemoryInventoryService() {
    // Pre-populate with sample stock across different warehouses
    stock.put("PROD-001", 100);
    stock.put("PROD-002", 50);
    stock.put("PROD-003", 25);
    stock.put("PROD-004", 0); // Out of stock
    stock.put("PROD-005", 10);
    stock.put("PROD-006", 5);

    // Assign products to warehouses for split shipment demos
    productWarehouses.put("PROD-001", "WAREHOUSE-UK-01");
    productWarehouses.put("PROD-002", "WAREHOUSE-UK-01");
    productWarehouses.put("PROD-003", "WAREHOUSE-EU-01");
    productWarehouses.put("PROD-004", "WAREHOUSE-UK-01");
    productWarehouses.put("PROD-005", "WAREHOUSE-EU-01");
    productWarehouses.put("PROD-006", "WAREHOUSE-INTL-01");
  }

  public void setStock(String productId, int quantity) {
    stock.put(productId, quantity);
  }

  public void setStock(String productId, int quantity, String warehouseId) {
    stock.put(productId, quantity);
    productWarehouses.put(productId, warehouseId);
  }

  @Override
  public Either<OrderError, InventoryCheckResult> checkAvailability(
      List<ValidatedOrderLine> lines) {
    var unavailable =
        lines.stream()
            .filter(
                line -> {
                  var available = stock.getOrDefault(line.productId().value(), 0);
                  return available < line.quantity();
                })
            .map(line -> line.productId().value())
            .toList();

    if (!unavailable.isEmpty()) {
      return Either.right(new InventoryCheckResult(false, unavailable, List.of()));
    }

    return Either.right(InventoryCheckResult.fullyAvailable());
  }

  @Override
  public Either<OrderError, InventoryReservation> reserve(
      OrderId orderId, List<ValidatedOrderLine> lines) {
    // Check availability first
    var unavailable =
        lines.stream()
            .filter(
                line -> {
                  var available = stock.getOrDefault(line.productId().value(), 0);
                  return available < line.quantity();
                })
            .map(line -> line.productId().value())
            .toList();

    if (!unavailable.isEmpty()) {
      return Either.left(OrderError.InventoryError.outOfStock(unavailable));
    }

    // Reserve the stock
    var reservedItems =
        lines.stream()
            .map(
                line -> {
                  var productId = line.productId().value();
                  stock.compute(productId, (k, v) -> (v == null ? 0 : v) - line.quantity());
                  return new InventoryReservation.ReservedItem(
                      line.productId(), line.quantity(), "WAREHOUSE-01");
                })
            .toList();

    var reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    var reservation =
        new InventoryReservation(
            reservationId, reservedItems, Instant.now().plusSeconds(900) // 15 minute expiry
            );

    reservations.put(reservationId, reservation);
    return Either.right(reservation);
  }

  @Override
  public Either<OrderError, Void> confirmReservation(String reservationId) {
    if (!reservations.containsKey(reservationId)) {
      return Either.left(
          OrderError.InventoryError.reservationFailed("Reservation not found: " + reservationId));
    }
    reservations.remove(reservationId);
    return Either.right(null);
  }

  @Override
  public Either<OrderError, Void> releaseReservation(String reservationId) {
    var reservation = reservations.remove(reservationId);
    if (reservation == null) {
      return Either.left(
          OrderError.InventoryError.reservationFailed("Reservation not found: " + reservationId));
    }

    // Return stock
    for (var item : reservation.items()) {
      stock.compute(item.productId().value(), (k, v) -> (v == null ? 0 : v) + item.quantity());
    }

    return Either.right(null);
  }

  @Override
  public Either<OrderError, List<ProductAvailability>> getDetailedAvailability(
      List<ValidatedOrderLine> lines) {
    var availability =
        lines.stream()
            .map(
                line -> {
                  var productId = line.productId();
                  var requested = line.quantity();
                  var available = stock.getOrDefault(productId.value(), 0);
                  var warehouseId =
                      productWarehouses.getOrDefault(productId.value(), "WAREHOUSE-UK-01");

                  if (available >= requested) {
                    return ProductAvailability.available(productId, requested, warehouseId);
                  } else if (available > 0) {
                    return ProductAvailability.partial(
                        productId, requested, available, warehouseId);
                  } else {
                    return ProductAvailability.unavailable(productId, requested);
                  }
                })
            .toList();

    return Either.right(availability);
  }

  @Override
  public Either<OrderError, InventoryReservation> reserveAvailable(
      OrderId orderId, List<ProductAvailability> availability) {
    // Only reserve items that have some availability
    var reservable = availability.stream().filter(a -> a.availableQty() > 0).toList();

    if (reservable.isEmpty()) {
      return Either.left(
          OrderError.InventoryError.outOfStock(
              availability.stream().map(a -> a.productId().value()).toList()));
    }

    // Reserve the available quantities
    var reservedItems =
        reservable.stream()
            .map(
                avail -> {
                  var productId = avail.productId().value();
                  var qtyToReserve = Math.min(avail.requestedQty(), avail.availableQty());
                  stock.compute(productId, (k, v) -> (v == null ? 0 : v) - qtyToReserve);
                  return new InventoryReservation.ReservedItem(
                      avail.productId(), qtyToReserve, avail.warehouseId());
                })
            .toList();

    var reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    var reservation =
        new InventoryReservation(
            reservationId, reservedItems, Instant.now().plusSeconds(900) // 15 minute expiry
            );

    reservations.put(reservationId, reservation);
    return Either.right(reservation);
  }
}
