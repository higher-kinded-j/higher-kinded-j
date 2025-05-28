// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.state.*;
import org.higherkindedj.hkt.unit.Unit;

/**
 * see {<a href="https://higher-kinded-j.github.io/state_monad.html">Managing State
 * Functionally</a>}
 */
public class BankAccountWorkflow {

  private static final StateMonad<AccountState> accountStateMonad = new StateMonad<>();

  public static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Unit>> deposit(
      String description) {
    return amount ->
        StateKindHelper.wrap(
            State.modify(
                currentState -> {
                  if (amount.compareTo(BigDecimal.ZERO) <= 0) {
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
    AccountState initialState = AccountState.initial(new BigDecimal("100.00"));
    StringBuilder report = new StringBuilder();

    Kind<StateKind.Witness<AccountState>, String> workflow =
        accountStateMonad.flatMap(
            depositTxResult ->
                accountStateMonad.flatMap(
                    withdrawTx1Success ->
                        accountStateMonad.flatMap(
                            withdrawTx2Result ->
                                accountStateMonad.flatMap(
                                    currentBalance ->
                                        accountStateMonad.map(
                                            history -> {
                                              history.forEach(
                                                  tx -> report.append("  - %s\n".formatted(tx)));
                                              return report.toString();
                                            },
                                            getHistory()),
                                    getBalance()),
                            withdraw("Groceries").apply(new BigDecimal("70.00"))),
                    withdraw("Bill Payment").apply(new BigDecimal("50.00"))),
            deposit("Salary").apply(new BigDecimal("20.00")));

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
