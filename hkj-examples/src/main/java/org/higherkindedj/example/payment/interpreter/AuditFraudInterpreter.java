// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.FraudCheckOp;
import org.higherkindedj.example.payment.effect.FraudCheckOpInterpreter;
import org.higherkindedj.example.payment.model.AuditLog;
import org.higherkindedj.example.payment.model.RiskScore;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Audit interpreter for fraud check operations.
 *
 * <p>Targets {@code WriterT<Id, AuditLog, A>}. Logs each fraud check and returns a deterministic
 * low-risk score.
 */
@NullMarked
public final class AuditFraudInterpreter
    extends FraudCheckOpInterpreter<WriterTKind.Witness<IdKind.Witness, AuditLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleCheckTransaction(
      FraudCheckOp.CheckTransaction<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(RiskScore.of(10));
    AuditLog log = AuditLog.of("FRAUD_CHECK", op.amount() + " for customer " + op.customer().id());
    return WriterT.writer(ID, result, log);
  }
}
