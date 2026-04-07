// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.higherkindedj.example.payment.effect.PaymentGatewayOp;
import org.higherkindedj.example.payment.effect.PaymentGatewayOpInterpreter;
import org.higherkindedj.example.payment.model.AuthorisationToken;
import org.higherkindedj.example.payment.model.ChargeResult;
import org.higherkindedj.example.payment.model.TransactionId;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.jspecify.annotations.NullMarked;

/**
 * Test interpreter that records all gateway operations and returns deterministic results.
 *
 * <p>Targets the {@code Id} monad for pure, synchronous testing. All operations are recorded and
 * can be inspected after interpretation to verify program behaviour.
 *
 * <p><b>Not thread-safe.</b> Create a fresh instance per program invocation. Do not share across
 * concurrent interpretations.
 */
@NullMarked
public final class RecordingGatewayInterpreter extends PaymentGatewayOpInterpreter<IdKind.Witness> {

  private final List<String> calls = new ArrayList<>();

  /**
   * Returns an unmodifiable view of the recorded operation names.
   *
   * @return the list of recorded calls
   */
  public List<String> calls() {
    return Collections.unmodifiableList(calls);
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleAuthorise(PaymentGatewayOp.Authorise<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    calls.add("authorise:" + op.amount());
    return new Id<>(op.k().apply(new AuthorisationToken("test-auth-token", op.amount())));
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    calls.add("charge:" + op.amount());
    return new Id<>(
        op.k().apply(ChargeResult.success(new TransactionId("test-txn-001"), op.amount())));
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleRefund(PaymentGatewayOp.Refund<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    calls.add("refund:" + op.transactionId());
    return new Id<>(op.k().apply(ChargeResult.success(op.transactionId(), op.amount())));
  }
}
