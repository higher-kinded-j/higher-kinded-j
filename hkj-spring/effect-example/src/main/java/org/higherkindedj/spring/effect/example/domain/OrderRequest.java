// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.domain;

/**
 * Request to place a new order.
 *
 * @param customerId the customer placing the order
 * @param itemId the item to order
 * @param quantity how many units to order
 */
public record OrderRequest(String customerId, String itemId, int quantity) {}
