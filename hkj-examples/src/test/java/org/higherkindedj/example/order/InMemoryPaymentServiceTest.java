// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.time.Duration;
import java.time.Instant;
import org.higherkindedj.example.order.model.PaymentConfirmation;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link InMemoryPaymentService} stamps confirmations from an injected {@link
 * TimeSource}, and that the no-arg constructor still declines the test card.
 */
@DisplayName("InMemoryPaymentService")
class InMemoryPaymentServiceTest {

  private static final Instant START = Instant.parse("2026-07-07T00:00:00Z");
  private static final Money AMOUNT = Money.gbp("10.00");
  private static final PaymentMethod.CreditCard ACCEPTED_CARD =
      new PaymentMethod.CreditCard("5500000000000004", "12", "2030", "123");
  private static final PaymentMethod.CreditCard DECLINED_CARD =
      new PaymentMethod.CreditCard("4111111111111111", "12", "2030", "123");

  @Test
  @DisplayName("a payment is stamped with the fixed instant")
  void paymentIsStampedWithTheFixedInstant() {
    var clock = SteppableClock.startingAt(START);
    var service = new InMemoryPaymentService(TimeSource.of(clock));

    PaymentConfirmation confirmation =
        service.processPayment(OrderId.generate(), AMOUNT, ACCEPTED_CARD).getRight();

    assertThat(confirmation.processedAt()).isEqualTo(START);
  }

  @Test
  @DisplayName("a refund is stamped later and the original keeps its time")
  void refundIsStampedLaterWhileOriginalKeepsItsTime() {
    var clock = SteppableClock.startingAt(START);
    var service = new InMemoryPaymentService(TimeSource.of(clock));

    PaymentConfirmation original =
        service.processPayment(OrderId.generate(), AMOUNT, ACCEPTED_CARD).getRight();
    clock.advance(Duration.ofMinutes(5));
    PaymentConfirmation refund = service.refundPayment(original.transactionId(), AMOUNT).getRight();

    assertThat(refund.processedAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));
    assertThat(original.processedAt()).isEqualTo(START);
  }

  @Test
  @DisplayName("the default constructor still declines the test card")
  void defaultConstructorStillDeclinesTheTestCard() {
    var service = new InMemoryPaymentService();

    var result = service.processPayment(OrderId.generate(), AMOUNT, DECLINED_CARD);

    assertThatEither(result).isLeft();
  }
}
