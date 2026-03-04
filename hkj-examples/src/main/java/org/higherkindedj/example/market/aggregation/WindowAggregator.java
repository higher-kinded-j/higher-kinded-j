// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.aggregation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.higherkindedj.example.market.model.AggregatedView;
import org.higherkindedj.example.market.model.RiskAssessment;
import org.higherkindedj.example.market.model.value.Price;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.example.market.model.value.Volume;
import org.higherkindedj.hkt.vstream.VStream;

/**
 * Aggregates risk-assessed ticks into windowed views using chunking and folding.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link VStream#chunk(int)} - Groups ticks into fixed-size windows
 *   <li>{@link VStream#flatMap} - Expands per-symbol groups into individual views
 * </ul>
 *
 * <p>Each window produces one {@link AggregatedView} per symbol, containing VWAP, best bid/ask,
 * total volume, and maximum risk score.
 */
public interface WindowAggregator {

  /**
   * Aggregates ticks into fixed-size windows on a per-symbol basis.
   *
   * <p>Each chunk is grouped by symbol before aggregation, ensuring that VWAP and other metrics are
   * computed correctly for each symbol rather than mixing ticks from different symbols.
   *
   * @param assessed the stream of risk assessments
   * @param windowSize the number of ticks per window
   * @return a stream of aggregated views, one per symbol per window
   */
  static VStream<AggregatedView> aggregate(VStream<RiskAssessment> assessed, int windowSize) {
    return assessed
        .chunk(windowSize)
        .flatMap(window -> VStream.fromList(computeViewsBySymbol(window)));
  }

  /**
   * Groups a window of assessments by symbol and computes an aggregated view for each group.
   *
   * @param window the list of risk assessments in this window
   * @return a list of aggregated views, one per symbol present in the window
   */
  private static List<AggregatedView> computeViewsBySymbol(List<RiskAssessment> window) {
    Map<Symbol, List<RiskAssessment>> bySymbol = new LinkedHashMap<>();
    for (RiskAssessment ra : window) {
      bySymbol.computeIfAbsent(ra.tick().tick().symbol(), k -> new ArrayList<>()).add(ra);
    }
    List<AggregatedView> views = new ArrayList<>();
    for (List<RiskAssessment> group : bySymbol.values()) {
      views.add(computeView(group));
    }
    return views;
  }

  /**
   * Computes an aggregated view from a window of assessments.
   *
   * <p>Calculates VWAP (Volume-Weighted Average Price):
   *
   * <pre>
   * VWAP = Sum(price_i * volume_i) / Sum(volume_i)
   * </pre>
   */
  private static AggregatedView computeView(List<RiskAssessment> window) {
    if (window.isEmpty()) {
      throw new IllegalArgumentException("window must not be empty");
    }

    var firstSymbol = window.getFirst().tick().tick().symbol();

    double totalVolumeWeightedPrice = 0.0;
    long totalVol = 0;
    double bestBid = Double.MIN_VALUE;
    double bestAsk = Double.MAX_VALUE;
    double maxRisk = 0.0;

    for (RiskAssessment ra : window) {
      double mid = ra.tick().midInUsd().toDouble();
      long vol = ra.tick().tick().volume().value();

      totalVolumeWeightedPrice += mid * vol;
      totalVol += vol;

      double bidVal = ra.tick().tick().bid().toDouble();
      double askVal = ra.tick().tick().ask().toDouble();

      if (bidVal > bestBid) {
        bestBid = bidVal;
      }
      if (askVal < bestAsk) {
        bestAsk = askVal;
      }
      if (ra.riskScore() > maxRisk) {
        maxRisk = ra.riskScore();
      }
    }

    double vwap = totalVol > 0 ? totalVolumeWeightedPrice / totalVol : 0.0;

    return new AggregatedView(
        firstSymbol,
        Price.of(vwap),
        Price.of(bestBid),
        Price.of(bestAsk),
        Volume.of(totalVol),
        window.size(),
        maxRisk);
  }
}
