// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.ProductAvailability;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.time.TimeSource;

/**
 * In-memory implementation of InventoryService for testing and examples.
 *
 * <p><strong>Thread Safety:</strong> {@link #reserve} decrements each product's stock with a single
 * atomic {@link Map#compute} guarded by a floor, so stock can never oversell or go negative even
 * under concurrent callers. A multi-line reservation is all-or-nothing: if any line is short the
 * lines already taken are returned before failing. Expired holds are reclaimed lazily on the next
 * reservation (see {@link #reclaimExpiredReservations()}), which is the only place {@code
 * expiresAt} is consulted.
 */
public class InMemoryInventoryService implements InventoryService {

  private final Map<String, Integer> stock = new ConcurrentHashMap<>();
  private final Map<String, String> productWarehouses = new ConcurrentHashMap<>();
  private final Map<String, InventoryReservation> reservations = new ConcurrentHashMap<>();

  /** How long a reservation is held before it can be reclaimed. */
  private static final Duration RESERVATION_HOLD = Duration.ofMinutes(15);

  /** Where this service reads time from; inject a fixed/steppable source in tests (#609). */
  private final TimeSource timeSource;

  public InMemoryInventoryService() {
    this(TimeSource.system());
  }

  public InMemoryInventoryService(TimeSource timeSource) {
    this.timeSource = Objects.requireNonNull(timeSource, "timeSource must not be null");
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
    reclaimExpiredReservations();

    // Take each line atomically with a floor, rolling back the lines already taken if any line is
    // short. This makes the whole reservation all-or-nothing and removes the check-then-act race:
    // two callers can no longer both pass a check and then both subtract the same stock.
    var taken = new ArrayList<InventoryReservation.ReservedItem>();
    var unavailable = new ArrayList<String>();
    for (var line : lines) {
      if (tryTake(line.productId().value(), line.quantity())) {
        taken.add(
            new InventoryReservation.ReservedItem(
                line.productId(), line.quantity(), warehouseOf(line.productId().value())));
      } else {
        unavailable.add(line.productId().value());
      }
    }

    if (!unavailable.isEmpty()) {
      taken.forEach(item -> returnStock(item.productId().value(), item.quantity()));
      return Either.left(OrderError.InventoryError.outOfStock(unavailable));
    }

    return Either.right(storeReservation(taken));
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
    reclaimExpiredReservations();

    // Best-effort partial reserve: take whatever each line can supply (up to the requested
    // quantity), again using the atomic floored take so a concurrent caller cannot oversell.
    var taken = new ArrayList<InventoryReservation.ReservedItem>();
    for (var avail : availability) {
      var qtyToReserve = Math.min(avail.requestedQty(), avail.availableQty());
      if (qtyToReserve > 0 && tryTake(avail.productId().value(), qtyToReserve)) {
        taken.add(
            new InventoryReservation.ReservedItem(
                avail.productId(), qtyToReserve, avail.warehouseId()));
      }
    }

    if (taken.isEmpty()) {
      return Either.left(
          OrderError.InventoryError.outOfStock(
              availability.stream().map(a -> a.productId().value()).toList()));
    }

    return Either.right(storeReservation(taken));
  }

  /**
   * Atomically subtracts {@code quantity} from a product's stock, but only if at least that much is
   * on hand. The floor inside {@link Map#compute} guarantees stock never goes negative and that two
   * concurrent takes cannot both succeed against the same insufficient stock.
   *
   * @return {@code true} if the stock was taken, {@code false} if there was not enough
   */
  private boolean tryTake(String productId, int quantity) {
    var took = new boolean[1];
    stock.compute(
        productId,
        (_, onHand) -> {
          if (onHand == null) {
            return null; // unknown product: leave it absent rather than inserting a 0 entry
          }
          if (onHand >= quantity) {
            took[0] = true;
            return onHand - quantity;
          }
          return onHand;
        });
    return took[0];
  }

  /** Returns {@code quantity} units of a product to stock. */
  private void returnStock(String productId, int quantity) {
    stock.compute(productId, (_, onHand) -> (onHand == null ? 0 : onHand) + quantity);
  }

  /** Records a reservation for the given taken items and returns it. */
  private InventoryReservation storeReservation(List<InventoryReservation.ReservedItem> items) {
    var reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    var reservation = new InventoryReservation(reservationId, items, now().plus(RESERVATION_HOLD));
    reservations.put(reservationId, reservation);
    return reservation;
  }

  /** The service's single read of current time - always through the injected source. */
  private Instant now() {
    return timeSource.now().unsafeRunSync();
  }

  /** Looks up the warehouse a product ships from, defaulting to the primary warehouse. */
  private String warehouseOf(String productId) {
    return productWarehouses.getOrDefault(productId, "WAREHOUSE-01");
  }

  /**
   * Releases any reservation whose hold has expired, returning its stock. Called before each
   * reserve so abandoned holds do not permanently strand inventory — and the one place {@code
   * expiresAt} is actually read.
   */
  private void reclaimExpiredReservations() {
    // A production service would expire holds with a background sweeper or a time-ordered
    // DelayQueue rather than scanning on the reserve path; for this in-memory demo a lazy scan is
    // adequate, and the empty-map fast path keeps the common case allocation-free.
    if (reservations.isEmpty()) {
      return;
    }
    var now = now();
    // Snapshot keys so we can mutate the map while iterating.
    for (var reservationId : List.copyOf(reservations.keySet())) {
      var reservation = reservations.get(reservationId);
      if (reservation != null
          && reservation.expiresAt().isBefore(now)
          && reservations.remove(reservationId, reservation)) {
        reservation.items().forEach(item -> returnStock(item.productId().value(), item.quantity()));
      }
    }
  }
}
