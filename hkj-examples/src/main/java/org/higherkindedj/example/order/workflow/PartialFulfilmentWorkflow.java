// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.*;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.example.order.service.NotificationService;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Workflow for processing orders with partial fulfilment support. Ships available items immediately
 * and creates back-orders for unavailable items.
 *
 * <p>Demonstrates ForPath comprehensions for complex conditional workflows:
 *
 * <ul>
 *   <li>Partitioning items by availability
 *   <li>Processing available subset
 *   <li>Creating back-orders for unavailable items
 *   <li>Calculating partial payments
 * </ul>
 */
public class PartialFulfilmentWorkflow {

  private final InventoryService inventoryService;
  private final PaymentService paymentService;
  private final ShippingService shippingService;
  private final NotificationService notificationService;

  public PartialFulfilmentWorkflow(
      InventoryService inventoryService,
      PaymentService paymentService,
      ShippingService shippingService,
      NotificationService notificationService) {
    this.inventoryService = inventoryService;
    this.paymentService = paymentService;
    this.shippingService = shippingService;
    this.notificationService = notificationService;
  }

  /**
   * Processes an order with partial fulfilment support. Ships available items and creates
   * back-orders for unavailable ones.
   *
   * <p>Uses via() chains for composing workflow steps since EitherPath ForPath is limited to 3
   * steps.
   *
   * @param order the validated order to process
   * @return either an error or the partial fulfilment result
   */
  public EitherPath<OrderError, PartialFulfilmentResult> process(ValidatedOrder order) {
    return getDetailedAvailability(order.lines())
        .via(
            availability -> {
              var partitioned = partitionByAvailability(availability);
              var availableItems = partitioned.get(true);
              var unavailableItems = partitioned.get(false);
              var partialTotal = calculateAvailableTotal(order, availableItems);
              var backOrderTotal = calculateBackOrderTotal(order, unavailableItems);

              return validateAvailability(partitioned, order)
                  .via(
                      validated ->
                          reserveAvailableItems(order, availableItems)
                              .via(
                                  reservationId ->
                                      processPartialPayment(order, partialTotal)
                                          .via(
                                              payment ->
                                                  createShipmentForAvailable(order, availableItems)
                                                      .via(
                                                          shipment ->
                                                              createBackOrders(
                                                                      order, unavailableItems)
                                                                  .via(
                                                                      backOrders ->
                                                                          sendPartialFulfilmentNotification(
                                                                                  order,
                                                                                  shipment,
                                                                                  backOrders,
                                                                                  partialTotal)
                                                                              .map(
                                                                                  notified ->
                                                                                      buildResult(
                                                                                          order,
                                                                                          payment,
                                                                                          shipment,
                                                                                          backOrders,
                                                                                          partialTotal,
                                                                                          backOrderTotal)))))));
            });
  }

  private EitherPath<OrderError, List<ProductAvailability>> getDetailedAvailability(
      List<ValidatedOrderLine> lines) {
    return Path.either(inventoryService.getDetailedAvailability(lines));
  }

  private Map<Boolean, List<ProductAvailability>> partitionByAvailability(
      List<ProductAvailability> availability) {
    return availability.stream()
        .collect(Collectors.partitioningBy(ProductAvailability::isAvailable));
  }

  private EitherPath<OrderError, Map<Boolean, List<ProductAvailability>>> validateAvailability(
      Map<Boolean, List<ProductAvailability>> partitioned, ValidatedOrder order) {
    var available = partitioned.get(true);
    var unavailable = partitioned.get(false);

    // If nothing is available, that's an error
    if (available.isEmpty()) {
      return Path.left(
          OrderError.InventoryError.outOfStock(
              unavailable.stream().map(a -> a.productId().value()).toList()));
    }

    return Path.right(partitioned);
  }

  private EitherPath<OrderError, String> reserveAvailableItems(
      ValidatedOrder order, List<ProductAvailability> available) {
    return Path.either(
        inventoryService
            .reserveAvailable(order.orderId(), available)
            .map(InventoryReservation::reservationId));
  }

  private Money calculateAvailableTotal(ValidatedOrder order, List<ProductAvailability> available) {
    var availableProductIds =
        available.stream().map(ProductAvailability::productId).collect(Collectors.toSet());

    return order.lines().stream()
        .filter(line -> availableProductIds.contains(line.productId()))
        .map(ValidatedOrderLine::lineTotal)
        .reduce(Money.ZERO_GBP, Money::add);
  }

  private EitherPath<OrderError, PaymentConfirmation> processPartialPayment(
      ValidatedOrder order, Money amount) {
    return Path.either(
        paymentService.processPayment(order.orderId(), amount, order.paymentMethod()));
  }

  private EitherPath<OrderError, ShipmentInfo> createShipmentForAvailable(
      ValidatedOrder order, List<ProductAvailability> available) {
    var availableProductIds =
        available.stream().map(ProductAvailability::productId).collect(Collectors.toSet());

    var availableLines =
        order.lines().stream()
            .filter(line -> availableProductIds.contains(line.productId()))
            .toList();

    return Path.either(
        shippingService.createShipment(order.orderId(), order.shippingAddress(), availableLines));
  }

  private EitherPath<OrderError, List<BackOrder>> createBackOrders(
      ValidatedOrder order, List<ProductAvailability> unavailable) {
    if (unavailable.isEmpty()) {
      return Path.right(List.of());
    }

    var productPrices =
        order.lines().stream()
            .collect(
                Collectors.toMap(ValidatedOrderLine::productId, ValidatedOrderLine::unitPrice));

    var backOrders =
        unavailable.stream()
            .map(
                avail ->
                    BackOrder.create(
                        order.orderId(),
                        avail.productId(),
                        avail.shortageQty(),
                        productPrices.getOrDefault(avail.productId(), Money.ZERO_GBP),
                        14 // Estimated 14 days for restock
                        ))
            .toList();

    return Path.right(backOrders);
  }

  private Money calculateBackOrderTotal(
      ValidatedOrder order, List<ProductAvailability> unavailable) {
    var unavailableProductIds =
        unavailable.stream().map(ProductAvailability::productId).collect(Collectors.toSet());

    return order.lines().stream()
        .filter(line -> unavailableProductIds.contains(line.productId()))
        .map(ValidatedOrderLine::lineTotal)
        .reduce(Money.ZERO_GBP, Money::add);
  }

  private EitherPath<OrderError, Boolean> sendPartialFulfilmentNotification(
      ValidatedOrder order,
      ShipmentInfo shipment,
      List<BackOrder> backOrders,
      Money fulfilledAmount) {
    // Use the fulfilled amount (items charged) for the notification, not shipping cost
    return Path.either(
            notificationService
                .sendOrderConfirmation(order.orderId(), order.customer(), fulfilledAmount)
                .map(NotificationResult::emailSent))
        .recoverWith(error -> Path.right(false));
  }

  private PartialFulfilmentResult buildResult(
      ValidatedOrder order,
      PaymentConfirmation payment,
      ShipmentInfo shipment,
      List<BackOrder> backOrders,
      Money fulfilledAmount,
      Money backOrderAmount) {
    if (backOrders.isEmpty()) {
      return PartialFulfilmentResult.complete(order.orderId(), shipment, payment, fulfilledAmount);
    }

    return PartialFulfilmentResult.partial(
        order.orderId(), shipment, payment, backOrders, fulfilledAmount, backOrderAmount);
  }
}
