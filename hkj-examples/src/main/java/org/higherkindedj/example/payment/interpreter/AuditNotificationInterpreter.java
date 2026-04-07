// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.NotificationOpInterpreter;
import org.higherkindedj.example.payment.model.AuditLog;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Audit interpreter for notification operations.
 *
 * <p>Targets {@code WriterT<Id, AuditLog, A>}. Logs each notification without performing real side
 * effects.
 */
@NullMarked
public final class AuditNotificationInterpreter
    extends NotificationOpInterpreter<WriterTKind.Witness<IdKind.Witness, AuditLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleSendReceipt(
      NotificationOp.SendReceipt<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(Unit.INSTANCE);
    AuditLog log = AuditLog.of("SEND_RECEIPT", "to " + op.customer().email());
    return WriterT.writer(ID, result, log);
  }

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleAlertFraudTeam(
      NotificationOp.AlertFraudTeam<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(Unit.INSTANCE);
    AuditLog log =
        AuditLog.of(
            "ALERT_FRAUD", "customer=" + op.customer().id() + " score=" + op.riskScore().score());
    return WriterT.writer(ID, result, log);
  }
}
