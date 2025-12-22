// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Audit logging for the Order Workflow V2 example.
 *
 * <p>This package provides an immutable {@link org.higherkindedj.example.order.audit.AuditLog} that
 * accumulates entries throughout the workflow using the Writer pattern.
 *
 * <h2>Integration with WriterPath</h2>
 *
 * <p>The {@code AuditLog} class provides a {@code Monoid} instance that enables automatic log
 * accumulation when used with {@code WriterPath}:
 *
 * <pre>{@code
 * WriterPath<AuditLog, ValidatedOrder> validated =
 *     Path.writer(AuditLog.monoid(), validatedOrder)
 *         .tell(AuditLog.of("ORDER_VALIDATED", "Order " + orderId + " validated"));
 * }</pre>
 */
@NullMarked
package org.higherkindedj.example.order.audit;

import org.jspecify.annotations.NullMarked;
