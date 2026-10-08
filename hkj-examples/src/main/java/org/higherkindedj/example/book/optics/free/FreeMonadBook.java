// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.free;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/free_monad_dsl.html">Free Monad DSL</a>
 * page. The page {@code {{#include}}}s the anchored regions, and {@code FreeMonadBookTest} holds
 * the claims the page makes about this code.
 */
public final class FreeMonadBook {

  /** What the three first programs give back when the direct interpreter runs them. */
  record FirstRuns(Integer age, Person updated, Person modified) {}

  /** What the audited transfer gives back, and the trail the logging interpreter kept. */
  record Audited(Transaction result, List<String> trail) {}

  private FreeMonadBook() {}

  static FirstRuns firstPrograms() {
    // ANCHOR: build
    Person person = new Person("Alice", 25, "ACTIVE");

    // Build a program that gets the age
    Free<OpticOpKind.Witness, Integer> getProgram = OpticPrograms.get(person, PersonLenses.age());

    // Build a program that sets the age
    Free<OpticOpKind.Witness, Person> setProgram =
        OpticPrograms.set(person, PersonLenses.age(), 30);

    // Build a program that modifies the age
    Free<OpticOpKind.Witness, Person> modifyProgram =
        OpticPrograms.modify(person, PersonLenses.age(), age -> age + 1);
    // ANCHOR_END: build

    // ANCHOR: run
    // Execute with direct interpreter
    DirectOpticInterpreter interpreter = OpticInterpreters.direct();

    Integer age = interpreter.run(getProgram);
    // 25

    Person updated = interpreter.run(setProgram);
    // age is now 30

    Person modified = interpreter.run(modifyProgram);
    // age is now 26
    // ANCHOR_END: run
    return new FirstRuns(age, updated, modified);
  }

  // ANCHOR: annual_review_program
  // Program: Annual review and potential promotion
  static Free<OpticOpKind.Witness, Employee> annualReviewProgram(Employee employee) {
    return OpticPrograms.get(employee, EmployeeLenses.salary())
        .flatMap(
            currentSalary -> {
              // Step 1: Give a 10% raise
              int newSalary = currentSalary + (currentSalary / 10);
              return OpticPrograms.set(employee, EmployeeLenses.salary(), newSalary);
            })
        .flatMap(
            raisedEmployee ->
                // Step 2: Check if salary justifies promotion
                OpticPrograms.get(raisedEmployee, EmployeeLenses.salary())
                    .flatMap(
                        salary -> {
                          if (salary > 100_000) {
                            return OpticPrograms.set(
                                raisedEmployee, EmployeeLenses.status(), EmployeeStatus.SENIOR);
                          } else {
                            return OpticPrograms.pure(raisedEmployee);
                          }
                        }));
  }

  // ANCHOR_END: annual_review_program

  static Employee annualReview() {
    // ANCHOR: annual_review_run
    // Execute for an employee
    Employee alice = new Employee("Alice", 95_000, EmployeeStatus.JUNIOR);
    Free<OpticOpKind.Witness, Employee> program = annualReviewProgram(alice);

    Employee promoted = OpticInterpreters.direct().run(program);
    // Employee[name=Alice, salary=104500, status=SENIOR]
    // ANCHOR_END: annual_review_run
    return promoted;
  }

  static Boolean doubleScores() {
    // ANCHOR: team
    Team team = new Team("Wildcats", List.of(new Player("Alice", 80), new Player("Bob", 90)));

    // Program: Double all scores and check if everyone passes
    Free<OpticOpKind.Witness, Boolean> scoreUpdateProgram =
        OpticPrograms.modifyAll(
                team, TeamTraversals.players().andThen(PlayerLenses.score()), score -> score * 2)
            .flatMap(
                updatedTeam ->
                    // Now check if all players have passing scores
                    OpticPrograms.all(
                        updatedTeam,
                        TeamTraversals.players().andThen(PlayerLenses.score()),
                        score -> score >= 100));

    // Execute
    Boolean allPass = OpticInterpreters.direct().run(scoreUpdateProgram);
    // true: every doubled score is at least 100
    // ANCHOR_END: team
    return allPass;
  }

  static Audited auditedTransfer() {
    // ANCHOR: transfer_run
    // Execute with logging for audit trail
    Account acc1 = new Account("ACC001", new BigDecimal("1000.00"));
    Account acc2 = new Account("ACC002", new BigDecimal("500.00"));
    Transaction txn = new Transaction(acc1, acc2, new BigDecimal("100.00"));

    Free<OpticOpKind.Witness, Transaction> program = transferProgram(txn);

    // Use logging interpreter to record every operation
    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Transaction result = logger.run(program);

    // Review the audit trail: one line per operation, each naming the optic's class
    //   GET: ... -> 100.00
    //   MODIFY: ... from 1000.00 to 900.00
    //   MODIFY: ... from 500.00 to 600.00
    logger.getLog().forEach(System.out::println);
    // ANCHOR_END: transfer_run
    return new Audited(result, logger.getLog());
  }

  // ANCHOR: transfer_program
  // Program: Transfer money between accounts
  static Free<OpticOpKind.Witness, Transaction> transferProgram(Transaction transaction) {
    return OpticPrograms.get(transaction, TransactionLenses.amount())
        .flatMap(
            amount ->
                // Deduct from source account
                OpticPrograms.modify(
                    transaction,
                    TransactionLenses.from().andThen(AccountLenses.balance()),
                    balance -> balance.subtract(amount)))
        .flatMap(
            txn ->
                // Add to destination account
                OpticPrograms.modify(
                    txn,
                    TransactionLenses.to().andThen(AccountLenses.balance()),
                    balance -> balance.add(txn.amount())));
  }
  // ANCHOR_END: transfer_program
}

// ANCHOR: person
@GenerateLenses
record Person(String name, int age, String status) {}

// ANCHOR_END: person

// ANCHOR: employee
@GenerateLenses
record Employee(String name, int salary, EmployeeStatus status) {}

enum EmployeeStatus {
  JUNIOR,
  SENIOR,
  PROBATION,
  RETIRED
}

// ANCHOR_END: employee

// ANCHOR: team_records
@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
record Player(String name, int score) {}

// ANCHOR_END: team_records

// ANCHOR: transfer_records
@GenerateLenses
record Account(String accountId, BigDecimal balance) {}

@GenerateLenses
record Transaction(Account from, Account to, BigDecimal amount) {}
// ANCHOR_END: transfer_records
