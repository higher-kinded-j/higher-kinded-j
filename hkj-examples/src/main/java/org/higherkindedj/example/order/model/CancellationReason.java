// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

/**
 * Reason for order cancellation.
 *
 * @param code the cancellation code
 * @param description human-readable description
 * @param initiatedBy who initiated the cancellation
 */
public record CancellationReason(Code code, String description, InitiatedBy initiatedBy) {
  /** Cancellation reason codes. */
  public enum Code {
    CUSTOMER_REQUEST,
    PAYMENT_FAILED,
    INVENTORY_UNAVAILABLE,
    FRAUD_SUSPECTED,
    DUPLICATE_ORDER,
    SHIPPING_UNAVAILABLE,
    SYSTEM_ERROR
  }

  /** Who initiated the cancellation. */
  public enum InitiatedBy {
    CUSTOMER,
    SYSTEM,
    ADMIN
  }

  /**
   * Creates a customer-initiated cancellation reason.
   *
   * @param description the reason description
   * @return a CancellationReason
   */
  public static CancellationReason customerRequest(String description) {
    return new CancellationReason(Code.CUSTOMER_REQUEST, description, InitiatedBy.CUSTOMER);
  }

  /**
   * Creates a payment failure cancellation reason.
   *
   * @param failureReason the payment failure details
   * @return a CancellationReason
   */
  public static CancellationReason paymentFailed(String failureReason) {
    return new CancellationReason(
        Code.PAYMENT_FAILED, "Payment failed: " + failureReason, InitiatedBy.SYSTEM);
  }

  /**
   * Creates an inventory unavailability cancellation reason.
   *
   * @return a CancellationReason
   */
  public static CancellationReason inventoryUnavailable() {
    return new CancellationReason(
        Code.INVENTORY_UNAVAILABLE,
        "Required inventory is no longer available",
        InitiatedBy.SYSTEM);
  }

  /**
   * Creates a fraud suspected cancellation reason.
   *
   * @param details the fraud detection details
   * @return a CancellationReason
   */
  public static CancellationReason fraudSuspected(String details) {
    return new CancellationReason(
        Code.FRAUD_SUSPECTED, "Fraud suspected: " + details, InitiatedBy.SYSTEM);
  }
}
