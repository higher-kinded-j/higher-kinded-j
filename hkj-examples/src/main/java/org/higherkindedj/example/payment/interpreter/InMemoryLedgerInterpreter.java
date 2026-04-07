// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.LedgerOpInterpreter;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.LedgerEntry;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.jspecify.annotations.NullMarked;

/**
 * Test interpreter that maintains an in-memory ledger.
 *
 * <p>Targets the {@code Id} monad for pure, synchronous testing. Tracks all recorded entries and
 * maintains per-account balances.
 *
 * <p><b>Not thread-safe.</b> Create a fresh instance per program invocation. Do not share across
 * concurrent interpretations.
 */
@NullMarked
public final class InMemoryLedgerInterpreter extends LedgerOpInterpreter<IdKind.Witness> {

  private final List<LedgerEntry> entries = new ArrayList<>();
  private final Map<CustomerId, Money> balances = new HashMap<>();

  /**
   * Sets the initial balance for an account.
   *
   * @param accountId the account identifier
   * @param balance the initial balance
   */
  public void setBalance(CustomerId accountId, Money balance) {
    balances.put(accountId, balance);
  }

  /**
   * Returns an unmodifiable view of all recorded entries.
   *
   * @return the list of ledger entries
   */
  public List<LedgerEntry> entries() {
    return Collections.unmodifiableList(entries);
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleRecordEntry(LedgerOp.RecordEntry<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    entries.add(op.entry());
    return new Id<>(op.k().apply(op.entry()));
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleGetBalance(LedgerOp.GetBalance<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    Money balance = balances.getOrDefault(op.accountId(), Money.gbp(BigDecimal.ZERO));
    return new Id<>(op.k().apply(balance));
  }
}
