// Fixture for hkj-book/src/monads/state_monad.md
//
// The page runs one bank account through deposits and withdrawals, threading balance and history
// as state. The workflow declares its own domain records; this fixture only supplies what the
// later snippets reach for.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.Objects.requireNonNull;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;

enum TransactionType {
  INITIAL_BALANCE,
  DEPOSIT,
  WITHDRAWAL,
  REJECTED_WITHDRAWAL,
  REJECTED_DEPOSIT
}

record Transaction(
    TransactionType type, BigDecimal amount, LocalDateTime timestamp, String description) {}

record AccountState(BigDecimal balance, List<Transaction> history) {

  static AccountState initial(BigDecimal initialBalance) {
    return new AccountState(initialBalance, List.of());
  }

  AccountState addTransaction(Transaction transaction) {
    List<Transaction> newHistory = new ArrayList<>(history);
    newHistory.add(transaction);
    return new AccountState(balance, Collections.unmodifiableList(newHistory));
  }

  AccountState withBalance(BigDecimal newBalance) {
    return new AccountState(newBalance, history);
  }
}

class Fixture {

  static final StateMonad<AccountState> accountStateMonad = StateMonad.instance();

  static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Unit>> deposit(
      String description) {
    return amount -> STATE.widen(State.modify(state -> state));
  }

  static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Boolean>> withdraw(
      String description) {
    return amount -> STATE.widen(State.of(state -> StateTuple.of(state, true)));
  }

  static Kind<StateKind.Witness<AccountState>, BigDecimal> getBalance() {
    return STATE.widen(State.of(state -> StateTuple.of(state, state.balance())));
  }

  static Kind<StateKind.Witness<AccountState>, List<Transaction>> getHistory() {
    return STATE.widen(State.of(state -> StateTuple.of(state, state.history())));
  }
}
