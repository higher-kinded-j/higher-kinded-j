// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import java.util.Optional;
import org.higherkindedj.example.order.audit.AuditLog;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Result of a successful order cancellation.
 *
 * @param orderId the cancelled order ID
 * @param reason the cancellation reason
 * @param refundResult the refund details, if payment was refunded
 * @param inventoryReleased whether inventory reservation was released
 * @param shipmentCancelled whether shipment was cancelled
 * @param cancelledAt when the cancellation occurred
 * @param notificationSent whether the customer was notified
 * @param auditLog the cancellation audit trail
 */
@GenerateLenses
public record CancellationResult(
    OrderId orderId,
    CancellationReason reason,
    Optional<RefundResult> refundResult,
    boolean inventoryReleased,
    boolean shipmentCancelled,
    Instant cancelledAt,
    boolean notificationSent,
    AuditLog auditLog) {
  /**
   * Creates a builder for CancellationResult.
   *
   * @param orderId the order being cancelled
   * @param reason the cancellation reason
   * @return a new builder
   */
  public static Builder builder(OrderId orderId, CancellationReason reason) {
    return new Builder(orderId, reason);
  }

  /** Builder for CancellationResult. */
  public static class Builder {
    private final OrderId orderId;
    private final CancellationReason reason;
    private Optional<RefundResult> refundResult = Optional.empty();
    private boolean inventoryReleased = false;
    private boolean shipmentCancelled = false;
    private Instant cancelledAt = Instant.now();
    private boolean notificationSent = false;
    private AuditLog auditLog = AuditLog.EMPTY;

    private Builder(OrderId orderId, CancellationReason reason) {
      this.orderId = orderId;
      this.reason = reason;
    }

    public Builder withRefund(RefundResult refund) {
      this.refundResult = Optional.of(refund);
      return this;
    }

    public Builder inventoryReleased() {
      this.inventoryReleased = true;
      return this;
    }

    public Builder shipmentCancelled() {
      this.shipmentCancelled = true;
      return this;
    }

    public Builder notificationSent() {
      this.notificationSent = true;
      return this;
    }

    public Builder withAuditLog(AuditLog log) {
      this.auditLog = log;
      return this;
    }

    public CancellationResult build() {
      return new CancellationResult(
          orderId,
          reason,
          refundResult,
          inventoryReleased,
          shipmentCancelled,
          cancelledAt,
          notificationSent,
          auditLog);
    }
  }

  /**
   * Result of a payment refund.
   *
   * @param originalTransactionId the original payment transaction ID
   * @param refundTransactionId the refund transaction ID
   * @param refundedAmount the amount refunded
   * @param refundedAt when the refund was processed
   */
  public record RefundResult(
      String originalTransactionId,
      String refundTransactionId,
      Money refundedAmount,
      Instant refundedAt) {
    /**
     * Creates a result indicating no refund was required.
     *
     * @return an empty refund result
     */
    public static Optional<RefundResult> notRequired() {
      return Optional.empty();
    }
  }
}
