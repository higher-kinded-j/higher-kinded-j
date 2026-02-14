// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.ValidatedShippingAddress.ShippingZone;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.hkt.either.Either;

/** In-memory implementation of ShippingService for testing and examples. */
public class InMemoryShippingService implements ShippingService {

  private final Map<String, ShipmentInfo> shipments = new ConcurrentHashMap<>();
  private final Map<String, ShipmentStatus> statuses = new ConcurrentHashMap<>();

  private static final Set<String> DOMESTIC_COUNTRIES = Set.of("GB", "UK");
  private static final Set<String> EUROPEAN_COUNTRIES =
      Set.of("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PL", "SE", "DK", "FI", "NO", "IE");

  @Override
  public Either<OrderError, ValidatedShippingAddress> validateAddress(ShippingAddress address) {
    // Basic validation
    if (address.postcode() == null || address.postcode().isBlank()) {
      return Either.left(OrderError.ShippingError.invalidAddress("Postcode is required"));
    }

    if (address.country() == null || address.country().isBlank()) {
      return Either.left(OrderError.ShippingError.invalidAddress("Country is required"));
    }

    var zone = determineShippingZone(address.country());
    return Either.right(ValidatedShippingAddress.from(address, zone));
  }

  private ShippingZone determineShippingZone(String country) {
    var upperCountry = country.toUpperCase();
    if (DOMESTIC_COUNTRIES.contains(upperCountry)) {
      return ShippingZone.DOMESTIC;
    }
    if (EUROPEAN_COUNTRIES.contains(upperCountry)) {
      return ShippingZone.EUROPE;
    }
    return ShippingZone.INTERNATIONAL;
  }

  @Override
  public Either<OrderError, ShipmentInfo> createShipment(
      OrderId orderId, ValidatedShippingAddress address, List<ValidatedOrderLine> lines) {
    var shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    var trackingNumber = generateTrackingNumber(address.shippingZone());
    var carrier = selectCarrier(address.shippingZone());
    var estimatedDelivery = calculateEstimatedDelivery(address.shippingZone());
    var shippingCost = calculateShippingCost(address.shippingZone());

    var shipmentInfo =
        ShipmentInfo.create(shipmentId, trackingNumber, carrier, estimatedDelivery, shippingCost);

    shipments.put(shipmentId, shipmentInfo);
    statuses.put(
        trackingNumber,
        new ShipmentStatus(ShipmentStatus.Status.PENDING, "Warehouse", estimatedDelivery));

    return Either.right(shipmentInfo);
  }

  @Override
  public Either<OrderError, ShipmentInfo> createShipmentFromWarehouse(
      OrderId orderId,
      WarehouseInfo warehouse,
      List<InventoryReservation.ReservedItem> items,
      ValidatedShippingAddress address) {
    var shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    var trackingNumber = generateTrackingNumber(address.shippingZone());
    var carrier = selectCarrier(address.shippingZone());
    var baseDays =
        switch (address.shippingZone()) {
          case DOMESTIC -> 2;
          case EUROPE -> 5;
          case INTERNATIONAL -> 10;
        };
    var estimatedDelivery =
        Instant.now().plus(baseDays + warehouse.processingDays(), ChronoUnit.DAYS);
    var shippingCost = Money.gbp(warehouse.shippingCost());

    var shipmentInfo =
        ShipmentInfo.fromWarehouse(
            shipmentId,
            trackingNumber,
            carrier,
            estimatedDelivery,
            shippingCost,
            warehouse.warehouseId());

    shipments.put(shipmentId, shipmentInfo);
    statuses.put(
        trackingNumber,
        new ShipmentStatus(ShipmentStatus.Status.PENDING, warehouse.name(), estimatedDelivery));

    return Either.right(shipmentInfo);
  }

  private Money calculateShippingCost(ShippingZone zone) {
    var amount =
        switch (zone) {
          case DOMESTIC -> new BigDecimal("3.99");
          case EUROPE -> new BigDecimal("9.99");
          case INTERNATIONAL -> new BigDecimal("19.99");
        };
    return Money.gbp(amount);
  }

  private String generateTrackingNumber(ShippingZone zone) {
    var prefix =
        switch (zone) {
          case DOMESTIC -> "RM";
          case EUROPE -> "DHL";
          case INTERNATIONAL -> "FDX";
        };
    return prefix + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
  }

  private String selectCarrier(ShippingZone zone) {
    return switch (zone) {
      case DOMESTIC -> "ROYAL_MAIL";
      case EUROPE -> "DHL";
      case INTERNATIONAL -> "FEDEX";
    };
  }

  private Instant calculateEstimatedDelivery(ShippingZone zone) {
    var days =
        switch (zone) {
          case DOMESTIC -> 2;
          case EUROPE -> 5;
          case INTERNATIONAL -> 10;
        };
    return Instant.now().plus(days, ChronoUnit.DAYS);
  }

  @Override
  public Either<OrderError, Void> cancelShipment(String shipmentId) {
    var shipment = shipments.get(shipmentId);
    if (shipment == null) {
      return Either.left(
          OrderError.ShippingError.invalidAddress("Shipment not found: " + shipmentId));
    }

    var status = statuses.get(shipment.trackingNumber());
    if (status != null && status.status() != ShipmentStatus.Status.PENDING) {
      return Either.left(
          OrderError.ShippingError.invalidAddress(
              "Cannot cancel shipment that has already been dispatched"));
    }

    shipments.remove(shipmentId);
    statuses.put(
        shipment.trackingNumber(),
        new ShipmentStatus(
            ShipmentStatus.Status.CANCELLED,
            status != null ? status.location() : "Unknown",
            status != null ? status.estimatedDelivery() : Instant.now()));

    return Either.right(null);
  }

  @Override
  public Either<OrderError, ShipmentStatus> getShipmentStatus(String trackingNumber) {
    var status = statuses.get(trackingNumber);
    if (status == null) {
      return Either.left(
          OrderError.ShippingError.invalidAddress("Tracking number not found: " + trackingNumber));
    }
    return Either.right(status);
  }
}
