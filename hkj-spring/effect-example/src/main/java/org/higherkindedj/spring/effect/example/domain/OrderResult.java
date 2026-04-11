// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.domain;

/**
 * Result of an order operation.
 *
 * @param orderId the generated order ID
 * @param status the order status
 * @param message optional message (e.g., rejection reason)
 */
public record OrderResult(String orderId, OrderStatus status, String message) {

  /** Creates a confirmed order result. */
  public static OrderResult confirmed(String orderId) {
    return new OrderResult(orderId, OrderStatus.CONFIRMED, "Order confirmed");
  }

  /** Creates a rejected order result with a reason. */
  public static OrderResult rejected(String reason) {
    return new OrderResult("", OrderStatus.REJECTED, reason);
  }
}
