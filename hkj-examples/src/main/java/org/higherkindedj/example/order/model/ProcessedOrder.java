// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import java.util.Optional;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A processed order with its current state.
 *
 * <p>This record represents an order that has been at least partially processed, including any
 * reservations, payments, or shipments that have been created.
 *
 * @param orderId the order identifier
 * @param customerId the customer identifier
 * @param customer the customer details
 * @param status the current order status
 * @param inventoryReservation the inventory reservation, if created
 * @param paymentConfirmation the payment confirmation, if processed
 * @param shipmentInfo the shipment info, if created
 * @param createdAt when the order was created
 * @param lastUpdatedAt when the order was last updated
 */
@GenerateLenses
@GenerateFocus
public record ProcessedOrder(
    OrderId orderId,
    CustomerId customerId,
    Customer customer,
    OrderStatus status,
    Optional<InventoryReservation> inventoryReservation,
    Optional<PaymentConfirmation> paymentConfirmation,
    Optional<ShipmentInfo> shipmentInfo,
    Instant createdAt,
    Instant lastUpdatedAt) {
  /**
   * Creates a processed order from a validated order.
   *
   * @param validatedOrder the validated order
   * @return a new processed order in PROCESSING status
   */
  public static ProcessedOrder fromValidated(ValidatedOrder validatedOrder) {
    var now = Instant.now();
    return new ProcessedOrder(
        validatedOrder.orderId(),
        validatedOrder.customerId(),
        validatedOrder.customer(),
        OrderStatus.PROCESSING,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        validatedOrder.createdAt(),
        now);
  }

  /**
   * Returns a new order with the status updated.
   *
   * @param newStatus the new status
   * @return updated order
   */
  public ProcessedOrder withStatus(OrderStatus newStatus) {
    return new ProcessedOrder(
        orderId,
        customerId,
        customer,
        newStatus,
        inventoryReservation,
        paymentConfirmation,
        shipmentInfo,
        createdAt,
        Instant.now());
  }

  /**
   * Returns a new order with inventory reservation set.
   *
   * @param reservation the inventory reservation
   * @return updated order
   */
  public ProcessedOrder withInventoryReservation(InventoryReservation reservation) {
    return new ProcessedOrder(
        orderId,
        customerId,
        customer,
        OrderStatus.INVENTORY_RESERVED,
        Optional.of(reservation),
        paymentConfirmation,
        shipmentInfo,
        createdAt,
        Instant.now());
  }

  /**
   * Returns a new order with payment confirmation set.
   *
   * @param confirmation the payment confirmation
   * @return updated order
   */
  public ProcessedOrder withPaymentConfirmation(PaymentConfirmation confirmation) {
    return new ProcessedOrder(
        orderId,
        customerId,
        customer,
        OrderStatus.PAYMENT_COMPLETE,
        inventoryReservation,
        Optional.of(confirmation),
        shipmentInfo,
        createdAt,
        Instant.now());
  }

  /**
   * Returns a new order with shipment info set.
   *
   * @param info the shipment info
   * @return updated order
   */
  public ProcessedOrder withShipmentInfo(ShipmentInfo info) {
    return new ProcessedOrder(
        orderId,
        customerId,
        customer,
        OrderStatus.SHIPPED,
        inventoryReservation,
        paymentConfirmation,
        Optional.of(info),
        createdAt,
        Instant.now());
  }

  /**
   * Checks if this order can be cancelled.
   *
   * @return true if the order can be cancelled
   */
  public boolean isCancellable() {
    return status.isCancellable();
  }

  /**
   * Checks if this order requires a refund on cancellation.
   *
   * @return true if a refund is needed
   */
  public boolean requiresRefund() {
    return paymentConfirmation.isPresent() && status.requiresRefund();
  }

  /**
   * Checks if this order has inventory to release on cancellation.
   *
   * @return true if inventory should be released
   */
  public boolean hasInventoryToRelease() {
    return inventoryReservation.isPresent() && status.hasReservedInventory();
  }

  /**
   * Checks if this order has a shipment that can be cancelled.
   *
   * @return true if shipment can be cancelled
   */
  public boolean hasShipmentToCancel() {
    return shipmentInfo.isPresent() && status == OrderStatus.SHIPPED;
  }
}
