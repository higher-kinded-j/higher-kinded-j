// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.error;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.time.TimeSource;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.jspecify.annotations.Nullable;

/**
 * Sealed error hierarchy for the order workflow.
 *
 * <p>Using a sealed interface enables exhaustive pattern matching and generates prisms for
 * type-safe error handling.
 *
 * <p>Each variant declares only its domain-specific components plus one {@link ErrorEnvelope}
 * carrying the shared fields (code, message, timestamp) and the typed {@link OrderErrorContext}
 * (issue #610). The {@code code()}/{@code message()}/{@code timestamp()}/{@code context()}
 * accessors delegate to the envelope, and the generated {@link OrderErrors} companion supplies
 * per-variant factories, the fluent context builder and the context wither.
 */
@GeneratePrisms
@GenerateErrorEnvelope
public sealed interface OrderError
    permits OrderError.ValidationError,
        OrderError.CustomerError,
        OrderError.InventoryError,
        OrderError.DiscountError,
        OrderError.PaymentError,
        OrderError.ShippingError,
        OrderError.NotificationError,
        OrderError.SystemError {

  /** The shared envelope: code, message, timestamp and typed context. */
  ErrorEnvelope<OrderErrorContext> envelope();

  /** Error code for categorisation. */
  default String code() {
    return envelope().code();
  }

  /** Human-readable error message. */
  default String message() {
    return envelope().message();
  }

  /** When the error occurred. */
  default Instant timestamp() {
    return envelope().timestamp();
  }

  /** Additional typed context for debugging and logging. */
  default OrderErrorContext context() {
    return envelope().context();
  }

  /**
   * Rebuilds this error with its context transformed by {@code edit}, seeded from the current
   * context; code, message and timestamp are preserved.
   */
  default OrderError editContext(UnaryOperator<OrderErrors.ContextBuilder> edit) {
    return OrderErrors.editContext(this, edit);
  }

  // -------------------------------------------------------------------------
  // Validation Errors
  // -------------------------------------------------------------------------

  /**
   * Validation errors from input data issues.
   *
   * @param fieldErrors the individual field validation failures
   * @param envelope the shared error envelope
   */
  record ValidationError(List<FieldError> fieldErrors, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {

    /**
     * Creates a validation error with field errors.
     *
     * @param message the overall message
     * @param fieldErrors the field-level errors
     */
    public ValidationError(String message, List<FieldError> fieldErrors) {
      this(
          fieldErrors,
          ErrorEnvelope.of("VALIDATION_ERROR", message, OrderErrors.context().build()));
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
   * @param customerId the customer ID involved
   * @param envelope the shared error envelope
   */
  record CustomerError(String customerId, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {

    /**
     * Creates an error for a customer not found.
     *
     * @param customerId the missing customer ID
     * @return a CustomerError
     */
    public static CustomerError notFound(String customerId) {
      return new CustomerError(
          customerId,
          ErrorEnvelope.of(
              "CUSTOMER_NOT_FOUND",
              "Customer not found: " + customerId,
              OrderErrors.context().customerId(new CustomerId(customerId)).build()));
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
          customerId,
          ErrorEnvelope.of(
              "CUSTOMER_SUSPENDED",
              "Customer account suspended: " + reason,
              OrderErrors.context().customerId(new CustomerId(customerId)).reason(reason).build()));
    }
  }

  // -------------------------------------------------------------------------
  // Inventory Errors
  // -------------------------------------------------------------------------

  /**
   * Inventory or stock errors.
   *
   * @param unavailableProducts products that are unavailable
   * @param envelope the shared error envelope
   */
  record InventoryError(List<String> unavailableProducts, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {

    /**
     * Creates an error for out of stock products, stamped from the live clock.
     *
     * @param productIds the unavailable product IDs
     * @return an InventoryError
     */
    public static InventoryError outOfStock(List<String> productIds) {
      return outOfStock(TimeSource.system(), productIds);
    }

    /**
     * Creates an error for out of stock products, stamped from the given time source.
     *
     * @param time the time source the timestamp is read from
     * @param productIds the unavailable product IDs
     * @return an InventoryError
     */
    public static InventoryError outOfStock(TimeSource time, List<String> productIds) {
      return new InventoryError(
          productIds,
          ErrorEnvelope.of(
              time,
              "OUT_OF_STOCK",
              "Products unavailable: " + String.join(", ", productIds),
              OrderErrors.context()
                  .productIds(productIds.stream().map(ProductId::new).toList())
                  .build()));
    }

    /**
     * Creates an error for a failed reservation, stamped from the live clock.
     *
     * @param reason the failure reason
     * @return an InventoryError
     */
    public static InventoryError reservationFailed(String reason) {
      return reservationFailed(TimeSource.system(), reason);
    }

    /**
     * Creates an error for a failed reservation, stamped from the given time source.
     *
     * @param time the time source the timestamp is read from
     * @param reason the failure reason
     * @return an InventoryError
     */
    public static InventoryError reservationFailed(TimeSource time, String reason) {
      return new InventoryError(
          List.of(),
          ErrorEnvelope.of(
              time,
              "RESERVATION_FAILED",
              "Could not reserve inventory: " + reason,
              OrderErrors.context().reason(reason).build()));
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
          List.of(productId),
          ErrorEnvelope.of(
              "PARTIAL_STOCK",
              "Only " + available + " of " + requested + " available for " + productId,
              OrderErrors.context()
                  .productId(new ProductId(productId))
                  .availableQuantity(available)
                  .requestedQuantity(requested)
                  .build()));
    }
  }

  // -------------------------------------------------------------------------
  // Discount Errors
  // -------------------------------------------------------------------------

  /**
   * Discount or promo code errors.
   *
   * @param promoCode the promo code involved, if any
   * @param envelope the shared error envelope
   */
  record DiscountError(Optional<String> promoCode, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {

    /**
     * Creates an error for an invalid promo code.
     *
     * @param code the invalid code
     * @return a DiscountError
     */
    public static DiscountError invalidCode(String code) {
      return new DiscountError(
          Optional.of(code),
          ErrorEnvelope.of(
              "INVALID_PROMO_CODE",
              "Promo code not valid: " + code,
              OrderErrors.context().promoCode(code).build()));
    }

    /**
     * Creates an error for an expired promo code.
     *
     * @param code the expired code
     * @return a DiscountError
     */
    public static DiscountError expired(String code) {
      return new DiscountError(
          Optional.of(code),
          ErrorEnvelope.of(
              "PROMO_CODE_EXPIRED",
              "Promo code has expired: " + code,
              OrderErrors.context().promoCode(code).build()));
    }
  }

  // -------------------------------------------------------------------------
  // Payment Errors
  // -------------------------------------------------------------------------

  /**
   * Payment processing errors.
   *
   * @param transactionId the transaction ID, if available
   * @param envelope the shared error envelope
   */
  record PaymentError(Optional<String> transactionId, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {

    /**
     * Creates an error for a declined payment.
     *
     * @param reason the decline reason
     * @return a PaymentError
     */
    public static PaymentError declined(String reason) {
      return new PaymentError(
          Optional.empty(),
          ErrorEnvelope.of(
              "PAYMENT_DECLINED",
              "Payment was declined: " + reason,
              OrderErrors.context().reason(reason).build()));
    }

    /**
     * Creates an error for insufficient funds.
     *
     * @return a PaymentError
     */
    public static PaymentError insufficientFunds() {
      return new PaymentError(
          Optional.empty(),
          ErrorEnvelope.of(
              "INSUFFICIENT_FUNDS",
              "Insufficient funds for payment",
              OrderErrors.context().build()));
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
          Optional.ofNullable(transactionId), // documented "if available" - may be absent
          ErrorEnvelope.of(
              "PAYMENT_PROCESSING_FAILED",
              "Payment processing failed: " + cause.getMessage(),
              OrderErrors.context().exceptionType(cause.getClass().getSimpleName()).build()));
    }
  }

  // -------------------------------------------------------------------------
  // Shipping Errors
  // -------------------------------------------------------------------------

  /**
   * Shipping errors.
   *
   * @param recoverable whether this error can be retried
   * @param envelope the shared error envelope
   */
  record ShippingError(boolean recoverable, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {

    /**
     * Creates an error for an invalid address.
     *
     * @param reason the validation failure reason
     * @return a ShippingError
     */
    public static ShippingError invalidAddress(String reason) {
      return new ShippingError(
          false,
          ErrorEnvelope.of(
              "INVALID_ADDRESS",
              "Shipping address invalid: " + reason,
              OrderErrors.context().reason(reason).build()));
    }

    /**
     * Creates an error for a temporary failure (can retry).
     *
     * @param reason the failure reason
     * @return a ShippingError
     */
    public static ShippingError temporaryFailure(String reason) {
      return new ShippingError(
          true,
          ErrorEnvelope.of(
              "SHIPPING_TEMPORARY_FAILURE",
              "Temporary shipping service failure: " + reason,
              OrderErrors.context().reason(reason).build()));
    }

    /**
     * Creates an error when no carrier is available.
     *
     * @return a ShippingError
     */
    public static ShippingError noCarrierAvailable() {
      return new ShippingError(
          false,
          ErrorEnvelope.of(
              "NO_CARRIER_AVAILABLE",
              "No shipping carrier available for this destination",
              OrderErrors.context().build()));
    }
  }

  // -------------------------------------------------------------------------
  // Notification Errors
  // -------------------------------------------------------------------------

  /**
   * Notification errors (non-critical).
   *
   * @param envelope the shared error envelope
   */
  record NotificationError(ErrorEnvelope<OrderErrorContext> envelope) implements OrderError {

    /**
     * Creates an error for an email send failure.
     *
     * @param reason the failure reason
     * @return a NotificationError
     */
    public static NotificationError emailFailed(String reason) {
      return new NotificationError(
          ErrorEnvelope.of(
              "EMAIL_SEND_FAILED",
              "Failed to send email: " + reason,
              OrderErrors.context().reason(reason).build()));
    }

    /**
     * Creates an error for an SMS send failure.
     *
     * @param reason the failure reason
     * @return a NotificationError
     */
    public static NotificationError smsFailed(String reason) {
      return new NotificationError(
          ErrorEnvelope.of(
              "SMS_SEND_FAILED",
              "Failed to send SMS: " + reason,
              OrderErrors.context().reason(reason).build()));
    }
  }

  // -------------------------------------------------------------------------
  // System Errors
  // -------------------------------------------------------------------------

  /**
   * System-level errors (infrastructure, configuration, etc.).
   *
   * @param cause the underlying exception, if any
   * @param envelope the shared error envelope
   */
  record SystemError(Optional<Throwable> cause, ErrorEnvelope<OrderErrorContext> envelope)
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
          Optional.of(cause),
          ErrorEnvelope.of(
              "SYSTEM_ERROR",
              message + ": " + cause.getMessage(),
              OrderErrors.context().exceptionType(cause.getClass().getSimpleName()).build()));
    }

    /**
     * Creates a timeout error.
     *
     * @param operation the operation that timed out
     * @return a SystemError
     */
    public static SystemError timeout(String operation) {
      return new SystemError(
          Optional.empty(),
          ErrorEnvelope.of(
              "TIMEOUT",
              "Operation timed out: " + operation,
              OrderErrors.context().operation(operation).build()));
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
          Optional.empty(),
          ErrorEnvelope.of(
              "TIMEOUT",
              "Operation timed out after " + timeout.toMillis() + "ms: " + operation,
              OrderErrors.context().operation(operation).timeout(timeout).build()));
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
          Optional.of(cause),
          ErrorEnvelope.of(
              "UNEXPECTED_ERROR",
              message + ": " + cause.getMessage(),
              OrderErrors.context().exceptionType(cause.getClass().getSimpleName()).build()));
    }

    /**
     * Creates a circuit breaker open error.
     *
     * @param service the service that is unavailable
     * @return a SystemError
     */
    public static SystemError circuitBreakerOpen(String service) {
      return new SystemError(
          Optional.empty(),
          ErrorEnvelope.of(
              "CIRCUIT_BREAKER_OPEN",
              "Service temporarily unavailable: " + service,
              OrderErrors.context().service(service).build()));
    }
  }
}
