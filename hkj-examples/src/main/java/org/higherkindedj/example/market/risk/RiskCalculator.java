// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.risk;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.RiskAssessment;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Computes risk assessments for enriched ticks.
 *
 * <p>Checks for:
 *
 * <ul>
 *   <li>Wide spreads (possible low liquidity)
 *   <li>Large volume spikes (possible institutional activity)
 *   <li>Price deviation from reference price
 * </ul>
 */
public class RiskCalculator {

  private final double spreadThreshold;
  private final long volumeThreshold;

  /**
   * Creates a risk calculator.
   *
   * @param spreadThreshold spread percentage above which to flag (e.g. 0.005 for 0.5%)
   * @param volumeThreshold volume above which to flag as unusual
   */
  public RiskCalculator(double spreadThreshold, long volumeThreshold) {
    this.spreadThreshold = spreadThreshold;
    this.volumeThreshold = volumeThreshold;
  }

  public RiskCalculator() {
    this(0.005, 5000);
  }

  /**
   * Assesses risk for a single enriched tick.
   *
   * @param tick the enriched tick to assess
   * @return a VTask producing the risk assessment
   */
  public VTask<RiskAssessment> assess(EnrichedTick tick) {
    return VTask.of(
        () -> {
          List<String> flags = new ArrayList<>();
          double score = 0.0;

          // Check spread
          double spreadPct = tick.tick().spread().toDouble() / tick.tick().mid().toDouble();
          if (spreadPct > spreadThreshold) {
            flags.add("WIDE_SPREAD(" + String.format("%.2f%%", spreadPct * 100) + ")");
            score += 0.3;
          }

          // Check volume
          if (tick.tick().volume().value() > volumeThreshold) {
            flags.add("HIGH_VOLUME(" + tick.tick().volume() + ")");
            score += 0.3;
          }

          // Check bid-ask inversion (anomalous)
          if (tick.tick().bid().compareTo(tick.tick().ask()) > 0) {
            flags.add("BID_ASK_INVERSION");
            score += 0.5;
          }

          return new RiskAssessment(tick, Math.min(score, 1.0), List.copyOf(flags));
        });
  }
}
