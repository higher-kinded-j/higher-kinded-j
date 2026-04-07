// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.NotificationOpInterpreter;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.jspecify.annotations.NullMarked;

/**
 * Test interpreter that captures all notification operations.
 *
 * <p>Targets the {@code Id} monad for pure, synchronous testing. Records receipts and fraud alerts
 * for assertion in tests.
 *
 * <p><b>Not thread-safe.</b> Create a fresh instance per program invocation. Do not share across
 * concurrent interpretations.
 */
@NullMarked
public final class CapturingNotificationInterpreter
    extends NotificationOpInterpreter<IdKind.Witness> {

  private final List<String> receipts = new ArrayList<>();
  private final List<String> alerts = new ArrayList<>();

  /**
   * Returns an unmodifiable view of all sent receipts.
   *
   * @return the list of receipt descriptions
   */
  public List<String> receipts() {
    return Collections.unmodifiableList(receipts);
  }

  /**
   * Returns an unmodifiable view of all fraud alerts.
   *
   * @return the list of alert descriptions
   */
  public List<String> alerts() {
    return Collections.unmodifiableList(alerts);
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleSendReceipt(NotificationOp.SendReceipt<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    receipts.add("receipt:" + op.customer().email());
    return new Id<>(op.k().apply(Unit.INSTANCE));
  }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleAlertFraudTeam(NotificationOp.AlertFraudTeam<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    alerts.add("fraud-alert:" + op.customer().id() + ":score=" + op.riskScore().score());
    return new Id<>(op.k().apply(Unit.INSTANCE));
  }
}
