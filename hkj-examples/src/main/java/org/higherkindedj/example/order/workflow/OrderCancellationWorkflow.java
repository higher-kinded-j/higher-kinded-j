// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.audit.AuditLog;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.CancellationReason;
import org.higherkindedj.example.order.model.CancellationResult;
import org.higherkindedj.example.order.model.NotificationResult;
import org.higherkindedj.example.order.model.ProcessedOrder;
import org.higherkindedj.example.order.service.InventoryService;
import org.higherkindedj.example.order.service.NotificationService;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.example.order.service.ShippingService;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;

/**
 * Workflow for cancelling orders with compensating transactions.
 *
 * <p>This workflow demonstrates the saga pattern for handling order cancellation:
 *
 * <ol>
 *   <li>Validate the order can be cancelled
 *   <li>Release inventory reservation (compensating action)
 *   <li>Refund payment (compensating action)
 *   <li>Cancel shipment (compensating action)
 *   <li>Update order status
 *   <li>Send cancellation notification
 * </ol>
 *
 * <p>Each step handles the case where the previous action may or may not have occurred, making the
 * cancellation idempotent and resilient.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * OrderCancellationWorkflow cancellation = new OrderCancellationWorkflow(
 *     inventoryService, paymentService, shippingService,
 *     notificationService, config
 * );
 *
 * EitherPath<OrderError, CancellationResult> result =
 *     cancellation.cancel(processedOrder, CancellationReason.customerRequest("Changed mind"));
 *
 * result.run().fold(
 *     error -> handleCancellationError(error),
 *     success -> notifyUserOfCancellation(success)
 * );
 * }</pre>
 */
public class OrderCancellationWorkflow {

  private final InventoryService inventoryService;
  private final PaymentService paymentService;
  private final ShippingService shippingService;
  private final NotificationService notificationService;
  private final WorkflowConfig config;

  /** Creates a new order cancellation workflow. */
  public OrderCancellationWorkflow(
      InventoryService inventoryService,
      PaymentService paymentService,
      ShippingService shippingService,
      NotificationService notificationService,
      WorkflowConfig config) {
    this.inventoryService = inventoryService;
    this.paymentService = paymentService;
    this.shippingService = shippingService;
    this.notificationService = notificationService;
    this.config = config;
  }

  /**
   * Cancels an order, performing compensating transactions as needed.
   *
   * <p>Uses via() chains for composing the cancellation steps. Each step checks whether the
   * previous action occurred and performs the appropriate compensation.
   *
   * @param order the order to cancel
   * @param reason the cancellation reason
   * @return either an error or the cancellation result
   */
  public EitherPath<OrderError, CancellationResult> cancel(
      ProcessedOrder order, CancellationReason reason) {
    return validateCancellable(order)
        .via(
            validOrder ->
                releaseInventory(validOrder)
                    .via(
                        inventoryReleased ->
                            refundPayment(validOrder)
                                .via(
                                    refundResult ->
                                        cancelShipment(validOrder)
                                            .via(
                                                shipmentCancelled ->
                                                    sendCancellationNotification(validOrder, reason)
                                                        .map(
                                                            notified ->
                                                                buildResult(
                                                                    validOrder,
                                                                    reason,
                                                                    inventoryReleased,
                                                                    refundResult,
                                                                    shipmentCancelled,
                                                                    notified))))));
  }

  // -------------------------------------------------------------------------
  // Cancellation Steps
  // -------------------------------------------------------------------------

  private EitherPath<OrderError, ProcessedOrder> validateCancellable(ProcessedOrder order) {
    if (order.isCancellable()) {
      return Path.right(order);
    }
    return Path.left(
        new OrderError.ValidationError(
            "Order cannot be cancelled in status: " + order.status(),
            List.of(
                new OrderError.FieldError("status", "Order is not cancellable", order.status()))));
  }

  private EitherPath<OrderError, Boolean> releaseInventory(ProcessedOrder order) {
    if (!order.hasInventoryToRelease()) {
      // No inventory to release, just succeed with false
      return Path.right(false);
    }

    return order
        .inventoryReservation()
        .map(
            reservation ->
                Path.either(inventoryService.releaseReservation(reservation.reservationId()))
                    .map(v -> true)
                    .recoverWith(
                        error -> {
                          // Log error but continue - inventory will expire anyway
                          return Path.right(false);
                        }))
        .orElse(Path.right(false));
  }

  private EitherPath<OrderError, Optional<CancellationResult.RefundResult>> refundPayment(
      ProcessedOrder order) {
    if (!order.requiresRefund()) {
      // No payment to refund
      return Path.right(Optional.empty());
    }

    return order
        .paymentConfirmation()
        .map(
            payment ->
                Path.either(
                        paymentService.refundPayment(
                            payment.transactionId(), payment.chargedAmount()))
                    .map(
                        refundConfirmation ->
                            Optional.of(
                                new CancellationResult.RefundResult(
                                    payment.transactionId(),
                                    refundConfirmation.transactionId(),
                                    refundConfirmation.chargedAmount(),
                                    Instant.now()))))
        .orElse(Path.right(Optional.empty()));
  }

  private EitherPath<OrderError, Boolean> cancelShipment(ProcessedOrder order) {
    if (!order.hasShipmentToCancel()) {
      return Path.right(false);
    }

    return order
        .shipmentInfo()
        .map(
            shipment ->
                Path.either(shippingService.cancelShipment(shipment.shipmentId()))
                    .map(v -> true)
                    .recoverWith(
                        error -> {
                          // Shipment might already be dispatched
                          return Path.right(false);
                        }))
        .orElse(Path.right(false));
  }

  private EitherPath<OrderError, Boolean> sendCancellationNotification(
      ProcessedOrder order, CancellationReason reason) {
    return Path.either(
            notificationService.sendCancellationNotification(
                order.orderId(), order.customer(), reason.description()))
        .map(NotificationResult::emailSent)
        .recoverWith(
            error -> {
              // Notification failures are non-critical
              return Path.right(false);
            });
  }

  private CancellationResult buildResult(
      ProcessedOrder order,
      CancellationReason reason,
      boolean inventoryReleased,
      Optional<CancellationResult.RefundResult> refundResult,
      boolean shipmentCancelled,
      boolean notificationSent) {
    var auditLog =
        AuditLog.EMPTY.append(
            AuditLog.of(
                "CANCELLATION_STARTED",
                "Order " + order.orderId() + " cancellation initiated: " + reason.code()));

    if (inventoryReleased) {
      auditLog =
          auditLog.append(AuditLog.of("INVENTORY_RELEASED", "Inventory reservation released"));
    }

    if (refundResult.isPresent()) {
      var refund = refundResult.get();
      auditLog =
          auditLog.append(
              AuditLog.of(
                  "PAYMENT_REFUNDED",
                  "Refund " + refund.refundTransactionId() + " for " + refund.refundedAmount()));
    }

    if (shipmentCancelled) {
      auditLog = auditLog.append(AuditLog.of("SHIPMENT_CANCELLED", "Shipment cancelled"));
    }

    auditLog =
        auditLog.append(
            AuditLog.of("CANCELLATION_COMPLETE", "Order " + order.orderId() + " cancelled"));

    var builder = CancellationResult.builder(order.orderId(), reason);
    if (inventoryReleased) {
      builder.inventoryReleased();
    }
    if (shipmentCancelled) {
      builder.shipmentCancelled();
    }
    if (notificationSent) {
      builder.notificationSent();
    }
    refundResult.ifPresent(builder::withRefund);
    builder.withAuditLog(auditLog);

    return builder.build();
  }
}
