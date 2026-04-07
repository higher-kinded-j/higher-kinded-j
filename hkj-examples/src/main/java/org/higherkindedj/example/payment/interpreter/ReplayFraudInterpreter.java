// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.FraudCheckOpInterpreter;
import org.higherkindedj.example.payment.model.EventLog;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Replay interpreter for fraud check operations.
 *
 * <p>Targets {@code ReaderT<Id, EventLog, A>}. Reads the pre-recorded risk score from the event
 * log.
 */
@NullMarked
public final class ReplayFraudInterpreter
    extends FraudCheckOpInterpreter<ReaderTKind.Witness<IdKind.Witness, EventLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleCheckTransaction(
      FraudCheckOp.CheckTransaction<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog log) -> op.k().apply(log.<RiskScore>get("fraudCheck")));
  }
}
