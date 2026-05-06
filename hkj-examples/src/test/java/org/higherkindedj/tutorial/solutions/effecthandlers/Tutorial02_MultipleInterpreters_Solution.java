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

/**
 * Solution for Tutorial02 MultipleInterpreters — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
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

  /**
   * Why this is idiomatic: building a {@code processPayment} program is just constructing a value —
   * no interpreter has run, no gateway has been called. The test asserts shape, not effect.
   *
   * <p>Alternative: bypass the program and call the gateway directly. Loses the ability to swap
   * interpreters; the rest of the tutorial relies on the same program being interpreted
   * differently.
   *
   * <p>Common wrong attempt: assume building the program also runs it. The Free encoding is the
   * entire point — programs are values until {@code interpret} is called.
   */
  @Test
  void exercise1_buildTestProgram() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    assertThat(program).isNotNull();
  }

  /**
   * Why this is idiomatic: combine four small interpreters — gateway, fraud, ledger, notifications
   * — into one composite via {@code Interpreters.combine}. The same payment program now runs
   * against fakes that record what happened.
   *
   * <p>Alternative: use a mocking library to stub each dependency. Mocks couple to call shape; the
   * recorded interpreter exposes the actual algebra calls, which stay stable across refactors of
   * the implementation.
   *
   * <p>Common wrong attempt: write one giant test interpreter that knows about every algebra. The
   * pieces become hard to reuse; small focused interpreters compose freely.
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

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
    Id<PaymentResult> id =
        IdKindHelper.ID.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));
    PaymentResult result = id.value();

    assertThat(result.isApproved()).isTrue();
    assertThat(gateway.calls()).isNotEmpty();
  }

  /**
   * Why this is idiomatic: swap one interpreter — the fraud check now returns a high-risk score —
   * and the same program declines. The rest of the wiring is untouched; the test proves which
   * decision produced which outcome.
   *
   * <p>Alternative: write two separate payment functions, one for low-risk and one for high-risk.
   * Same coverage; loses the symmetry that makes the Free design pay off.
   *
   * <p>Common wrong attempt: rebuild the program for the second test instead of just
   * re-interpreting. The program is reusable; only the interpreter changes.
   */
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
