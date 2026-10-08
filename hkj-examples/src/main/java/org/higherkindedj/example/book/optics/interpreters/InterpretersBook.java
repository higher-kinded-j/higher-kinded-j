// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.interpreters;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/interpreters.html">Interpreters</a> page.
 * The page {@code {{#include}}}s the anchored regions, and {@code InterpretersBookTest} holds the
 * claims the page makes about this code.
 */
public final class InterpretersBook {

  /** What a logged run gives back, and the log the interpreter kept. */
  record Logged<A>(A result, List<String> log) {}

  private InterpretersBook() {}

  static Person directRun() {
    // ANCHOR: direct
    Person person = new Person("Alice", 25);

    // Build a program
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, PersonLenses.age(), age -> age + 1);

    // Execute with direct interpreter
    DirectOpticInterpreter interpreter = OpticInterpreters.direct();
    Person result = interpreter.run(program);

    System.out.println(result);
    // Person[name=Alice, age=26]
    // ANCHOR_END: direct
    return result;
  }

  static ValidationOpticInterpreter.ValidationResult validatingRun() {
    // ANCHOR: validating
    Person person = new Person("Alice", 25);

    // Build a program
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.set(person, PersonLenses.name(), null); // Oops!

    // Run it, and take the report rather than the value
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

    if (!result.isValid()) {
      // Has errors
      result.errors().forEach(System.err::println);
    }

    if (result.hasWarnings()) {
      // Has warnings: this one prints "SET operation with null value: " and the optic's
      // toString(), which names the operation and the lens object, not the field
      result.warnings().forEach(System.out::println);
    }
    // ANCHOR_END: validating
    return result;
  }

  static Logged<Account> loggingRun() {
    // ANCHOR: logging
    Account account = new Account("ACC001", new BigDecimal("1000.00"));

    // Build a program
    Free<OpticOpKind.Witness, Account> program =
        OpticPrograms.modify(
            account,
            AccountLenses.balance(),
            balance -> balance.subtract(new BigDecimal("100.00")));

    // Execute with logging
    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Account result = logger.run(program);

    // Review the log: one line per operation, naming the optic by its runtime class
    //   MODIFY: ... from 1000.00 to 900.00
    List<String> log = logger.getLog();
    log.forEach(System.out::println);
    // ANCHOR_END: logging
    return new Logged<>(result, log);
  }

  static Logged<Transaction> auditedTransfer(AuditService auditService) {
    // ANCHOR: transfer_run
    // Execute with audit logging
    Transaction txn =
        new Transaction(
            "TXN-12345",
            new Account("ACC001", new BigDecimal("1000.00")),
            new Account("ACC002", new BigDecimal("500.00")),
            new BigDecimal("250.00"),
            LocalDateTime.now());

    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Transaction result = logger.run(transferProgram(txn));

    // Persist audit trail to database
    logger.getLog().forEach(entry -> auditService.record(txn.txnId(), entry));
    // ANCHOR_END: transfer_run
    return new Logged<>(result, logger.getLog());
  }

  // ANCHOR: transfer_program
  // Build a transfer program
  static Free<OpticOpKind.Witness, Transaction> transferProgram(Transaction txn) {
    return OpticPrograms.get(txn, TransactionLenses.amount())
        .flatMap(
            amount ->
                // Debit source account
                OpticPrograms.modify(
                    txn,
                    TransactionLenses.from().andThen(AccountLenses.balance()),
                    balance -> balance.subtract(amount)))
        .flatMap(
            debited ->
                // Credit destination account
                OpticPrograms.modify(
                    debited,
                    TransactionLenses.to().andThen(AccountLenses.balance()),
                    balance -> balance.add(debited.amount())));
  }
  // ANCHOR_END: transfer_program
}

/** Where an audit trail is kept: a database table in production, a list in the test. */
interface AuditService {
  void record(String txnId, String entry);
}

// ANCHOR: account
@GenerateLenses
record Account(String accountId, BigDecimal balance) {}

// ANCHOR_END: account

// ANCHOR: transaction
@GenerateLenses
record Transaction(
    String txnId, Account from, Account to, BigDecimal amount, LocalDateTime timestamp) {}

// ANCHOR_END: transaction

// ANCHOR: person
@GenerateLenses
record Person(String name, int age) {}
// ANCHOR_END: person
