// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.interpreter.CapturingNotificationInterpreter;
import org.higherkindedj.example.payment.interpreter.FixedRiskInterpreter;
import org.higherkindedj.example.payment.interpreter.InMemoryLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.RecordingGatewayInterpreter;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.example.payment.service.PaymentService;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/** Solutions for Tutorial 02: Multiple Interpreters. */
public class Tutorial02_MultipleInterpreters_Solution {

  private static final Customer CUSTOMER =
      new Customer(
          new CustomerId("cust-001"),
          "Jane Smith",
          "jane@example.com",
          new CustomerId("acc-001"),
          null); // No backup payment method (@Nullable field, not used in this tutorial)

  private static final Money AMOUNT = Money.gbp("25.00");
  private static final PaymentMethod VISA = new PaymentMethod.CreditCard("4242", "VISA");

  @Test
  void exercise1_buildTestProgram() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    assertThat(program).isNotNull();
  }

  @Test
  void exercise2_interpretWithRecording() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var gateway = new RecordingGatewayInterpreter();
    var fraud = new FixedRiskInterpreter(RiskScore.of(10));
    var ledger = new InMemoryLedgerInterpreter();
    ledger.setBalance(new CustomerId("acc-001"), Money.gbp("1000.00"));
    var notification = new CapturingNotificationInterpreter();

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
    Id<PaymentResult> id =
        IdKindHelper.ID.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));
    PaymentResult result = id.value();

    assertThat(result.isApproved()).isTrue();
    assertThat(gateway.calls()).isNotEmpty();
  }

  @Test
  void exercise3_differentInterpreterDifferentOutcome() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var gateway = new RecordingGatewayInterpreter();
    var fraud = new FixedRiskInterpreter(RiskScore.of(95));
    var ledger = new InMemoryLedgerInterpreter();
    var notification = new CapturingNotificationInterpreter();

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
    Id<PaymentResult> id =
        IdKindHelper.ID.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));

    assertThat(id.value().isDeclined()).isTrue();
    assertThat(gateway.calls()).isEmpty();
    assertThat(notification.alerts()).hasSize(1);
  }
}
