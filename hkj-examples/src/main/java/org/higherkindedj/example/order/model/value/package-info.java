// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Value objects for the Order Workflow V2 example.
 *
 * <p>This package contains type-safe value objects that prevent common programming errors like
 * mixing up different types of identifiers. Each value object validates its invariants in the
 * constructor, ensuring that only valid instances can exist.
 *
 * <h2>Value Objects</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.model.value.OrderId} - Type-safe order identifier
 *   <li>{@link org.higherkindedj.example.order.model.value.CustomerId} - Type-safe customer
 *       identifier
 *   <li>{@link org.higherkindedj.example.order.model.value.ProductId} - Type-safe product
 *       identifier
 *   <li>{@link org.higherkindedj.example.order.model.value.Money} - Currency-aware money with safe
 *       arithmetic
 *   <li>{@link org.higherkindedj.example.order.model.value.Percentage} - Validated percentage
 *       (0-100)
 *   <li>{@link org.higherkindedj.example.order.model.value.PromoCode} - Promotional code with
 *       discount
 * </ul>
 */
@NullMarked
package org.higherkindedj.example.order.model.value;

import org.jspecify.annotations.NullMarked;
