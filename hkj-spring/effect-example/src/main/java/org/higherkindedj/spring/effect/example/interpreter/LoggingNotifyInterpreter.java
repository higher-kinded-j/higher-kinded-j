// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.spring.autoconfigure.effect.Interpreter;
import org.higherkindedj.spring.effect.example.effect.NotifyOp;
import org.higherkindedj.spring.effect.example.effect.NotifyOpKind;
import org.higherkindedj.spring.effect.example.effect.NotifyOpKindHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging interpreter for {@link NotifyOp} targeting the IO monad.
 *
 * <p>Logs notifications instead of sending them. Records sent notifications for testing.
 */
@Interpreter(NotifyOp.class)
public class LoggingNotifyInterpreter implements Natural<NotifyOpKind.Witness, IOKind.Witness> {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotifyInterpreter.class);

  private final List<String> sentNotifications = Collections.synchronizedList(new ArrayList<>());

  @Override
  @SuppressWarnings("unchecked")
  public <A> Kind<IOKind.Witness, A> apply(Kind<NotifyOpKind.Witness, A> fa) {
    NotifyOp<A> op = NotifyOpKindHelper.NOTIFY_OP.narrow(fa);
    return switch (op) {
      case NotifyOp.SendConfirmation<A> send ->
          IOKindHelper.IO_OP.widen(
              IO.delay(
                  () -> {
                    String message =
                        "Order " + send.orderId() + " confirmed for customer " + send.customerId();
                    log.info("[Notify] {}", message);
                    sentNotifications.add(message);
                    return send.k().apply(Unit.INSTANCE);
                  }));
    };
  }

  /** Returns sent notifications for testing/inspection. */
  public List<String> sentNotifications() {
    return Collections.unmodifiableList(sentNotifications);
  }
}
