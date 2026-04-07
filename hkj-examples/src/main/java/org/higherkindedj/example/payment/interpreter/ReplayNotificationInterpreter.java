// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.NotificationOpInterpreter;
import org.higherkindedj.example.payment.model.EventLog;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Replay interpreter for notification operations.
 *
 * <p>Targets {@code ReaderT<Id, EventLog, A>}. Notifications are side effects that produce {@link
 * Unit}; during replay they return Unit without performing any actual notification.
 */
@NullMarked
public final class ReplayNotificationInterpreter
    extends NotificationOpInterpreter<ReaderTKind.Witness<IdKind.Witness, EventLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleSendReceipt(
      NotificationOp.SendReceipt<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog _) -> op.k().apply(Unit.INSTANCE));
  }

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleAlertFraudTeam(
      NotificationOp.AlertFraudTeam<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog _) -> op.k().apply(Unit.INSTANCE));
  }
}
