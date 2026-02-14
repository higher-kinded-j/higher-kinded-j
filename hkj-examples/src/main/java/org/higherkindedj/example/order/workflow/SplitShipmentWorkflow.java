// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.InventoryReservation;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.SplitShipmentResult;
import org.higherkindedj.example.order.model.ValidatedOrder;
import org.higherkindedj.example.order.model.ValidatedShippingAddress;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.example.order.service.WarehouseService;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.ForPath;

/**
 * Workflow for creating split shipments from multiple warehouses.
 *
 * <p>Demonstrates ForPath comprehensions for:
 *
 * <ul>
 *   <li>Grouping items by warehouse location
 *   <li>Creating parallel shipments from each warehouse
 *   <li>Combining tracking information
 *   <li>Calculating total shipping costs
 * </ul>
 */
public class SplitShipmentWorkflow {

  private final WarehouseService warehouseService;
  private final ShippingService shippingService;

  public SplitShipmentWorkflow(WarehouseService warehouseService, ShippingService shippingService) {
    this.warehouseService = warehouseService;
    this.shippingService = shippingService;
  }

  /**
   * Creates split shipments when items are in different warehouses.
   *
   * @param order the validated order
   * @param reservation the inventory reservation with warehouse assignments
   * @return either an error or the split shipment result
   */
  public EitherPath<OrderError, SplitShipmentResult> createSplitShipments(
      ValidatedOrder order, InventoryReservation reservation) {
    // Group reserved items by warehouse
    var byWarehouse =
        reservation.items().stream()
            .collect(Collectors.groupingBy(InventoryReservation.ReservedItem::warehouseId));

    // If only one warehouse, return single shipment
    if (byWarehouse.size() == 1) {
      return createSingleShipment(order, reservation);
    }

    return ForPath
        // Step 1: Create shipments for each warehouse
        .from(createWarehouseShipments(order.orderId(), byWarehouse, order.shippingAddress()))

        // Step 2: Calculate total shipping cost
        .let(this::calculateTotalShippingCost)

        // Step 3: Build result
        .yield(
            (shipments, totalCost) ->
                SplitShipmentResult.fromShipments(order.orderId(), shipments));
  }

  private EitherPath<OrderError, SplitShipmentResult> createSingleShipment(
      ValidatedOrder order, InventoryReservation reservation) {
    return Path.either(
        shippingService
            .createShipment(order.orderId(), order.shippingAddress(), order.lines())
            .map(shipment -> SplitShipmentResult.single(order.orderId(), shipment)));
  }

  /**
   * Creates shipments for each warehouse using functional composition.
   *
   * <p>Uses a traverse-like pattern: maps each warehouse entry to an EitherPath, then sequences
   * them together, short-circuiting on the first error.
   */
  private EitherPath<OrderError, List<ShipmentInfo>> createWarehouseShipments(
      OrderId orderId,
      Map<String, List<InventoryReservation.ReservedItem>> byWarehouse,
      ValidatedShippingAddress address) {
    // Start with an empty list wrapped in EitherPath
    EitherPath<OrderError, List<ShipmentInfo>> accumulated = Path.right(List.of());

    // Fold over each warehouse entry, sequencing the shipment creation
    for (var entry : byWarehouse.entrySet()) {
      accumulated =
          accumulated.via(
              shipments ->
                  lookupWarehouseAndCreateShipment(
                          orderId, entry.getKey(), entry.getValue(), address)
                      .map(shipment -> appendToList(shipments, shipment)));
    }

    return accumulated;
  }

  /**
   * Looks up a warehouse and creates a shipment from it.
   *
   * <p>Combines the Optional warehouse lookup with the EitherPath shipment creation.
   */
  private EitherPath<OrderError, ShipmentInfo> lookupWarehouseAndCreateShipment(
      OrderId orderId,
      String warehouseId,
      List<InventoryReservation.ReservedItem> items,
      ValidatedShippingAddress address) {
    return warehouseService
        .getWarehouse(warehouseId)
        .map(warehouse -> createWarehouseShipment(orderId, warehouse, items, address))
        .orElseGet(
            () ->
                Path.left(
                    OrderError.SystemError.unexpected(
                        "Warehouse not found: " + warehouseId,
                        new IllegalStateException("Unknown warehouse"))));
  }

  /** Appends an element to an immutable list, returning a new list. */
  private static <T> List<T> appendToList(List<T> list, T element) {
    return Stream.concat(list.stream(), Stream.of(element)).toList();
  }

  private EitherPath<OrderError, ShipmentInfo> createWarehouseShipment(
      OrderId orderId,
      WarehouseInfo warehouse,
      List<InventoryReservation.ReservedItem> items,
      ValidatedShippingAddress address) {
    return ForPath
        // Step 1: Create shipment from warehouse
        .from(
            Path.either(
                shippingService.createShipmentFromWarehouse(orderId, warehouse, items, address)))
        // Step 2: Add warehouse shipping cost
        .let(shipment -> Money.gbp(warehouse.shippingCost()))
        // Step 3: Update shipment with cost
        .yield(ShipmentInfo::withShippingCost);
  }

  private Money calculateTotalShippingCost(List<ShipmentInfo> shipments) {
    return shipments.stream().map(ShipmentInfo::shippingCost).reduce(Money.ZERO_GBP, Money::add);
  }

  /**
   * Determines if an order requires split shipments.
   *
   * @param reservation the inventory reservation
   * @return true if items are in multiple warehouses
   */
  public boolean requiresSplitShipment(InventoryReservation reservation) {
    var warehouseCount =
        reservation.items().stream()
            .map(InventoryReservation.ReservedItem::warehouseId)
            .distinct()
            .count();
    return warehouseCount > 1;
  }
}
