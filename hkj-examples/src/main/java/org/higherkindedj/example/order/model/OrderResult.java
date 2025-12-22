// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import org.higherkindedj.example.order.audit.AuditLog;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * The final result of a successful order workflow.
 *
 * @param orderId the order identifier
 * @param customerId the customer identifier
 * @param totalCharged the total amount charged
 * @param transactionId the payment transaction ID
 * @param trackingNumber the shipment tracking number
 * @param estimatedDelivery the estimated delivery time
 * @param auditLog the complete audit trail for this order
 */
@GenerateLenses
@GenerateFocus
public record OrderResult(
    OrderId orderId,
    CustomerId customerId,
    Money totalCharged,
    String transactionId,
    String trackingNumber,
    Instant estimatedDelivery,
    AuditLog auditLog) {}
