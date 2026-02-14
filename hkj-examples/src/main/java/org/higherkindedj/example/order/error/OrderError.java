// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.error;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.jspecify.annotations.Nullable;

/**
 * Sealed error hierarchy for the order workflow.
 *
 * <p>Using a sealed interface enables exhaustive pattern matching and generates prisms for
 * type-safe error handling.
 */
@GeneratePrisms
public sealed interface OrderError
    permits OrderError.ValidationError,
        OrderError.CustomerError,
        OrderError.InventoryError,
        OrderError.DiscountError,
        OrderError.PaymentError,
        OrderError.ShippingError,
        OrderError.NotificationError,
        OrderError.SystemError {

  /**
   * Error code for categorisation.
   *
   * @return the error code
   */
  String code();

  /**
   * Human-readable error message.
   *
   * @return the message
   */
  String message();

  /**
   * When the error occurred.
   *
   * @return the timestamp
   */
  Instant timestamp();

  /**
   * Additional context for debugging and logging.
   *
   * @return the context map
   */
  Map<String, Object> context();

  // -------------------------------------------------------------------------
  // Validation Errors
  // -------------------------------------------------------------------------

  /**
   * Validation errors from input data issues.
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param fieldErrors the individual field validation failures
   */
  record ValidationError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      List<FieldError> fieldErrors)
      implements OrderError {

    /**
     * Creates a validation error with field errors.
     *
     * @param message the overall message
     * @param fieldErrors the field-level errors
     */
    public ValidationError(String message, List<FieldError> fieldErrors) {
      this("VALIDATION_ERROR", message, Instant.now(), Map.of(), fieldErrors);
    }

    /**
     * Creates a validation error for a single field.
     *
     * @param field the field name
     * @param message the error message
     * @return a ValidationError
     */
    public static ValidationError forField(String field, String message) {
      return new ValidationError(
          "Validation failed for " + field, List.of(new FieldError(field, message, null)));
    }
  }

  /**
   * A single field validation error.
   *
   * @param field the field that failed validation
   * @param message the error message
   * @param rejectedValue the value that was rejected (may be null)
   */
  record FieldError(String field, String message, @Nullable Object rejectedValue) {}

  // -------------------------------------------------------------------------
  // Customer Errors
  // -------------------------------------------------------------------------

  /**
   * Customer lookup or validation errors.
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param customerId the customer ID involved
   */
  record CustomerError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      String customerId)
      implements OrderError {

    /**
     * Creates an error for a customer not found.
     *
     * @param customerId the missing customer ID
     * @return a CustomerError
     */
    public static CustomerError notFound(String customerId) {
      return new CustomerError(
          "CUSTOMER_NOT_FOUND",
          "Customer not found: " + customerId,
          Instant.now(),
          Map.of("customerId", customerId),
          customerId);
    }

    /**
     * Creates an error for a suspended customer.
     *
     * @param customerId the customer ID
     * @param reason the suspension reason
     * @return a CustomerError
     */
    public static CustomerError suspended(String customerId, String reason) {
      return new CustomerError(
          "CUSTOMER_SUSPENDED",
          "Customer account suspended: " + reason,
          Instant.now(),
          Map.of("customerId", customerId, "reason", reason),
          customerId);
    }
  }

  // -------------------------------------------------------------------------
  // Inventory Errors
  // -------------------------------------------------------------------------

  /**
   * Inventory or stock errors.
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param unavailableProducts products that are unavailable
   */
  record InventoryError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      List<String> unavailableProducts)
      implements OrderError {

    /**
     * Creates an error for out of stock products.
     *
     * @param productIds the unavailable product IDs
     * @return an InventoryError
     */
    public static InventoryError outOfStock(List<String> productIds) {
      return new InventoryError(
          "OUT_OF_STOCK",
          "Products unavailable: " + String.join(", ", productIds),
          Instant.now(),
          Map.of("productIds", productIds),
          productIds);
    }

    /**
     * Creates an error for a failed reservation.
     *
     * @param reason the failure reason
     * @return an InventoryError
     */
    public static InventoryError reservationFailed(String reason) {
      return new InventoryError(
          "RESERVATION_FAILED",
          "Could not reserve inventory: " + reason,
          Instant.now(),
          Map.of("reason", reason),
          List.of());
    }

    /**
     * Creates an error for partial stock availability.
     *
     * @param available the available quantity
     * @param requested the requested quantity
     * @param productId the product ID
     * @return an InventoryError
     */
    public static InventoryError partialStock(int available, int requested, String productId) {
      return new InventoryError(
          "PARTIAL_STOCK",
          "Only " + available + " of " + requested + " available for " + productId,
          Instant.now(),
          Map.of("available", available, "requested", requested, "productId", productId),
          List.of(productId));
    }
  }

  // -------------------------------------------------------------------------
  // Discount Errors
  // -------------------------------------------------------------------------

  /**
   * Discount or promo code errors.
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param promoCode the promo code involved, if any
   */
  record DiscountError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      Optional<String> promoCode)
      implements OrderError {

    /**
     * Creates an error for an invalid promo code.
     *
     * @param code the invalid code
     * @return a DiscountError
     */
    public static DiscountError invalidCode(String code) {
      return new DiscountError(
          "INVALID_PROMO_CODE",
          "Promo code not valid: " + code,
          Instant.now(),
          Map.of("promoCode", code),
          Optional.of(code));
    }

    /**
     * Creates an error for an expired promo code.
     *
     * @param code the expired code
     * @return a DiscountError
     */
    public static DiscountError expired(String code) {
      return new DiscountError(
          "PROMO_CODE_EXPIRED",
          "Promo code has expired: " + code,
          Instant.now(),
          Map.of("promoCode", code),
          Optional.of(code));
    }
  }

  // -------------------------------------------------------------------------
  // Payment Errors
  // -------------------------------------------------------------------------

  /**
   * Payment processing errors.
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param transactionId the transaction ID, if available
   */
  record PaymentError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      Optional<String> transactionId)
      implements OrderError {

    /**
     * Creates an error for a declined payment.
     *
     * @param reason the decline reason
     * @return a PaymentError
     */
    public static PaymentError declined(String reason) {
      return new PaymentError(
          "PAYMENT_DECLINED",
          "Payment was declined: " + reason,
          Instant.now(),
          Map.of("reason", reason),
          Optional.empty());
    }

    /**
     * Creates an error for insufficient funds.
     *
     * @return a PaymentError
     */
    public static PaymentError insufficientFunds() {
      return new PaymentError(
          "INSUFFICIENT_FUNDS",
          "Insufficient funds for payment",
          Instant.now(),
          Map.of(),
          Optional.empty());
    }

    /**
     * Creates an error for a processing failure.
     *
     * @param transactionId the transaction ID, if available
     * @param cause the underlying cause
     * @return a PaymentError
     */
    public static PaymentError processingFailed(String transactionId, Throwable cause) {
      return new PaymentError(
          "PAYMENT_PROCESSING_FAILED",
          "Payment processing failed: " + cause.getMessage(),
          Instant.now(),
          Map.of("cause", cause.getClass().getSimpleName()),
          Optional.of(transactionId));
    }
  }

  // -------------------------------------------------------------------------
  // Shipping Errors
  // -------------------------------------------------------------------------

  /**
   * Shipping errors.
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param recoverable whether this error can be retried
   */
  record ShippingError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      boolean recoverable)
      implements OrderError {

    /**
     * Creates an error for an invalid address.
     *
     * @param reason the validation failure reason
     * @return a ShippingError
     */
    public static ShippingError invalidAddress(String reason) {
      return new ShippingError(
          "INVALID_ADDRESS",
          "Shipping address invalid: " + reason,
          Instant.now(),
          Map.of("reason", reason),
          false);
    }

    /**
     * Creates an error for a temporary failure (can retry).
     *
     * @param reason the failure reason
     * @return a ShippingError
     */
    public static ShippingError temporaryFailure(String reason) {
      return new ShippingError(
          "SHIPPING_TEMPORARY_FAILURE",
          "Temporary shipping service failure: " + reason,
          Instant.now(),
          Map.of("reason", reason),
          true);
    }

    /**
     * Creates an error when no carrier is available.
     *
     * @return a ShippingError
     */
    public static ShippingError noCarrierAvailable() {
      return new ShippingError(
          "NO_CARRIER_AVAILABLE",
          "No shipping carrier available for this destination",
          Instant.now(),
          Map.of(),
          false);
    }
  }

  // -------------------------------------------------------------------------
  // Notification Errors
  // -------------------------------------------------------------------------

  /**
   * Notification errors (non-critical).
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   */
  record NotificationError(
      String code, String message, Instant timestamp, Map<String, Object> context)
      implements OrderError {

    /**
     * Creates an error for an email send failure.
     *
     * @param reason the failure reason
     * @return a NotificationError
     */
    public static NotificationError emailFailed(String reason) {
      return new NotificationError(
          "EMAIL_SEND_FAILED",
          "Failed to send email: " + reason,
          Instant.now(),
          Map.of("reason", reason));
    }

    /**
     * Creates an error for an SMS send failure.
     *
     * @param reason the failure reason
     * @return a NotificationError
     */
    public static NotificationError smsFailed(String reason) {
      return new NotificationError(
          "SMS_SEND_FAILED",
          "Failed to send SMS: " + reason,
          Instant.now(),
          Map.of("reason", reason));
    }
  }

  // -------------------------------------------------------------------------
  // System Errors
  // -------------------------------------------------------------------------

  /**
   * System-level errors (infrastructure, configuration, etc.).
   *
   * @param code the error code
   * @param message the error message
   * @param timestamp when the error occurred
   * @param context additional context
   * @param cause the underlying exception, if any
   */
  record SystemError(
      String code,
      String message,
      Instant timestamp,
      Map<String, Object> context,
      Optional<Throwable> cause)
      implements OrderError {

    /**
     * Creates a system error from an exception.
     *
     * @param message the error message
     * @param cause the underlying cause
     * @return a SystemError
     */
    public static SystemError fromException(String message, Throwable cause) {
      return new SystemError(
          "SYSTEM_ERROR",
          message + ": " + cause.getMessage(),
          Instant.now(),
          Map.of("exceptionType", cause.getClass().getSimpleName()),
          Optional.of(cause));
    }

    /**
     * Creates a timeout error.
     *
     * @param operation the operation that timed out
     * @return a SystemError
     */
    public static SystemError timeout(String operation) {
      return new SystemError(
          "TIMEOUT",
          "Operation timed out: " + operation,
          Instant.now(),
          Map.of("operation", operation),
          Optional.empty());
    }

    /**
     * Creates a timeout error with duration.
     *
     * @param operation the operation that timed out
     * @param timeout the timeout duration
     * @return a SystemError
     */
    public static SystemError timeout(String operation, Duration timeout) {
      return new SystemError(
          "TIMEOUT",
          "Operation timed out after " + timeout.toMillis() + "ms: " + operation,
          Instant.now(),
          Map.of("operation", operation, "timeoutMs", timeout.toMillis()),
          Optional.empty());
    }

    /**
     * Creates an unexpected error.
     *
     * @param message the error message
     * @param cause the underlying cause
     * @return a SystemError
     */
    public static SystemError unexpected(String message, Throwable cause) {
      return new SystemError(
          "UNEXPECTED_ERROR",
          message + ": " + cause.getMessage(),
          Instant.now(),
          Map.of("exceptionType", cause.getClass().getSimpleName()),
          Optional.of(cause));
    }

    /**
     * Creates a circuit breaker open error.
     *
     * @param service the service that is unavailable
     * @return a SystemError
     */
    public static SystemError circuitBreakerOpen(String service) {
      return new SystemError(
          "CIRCUIT_BREAKER_OPEN",
          "Service temporarily unavailable: " + service,
          Instant.now(),
          Map.of("service", service),
          Optional.empty());
    }
  }
}
