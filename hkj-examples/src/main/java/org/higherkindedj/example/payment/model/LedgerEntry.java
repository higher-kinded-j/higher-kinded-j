// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * A record in the accounting ledger.
 *
 * @param accountId the account this entry belongs to
 * @param amount the monetary amount
 * @param transactionId the associated payment transaction
 */
@NullMarked
public record LedgerEntry(CustomerId accountId, Money amount, TransactionId transactionId) {

  public LedgerEntry {
    Objects.requireNonNull(accountId, "accountId cannot be null");
    Objects.requireNonNull(amount, "amount cannot be null");
    Objects.requireNonNull(transactionId, "transactionId cannot be null");
  }
}
