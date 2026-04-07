// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpInterpreter;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Production interpreter for payment gateway operations.
 *
 * <p>Targets the {@code IO} monad. In a real system, these would call Stripe, Adyen, or similar
 * payment gateways. This example uses simulated delays and deterministic responses.
 */
@NullMarked
public final class ProductionGatewayInterpreter
    extends PaymentGatewayOpInterpreter<IOKind.Witness> {

  @Override
  protected <A> Kind<IOKind.Witness, A> handleAuthorise(PaymentGatewayOp.Authorise<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(
        IO.delay(
            () -> op.k().apply(new AuthorisationToken("auth-" + System.nanoTime(), op.amount()))));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(
        IO.delay(() -> op.k().apply(ChargeResult.success(TransactionId.generate(), op.amount()))));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handleRefund(PaymentGatewayOp.Refund<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(
        IO.delay(() -> op.k().apply(ChargeResult.success(op.transactionId(), op.amount()))));
  }
}
