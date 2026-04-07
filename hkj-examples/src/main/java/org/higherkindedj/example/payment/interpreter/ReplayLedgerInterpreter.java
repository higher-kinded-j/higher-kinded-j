// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.LedgerOpInterpreter;
import org.higherkindedj.example.payment.model.EventLog;
import org.higherkindedj.example.payment.model.LedgerEntry;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Replay interpreter for ledger operations.
 *
 * <p>Targets {@code ReaderT<Id, EventLog, A>}. Reads pre-recorded balance and ledger entries from
 * the event log.
 */
@NullMarked
public final class ReplayLedgerInterpreter
    extends LedgerOpInterpreter<ReaderTKind.Witness<IdKind.Witness, EventLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleRecordEntry(
      LedgerOp.RecordEntry<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog log) -> op.k().apply(log.<LedgerEntry>get("ledgerRecord")));
  }

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleGetBalance(
      LedgerOp.GetBalance<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog log) -> op.k().apply(log.<Money>get("balance")));
  }
}
