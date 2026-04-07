// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpInterpreter;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.jspecify.annotations.NullMarked;

/**
 * Test interpreter that always returns a failed charge result.
 *
 * <p>Targets the {@code Id} monad. Useful for testing the failure path in payment processing.
 *
 * <p><b>Not thread-safe.</b> Create a fresh instance per program invocation.
 */
@NullMarked
public final class FailingGatewayInterpreter extends PaymentGatewayOpInterpreter<IdKind.Witness> {

  @Override
  protected <A> Kind<IdKind.Witness, A> handleAuthorise(PaymentGatewayOp.Authorise<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return new Id<>(op.k().apply(new AuthorisationToken("fail-auth", op.amount())));
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return new Id<>(op.k().apply(ChargeResult.failed(op.amount(), "Card declined")));
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleRefund(PaymentGatewayOp.Refund<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return new Id<>(op.k().apply(ChargeResult.failed(op.amount(), "Refund failed")));
  }
}
