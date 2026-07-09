// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.pipeline;

import java.time.Duration;
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
import org.higherkindedj.hkt.vstream.VStreamThrottle;

/**
 * The main market data pipeline that wires together all processing stages.
 *
 * <p>The pipeline processes data through these stages:
 *
 * <pre>{@code
 * Exchange Feeds ──→ Merge ──→ Resilience+Throttle ──→ Enrich ──→ Risk ──→ Aggregate ──→ Detect ──→ Dispatch
 *  (VStreamPar.merge)   (recoverWith, throttle)       (parEvalMap)  (chunk)   (flatMap)  (Scope.allSucceed)
 * }</pre>
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link VStream#take} - Safety valve limiting total ticks processed
 *   <li>{@link VStream#recoverWith} - Falls back to an empty stream if a feed errors, so one bad
 *       exchange cannot abort the pipeline
 *   <li>{@link VStreamThrottle#throttle} - Rate-limits feed ingestion to a sustainable burst
 * </ul>
 */
public class MarketDataPipeline {

  /** Maximum ticks admitted per throttle window (a small burst to demonstrate rate limiting). */
  private static final int FEED_THROTTLE_BURST = 10;

  private static final Duration FEED_THROTTLE_WINDOW = Duration.ofMillis(1);

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

  /**
   * Returns the merged raw tick stream from all feeds, limited by config, with feed resilience and
   * throttling applied on the live path.
   */
  public VStream<PriceTick> mergedTicks() {
    VStream<PriceTick> merged = FeedMerger.merge(feeds).take(config.maxTicks());
    // Resilience on the live feed path: fall back to an empty stream if a feed errors (so one bad
    // exchange cannot kill the pipeline), then throttle ingestion to a sustainable burst.
    // Per-task protection (retry, circuit breaking, bulkheads) lives on the core path types —
    // VTaskPath/VResultPath withRetry/withCircuitBreaker/withBulkhead — not on the stream layer.
    VStream<PriceTick> resilient = merged.recoverWith(_ -> VStream.empty());
    return VStreamThrottle.throttle(resilient, FEED_THROTTLE_BURST, FEED_THROTTLE_WINDOW);
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
