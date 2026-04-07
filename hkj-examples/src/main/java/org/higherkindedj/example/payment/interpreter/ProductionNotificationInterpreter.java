// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.interpreter;

import java.util.Objects;
import org.higherkindedj.example.payment.effect.NotificationOp;
import org.higherkindedj.example.payment.effect.NotificationOpInterpreter;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Production interpreter for notification operations.
 *
 * <p>Targets the {@code IO} monad. In a real system, this would send emails or push notifications.
 * This example simulates by printing to the console.
 */
@NullMarked
public final class ProductionNotificationInterpreter
    extends NotificationOpInterpreter<IOKind.Witness> {

  @Override
  protected <A> Kind<IOKind.Witness, A> handleSendReceipt(NotificationOp.SendReceipt<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(
        IO.delay(
            () -> {
              System.out.println("  [Notification] Receipt sent to " + op.customer().email());
              return op.k().apply(Unit.INSTANCE);
            }));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handleAlertFraudTeam(NotificationOp.AlertFraudTeam<A> op) {
    Objects.requireNonNull(op, "op cannot be null");
    return IOKindHelper.IO_OP.widen(
        IO.delay(
            () -> {
              System.out.println(
                  "  [Notification] Fraud alert for customer "
                      + op.customer().id()
                      + " (score: "
                      + op.riskScore().score()
                      + ")");
              return op.k().apply(Unit.INSTANCE);
            }));
  }
}
