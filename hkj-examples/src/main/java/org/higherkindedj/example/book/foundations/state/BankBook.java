// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.state;

import static org.higherkindedj.hkt.instances.Witnesses.state;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * The bank account shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/state_monad.html">State</a> page: deposits
 * and withdrawals that each record a transaction, a refused withdrawal, and a month of them
 * sequenced with {@code For} over the {@code State} monad. The account here records no time, so the
 * same steps give the same history on every run. The page {@code {{#include}}}s the anchored
 * regions, {@code BankBookTest} holds the claims the page makes, and the book's output gate runs
 * {@link #main} against the comments in the regions.
 */
public final class BankBook {

  private BankBook() {}

  // ANCHOR: account
  enum TransactionType {
    OPENING_BALANCE,
    DEPOSIT,
    WITHDRAWAL,
    REJECTED_DEPOSIT,
    REJECTED_WITHDRAWAL
  }

  record Transaction(TransactionType type, BigDecimal amount, String description) {}

  // The balance, and every transaction so far, including those refused
  record Account(BigDecimal balance, List<Transaction> history) {

    Account {
      Objects.requireNonNull(balance, "balance");
      history = List.copyOf(history);
    }

    static Account opened(BigDecimal balance) {
      return new Account(
          balance,
          List.of(new Transaction(TransactionType.OPENING_BALANCE, balance, "Opening balance")));
    }

    Account addTransaction(Transaction transaction) {
      return new Account(balance, Stream.concat(history.stream(), Stream.of(transaction)).toList());
    }

    Account withBalance(BigDecimal newBalance) {
      return new Account(newBalance, history);
    }
  }

  // ANCHOR_END: account

  // ANCHOR: steps
  // A deposit changes the account and has nothing to report, so it gives Unit
  static State<Account, Unit> deposit(String description, BigDecimal amount) {
    return State.modify(
        account ->
            amount.signum() <= 0
                ? account.addTransaction(
                    new Transaction(TransactionType.REJECTED_DEPOSIT, amount, description))
                : account
                    .withBalance(account.balance().add(amount))
                    .addTransaction(new Transaction(TransactionType.DEPOSIT, amount, description)));
  }

  // A withdrawal reports whether it was paid, as well as changing the account
  static State<Account, Boolean> withdraw(String description, BigDecimal amount) {
    return State.of(
        account -> {
          if (amount.signum() <= 0 || account.balance().compareTo(amount) < 0) {
            Transaction refused =
                new Transaction(TransactionType.REJECTED_WITHDRAWAL, amount, description);
            return new StateTuple<>(false, account.addTransaction(refused));
          }
          Transaction paid = new Transaction(TransactionType.WITHDRAWAL, amount, description);
          return new StateTuple<>(
              true, account.withBalance(account.balance().subtract(amount)).addTransaction(paid));
        });
  }

  // ANCHOR_END: steps

  // ANCHOR: month
  static Kind<StateKind.Witness<Account>, String> month() {
    Monad<StateKind.Witness<Account>> accounts = Instances.monad(state());

    return For.from(accounts, STATE.widen(deposit("Salary", new BigDecimal("20.00"))))
        .from(_ -> STATE.widen(withdraw("Bill payment", new BigDecimal("50.00"))))
        .from(_ -> STATE.widen(withdraw("Groceries", new BigDecimal("70.00"))))
        .from(_ -> STATE.widen(withdraw("Cinema", new BigDecimal("12.00"))))
        .yield(
            (_, bill, groceries, cinema) ->
                "bill paid: %s, groceries paid: %s, cinema paid: %s"
                    .formatted(bill, groceries, cinema));
  }

  // ANCHOR_END: month

  public static void main(String[] args) {
    // ANCHOR: run
    StateTuple<Account, String> after =
        STATE.runState(month(), Account.opened(new BigDecimal("100.00")));

    String report = after.value();
    BigDecimal balance = after.state().balance();
    // 0.00
    int transactions = after.state().history().size();
    // 5
    // ANCHOR_END: run
    System.out.println(report);
    System.out.println(balance);
    System.out.println(transactions);
  }
}
