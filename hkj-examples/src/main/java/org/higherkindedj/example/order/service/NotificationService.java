// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.NotificationResult;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/** Service for customer notifications. */
@GeneratePathBridge
public interface NotificationService {

  /**
   * Sends an order confirmation notification.
   *
   * @param orderId the order identifier
   * @param customer the customer to notify
   * @param total the order total
   * @return either an error or the notification result
   */
  @PathVia(doc = "Sends order confirmation email and SMS")
  Either<OrderError, NotificationResult> sendOrderConfirmation(
      OrderId orderId, Customer customer, Money total);

  /**
   * Sends a shipment notification.
   *
   * @param orderId the order identifier
   * @param customer the customer to notify
   * @param shipmentInfo the shipment details
   * @return either an error or the notification result
   */
  @PathVia(doc = "Sends shipment notification with tracking details")
  Either<OrderError, NotificationResult> sendShipmentNotification(
      OrderId orderId, Customer customer, ShipmentInfo shipmentInfo);

  /**
   * Sends an order cancellation notification.
   *
   * @param orderId the order identifier
   * @param customer the customer to notify
   * @param reason the cancellation reason
   * @return either an error or the notification result
   */
  @PathVia(doc = "Sends order cancellation notification")
  Either<OrderError, NotificationResult> sendCancellationNotification(
      OrderId orderId, Customer customer, String reason);
}
