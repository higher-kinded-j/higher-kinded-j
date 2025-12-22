// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.util.List;
import org.higherkindedj.example.order.audit.AuditLog;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Result of processing an order with partial fulfilment support. Contains the shipped items and any
 * back-orders created.
 *
 * @param orderId the order identifier
 * @param shipment shipment info for available items (null if none shipped)
 * @param payment payment confirmation for fulfilled portion
 * @param backOrders list of back-orders for unavailable items
 * @param status overall fulfilment status
 * @param fulfilledAmount amount charged for shipped items
 * @param backOrderAmount amount to be charged when back-orders ship
 */
@GenerateLenses
public record PartialFulfilmentResult(
    OrderId orderId,
    ShipmentInfo shipment,
    PaymentConfirmation payment,
    List<BackOrder> backOrders,
    FulfilmentStatus status,
    Money fulfilledAmount,
    Money backOrderAmount) {
  /**
   * Creates a result for complete fulfilment (no back-orders).
   *
   * @param orderId the order ID
   * @param shipment the shipment information
   * @param payment the payment confirmation
   * @param amount the total amount charged
   * @return a PartialFulfilmentResult with COMPLETE status
   */
  public static PartialFulfilmentResult complete(
      OrderId orderId, ShipmentInfo shipment, PaymentConfirmation payment, Money amount) {
    return new PartialFulfilmentResult(
        orderId, shipment, payment, List.of(), FulfilmentStatus.COMPLETE, amount, Money.ZERO_GBP);
  }

  /**
   * Creates a result for partial fulfilment with back-orders.
   *
   * @param orderId the order ID
   * @param shipment the shipment for available items
   * @param payment the payment for available items
   * @param backOrders the back-orders created
   * @param fulfilledAmount amount charged now
   * @param backOrderAmount amount to charge when back-orders ship
   * @return a PartialFulfilmentResult with PARTIAL status
   */
  public static PartialFulfilmentResult partial(
      OrderId orderId,
      ShipmentInfo shipment,
      PaymentConfirmation payment,
      List<BackOrder> backOrders,
      Money fulfilledAmount,
      Money backOrderAmount) {
    return new PartialFulfilmentResult(
        orderId,
        shipment,
        payment,
        backOrders,
        FulfilmentStatus.PARTIAL,
        fulfilledAmount,
        backOrderAmount);
  }

  /**
   * Creates a result indicating all items were back-ordered.
   *
   * @param orderId the order ID
   * @param backOrders the back-orders created
   * @param backOrderAmount total amount to charge when items ship
   * @return a PartialFulfilmentResult with PENDING status
   */
  public static PartialFulfilmentResult allBackOrdered(
      OrderId orderId, List<BackOrder> backOrders, Money backOrderAmount) {
    return new PartialFulfilmentResult(
        orderId, null, null, backOrders, FulfilmentStatus.PENDING, Money.ZERO_GBP, backOrderAmount);
  }

  /**
   * Checks if all items were fulfilled immediately.
   *
   * @return true if no back-orders exist
   */
  public boolean isFullyFulfilled() {
    return backOrders.isEmpty();
  }

  /**
   * Checks if any items were back-ordered.
   *
   * @return true if back-orders exist
   */
  public boolean hasBackOrders() {
    return !backOrders.isEmpty();
  }

  /**
   * Returns the total order value (fulfilled plus back-ordered).
   *
   * @return the combined amount
   */
  public Money totalOrderValue() {
    return fulfilledAmount.add(backOrderAmount);
  }

  /**
   * Returns the number of back-ordered items.
   *
   * @return count of back-orders
   */
  public int backOrderCount() {
    return backOrders.size();
  }

  /**
   * Converts this partial fulfilment result to an OrderResult.
   *
   * <p>This is useful when integrating partial fulfilment with the standard workflow.
   *
   * @param customerId the customer ID for the order
   * @return an OrderResult representing the fulfilled portion
   */
  public OrderResult toOrderResult(CustomerId customerId) {
    return new OrderResult(
        orderId,
        customerId,
        fulfilledAmount,
        payment != null ? payment.transactionId() : "PENDING",
        shipment != null ? shipment.trackingNumber() : "PENDING",
        shipment != null ? shipment.estimatedDelivery() : null,
        AuditLog.EMPTY.append(
            AuditLog.of(
                "PARTIAL_FULFILMENT",
                "Fulfilled "
                    + (isFullyFulfilled() ? "completely" : "partially")
                    + " with "
                    + backOrderCount()
                    + " back-orders")));
  }
}
