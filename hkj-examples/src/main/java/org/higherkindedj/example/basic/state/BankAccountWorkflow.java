// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.state.*;

/**
 * see {<a href="https://higher-kinded-j.github.io/state_monad.html">Managing State
 * Functionally</a>}
 */
public class BankAccountWorkflow {

  private static final StateMonad<AccountState> accountStateMonad = new StateMonad<>();

  public static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Unit>> deposit(
      String description) {
    return amount ->
        STATE.widen(
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
        STATE.widen(
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
    return STATE.widen(State.inspect(AccountState::balance));
  }

  public static Kind<StateKind.Witness<AccountState>, List<Transaction>> getHistory() {
    return STATE.widen(State.inspect(AccountState::history));
  }

  public static void main(String[] args) {
    AccountState initialState = AccountState.initial(new BigDecimal("100.00"));

    var workflow =
        For.from(accountStateMonad, deposit("Salary").apply(new BigDecimal("20.00")))
            .from(a -> withdraw("Bill Payment").apply(new BigDecimal("50.00")))
            .from(b -> withdraw("Groceries").apply(new BigDecimal("70.00")))
            .from(c -> getBalance())
            .from(t -> getHistory())
            .yield(
                (deposit, w1, w2, bal, history) -> {
                  var report = new StringBuilder();
                  history.forEach(tx -> report.append("  - %s\n".formatted(tx)));
                  return report.toString();
                });
    StateTuple<AccountState, String> finalResultTuple = STATE.runState(workflow, initialState);

    System.out.println(finalResultTuple.value());

    System.out.println("\nDirect Final Account State:");
    System.out.println("Balance: £" + finalResultTuple.state().balance());
    System.out.println(
        "History contains " + finalResultTuple.state().history().size() + " transaction(s):");
    finalResultTuple.state().history().forEach(tx -> System.out.println("  - " + tx));
  }
}
