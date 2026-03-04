// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.pipeline;

import java.util.List;
import java.util.Objects;
import org.higherkindedj.example.market.aggregation.WindowAggregator;
import org.higherkindedj.example.market.alert.AlertDispatcher;
import org.higherkindedj.example.market.alert.AnomalyDetector;
import org.higherkindedj.example.market.enrichment.TickEnricher;
import org.higherkindedj.example.market.feed.ExchangeFeed;
import org.higherkindedj.example.market.feed.FeedMerger;
import org.higherkindedj.example.market.model.AggregatedView;
import org.higherkindedj.example.market.model.Alert;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.example.market.model.RiskAssessment;
import org.higherkindedj.example.market.risk.RiskPipeline;
import org.higherkindedj.hkt.vstream.VStream;

/**
 * The main market data pipeline that wires together all processing stages.
 *
 * <p>The pipeline processes data through these stages:
 *
 * <pre>{@code
 * Exchange Feeds ──→ Merge ──→ Enrich ──→ Risk ──→ Aggregate ──→ Detect ──→ Dispatch
 *  (VStreamPar.merge)  (Par.map2)  (parEvalMap)  (chunk+fold)  (flatMap)  (Scope.allSucceed)
 * }</pre>
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link VStream#take} - Safety valve limiting total ticks processed
 *   <li>{@link VStream#peek} - Observation tap for monitoring/logging
 *   <li>{@link VStream#onFinalize} - Cleanup when pipeline shuts down
 * </ul>
 */
public class MarketDataPipeline {

  private final List<ExchangeFeed> feeds;
  private final TickEnricher enricher;
  private final RiskPipeline riskPipeline;
  private final AnomalyDetector anomalyDetector;
  private final AlertDispatcher alertDispatcher;
  private final PipelineConfig config;

  public MarketDataPipeline(
      List<ExchangeFeed> feeds,
      TickEnricher enricher,
      RiskPipeline riskPipeline,
      AnomalyDetector anomalyDetector,
      AlertDispatcher alertDispatcher,
      PipelineConfig config) {
    this.feeds = List.copyOf(Objects.requireNonNull(feeds));
    this.enricher = Objects.requireNonNull(enricher);
    this.riskPipeline = Objects.requireNonNull(riskPipeline);
    this.anomalyDetector = Objects.requireNonNull(anomalyDetector);
    this.alertDispatcher = Objects.requireNonNull(alertDispatcher);
    this.config = Objects.requireNonNull(config);
  }

  /** Returns the merged raw tick stream from all feeds, limited by config. */
  public VStream<PriceTick> mergedTicks() {
    return FeedMerger.merge(feeds).take(config.maxTicks());
  }

  /** Returns the enriched tick stream. */
  public VStream<EnrichedTick> enrichedTicks() {
    return enricher.enrich(mergedTicks());
  }

  /** Returns the risk-assessed stream. */
  public VStream<RiskAssessment> assessedTicks() {
    return riskPipeline.assess(enrichedTicks());
  }

  /** Returns the windowed aggregation stream. */
  public VStream<AggregatedView> aggregatedViews() {
    return WindowAggregator.aggregate(assessedTicks(), config.windowSize());
  }

  /** Returns the alert stream (anomaly detection applied). */
  public VStream<Alert> alerts() {
    return anomalyDetector.detect(aggregatedViews());
  }

  /** Returns the full pipeline: alerts dispatched to all channels. */
  public VStream<Alert> fullPipeline() {
    return alertDispatcher.dispatch(alerts());
  }
}
