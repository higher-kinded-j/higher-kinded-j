// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.state;

import static java.util.Objects.requireNonNull;
import static org.higherkindedj.example.basic.state.TransactionType.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transaction(
    TransactionType type, BigDecimal amount, LocalDateTime timestamp, String description) {
  public Transaction {
    requireNonNull(type, "Transaction type cannot be null");
    requireNonNull(amount, "Transaction amount cannot be null");
    requireNonNull(timestamp, "Transaction timestamp cannot be null");
    requireNonNull(description, "Transaction description cannot be null");
    if (type != INITIAL_BALANCE && amount.compareTo(BigDecimal.ZERO) <= 0) {
      if (!(type == REJECTED_DEPOSIT && amount.compareTo(BigDecimal.ZERO) <= 0)
          && !(type == REJECTED_WITHDRAWAL && amount.compareTo(BigDecimal.ZERO) <= 0)) {
        throw new IllegalArgumentException(
            "Transaction amount must be positive for actual operations.");
      }
    }
  }
}
