// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.PaymentService;
import org.higherkindedj.hkt.either.Either;

/** In-memory implementation of PaymentService for testing and examples. */
public class InMemoryPaymentService implements PaymentService {

  private final Map<String, PaymentConfirmation> transactions = new ConcurrentHashMap<>();
  private final Set<String> declinedCards = ConcurrentHashMap.newKeySet();

  public InMemoryPaymentService() {
    // Add some test cards that will be declined
    declinedCards.add("4111111111111111");
  }

  public void addDeclinedCard(String cardNumber) {
    declinedCards.add(cardNumber);
  }

  @Override
  public Either<OrderError, PaymentConfirmation> processPayment(
      OrderId orderId, Money amount, PaymentMethod paymentMethod) {
    // Check for declined cards
    if (paymentMethod instanceof PaymentMethod.CreditCard card) {
      if (declinedCards.contains(card.cardNumber())) {
        return Either.left(OrderError.PaymentError.declined("Card declined by issuer"));
      }
    }

    if (paymentMethod instanceof PaymentMethod.DebitCard card) {
      if (declinedCards.contains(card.cardNumber())) {
        return Either.left(OrderError.PaymentError.insufficientFunds());
      }
    }

    // Simulate successful payment
    var transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    var authCode = "AUTH-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    var confirmation = new PaymentConfirmation(transactionId, amount, Instant.now(), authCode);

    transactions.put(transactionId, confirmation);
    return Either.right(confirmation);
  }

  @Override
  public Either<OrderError, PaymentConfirmation> refundPayment(String transactionId, Money amount) {
    var original = transactions.get(transactionId);
    if (original == null) {
      return Either.left(
          OrderError.PaymentError.processingFailed(
              transactionId, new IllegalArgumentException("Transaction not found")));
    }

    // Create refund confirmation
    var refundId = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    var refundConfirmation =
        new PaymentConfirmation(
            refundId, amount, Instant.now(), "REFUND-" + original.authorizationCode());

    transactions.put(refundId, refundConfirmation);
    return Either.right(refundConfirmation);
  }

  @Override
  public Either<OrderError, PaymentMethod> validatePaymentMethod(PaymentMethod paymentMethod) {
    // Basic validation
    return switch (paymentMethod) {
      case PaymentMethod.CreditCard card -> {
        if (card.cardNumber().length() < 13) {
          yield Either.left(
              OrderError.ValidationError.forField("cardNumber", "Card number too short"));
        }
        yield Either.right(paymentMethod);
      }
      case PaymentMethod.DebitCard card -> {
        if (card.cardNumber().length() < 13) {
          yield Either.left(
              OrderError.ValidationError.forField("cardNumber", "Card number too short"));
        }
        yield Either.right(paymentMethod);
      }
      default -> Either.right(paymentMethod);
    };
  }
}
