// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effecthandlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.example.payment.model.Customer;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.EventLog;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.example.payment.model.PaymentMethod;
import org.higherkindedj.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 06: Advanced Interpreters (Audit and Replay)
 *
 * <p>The same Free program can target monad transformers for advanced interpretation strategies:
 *
 * <ul>
 *   <li><b>Audit</b>: {@code WriterT<Id, AuditLog, A>} accumulates a structured audit log alongside
 *       the result, without modifying the program
 *   <li><b>Replay</b>: {@code ReaderT<Id, EventLog, A>} reconstructs a past execution from stored
 *       events, without calling external services
 * </ul>
 *
 * <p>This tutorial uses the Payment Processing example interpreters.
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial06_AdvancedInterpreters {

  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

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
   * Exercise 1: Audit interpretation
   *
   * <p>The audit interpreters target {@code WriterT<Id, AuditLog>}. After interpretation, the
   * result contains both the payment outcome and the accumulated audit log.
   *
   * <p>Task: Combine audit interpreters, interpret the program, and extract the audit log
   */
  @Test
  void exercise1_auditInterpretation() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    // TODO: Combine the four audit interpreters using Interpreters.combine
    var interpreter = answerRequired();

    // TODO: Create a WriterTMonad with IdMonad and AuditLogMonoid
    // Hint: new WriterTMonad<>(IdMonad.instance(), AuditLogMonoid.INSTANCE)
    var writerMonad = answerRequired();

    // Once you have the interpreter and monad, interpret and extract:
    // var resultKind = PaymentEffectsWiring.interpret(program, interpreter, writerMonad);
    // WriterT<...> writerT = WriterTKindHelper.WRITER_T.narrow(resultKind);
    // Pair<PaymentResult, AuditLog> pair = IdKindHelper.ID.unwrap(writerT.run());

    // assertThat(pair.first().isApproved()).isTrue();
    // assertThat(pair.second().entries()).isNotEmpty();
    assertThat(true).isTrue(); // Replace with real assertions
  }

  /**
   * Exercise 2: Replay interpretation
   *
   * <p>The replay interpreters target {@code ReaderT<Id, EventLog>}. They read pre-recorded results
   * from the event log instead of calling external services.
   *
   * <p>Task: Build an event log, combine replay interpreters, and interpret the program
   */
  @Test
  void exercise2_replayInterpretation() {
    var service = PaymentService.create();
    var program = service.processPayment(CUSTOMER, AMOUNT, VISA);

    // TODO: Create an EventLog with pre-recorded results for each operation
    // Hint: new EventLog(Map.of(
    //     "fraudCheck", RiskScore.of(10),
    //     "balance", Money.gbp("5000.00"),
    //     "charge", ChargeResult.success(new TransactionId("replay-txn"), AMOUNT),
    //     "ledgerRecord", new LedgerEntry(new CustomerId("acc-001"), AMOUNT,
    //         new TransactionId("replay-txn"))))
    EventLog eventLog = answerRequired();

    // TODO: Combine the four replay interpreters using Interpreters.combine
    var interpreter = answerRequired();

    // TODO: Create a ReaderTMonadReader with IdMonad
    // Hint: new ReaderTMonadReader<>(IdMonad.instance())
    var readerMonad = answerRequired();

    // Once you have everything, interpret and run with the event log:
    // var resultKind = PaymentEffectsWiring.interpret(program, interpreter, readerMonad);
    // ReaderT<...> readerT = ReaderTKindHelper.READER_T.narrow(resultKind);
    // PaymentResult result = IdKindHelper.ID.unwrap(readerT.run().apply(eventLog));

    // assertThat(result.isApproved()).isTrue();
    assertThat(true).isTrue(); // Replace with real assertions
  }
}
