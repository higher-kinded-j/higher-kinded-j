// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import java.util.UUID;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.ProductId;

/**
 * Represents a back-order for items that were unavailable at order time. Back-orders are
 * automatically fulfilled when stock becomes available.
 *
 * @param backOrderId unique identifier for this back-order
 * @param originalOrderId the order this back-order is associated with
 * @param productId the product that was unavailable
 * @param quantity the quantity back-ordered
 * @param unitPrice the price locked in at order time
 * @param status current back-order status
 * @param createdAt when the back-order was created
 * @param estimatedAvailable estimated date when stock will be available
 */
public record BackOrder(
    String backOrderId,
    OrderId originalOrderId,
    ProductId productId,
    int quantity,
    Money unitPrice,
    Status status,
    Instant createdAt,
    Instant estimatedAvailable) {
  /** Back-order status. */
  public enum Status {
    /** Waiting for stock to become available. */
    PENDING,

    /** Stock is available, ready to ship. */
    READY_TO_SHIP,

    /** Back-order has been shipped. */
    SHIPPED,

    /** Back-order was cancelled. */
    CANCELLED,

    /** Back-order has been fulfilled. */
    FULFILLED
  }

  /**
   * Creates a new pending back-order.
   *
   * @param originalOrderId the original order ID
   * @param productId the product to back-order
   * @param quantity the quantity
   * @param unitPrice the locked-in price
   * @param estimatedDays estimated days until stock available
   * @return a new BackOrder in PENDING status
   */
  public static BackOrder create(
      OrderId originalOrderId,
      ProductId productId,
      int quantity,
      Money unitPrice,
      int estimatedDays) {
    return new BackOrder(
        "BO-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
        originalOrderId,
        productId,
        quantity,
        unitPrice,
        Status.PENDING,
        Instant.now(),
        Instant.now().plusSeconds(estimatedDays * 24L * 60 * 60));
  }

  /**
   * Calculates the total value of this back-order.
   *
   * @return quantity multiplied by unit price
   */
  public Money totalValue() {
    return unitPrice.multiply(quantity);
  }

  /**
   * Returns a copy with updated status.
   *
   * @param newStatus the new status
   * @return a new BackOrder with the updated status
   */
  public BackOrder withStatus(Status newStatus) {
    return new BackOrder(
        backOrderId,
        originalOrderId,
        productId,
        quantity,
        unitPrice,
        newStatus,
        createdAt,
        estimatedAvailable);
  }

  /**
   * Checks if this back-order can be cancelled.
   *
   * @return true if cancellation is allowed
   */
  public boolean isCancellable() {
    return status == Status.PENDING || status == Status.READY_TO_SHIP;
  }
}
