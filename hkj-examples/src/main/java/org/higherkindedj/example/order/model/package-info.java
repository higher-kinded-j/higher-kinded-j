// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Domain models for the Order Workflow V2 example.
 *
 * <p>This package contains immutable record types that represent the domain model for order
 * processing. All records use code generation annotations to automatically create lenses and focus
 * paths for type-safe manipulation.
 *
 * <h2>Request Models</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.model.OrderRequest} - Initial order request from
 *       client
 *   <li>{@link org.higherkindedj.example.order.model.OrderLineRequest} - Line item in a request
 *   <li>{@link org.higherkindedj.example.order.model.ShippingAddress} - Delivery address
 *   <li>{@link org.higherkindedj.example.order.model.PaymentMethod} - Payment method sealed
 *       hierarchy
 * </ul>
 *
 * <h2>Validated Models</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.model.ValidatedOrder} - Order after validation
 *   <li>{@link org.higherkindedj.example.order.model.ValidatedOrderLine} - Validated line with
 *       product details
 *   <li>{@link org.higherkindedj.example.order.model.ValidatedShippingAddress} - Validated address
 *       with shipping zone
 * </ul>
 *
 * <h2>Reference Data</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.model.Customer} - Customer details
 *   <li>{@link org.higherkindedj.example.order.model.Product} - Product catalogue entry
 * </ul>
 *
 * <h2>Result Types</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.model.InventoryReservation} - Inventory reservation
 *       result
 *   <li>{@link org.higherkindedj.example.order.model.DiscountResult} - Discount calculation result
 *   <li>{@link org.higherkindedj.example.order.model.PaymentConfirmation} - Payment confirmation
 *   <li>{@link org.higherkindedj.example.order.model.ShipmentInfo} - Shipment creation result
 *   <li>{@link org.higherkindedj.example.order.model.NotificationResult} - Notification delivery
 *       result
 *   <li>{@link org.higherkindedj.example.order.model.OrderResult} - Final workflow result
 * </ul>
 *
 * @see org.higherkindedj.example.order.model.value Value objects package
 */
@NullMarked
package org.higherkindedj.example.order.model;

import org.jspecify.annotations.NullMarked;
