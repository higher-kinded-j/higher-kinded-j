// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.service;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;

import java.util.function.Function;
import org.higherkindedj.example.payment.effect.FraudCheckOpKind;
import org.higherkindedj.example.payment.effect.FraudCheckOpOps;
import org.higherkindedj.example.payment.effect.LedgerOpKind;
import org.higherkindedj.example.payment.effect.LedgerOpOps;
import org.higherkindedj.example.payment.effect.NotificationOpKind;
import org.higherkindedj.example.payment.effect.NotificationOpOps;
import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpKind;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpOps;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.LedgerEntry;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Payment processing service using Free monad effect handlers.
 *
 * <p>This service demonstrates how to write business logic as a pure program that describes what to
 * do without specifying how. The same program can be interpreted in multiple ways:
 *
 * <ul>
 *   <li><b>Production</b> — IO monad with real payment gateway, fraud detection, etc.
 *   <li><b>Testing</b> — Id monad with recording and fixed-response interpreters
 *   <li><b>Quote</b> — Id monad that estimates fees without side effects
 *   <li><b>Audit</b> — IO monad with structured audit logging
 * </ul>
 *
 * <p>The service uses the {@code boundTo} pattern for constructor injection of effect algebras,
 * mirroring the familiar Spring constructor injection pattern. Each bound instance provides
 * type-safe access to effect operations within the combined effect type.
 *
 * <p>All operations use continuation-passing style (CPS), so the type parameter {@code A} is
 * properly constrained at every call site. No raw types or unchecked casts are needed.
 *
 * @param <G> the combined effect type (e.g. the composed EitherFKind.Witness nesting)
 */
@NullMarked
public final class PaymentService<G extends WitnessArity<TypeArity.Unary>> {

  /** Risk score threshold above which a transaction is declined. */
  private static final int RISK_THRESHOLD = 70;

  private final PaymentGatewayOpOps.Bound<G> gateway;
  private final FraudCheckOpOps.Bound<G> fraud;
  private final LedgerOpOps.Bound<G> ledger;
  private final NotificationOpOps.Bound<G> notification;
  private final Functor<G> functor;

  /**
   * Creates a PaymentService with bound effect algebras.
   *
   * <p>This mirrors the familiar Spring constructor injection pattern. The bound instances act like
   * injected repositories or services — each provides operations for a specific domain.
   *
   * @param gateway bound payment gateway operations
   * @param fraud bound fraud check operations
   * @param ledger bound ledger operations
   * @param notification bound notification operations
   * @param functor the functor for the combined effect type
   */
  public PaymentService(
      PaymentGatewayOpOps.Bound<G> gateway,
      FraudCheckOpOps.Bound<G> fraud,
      LedgerOpOps.Bound<G> ledger,
      NotificationOpOps.Bound<G> notification,
      Functor<G> functor) {
    this.gateway = Validation.function().require(gateway, "gateway", CONSTRUCTION);
    this.fraud = Validation.function().require(fraud, "fraud", CONSTRUCTION);
    this.ledger = Validation.function().require(ledger, "ledger", CONSTRUCTION);
    this.notification = Validation.function().require(notification, "notification", CONSTRUCTION);
    this.functor = Validation.function().require(functor, "functor", CONSTRUCTION);
  }

  /**
   * Creates a PaymentService using the {@link PaymentEffectsWiring} helper.
   *
   * <p>This factory method demonstrates the intended usage pattern with composed effects. The
   * {@code BoundSet} provides all bound instances in one call. Callers should use {@code var} to
   * avoid spelling out the full composed witness type.
   *
   * @return a PaymentService wired to the composed PaymentEffects type
   */
  public static PaymentService<
          EitherFKind.Witness<
              PaymentGatewayOpKind.Witness,
              EitherFKind.Witness<
                  FraudCheckOpKind.Witness,
                  EitherFKind.Witness<LedgerOpKind.Witness, NotificationOpKind.Witness>>>>
      create() {
    var bounds = PaymentEffectsWiring.boundSet();
    return new PaymentService<>(
        bounds.gateway(),
        bounds.fraud(),
        bounds.ledger(),
        bounds.notification(),
        PaymentEffectsWiring.functor());
  }

  /**
   * Processes a payment as a Free monad program.
   *
   * <p>This method builds a pure program description. No side effects occur until the returned Free
   * program is interpreted via {@code foldMap} with a concrete interpreter. The same program can
   * then be interpreted in fundamentally different ways.
   *
   * <p>Each effect operation uses CPS: the {@code Function.identity()} continuation means "give me
   * the natural result type directly." The generated smart constructors properly constrain the type
   * parameter {@code A}, so no casts are needed anywhere in this method.
   *
   * @param customer the customer making the payment
   * @param amount the payment amount
   * @param method the payment method to use
   * @return a Free program that, when interpreted, produces a PaymentResult
   */
  public Free<G, PaymentResult> processPayment(
      Customer customer, Money amount, PaymentMethod method) {

    // Step 1: Fraud check — Function.identity() means A = RiskScore
    Free<G, RiskScore> checkRisk = fraud.checkTransaction(amount, customer, Function.identity());

    return checkRisk.flatMap(
        risk -> {
          // Step 2: Get balance — Function.identity() means A = Money
          Free<G, Money> getBalance = ledger.getBalance(customer.accountId(), Function.identity());

          return getBalance.flatMap(
              balance -> {
                // Step 3: Risk and balance decision
                if (risk.exceeds(RISK_THRESHOLD)) {
                  return alertAndDecline(customer, risk);
                }
                if (balance.lessThan(amount)) {
                  return Free.pure(PaymentResult.declined("Insufficient funds"));
                }
                // Step 4-7: Charge, record, notify, assemble
                return chargeAndRecord(customer, amount, method, risk);
              });
        });
  }

  /** Alerts the fraud team and returns a declined result. */
  private Free<G, PaymentResult> alertAndDecline(Customer customer, RiskScore risk) {
    Free<G, Unit> alert = notification.alertFraudTeam(customer, risk, Function.identity());
    return alert.map(_ -> PaymentResult.declined("High risk: " + risk.reason()));
  }

  /** Charges the payment method, records the ledger entry, and sends a receipt. */
  private Free<G, PaymentResult> chargeAndRecord(
      Customer customer, Money amount, PaymentMethod method, RiskScore risk) {

    Free<G, ChargeResult> charge = gateway.charge(amount, method, Function.identity());

    return charge.flatMap(
        chargeResult -> {
          if (chargeResult.isFailed()) {
            return Free.pure(PaymentResult.failed(chargeResult));
          }

          // Record ledger entry
          LedgerEntry entry = new LedgerEntry(customer.accountId(), amount, chargeResult.id());
          Free<G, LedgerEntry> record = ledger.recordEntry(entry, Function.identity());

          return record.flatMap(
              ledgerEntry -> {
                // Send receipt (non-critical — recovered on failure)
                Free<G, Unit> receipt =
                    notification.sendReceipt(customer, chargeResult, Function.identity());
                Free<G, Unit> safeReceipt =
                    receipt.handleError(Throwable.class, _ -> Free.pure(Unit.INSTANCE));

                return safeReceipt.map(
                    _ -> new PaymentResult.Approved(chargeResult, ledgerEntry, risk));
              });
        });
  }
}
