// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.NotificationResult;
import org.higherkindedj.example.order.model.ShipmentInfo;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.NotificationService;
import org.higherkindedj.hkt.either.Either;

/**
 * In-memory implementation of NotificationService for testing and examples.
 *
 * <p>Tracks sent notifications for verification in tests.
 */
public class InMemoryNotificationService implements NotificationService {

  private final List<NotificationRecord> sentNotifications = new ArrayList<>();
  private boolean emailEnabled = true;
  private boolean smsEnabled = true;

  public void setEmailEnabled(boolean enabled) {
    this.emailEnabled = enabled;
  }

  public void setSmsEnabled(boolean enabled) {
    this.smsEnabled = enabled;
  }

  public List<NotificationRecord> getSentNotifications() {
    return List.copyOf(sentNotifications);
  }

  public void clearNotifications() {
    sentNotifications.clear();
  }

  @Override
  public Either<OrderError, NotificationResult> sendOrderConfirmation(
      OrderId orderId, Customer customer, Money total) {
    var messageId = "MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    if (emailEnabled) {
      sentNotifications.add(
          new NotificationRecord(
              NotificationType.ORDER_CONFIRMATION,
              customer.email(),
              "Order " + orderId + " confirmed. Total: " + total));
    }

    if (smsEnabled) {
      sentNotifications.add(
          new NotificationRecord(
              NotificationType.ORDER_CONFIRMATION_SMS,
              customer.phone(),
              "Order " + orderId + " confirmed."));
    }

    if (emailEnabled && smsEnabled) {
      return Either.right(NotificationResult.allSent(messageId));
    } else if (emailEnabled) {
      return Either.right(NotificationResult.emailOnly(messageId));
    } else {
      return Either.right(NotificationResult.none());
    }
  }

  @Override
  public Either<OrderError, NotificationResult> sendShipmentNotification(
      OrderId orderId, Customer customer, ShipmentInfo shipmentInfo) {
    var messageId = "MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    if (emailEnabled) {
      sentNotifications.add(
          new NotificationRecord(
              NotificationType.SHIPMENT_NOTIFICATION,
              customer.email(),
              "Order " + orderId + " shipped. Tracking: " + shipmentInfo.trackingNumber()));
    }

    if (smsEnabled) {
      sentNotifications.add(
          new NotificationRecord(
              NotificationType.SHIPMENT_NOTIFICATION_SMS,
              customer.phone(),
              "Order shipped. Track: " + shipmentInfo.trackingNumber()));
    }

    if (emailEnabled && smsEnabled) {
      return Either.right(NotificationResult.allSent(messageId));
    } else if (emailEnabled) {
      return Either.right(NotificationResult.emailOnly(messageId));
    } else {
      return Either.right(NotificationResult.none());
    }
  }

  @Override
  public Either<OrderError, NotificationResult> sendCancellationNotification(
      OrderId orderId, Customer customer, String reason) {
    var messageId = "MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    if (emailEnabled) {
      sentNotifications.add(
          new NotificationRecord(
              NotificationType.CANCELLATION,
              customer.email(),
              "Order " + orderId + " cancelled. Reason: " + reason));
    }

    if (emailEnabled) {
      return Either.right(NotificationResult.emailOnly(messageId));
    } else {
      return Either.right(NotificationResult.none());
    }
  }

  /** Record of a sent notification for testing verification. */
  public record NotificationRecord(NotificationType type, String recipient, String message) {}

  /** Types of notifications sent. */
  public enum NotificationType {
    ORDER_CONFIRMATION,
    ORDER_CONFIRMATION_SMS,
    SHIPMENT_NOTIFICATION,
    SHIPMENT_NOTIFICATION_SMS,
    CANCELLATION
  }
}
