// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.state.BankBook.deposit;
import static org.higherkindedj.example.book.foundations.state.BankBook.month;
import static org.higherkindedj.example.book.foundations.state.BankBook.withdraw;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.math.BigDecimal;
import org.higherkindedj.example.book.foundations.state.BankBook.Account;
import org.higherkindedj.example.book.foundations.state.BankBook.Transaction;
import org.higherkindedj.example.book.foundations.state.BankBook.TransactionType;
import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Holds the claims the State page makes about the bank account. */
@DisplayName("State: a bank account each deposit and withdrawal hands on")
class BankBookTest {

  private static final Account OPENED = Account.opened(new BigDecimal("100.00"));

  @Nested
  @DisplayName("deposits and withdrawals")
  class Steps {

    @Test
    @DisplayName("a deposit adds to the balance and records a transaction")
    void depositAddsToTheBalance() {
      Account after = deposit("Salary", new BigDecimal("20.00")).run(OPENED).state();

      assertThat(after.balance()).isEqualByComparingTo("120.00");
      assertThat(after.history())
          .last()
          .isEqualTo(new Transaction(TransactionType.DEPOSIT, new BigDecimal("20.00"), "Salary"));
    }

    @Test
    @DisplayName("a deposit of nothing is recorded as refused and leaves the balance")
    void refusedDeposit() {
      Account after = deposit("Nothing", BigDecimal.ZERO).run(OPENED).state();

      assertThat(after.balance()).isEqualByComparingTo("100.00");
      assertThat(after.history())
          .last()
          .extracting(Transaction::type)
          .isEqualTo(TransactionType.REJECTED_DEPOSIT);
    }

    @Test
    @DisplayName("a withdrawal the balance covers is paid")
    void paidWithdrawal() {
      StateTuple<Account, Boolean> after =
          withdraw("Bill payment", new BigDecimal("100.00")).run(OPENED);

      assertThat(after.value()).isTrue();
      assertThat(after.state().balance()).isEqualByComparingTo("0.00");
      assertThat(after.state().history())
          .last()
          .extracting(Transaction::type)
          .isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    @DisplayName("a withdrawal the balance does not cover is refused and recorded")
    void refusedWithdrawal() {
      StateTuple<Account, Boolean> after = withdraw("Car", new BigDecimal("100.01")).run(OPENED);

      assertThat(after.value()).isFalse();
      assertThat(after.state().balance()).isEqualByComparingTo("100.00");
      assertThat(after.state().history())
          .last()
          .extracting(Transaction::type)
          .isEqualTo(TransactionType.REJECTED_WITHDRAWAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    @DisplayName("a withdrawal of nothing, or less, is refused and recorded")
    void nonPositiveWithdrawal(String amount) {
      StateTuple<Account, Boolean> after = withdraw("Nothing", new BigDecimal(amount)).run(OPENED);

      assertThat(after.value()).isFalse();
      assertThat(after.state().balance()).isEqualByComparingTo("100.00");
      assertThat(after.state().history())
          .last()
          .extracting(Transaction::type)
          .isEqualTo(TransactionType.REJECTED_WITHDRAWAL);
    }

    @Test
    @DisplayName("an account opens with its opening balance as the first transaction")
    void opened() {
      assertThat(OPENED.history())
          .containsExactly(
              new Transaction(
                  TransactionType.OPENING_BALANCE, new BigDecimal("100.00"), "Opening balance"));
    }
  }

  @Nested
  @DisplayName("a month of transactions")
  class Month {

    @Test
    @DisplayName("the bills are paid, the cinema refused, and the balance is 0.00")
    void theMonth() {
      StateTuple<Account, String> after = STATE.runState(month(), OPENED);

      assertThat(after.value())
          .isEqualTo("bill paid: true, groceries paid: true, cinema paid: false");
      assertThat(after.state().balance()).isEqualByComparingTo("0.00");
      assertThat(after.state().balance().toPlainString()).isEqualTo("0.00");
      assertThat(after.state().history())
          .extracting(Transaction::type)
          .containsExactly(
              TransactionType.OPENING_BALANCE,
              TransactionType.DEPOSIT,
              TransactionType.WITHDRAWAL,
              TransactionType.WITHDRAWAL,
              TransactionType.REJECTED_WITHDRAWAL);
    }

    @Test
    @DisplayName("the account records no time, so two runs give the same history")
    void twoRunsAgree() {
      assertThat(STATE.runState(month(), OPENED)).isEqualTo(STATE.runState(month(), OPENED));
    }
  }
}
