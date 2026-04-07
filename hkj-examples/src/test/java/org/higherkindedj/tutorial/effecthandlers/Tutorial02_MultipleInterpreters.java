// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effecthandlers;

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
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Multiple Interpreters
 *
 * <p>The same Free monad program can be interpreted in fundamentally different ways. This tutorial
 * explores how the Payment Processing example uses different interpreters for different scenarios.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>Same program, different interpreters
 *   <li>{@code Interpreters.combine} merges multiple interpreters
 *   <li>Test interpreters use the Id monad (no side effects)
 *   <li>Recording interpreters capture operations for assertion
 * </ul>
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial02_MultipleInterpreters {

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  private static final Customer CUSTOMER =
      new Customer(
          new CustomerId("cust-001"),
          "Jane Smith",
          "jane@example.com",
          new CustomerId("acc-001"),
          null); // No backup payment method (@Nullable field, not used in this tutorial)

  private static final Money AMOUNT = Money.gbp("25.00");
  private static final PaymentMethod VISA = new PaymentMethod.CreditCard("4242", "VISA");

  /**
   * Exercise 1: Building a test program
   *
   * <p>Use {@code PaymentService.create()} to create a service and build a payment program.
   *
   * <p>Task: Create a payment service and call processPayment
   */
  @Test
  void exercise1_buildTestProgram() {
    // TODO: Create a PaymentService using PaymentService.create()
    // Then call processPayment(CUSTOMER, AMOUNT, VISA)
    Free<?, PaymentResult> program = answerRequired();

    assertThat(program).isNotNull();
  }

  /**
   * Exercise 2: Interpreting with recording interpreters
   *
   * <p>Recording interpreters capture all operations for later inspection.
   *
   * <p>Task: Combine interpreters and interpret the program
   */
  @Test
  void exercise2_interpretWithRecording() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var gateway = new RecordingGatewayInterpreter();
    var fraud = new FixedRiskInterpreter(RiskScore.of(10));
    var ledger = new InMemoryLedgerInterpreter();
    ledger.setBalance(new CustomerId("acc-001"), Money.gbp("1000.00"));
    var notification = new CapturingNotificationInterpreter();

    // TODO: Combine interpreters using Interpreters.combine(gateway, fraud, ledger, notification)
    // Then interpret using PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance())
    // Then narrow the result using IdKindHelper.ID.narrow(...)
    PaymentResult result = answerRequired();

    assertThat(result.isApproved()).isTrue();
    assertThat(gateway.calls()).isNotEmpty();
  }

  /**
   * Exercise 3: Different interpreter, different outcome
   *
   * <p>The SAME program with a high-risk interpreter produces a decline.
   *
   * <p>Task: Use a FixedRiskInterpreter with a high score (95) and verify the decline
   */
  @Test
  void exercise3_differentInterpreterDifferentOutcome() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var gateway = new RecordingGatewayInterpreter();
    // TODO: Create a FixedRiskInterpreter with score 95
    var fraud = new FixedRiskInterpreter(answerRequired());
    var ledger = new InMemoryLedgerInterpreter();
    var notification = new CapturingNotificationInterpreter();

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
    Id<PaymentResult> id =
        IdKindHelper.ID.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));

    assertThat(id.value().isDeclined()).isTrue();
    // High risk means no gateway calls
    assertThat(gateway.calls()).isEmpty();
    // But fraud alert should be sent
    assertThat(notification.alerts()).hasSize(1);
  }
}
