// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.market.model.AggregatedView;
import org.higherkindedj.example.market.model.Alert;
import org.higherkindedj.hkt.vstream.VStream;

/**
 * Detects anomalies in aggregated market views and produces alerts.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link VStream#flatMap} - Each view may produce zero or more alerts
 *   <li>{@link VStream#filter} - Pre-filter views with potential anomalies
 * </ul>
 *
 * <p>Anomaly conditions:
 *
 * <ul>
 *   <li>High risk score in window (> 0.5)
 *   <li>Wide spread (> configurable threshold)
 *   <li>High volume (> configurable threshold)
 * </ul>
 */
public class AnomalyDetector {

  private final double riskThreshold;
  private final double spreadThresholdPct;
  private final long volumeThreshold;

  public AnomalyDetector(double riskThreshold, double spreadThresholdPct, long volumeThreshold) {
    this.riskThreshold = riskThreshold;
    this.spreadThresholdPct = spreadThresholdPct;
    this.volumeThreshold = volumeThreshold;
  }

  public AnomalyDetector() {
    this(0.5, 0.01, 20000);
  }

  /**
   * Detects anomalies in a stream of aggregated views.
   *
   * @param views the input stream
   * @return a stream of alerts for detected anomalies
   */
  public VStream<Alert> detect(VStream<AggregatedView> views) {
    return views.flatMap(
        view -> {
          List<Alert> alerts = checkView(view);
          return alerts.isEmpty() ? VStream.empty() : VStream.fromList(alerts);
        });
  }

  List<Alert> checkView(AggregatedView view) {
    List<Alert> alerts = new ArrayList<>();
    Instant now = Instant.now();

    if (view.maxRiskScore() > riskThreshold) {
      alerts.add(
          new Alert(
              view.symbol(),
              view.maxRiskScore() > 0.8 ? Alert.Severity.CRITICAL : Alert.Severity.WARNING,
              String.format(
                  "High risk score %.2f in window of %d ticks",
                  view.maxRiskScore(), view.tickCount()),
              now));
    }

    double spreadPct =
        view.bestAsk().toDouble() > 0 ? view.spread().toDouble() / view.bestAsk().toDouble() : 0.0;
    if (spreadPct > spreadThresholdPct) {
      alerts.add(
          new Alert(
              view.symbol(),
              Alert.Severity.WARNING,
              String.format("Wide spread %.2f%% across window", spreadPct * 100),
              now));
    }

    if (view.totalVolume().value() > volumeThreshold) {
      alerts.add(
          new Alert(
              view.symbol(),
              Alert.Severity.INFO,
              String.format(
                  "High volume %d across %d ticks", view.totalVolume().value(), view.tickCount()),
              now));
    }

    return alerts;
  }
}
