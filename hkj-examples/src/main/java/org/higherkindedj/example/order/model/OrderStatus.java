// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

/** Order lifecycle status. */
public enum OrderStatus {
  /** Order has been created but not yet validated. */
  PENDING,

  /** Order has been validated and is being processed. */
  PROCESSING,

  /** Inventory has been reserved for the order. */
  INVENTORY_RESERVED,

  /** Payment has been processed successfully. */
  PAYMENT_COMPLETE,

  /** Shipment has been created. */
  SHIPPED,

  /** Order has been delivered. */
  DELIVERED,

  /** Order has been cancelled. */
  CANCELLED,

  /** Order has been refunded. */
  REFUNDED,

  /** Order partially fulfilled (some items shipped, others back-ordered). */
  PARTIALLY_FULFILLED;

  /**
   * Checks if the order can be cancelled in this status.
   *
   * @return true if cancellation is allowed
   */
  public boolean isCancellable() {
    return switch (this) {
      case PENDING, PROCESSING, INVENTORY_RESERVED, PAYMENT_COMPLETE -> true;
      case SHIPPED, DELIVERED, CANCELLED, REFUNDED, PARTIALLY_FULFILLED -> false;
    };
  }

  /**
   * Checks if the order is in a terminal state.
   *
   * @return true if no further state transitions are possible
   */
  public boolean isTerminal() {
    return switch (this) {
      case DELIVERED, CANCELLED, REFUNDED -> true;
      default -> false;
    };
  }

  /**
   * Checks if inventory should be released on cancellation.
   *
   * @return true if inventory was reserved
   */
  public boolean hasReservedInventory() {
    return switch (this) {
      case INVENTORY_RESERVED, PAYMENT_COMPLETE, SHIPPED, PARTIALLY_FULFILLED -> true;
      default -> false;
    };
  }

  /**
   * Checks if a refund is required on cancellation.
   *
   * @return true if payment was processed
   */
  public boolean requiresRefund() {
    return switch (this) {
      case PAYMENT_COMPLETE, SHIPPED, PARTIALLY_FULFILLED -> true;
      default -> false;
    };
  }
}
