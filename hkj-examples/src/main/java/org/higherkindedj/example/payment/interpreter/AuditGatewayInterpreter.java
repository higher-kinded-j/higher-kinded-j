// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpInterpreter;
import org.higherkindedj.example.payment.model.AuditLog;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.jspecify.annotations.NullMarked;

/**
 * Audit interpreter for payment gateway operations.
 *
 * <p>Targets {@code WriterT<Id, AuditLog, A>}. Each operation logs an audit entry and returns a
 * deterministic result. The accumulated {@link AuditLog} can be extracted after interpretation for
 * compliance review.
 */
@NullMarked
public final class AuditGatewayInterpreter
    extends PaymentGatewayOpInterpreter<WriterTKind.Witness<IdKind.Witness, AuditLog>> {

  private static final IdMonad ID = IdMonad.instance();

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleAuthorise(
      PaymentGatewayOp.Authorise<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(new AuthorisationToken("audit-auth", op.amount()));
    AuditLog log = AuditLog.of("AUTHORISE", op.amount() + " via " + op.method());
    return WriterT.writer(ID, result, log);
  }

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleCharge(
      PaymentGatewayOp.Charge<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(ChargeResult.success(new TransactionId("audit-txn-001"), op.amount()));
    AuditLog log = AuditLog.of("CHARGE", op.amount() + " via " + op.method());
    return WriterT.writer(ID, result, log);
  }

  @Override
  protected <A> Kind<WriterTKind.Witness<IdKind.Witness, AuditLog>, A> handleRefund(
      PaymentGatewayOp.Refund<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    A result = op.k().apply(ChargeResult.success(op.transactionId(), op.amount()));
    AuditLog log = AuditLog.of("REFUND", op.transactionId() + " for " + op.amount());
    return WriterT.writer(ID, result, log);
  }
}
