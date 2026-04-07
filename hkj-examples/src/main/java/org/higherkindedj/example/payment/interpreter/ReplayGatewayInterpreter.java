// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpInterpreter;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.EventLog;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Replay interpreter for payment gateway operations.
 *
 * <p>Targets {@code ReaderT<Id, EventLog, A>}. Reads pre-recorded results from the {@link EventLog}
 * environment, allowing deterministic reconstruction of a past execution without calling external
 * services.
 */
@NullMarked
public final class ReplayGatewayInterpreter
    extends PaymentGatewayOpInterpreter<ReaderTKind.Witness<IdKind.Witness, EventLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleAuthorise(
      PaymentGatewayOp.Authorise<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(
        ID, (EventLog log) -> op.k().apply(log.<AuthorisationToken>get("authorise")));
  }

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleCharge(
      PaymentGatewayOp.Charge<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog log) -> op.k().apply(log.<ChargeResult>get("charge")));
  }

  @Override
  protected <A> Kind<ReaderTKind.Witness<IdKind.Witness, EventLog>, A> handleRefund(
      PaymentGatewayOp.Refund<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return ReaderT.reader(ID, (EventLog log) -> op.k().apply(log.<ChargeResult>get("refund")));
  }
}
