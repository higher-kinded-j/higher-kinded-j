// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateKindHelper;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * see {<a href="https://higher-kinded-j.github.io/state_monad.html">State Monad - - Managing State
 * Functionally</a>}
 */
public class BankAccountWorkflow {

  private static final StateMonad<AccountState> accountStateMonad = new StateMonad<>();

  public static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Void>> deposit(
      String description) {
    return amount ->
        StateKindHelper.wrap(
            State.modify(
                currentState -> {
                  if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    // For rejected deposit, log the problematic amount
                    Transaction rejected =
                        new Transaction(
                            TransactionType.REJECTED_DEPOSIT,
                            amount,
                            LocalDateTime.now(),
                            "Rejected Deposit: " + description + " - Invalid Amount " + amount);
                    return currentState.addTransaction(rejected);
                  }
                  BigDecimal newBalance = currentState.balance().add(amount);
                  Transaction tx =
                      new Transaction(
                          TransactionType.DEPOSIT, amount, LocalDateTime.now(), description);
                  return currentState.withBalance(newBalance).addTransaction(tx);
                }));
  }

  public static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Boolean>> withdraw(
      String description) {
    return amount ->
        StateKindHelper.wrap(
            State.of(
                currentState -> {
                  if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    // For rejected withdrawal due to invalid amount, log the problematic amount
                    Transaction rejected =
                        new Transaction(
                            TransactionType.REJECTED_WITHDRAWAL,
                            amount,
                            LocalDateTime.now(),
                            "Rejected Withdrawal: " + description + " - Invalid Amount " + amount);
                    return new StateTuple<>(false, currentState.addTransaction(rejected));
                  }
                  if (currentState.balance().compareTo(amount) >= 0) {
                    BigDecimal newBalance = currentState.balance().subtract(amount);
                    Transaction tx =
                        new Transaction(
                            TransactionType.WITHDRAWAL, amount, LocalDateTime.now(), description);
                    AccountState updatedState =
                        currentState.withBalance(newBalance).addTransaction(tx);
                    return new StateTuple<>(true, updatedState);
                  } else {
                    // For rejected withdrawal due to insufficient funds, log the amount that was
                    // attempted
                    Transaction tx =
                        new Transaction(
                            TransactionType.REJECTED_WITHDRAWAL,
                            amount,
                            LocalDateTime.now(),
                            "Rejected Withdrawal: "
                                + description
                                + " - Insufficient Funds. Balance: "
                                + currentState.balance());
                    AccountState updatedState = currentState.addTransaction(tx);
                    return new StateTuple<>(false, updatedState);
                  }
                }));
  }

  public static Kind<StateKind.Witness<AccountState>, BigDecimal> getBalance() {
    return StateKindHelper.wrap(State.inspect(AccountState::balance));
  }

  public static Kind<StateKind.Witness<AccountState>, List<Transaction>> getHistory() {
    return StateKindHelper.wrap(State.inspect(AccountState::history));
  }

  public static void main(String[] args) {
    // Initial state: Account with £100 balance.
    AccountState initialState = AccountState.initial(new BigDecimal("100.00"));
    StringBuilder report = new StringBuilder();
    // Define a sequence of operations
    Kind<StateKind.Witness<AccountState>, String> workflow =
        accountStateMonad.flatMap(
            depositTxResult -> // result of deposit("Salary")
            accountStateMonad.flatMap(
                    withdrawTx1Success -> // result of withdraw("Bill Payment")
                    accountStateMonad.flatMap(
                            withdrawTx2Result -> // result of withdraw("Groceries")
                            accountStateMonad.flatMap(
                                    currentBalance -> // result of getBalance()
                                    accountStateMonad.map(
                                            history -> { // 'history' is the result of getHistory()
                                              history.forEach(
                                                  tx -> report.append("  - %s\n".formatted(tx)));
                                              return report.toString();
                                            },
                                            getHistory()),
                                    getBalance()),
                            withdraw("Groceries").apply(new BigDecimal("70.00"))),
                    withdraw("Bill Payment").apply(new BigDecimal("50.00"))),
            deposit("Salary")
                .apply(
                    new BigDecimal(
                        "20.00")) // This is the first stateful ACTION in the flatMap chain
            );

    StateTuple<AccountState, String> finalResultTuple =
        StateKindHelper.runState(workflow, initialState);

    System.out.println(finalResultTuple.value());

    System.out.println("\nDirect Final Account State:");
    System.out.println("Balance: £" + finalResultTuple.state().balance());
    System.out.println(
        "History contains " + finalResultTuple.state().history().size() + " transaction(s):");
    finalResultTuple.state().history().forEach(tx -> System.out.println("  - " + tx));
  }
}
