// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.LedgerOpInterpreter;
import org.higherkindedj.example.payment.model.AuditLog;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Audit interpreter for ledger operations.
 *
 * <p>Targets {@code WriterT<Id, AuditLog, A>}. Logs each ledger operation and returns deterministic
 * results.
 */
@NullMarked
public final class AuditLedgerInterpreter
    extends LedgerOpInterpreter<WriterTKind.Witness<IdKind.Witness, AuditLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleRecordEntry(
      LedgerOp.RecordEntry<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(op.entry());
    AuditLog log =
        AuditLog.of(
            "LEDGER_RECORD",
            op.entry().accountId()
                + " "
                + op.entry().amount()
                + " txn="
                + op.entry().transactionId());
    return WriterT.writer(ID, result, log);
  }

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleGetBalance(
      LedgerOp.GetBalance<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(Money.gbp("5000.00"));
    AuditLog log = AuditLog.of("LEDGER_BALANCE", "account=" + op.accountId());
    return WriterT.writer(ID, result, log);
  }
}
