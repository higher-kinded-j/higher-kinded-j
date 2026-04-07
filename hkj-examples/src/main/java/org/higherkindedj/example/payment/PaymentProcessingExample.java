// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment;

import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.interpreter.CapturingNotificationInterpreter;
import org.higherkindedj.example.payment.interpreter.FixedRiskInterpreter;
import org.higherkindedj.example.payment.interpreter.InMemoryLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionFraudInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.ProductionNotificationInterpreter;
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
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.jspecify.annotations.NullMarked;

/**
 * Demonstrates payment processing with four interpretation strategies.
 *
 * <p>This example shows the core value proposition of effect handlers: the same program, written
 * once, interpreted in fundamentally different ways without modification:
 *
 * <ol>
 *   <li><b>Production</b> — IO monad with simulated external services
 *   <li><b>Testing</b> — Id monad with recording and fixed-response interpreters
 *   <li><b>Quote</b> — Id monad that estimates fees without side effects
 *   <li><b>High Risk</b> — Id monad demonstrating risk-based decline
 * </ol>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.payment.PaymentProcessingExample}
 */
@NullMarked
public final class PaymentProcessingExample {

  private PaymentProcessingExample() {}

  /** Sample customer for all examples. */
  private static final Customer CUSTOMER =
      new Customer(
          new CustomerId("cust-001"),
          "Jane Smith",
          "jane@example.com",
          new CustomerId("acc-001"),
          new PaymentMethod.CreditCard("4242", "VISA"));

  /** Sample payment amount. */
  private static final Money AMOUNT = Money.gbp("49.99");

  /** Sample payment method. */
  private static final PaymentMethod VISA = new PaymentMethod.CreditCard("4242", "VISA");

  /**
   * Entry point demonstrating all four interpretation strategies.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    System.out.println("=== Payment Processing: Same Program, Four Interpretations ===");
    System.out.println();

    runProductionInterpretation();
    System.out.println();

    runTestInterpretation();
    System.out.println();

    runQuoteInterpretation();
    System.out.println();

    runHighRiskInterpretation();
  }

  /** Interpretation 1: Production — IO monad with simulated services. */
  private static void runProductionInterpretation() {
    System.out.println("--- 1. Production Interpretation (IO Monad) ---");

    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    // Combine interpreters for the four effect algebras
    var interpreter =
        Interpreters.combine(
            new ProductionGatewayInterpreter(),
            new ProductionFraudInterpreter(),
            new ProductionLedgerInterpreter(),
            new ProductionNotificationInterpreter());

    // Interpret into IO and execute
    IO<PaymentResult> io =
        IOKindHelper.IO_OP.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IOMonad.INSTANCE));
    PaymentResult result = io.unsafeRunSync();

    System.out.println("  Result: " + result);
  }

  /** Interpretation 2: Testing — Id monad with recording interpreters. */
  private static void runTestInterpretation() {
    System.out.println("--- 2. Test Interpretation (Id Monad, Recording) ---");

    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var gateway = new RecordingGatewayInterpreter();
    var fraud = new FixedRiskInterpreter(RiskScore.of(15));
    var ledger = new InMemoryLedgerInterpreter();
    ledger.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
    var notification = new CapturingNotificationInterpreter();

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);

    Id<PaymentResult> id =
        IdKindHelper.ID.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));
    PaymentResult result = id.value();

    System.out.println("  Result: " + result);
    System.out.println("  Gateway calls: " + gateway.calls());
    System.out.println("  Ledger entries: " + ledger.entries().size());
    System.out.println("  Receipts sent: " + notification.receipts());
    System.out.println("  Fraud alerts: " + notification.alerts());
  }

  /** Interpretation 3: Quote — Id monad that estimates fees without side effects. */
  private static void runQuoteInterpretation() {
    System.out.println("--- 3. Quote Interpretation (Fee Estimation) ---");

    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var gateway = new QuoteGatewayInterpreter();
    var fraud = new FixedRiskInterpreter(RiskScore.of(5));
    var ledger = new InMemoryLedgerInterpreter();
    ledger.setBalance(new CustomerId("acc-001"), Money.gbp("10000.00"));
    var notification = new CapturingNotificationInterpreter();

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);

    Id<PaymentResult> id =
        IdKindHelper.ID.narrow(
            PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));
    PaymentResult result = id.value();

    System.out.println("  Result: " + result);
    if (result instanceof PaymentResult.Approved approved) {
      System.out.println("  Estimated total (including fees): " + approved.chargeResult().amount());
    }
  }

  /** Interpretation 4: High Risk — demonstrates risk-based decline. */
  private static void runHighRiskInterpretation() {
    System.out.println("--- 4. High Risk Interpretation (Decline) ---");

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
    PaymentResult result = id.value();

    System.out.println("  Result: " + result);
    System.out.println("  Gateway calls: " + gateway.calls() + " (no charges attempted)");
    System.out.println("  Fraud alerts: " + notification.alerts());
  }
}
