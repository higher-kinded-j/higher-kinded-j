// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.interpreter.CapturingNotificationInterpreter;
import org.higherkindedj.example.payment.interpreter.FailingGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.FixedRiskInterpreter;
import org.higherkindedj.example.payment.interpreter.InMemoryLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.QuoteGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.RecordingGatewayInterpreter;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.example.payment.service.PaymentService;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for the Payment Processing example.
 *
 * <p>Demonstrates testing without mocks: each test uses purpose-built interpreters that target the
 * Id monad for pure, synchronous execution. No mock frameworks, no reflection, no side effects.
 */
@DisplayName("Payment Processing")
class PaymentProcessingTest {

  private static final Customer CUSTOMER =
      new Customer(
          new CustomerId("cust-001"),
          "Jane Smith",
          "jane@example.com",
          new CustomerId("acc-001"),
          new PaymentMethod.CreditCard("4242", "VISA"));

  private static final Money TEN_POUNDS = Money.gbp("10.00");
  private static final PaymentMethod VISA = new PaymentMethod.CreditCard("4242", "VISA");

  /**
   * Builds a program and interprets it with the given interpreters. Uses local {@code var} to
   * preserve the concrete composed witness type (avoiding wildcard capture issues).
   */
  private static PaymentResult buildAndInterpret(
      RecordingGatewayInterpreter gateway,
      FixedRiskInterpreter fraud,
      InMemoryLedgerInterpreter ledger,
      CapturingNotificationInterpreter notification) {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, TEN_POUNDS, VISA);
    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
    return IdKindHelper.ID
        .<PaymentResult>narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()))
        .value();
  }

  @Nested
  @DisplayName("Successful Payments")
  class SuccessfulPayments {

    @Test
    @DisplayName("should approve payment with low risk and sufficient funds")
    void approvesLowRiskPayment() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(15));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
      var notification = new CapturingNotificationInterpreter();

      PaymentResult result = buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(result).isInstanceOf(PaymentResult.Approved.class);
      assertThat(result.isApproved()).isTrue();
    }

    @Test
    @DisplayName("should record gateway charge call")
    void recordsGatewayCall() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(10));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("1000.00"));
      var notification = new CapturingNotificationInterpreter();

      buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(gateway.calls()).isNotEmpty();
      assertThat(gateway.calls()).anyMatch(call -> call.startsWith("charge:"));
    }

    @Test
    @DisplayName("should create ledger entry for approved payment")
    void createsLedgerEntry() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(10));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("1000.00"));
      var notification = new CapturingNotificationInterpreter();

      buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(ledger.entries()).hasSize(1);
      assertThat(ledger.entries().getFirst().amount()).isEqualTo(TEN_POUNDS);
    }

    @Test
    @DisplayName("should send receipt for approved payment")
    void sendsReceipt() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(10));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("1000.00"));
      var notification = new CapturingNotificationInterpreter();

      buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(notification.receipts()).hasSize(1);
      assertThat(notification.receipts().getFirst()).contains("jane@example.com");
    }
  }

  @Nested
  @DisplayName("Declined Payments")
  class DeclinedPayments {

    @Test
    @DisplayName("should decline high-risk transaction")
    void declinesHighRisk() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(95));
      var ledger = new InMemoryLedgerInterpreter();
      var notification = new CapturingNotificationInterpreter();

      PaymentResult result = buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(result).isInstanceOf(PaymentResult.Declined.class);
      assertThat(result.isDeclined()).isTrue();
    }

    @Test
    @DisplayName("should not call gateway for high-risk transaction")
    void doesNotChargeHighRisk() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(95));
      var ledger = new InMemoryLedgerInterpreter();
      var notification = new CapturingNotificationInterpreter();

      buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(gateway.calls()).isEmpty();
    }

    @Test
    @DisplayName("should alert fraud team for high-risk transaction")
    void alertsFraudTeam() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(95));
      var ledger = new InMemoryLedgerInterpreter();
      var notification = new CapturingNotificationInterpreter();

      buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(notification.alerts()).hasSize(1);
    }

    @Test
    @DisplayName("should decline when funds are insufficient")
    void declinesInsufficientFunds() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(5));
      var ledger = new InMemoryLedgerInterpreter();
      // Balance is zero — insufficient
      var notification = new CapturingNotificationInterpreter();

      PaymentResult result = buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(result).isInstanceOf(PaymentResult.Declined.class);
      if (result instanceof PaymentResult.Declined declined) {
        assertThat(declined.reason()).contains("Insufficient funds");
      }
    }

    @Test
    @DisplayName("should approve when balance exactly matches amount")
    void approvesExactBalance() {
      var gateway = new RecordingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(5));
      var ledger = new InMemoryLedgerInterpreter();
      // Balance exactly equals the payment amount
      ledger.setBalance(new CustomerId("acc-001"), TEN_POUNDS);
      var notification = new CapturingNotificationInterpreter();

      PaymentResult result = buildAndInterpret(gateway, fraud, ledger, notification);

      // Equal balance is not "less than" so should proceed
      assertThat(result.isApproved()).isTrue();
    }

    @Test
    @DisplayName("should decline when score exceeds the risk threshold")
    void declinesAboveThreshold() {
      var gateway = new RecordingGatewayInterpreter();
      // Risk threshold is 70; score of 71 exceeds it
      var fraud = new FixedRiskInterpreter(RiskScore.of(71));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
      var notification = new CapturingNotificationInterpreter();

      PaymentResult result = buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(result.isDeclined()).isTrue();
    }

    @Test
    @DisplayName("should approve at exactly the risk threshold boundary")
    void approvesAtExactThreshold() {
      var gateway = new RecordingGatewayInterpreter();
      // Risk threshold is 70; score of exactly 70 does NOT exceed it
      var fraud = new FixedRiskInterpreter(RiskScore.of(70));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
      var notification = new CapturingNotificationInterpreter();

      PaymentResult result = buildAndInterpret(gateway, fraud, ledger, notification);

      assertThat(result.isApproved()).isTrue();
    }
  }

  @Nested
  @DisplayName("Failed Payments")
  class FailedPayments {

    @Test
    @DisplayName("should return failed result when gateway charge fails")
    void returnsFailedOnChargeDecline() {
      var service = PaymentService.create();
      var program = service.processPayment(CUSTOMER, TEN_POUNDS, VISA);

      var gateway = new FailingGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(10));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
      var notification = new CapturingNotificationInterpreter();

      var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
      PaymentResult result =
          IdKindHelper.ID
              .<PaymentResult>narrow(
                  PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()))
              .value();

      assertThat(result).isInstanceOf(PaymentResult.Failed.class);
      if (result instanceof PaymentResult.Failed failed) {
        assertThat(failed.chargeResult().isFailed()).isTrue();
        assertThat(failed.chargeResult().errorMessage()).contains("Card declined");
      }
      // No ledger entry should be recorded for a failed charge
      assertThat(ledger.entries()).isEmpty();
      // No receipt should be sent for a failed charge
      assertThat(notification.receipts()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Quote Interpretation")
  class QuoteInterpretation {

    @Test
    @DisplayName("should estimate fees without side effects")
    void estimatesFees() {
      var service = PaymentService.create();
      var program = service.processPayment(CUSTOMER, TEN_POUNDS, VISA);

      var gateway = new QuoteGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(5));
      var ledger = new InMemoryLedgerInterpreter();
      ledger.setBalance(new CustomerId("acc-001"), Money.gbp("10000.00"));
      var notification = new CapturingNotificationInterpreter();

      var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
      PaymentResult result =
          IdKindHelper.ID
              .<PaymentResult>narrow(
                  PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()))
              .value();

      assertThat(result).isInstanceOf(PaymentResult.Approved.class);
      if (result instanceof PaymentResult.Approved approved) {
        // Quote interpreter adds 2.9% + GBP 0.30 processing fee
        // GBP 10.00 * 0.029 + 0.30 = GBP 0.59, total = GBP 10.59
        assertThat(approved.chargeResult().amount().amount())
            .isEqualByComparingTo(new BigDecimal("10.59"));
      }
    }
  }

  @Nested
  @DisplayName("Same Program, Multiple Interpretations")
  class MultipleInterpretations {

    @Test
    @DisplayName("should produce consistent business logic across interpretations")
    void consistentBusinessLogic() {
      // Create service locally to get concrete type (not wildcard)
      var service = PaymentService.create();
      var program = service.processPayment(CUSTOMER, TEN_POUNDS, VISA);

      // ...interpreted two different ways...
      var recording = new RecordingGatewayInterpreter();
      var quote = new QuoteGatewayInterpreter();
      var fraud = new FixedRiskInterpreter(RiskScore.of(15));
      var ledger1 = new InMemoryLedgerInterpreter();
      ledger1.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
      var ledger2 = new InMemoryLedgerInterpreter();
      ledger2.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
      var notif1 = new CapturingNotificationInterpreter();
      var notif2 = new CapturingNotificationInterpreter();

      var interp1 = Interpreters.combine(recording, fraud, ledger1, notif1);
      var interp2 = Interpreters.combine(quote, fraud, ledger2, notif2);

      PaymentResult result1 =
          IdKindHelper.ID
              .<PaymentResult>narrow(
                  PaymentEffectsWiring.interpret(program, interp1, IdMonad.instance()))
              .value();
      PaymentResult result2 =
          IdKindHelper.ID
              .<PaymentResult>narrow(
                  PaymentEffectsWiring.interpret(program, interp2, IdMonad.instance()))
              .value();

      // ...both approve the payment (consistent business logic)
      assertThat(result1.isApproved()).isTrue();
      assertThat(result2.isApproved()).isTrue();
    }
  }
}
