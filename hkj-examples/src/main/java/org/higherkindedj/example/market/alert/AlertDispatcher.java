// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.alert;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.higherkindedj.example.market.model.Alert;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Dispatches alerts to multiple channels using structured concurrency.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link Scope#allSucceed()} - Sends alerts to all channels concurrently
 *   <li>{@link VStream#mapTask} - Effectful dispatch per alert in the stream
 * </ul>
 *
 * <p>All channels are notified in parallel for each alert. If any channel fails, the dispatch for
 * that alert fails fast.
 */
@SuppressWarnings("preview")
public class AlertDispatcher {

  private final List<AlertChannel> channels;
  private final List<Alert> dispatchedAlerts = new CopyOnWriteArrayList<>();

  /**
   * Creates a dispatcher with the given channels.
   *
   * @param channels the alert channels to dispatch to
   */
  public AlertDispatcher(List<AlertChannel> channels) {
    this.channels = List.copyOf(Objects.requireNonNull(channels));
  }

  /**
   * An alert delivery channel (e.g. log, email, webhook).
   *
   * @param name the channel name
   * @param handler the handler that delivers the alert
   */
  public record AlertChannel(String name, Consumer<Alert> handler) {
    public AlertChannel {
      Objects.requireNonNull(name);
      Objects.requireNonNull(handler);
    }
  }

  /**
   * Dispatches a single alert to all channels concurrently using {@link Scope#allSucceed()}.
   *
   * @param alert the alert to dispatch
   * @return a VTask that completes when all channels have been notified
   */
  public VTask<Unit> dispatchOne(Alert alert) {
    Scope<Unit, List<Unit>> scope = Scope.allSucceed();
    for (AlertChannel channel : channels) {
      scope = scope.fork(VTask.exec(() -> channel.handler().accept(alert)));
    }
    return scope
        .join()
        .map(
            results -> {
              dispatchedAlerts.add(alert);
              return Unit.INSTANCE;
            });
  }

  /**
   * Dispatches all alerts in a stream to all channels.
   *
   * @param alerts the stream of alerts
   * @return a stream that has dispatched each alert
   */
  public VStream<Alert> dispatch(VStream<Alert> alerts) {
    return alerts.mapTask(alert -> dispatchOne(alert).map(u -> alert));
  }

  /** Returns all alerts that have been dispatched (for testing/verification). */
  public List<Alert> getDispatchedAlerts() {
    return List.copyOf(dispatchedAlerts);
  }
}
