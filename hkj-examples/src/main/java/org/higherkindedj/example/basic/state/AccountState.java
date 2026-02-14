// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AccountState(BigDecimal balance, List<Transaction> history) {
  public AccountState {
    requireNonNull(balance, "Balance cannot be null.");
    requireNonNull(history, "History cannot be null.");
    // Ensure history is unmodifiable and a defensive copy is made.
    history = Collections.unmodifiableList(new ArrayList<>(history));
  }

  // Convenience constructor for initial state
  public static AccountState initial(BigDecimal initialBalance) {
    requireNonNull(initialBalance, "Initial balance cannot be null");
    if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Initial balance cannot be negative.");
    }
    Transaction initialTx =
        new Transaction(
            TransactionType.INITIAL_BALANCE,
            initialBalance,
            LocalDateTime.now(),
            "Initial account balance");
    // The history now starts with this initial transaction
    return new AccountState(initialBalance, Collections.singletonList(initialTx));
  }

  public AccountState addTransaction(Transaction transaction) {
    requireNonNull(transaction, "Transaction cannot be null");
    List<Transaction> newHistory = new ArrayList<>(history); // Takes current history
    newHistory.add(transaction); // Adds new one
    return new AccountState(this.balance, Collections.unmodifiableList(newHistory));
  }

  public AccountState withBalance(BigDecimal newBalance) {
    requireNonNull(newBalance, "New balance cannot be null");
    return new AccountState(newBalance, this.history);
  }
}
