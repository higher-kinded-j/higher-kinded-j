// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Error types for the Order Workflow V2 example.
 *
 * <p>This package contains a sealed error hierarchy that enables exhaustive pattern matching and
 * type-safe error handling throughout the workflow.
 *
 * <h2>Error Hierarchy</h2>
 *
 * <p>The {@link org.higherkindedj.example.order.error.OrderError} sealed interface has the
 * following subtypes:
 *
 * <ul>
 *   <li>{@code ValidationError} - Input validation failures with field-level details
 *   <li>{@code CustomerError} - Customer lookup or status issues
 *   <li>{@code InventoryError} - Stock availability or reservation failures
 *   <li>{@code DiscountError} - Promo code validation failures
 *   <li>{@code PaymentError} - Payment processing failures
 *   <li>{@code ShippingError} - Address validation or carrier issues
 *   <li>{@code NotificationError} - Non-critical notification failures
 *   <li>{@code SystemError} - Infrastructure or timeout errors
 * </ul>
 *
 * <h2>Usage with Effect Paths</h2>
 *
 * <pre>{@code
 * EitherPath<OrderError, ValidatedOrder> result =
 *     Path.either(validateOrder(request))
 *         .recoverWith(e -> switch (e) {
 *             case ValidationError ve -> Path.left(ve);
 *             case SystemError se when se.code().equals("TIMEOUT") ->
 *                 retryValidation(request);
 *             default -> Path.left(e);
 *         });
 * }</pre>
 */
@NullMarked
package org.higherkindedj.example.order.error;

import org.jspecify.annotations.NullMarked;
