// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.error;

import java.io.Serializable;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * Represents a hierarchy of domain-specific errors that can occur within the order processing
 * workflow.
 *
 * <p>This {@code sealed interface} ensures that all direct permitted subtypes are known at compile
 * time, which is beneficial for exhaustive error handling, such as in {@code switch} expressions or
 * pattern matching. Each implementing record represents a distinct type of error that might arise
 * during different stages of order processing (e.g., validation, stock checking, payment).
 *
 * <p>All domain errors must provide a user-friendly error {@link #message()}.
 *
 * <p>Being {@link Serializable} allows these error objects to be potentially transferred across
 * different layers or systems if needed, though in this context, it's primarily a marker.
 *
 * @see ValidationError
 * @see StockError
 * @see PaymentError
 * @see ShippingError
 * @see NotificationError
 */
@GeneratePrisms
public sealed interface DomainError extends Serializable {
  /**
   * Retrieves the user-friendly message associated with this domain error.
   *
   * @return A non-null string describing the error.
   */
  String message();

  /**
   * Represents an error that occurred during data validation. This could be due to invalid input
   * parameters, incorrect format, or unmet business rules related to the data structure or content.
   *
   * @param message A detailed message describing the validation failure.
   */
  record ValidationError(String message) implements DomainError {
    /**
     * {@inheritDoc}
     *
     * @return The validation error message.
     */
    @Override
    public String message() {
      return message;
    }
  }

  /**
   * Represents an error related to product stock availability. This typically occurs when a
   * requested product is out of stock or insufficient quantity is available.
   *
   * @param productId The identifier of the product for which the stock error occurred.
   */
  record StockError(String productId) implements DomainError {
    /**
     * {@inheritDoc}
     *
     * @return A message indicating that the product with the given {@code productId} is out of
     *     stock.
     */
    @Override
    public String message() {
      return "Out of stock for product: " + productId;
    }
  }

  /**
   * Represents an error that occurred during the payment processing stage. This could be due to
   * issues like declined card, insufficient funds, or payment gateway failures.
   *
   * @param reason A message detailing the reason for the payment failure.
   */
  record PaymentError(String reason) implements DomainError {
    /**
     * {@inheritDoc}
     *
     * @return A message indicating that payment failed, along with the {@code reason}.
     */
    @Override
    public String message() {
      return "Payment failed: " + reason;
    }
  }

  /**
   * Represents an error encountered during the order shipping process. This could involve issues
   * with address validation, carrier selection, or logistics.
   *
   * @param reason A message detailing the reason for the shipping failure.
   */
  record ShippingError(String reason) implements DomainError {
    /**
     * {@inheritDoc}
     *
     * @return A message indicating that shipping failed, along with the {@code reason}.
     */
    @Override
    public String message() {
      return "Shipping failed: " + reason;
    }
  }

  /**
   * Represents an error that occurred while attempting to send a notification. This could be
   * related to email delivery failures, SMS gateway issues, or other notification channel problems.
   *
   * @param reason A message detailing the reason for the notification failure.
   */
  record NotificationError(String reason) implements DomainError {
    /**
     * {@inheritDoc}
     *
     * @return A message indicating that notification failed, along with the {@code reason}.
     */
    @Override
    public String message() {
      return "Notification failed: " + reason;
    }
  }
}
