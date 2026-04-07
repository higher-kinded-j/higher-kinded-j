// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.LedgerOp;
import org.higherkindedj.example.payment.effect.LedgerOpInterpreter;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Production interpreter for ledger operations.
 *
 * <p>Targets the {@code IO} monad. In a real system, this would write to a database. This example
 * uses in-memory simulation with deterministic responses.
 */
@NullMarked
public final class ProductionLedgerInterpreter extends LedgerOpInterpreter<IOKind.Witness> {

  @Override
  protected <A> Kind<IOKind.Witness, A> handleRecordEntry(LedgerOp.RecordEntry<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply(op.entry())));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handleGetBalance(LedgerOp.GetBalance<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(IO.delay(() -> op.k().apply(Money.gbp("5000.00"))));
  }
}
