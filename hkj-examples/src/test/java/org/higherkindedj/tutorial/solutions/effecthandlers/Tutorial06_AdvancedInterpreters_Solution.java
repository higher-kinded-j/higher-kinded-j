// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.Map;
import org.higherkindedj.example.payment.effect.PaymentEffectsWiring;
import org.higherkindedj.example.payment.interpreter.AuditFraudInterpreter;
import org.higherkindedj.example.payment.interpreter.AuditGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.AuditLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.AuditNotificationInterpreter;
import org.higherkindedj.example.payment.interpreter.ReplayFraudInterpreter;
import org.higherkindedj.example.payment.interpreter.ReplayGatewayInterpreter;
import org.higherkindedj.example.payment.interpreter.ReplayLedgerInterpreter;
import org.higherkindedj.example.payment.interpreter.ReplayNotificationInterpreter;
import org.higherkindedj.example.payment.model.AuditLog;
import org.higherkindedj.example.payment.model.AuditLogMonoid;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.EventLog;
import org.higherkindedj.example.payment.model.LedgerEntry;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.model.PaymentResult;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.example.payment.service.PaymentService;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKindHelper;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKindHelper;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial06 AdvancedInterpreters — teaching-solution format.
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
@SuppressWarnings("unchecked")
public class Tutorial06_AdvancedInterpreters_Solution {

  private static final Customer CUSTOMER =
      new Customer(
          new CustomerId("cust-001"),
          "Jane Smith",
          "jane@example.com",
          new CustomerId("acc-001"),
          null);

  private static final Money AMOUNT = Money.gbp("25.00");
  private static final PaymentMethod VISA = new PaymentMethod.CreditCard("4242", "VISA");

  /**
   * Why this is idiomatic: audit is just another interpreter. Each {@code Audit*Interpreter} gives
   * its result in {@code WriterT<Id, AuditLog, A>} with one log entry, and {@code foldMap} combines
   * the entries with {@code AuditLogMonoid}, so the trail comes back as a value beside the result.
   *
   * <p>Alternative: recording interpreters targeting {@code Id} capture the same trail in mutable
   * lists, as the payment example's tests do.
   *
   * <p>Common wrong attempt: log inside the production gateway and assert against stdout. Couples
   * the test to formatting; a log value gives structured data the test can match precisely.
   */
  @Test
  void exercise1_auditInterpretation() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    var interpreter =
        Interpreters.combine(
            new AuditGatewayInterpreter(),
            new AuditFraudInterpreter(),
            new AuditLedgerInterpreter(),
            new AuditNotificationInterpreter());
    var writerMonad = Instances.writerT(Instances.monad(id()), AuditLogMonoid.INSTANCE);

    var resultKind = PaymentEffectsWiring.interpret(program, interpreter, writerMonad);
    WriterT<IdKind.Witness, AuditLog, PaymentResult> writerT =
        WriterTKindHelper.WRITER_T.narrow(resultKind);
    Pair<PaymentResult, AuditLog> pair = IdKindHelper.ID.narrow(writerT.run()).value();

    // One entry for each step: fraud check, balance, charge, ledger record, receipt
    assertThat(pair.first().isApproved()).isTrue();
    assertThat(pair.second().entries()).hasSize(5);
  }

  /**
   * Why this is idiomatic: a replay interpreter feeds pre-recorded values back into the algebra.
   * The same payment program runs deterministically against the canned event log — useful for
   * regression tests and incident reproduction.
   *
   * <p>Alternative: stub each dependency individually. Same outcome; the replay interpreter
   * centralises the canned answers in one map keyed by operation.
   *
   * <p>Common wrong attempt: replay against a slightly different program than the one that produced
   * the log. The keys must align; record and replay against the same program shape or the replay
   * misses entries.
   */
  @Test
  void exercise2_replayInterpretation() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    EventLog eventLog =
        new EventLog(
            Map.of(
                "fraudCheck", RiskScore.of(10),
                "balance", Money.gbp("5000.00"),
                "charge", ChargeResult.success(new TransactionId("replay-txn"), AMOUNT),
                "ledgerRecord",
                    new LedgerEntry(
                        new CustomerId("acc-001"), AMOUNT, new TransactionId("replay-txn"))));

    var interpreter =
        Interpreters.combine(
            new ReplayGatewayInterpreter(),
            new ReplayFraudInterpreter(),
            new ReplayLedgerInterpreter(),
            new ReplayNotificationInterpreter());

    ReaderTMonadReader<IdKind.Witness, EventLog> readerMonad =
        new ReaderTMonadReader<>(Instances.monad(id()));

    var resultKind = PaymentEffectsWiring.interpret(program, interpreter, readerMonad);
    ReaderT<IdKind.Witness, EventLog, PaymentResult> readerT =
        ReaderTKindHelper.READER_T.narrow(resultKind);
    PaymentResult result =
        IdKindHelper.ID.<PaymentResult>narrow(readerT.run().apply(eventLog)).value();

    assertThat(result.isApproved()).isTrue();
  }
}
