// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/** Service for payment processing. */
@GeneratePathBridge
public interface PaymentService {

  /**
   * Processes a payment for an order.
   *
   * @param orderId the order identifier
   * @param amount the amount to charge
   * @param paymentMethod the payment method to use
   * @return either an error or the payment confirmation
   */
  @PathVia(doc = "Processes payment for the specified amount")
  Either<OrderError, PaymentConfirmation> processPayment(
      OrderId orderId, Money amount, PaymentMethod paymentMethod);

  /**
   * Refunds a payment.
   *
   * @param transactionId the original transaction ID
   * @param amount the amount to refund
   * @return either an error or the refund confirmation
   */
  @PathVia(doc = "Refunds a previously processed payment")
  Either<OrderError, PaymentConfirmation> refundPayment(String transactionId, Money amount);

  /**
   * Validates that a payment method is acceptable.
   *
   * @param paymentMethod the payment method to validate
   * @return either an error or the validated payment method
   */
  @PathVia(doc = "Validates payment method details")
  Either<OrderError, PaymentMethod> validatePaymentMethod(PaymentMethod paymentMethod);
}
