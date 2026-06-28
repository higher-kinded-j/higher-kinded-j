// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.*;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.example.order.service.NotificationService;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.ForPath;

/**
 * Workflow for processing orders with partial fulfilment support. Ships available items immediately
 * and creates back-orders for the shortage.
 *
 * <p>Demonstrates ForPath comprehensions for complex conditional workflows:
 *
 * <ul>
 *   <li>Splitting each line into a ship-now quantity and a back-order quantity
 *   <li>Shipping (and charging for) the available units of every line
 *   <li>Back-ordering only the shortage, at the correct money
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
   * Processes an order with partial fulfilment support. Ships available units and creates
   * back-orders for the shortage.
   *
   * @param order the validated order to process
   * @return either an error or the partial fulfilment result
   */
  public EitherPath<OrderError, PartialFulfilmentResult> process(ValidatedOrder order) {
    return getDetailedAvailability(order.lines())
        .via(availability -> processWithAvailability(order, availability));
  }

  /**
   * Processes an order after availability information has been retrieved.
   *
   * <p>Splits <em>each</em> line into a ship-now quantity ({@code min(requested, available)}) and a
   * back-order quantity (the shortage), rather than bucketing whole lines as available/unavailable.
   * A partially-available line therefore ships and is charged for its available units, and
   * back-orders only its shortage at the correct money — the previous boolean partition dropped a
   * partial line's available units and back-ordered (and over-charged) the whole line.
   */
  private EitherPath<OrderError, PartialFulfilmentResult> processWithAvailability(
      ValidatedOrder order, List<ProductAvailability> availability) {
    var unitPrices =
        order.lines().stream()
            .collect(
                Collectors.toMap(ValidatedOrderLine::productId, ValidatedOrderLine::unitPrice));
    var linesById =
        order.lines().stream()
            .collect(Collectors.toMap(ValidatedOrderLine::productId, line -> line));

    var shippable = availability.stream().filter(a -> a.availableQty() > 0).toList();
    var backordered = availability.stream().filter(a -> a.shortageQty() > 0).toList();

    var fulfilledAmount =
        shippable.stream()
            .map(a -> unitPriceOf(unitPrices, a).multiply(shipQty(a)))
            .reduce(Money.ZERO_GBP, Money::add);
    var backOrderAmount =
        backordered.stream()
            .map(a -> unitPriceOf(unitPrices, a).multiply(a.shortageQty()))
            .reduce(Money.ZERO_GBP, Money::add);

    return ForPath.from(validateHasAvailability(shippable, availability))
        .from(_ -> reserveAvailableItems(order, availability))
        .from(_ -> processPartialPayment(order, fulfilledAmount))
        .from(_ -> createShipmentForAvailable(order, shippable, linesById))
        .from(_ -> createBackOrders(order, backordered, unitPrices))
        .from(_ -> sendPartialFulfilmentNotification(order, fulfilledAmount))
        .yield(
            (_, _, payment, shipment, backOrders, _) ->
                buildResult(
                    order, payment, shipment, backOrders, fulfilledAmount, backOrderAmount));
  }

  /** The quantity of a line that can ship now. */
  private static int shipQty(ProductAvailability availability) {
    return Math.min(availability.requestedQty(), availability.availableQty());
  }

  private static Money unitPriceOf(
      Map<ProductId, Money> unitPrices, ProductAvailability availability) {
    return unitPrices.getOrDefault(availability.productId(), Money.ZERO_GBP);
  }

  private EitherPath<OrderError, List<ProductAvailability>> getDetailedAvailability(
      List<ValidatedOrderLine> lines) {
    return Path.either(inventoryService.getDetailedAvailability(lines));
  }

  /** Fails fast only when nothing at all can ship; a partial order is a success here. */
  private EitherPath<OrderError, List<ProductAvailability>> validateHasAvailability(
      List<ProductAvailability> shippable, List<ProductAvailability> all) {
    if (shippable.isEmpty()) {
      return Path.left(
          OrderError.InventoryError.outOfStock(
              all.stream().map(a -> a.productId().value()).toList()));
    }
    return Path.right(shippable);
  }

  private EitherPath<OrderError, String> reserveAvailableItems(
      ValidatedOrder order, List<ProductAvailability> availability) {
    return Path.either(
        inventoryService
            .reserveAvailable(order.orderId(), availability)
            .map(InventoryReservation::reservationId));
  }

  private EitherPath<OrderError, PaymentConfirmation> processPartialPayment(
      ValidatedOrder order, Money amount) {
    return Path.either(
        paymentService.processPayment(order.orderId(), amount, order.paymentMethod()));
  }

  private EitherPath<OrderError, ShipmentInfo> createShipmentForAvailable(
      ValidatedOrder order,
      List<ProductAvailability> shippable,
      Map<ProductId, ValidatedOrderLine> linesById) {
    // Ship the available quantity of each line, not the full requested line.
    var shipLines =
        shippable.stream()
            .map(
                a ->
                    ValidatedOrderLine.of(
                        a.productId(), linesById.get(a.productId()).product(), shipQty(a)))
            .toList();

    return Path.either(
        shippingService.createShipment(order.orderId(), order.shippingAddress(), shipLines));
  }

  private EitherPath<OrderError, List<BackOrder>> createBackOrders(
      ValidatedOrder order,
      List<ProductAvailability> backordered,
      Map<ProductId, Money> unitPrices) {
    if (backordered.isEmpty()) {
      return Path.right(List.of());
    }

    var backOrders =
        backordered.stream()
            .map(
                a ->
                    BackOrder.create(
                        order.orderId(),
                        a.productId(),
                        a.shortageQty(),
                        unitPriceOf(unitPrices, a),
                        14 // Estimated 14 days for restock
                        ))
            .toList();

    return Path.right(backOrders);
  }

  private EitherPath<OrderError, Boolean> sendPartialFulfilmentNotification(
      ValidatedOrder order, Money fulfilledAmount) {
    // Use the fulfilled amount (items charged) for the notification, not shipping cost.
    return Path.either(
            notificationService
                .sendOrderConfirmation(order.orderId(), order.customer(), fulfilledAmount)
                .map(NotificationResult::emailSent))
        .recoverWith(_ -> Path.right(false));
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
