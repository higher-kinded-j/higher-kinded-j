// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.runner;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.market.aggregation.WindowAggregator;
import org.higherkindedj.example.market.alert.AlertDispatcher;
import org.higherkindedj.example.market.alert.AnomalyDetector;
import org.higherkindedj.example.market.enrichment.InMemoryFxRateService;
import org.higherkindedj.example.market.enrichment.InMemoryReferenceDataService;
import org.higherkindedj.example.market.enrichment.TickEnricher;
import org.higherkindedj.example.market.feed.FeedMerger;
import org.higherkindedj.example.market.feed.SimulatedExchangeFeed;
import org.higherkindedj.example.market.model.AggregatedView;
import org.higherkindedj.example.market.model.Alert;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.Exchange;
import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.example.market.model.RiskAssessment;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.example.market.pipeline.MarketDataPipeline;
import org.higherkindedj.example.market.pipeline.PipelineConfig;
import org.higherkindedj.example.market.resilience.FeedResilience;
import org.higherkindedj.example.market.risk.RiskCalculator;
import org.higherkindedj.example.market.risk.RiskPipeline;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vstream.VStreamThrottle;
import org.higherkindedj.hkt.vtask.Par;

/**
 * Progressive tutorial demonstrating a market data aggregation pipeline.
 *
 * <p>This example builds up a complete market data pipeline step by step, introducing one
 * Higher-Kinded-J concept at a time:
 *
 * <ol>
 *   <li><b>Step 1:</b> {@link VStream#unfold} — Generate simulated price ticks
 *   <li><b>Step 2:</b> {@link VStreamPar#merge} — Merge feeds from multiple exchanges
 *   <li><b>Step 3:</b> {@link Par#map2} + {@link VStreamPar#parEvalMap} — Concurrent enrichment
 *   <li><b>Step 4:</b> {@link VStreamPar#parEvalMapUnordered} — Parallel risk assessment
 *   <li><b>Step 5:</b> {@link VStream#chunk} — Windowed aggregation (VWAP)
 *   <li><b>Step 6:</b> Anomaly detection + {@link org.higherkindedj.hkt.vtask.Scope#allSucceed} —
 *       Multi-channel alert dispatch
 *   <li><b>Step 7:</b> {@link VStreamThrottle#throttle} — Rate-limited publishing
 *   <li><b>Step 8:</b> {@link CircuitBreaker} + Recovery — Resilient feeds
 *   <li><b>Step 9:</b> Full pipeline — End-to-end integration
 * </ol>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.market.runner.MarketDataDemo}
 */
@SuppressWarnings("preview")
public class MarketDataDemo {

  // Common symbols used across demos
  private static final List<Symbol> SYMBOLS =
      List.of(new Symbol("AAPL"), new Symbol("GOOGL"), new Symbol("MSFT"));

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║     Market Data Aggregator — Virtual Threads Capstone          ║");
    System.out.println("╠══════════════════════════════════════════════════════════════════╣");
    System.out.println("║  A progressive tutorial building a real-time market data       ║");
    System.out.println("║  pipeline using VStream, VTask, Par, Scope, and Resilience.    ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

    step1_GenerateTicksWithUnfold();
    step2_MergeExchangeFeeds();
    step3_ConcurrentEnrichment();
    step4_ParallelRiskAssessment();
    step5_WindowedAggregation();
    step6_AnomalyDetectionAndAlerts();
    step7_RateLimitedPublishing();
    step8_ResilientFeeds();
    step9_FullPipeline();

    System.out.println("\n=== Tutorial Complete ===");
  }

  // =========================================================================
  // Step 1: VStream.unfold — Generate Simulated Price Ticks
  // =========================================================================

  /**
   * Introduces VStream.unfold for effectful state-based stream generation.
   *
   * <p>Each tick is produced by a random walk applied to the previous price. The unfold function
   * receives the current state (prices + RNG) and produces a tick plus the next state.
   */
  private static void step1_GenerateTicksWithUnfold() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 1: VStream.unfold — Generate Simulated Price Ticks        │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  VStream.unfold(state, f) generates ticks from a seed state.");
    System.out.println("  Each pull applies a random walk to produce the next price.\n");

    SimulatedExchangeFeed nyseFeed =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);

    // Take 5 ticks — nothing happens until toList().run()
    List<PriceTick> ticks = nyseFeed.ticks().take(5).toList().run();

    System.out.println("  Generated " + ticks.size() + " ticks from NYSE:");
    for (PriceTick tick : ticks) {
      System.out.printf(
          "    %s  bid=%-10s ask=%-10s vol=%-5s [%s]%n",
          tick.symbol(), tick.bid(), tick.ask(), tick.volume(), tick.exchange().name());
    }
    System.out.println("  Key: VStream is lazy — no ticks are generated until toList().run().\n");
  }

  // =========================================================================
  // Step 2: VStreamPar.merge — Merge Feeds from Multiple Exchanges
  // =========================================================================

  /**
   * Introduces concurrent stream merging with VStreamPar.merge.
   *
   * <p>Each exchange feed runs on its own virtual thread. Ticks are emitted as soon as any exchange
   * produces one, providing true concurrent interleaving.
   */
  private static void step2_MergeExchangeFeeds() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 2: VStreamPar.merge — Merge Feeds from Multiple Exchanges │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  VStreamPar.merge(feeds) runs each feed on a virtual thread.");
    System.out.println("  Ticks arrive in whatever order they're produced.\n");

    SimulatedExchangeFeed nyse =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
    SimulatedExchangeFeed lse = new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.002, 99L);
    SimulatedExchangeFeed tse = new SimulatedExchangeFeed(Exchange.TSE, SYMBOLS, 150.0, 0.002, 7L);

    VStream<PriceTick> merged = FeedMerger.merge(List.of(nyse, lse, tse));

    List<PriceTick> ticks = merged.take(9).toList().run();

    System.out.println("  Merged " + ticks.size() + " ticks from 3 exchanges:");
    for (PriceTick tick : ticks) {
      System.out.printf(
          "    %-6s %-5s bid=%-10s [%s]%n",
          tick.exchange().name(), tick.symbol(), tick.bid(), tick.exchange().region());
    }
    System.out.println("  Key: Each feed runs on its own virtual thread — true concurrency.\n");
  }

  // =========================================================================
  // Step 3: Par.map2 + VStreamPar.parEvalMap — Concurrent Enrichment
  // =========================================================================

  /**
   * Introduces concurrent enrichment using Par.map2 (per-tick) and VStreamPar.parEvalMap (across
   * the stream).
   *
   * <p>For each tick, we fetch instrument data and FX rate in parallel using Par.map2. Across the
   * stream, we process up to 8 ticks concurrently using parEvalMap.
   */
  private static void step3_ConcurrentEnrichment() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 3: Par.map2 + parEvalMap — Concurrent Enrichment          │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  Par.map2(taskA, taskB, combine) runs two lookups concurrently.");
    System.out.println("  parEvalMap(stream, 8, f) processes 8 ticks in parallel.\n");

    SimulatedExchangeFeed feed =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
    TickEnricher enricher =
        new TickEnricher(new InMemoryReferenceDataService(0), new InMemoryFxRateService(0), 8);

    long start = System.currentTimeMillis();
    List<EnrichedTick> enriched = enricher.enrich(feed.ticks().take(10)).toList().run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("  Enriched " + enriched.size() + " ticks in " + elapsed + "ms:");
    for (EnrichedTick et : enriched.subList(0, Math.min(5, enriched.size()))) {
      System.out.printf(
          "    %-5s %-20s fx=%-6s midUSD=%-10s%n",
          et.tick().symbol(), et.instrument().name(), et.fxRate(), et.midInUsd());
    }
    if (enriched.size() > 5) {
      System.out.println("    ... (" + (enriched.size() - 5) + " more)");
    }
    System.out.println("  Key: Par.map2 fetches ref-data + FX concurrently per tick.\n");
  }

  // =========================================================================
  // Step 4: VStreamPar.parEvalMapUnordered — Parallel Risk Assessment
  // =========================================================================

  /**
   * Introduces unordered parallel processing for independent computations.
   *
   * <p>Risk assessments are independent of each other, so we use parEvalMapUnordered for maximum
   * throughput — results emit in completion order rather than input order.
   */
  private static void step4_ParallelRiskAssessment() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 4: parEvalMapUnordered — Parallel Risk Assessment         │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  parEvalMapUnordered emits in completion order for max throughput.");
    System.out.println("  Risk assessments are independent — order doesn't matter.\n");

    SimulatedExchangeFeed feed =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
    TickEnricher enricher =
        new TickEnricher(new InMemoryReferenceDataService(0), new InMemoryFxRateService(0), 8);
    RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

    List<RiskAssessment> assessed =
        riskPipeline.assess(enricher.enrich(feed.ticks().take(10))).toList().run();

    System.out.println("  Assessed " + assessed.size() + " ticks:");
    for (RiskAssessment ra : assessed) {
      String flagStr = ra.flags().isEmpty() ? "none" : String.join(", ", ra.flags());
      System.out.printf(
          "    %-5s risk=%.2f flags=[%s]%n", ra.tick().tick().symbol(), ra.riskScore(), flagStr);
    }
    System.out.println("  Key: Unordered processing maximises throughput for independent tasks.\n");
  }

  // =========================================================================
  // Step 5: VStream.chunk — Windowed Aggregation (VWAP)
  // =========================================================================

  /**
   * Introduces chunking for windowed aggregation.
   *
   * <p>VStream.chunk(n) groups ticks into windows of n elements. Each window is then folded into an
   * AggregatedView containing VWAP, best bid/ask, total volume, and risk summary.
   */
  private static void step5_WindowedAggregation() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 5: VStream.chunk — Windowed Aggregation (VWAP)            │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  VStream.chunk(5) groups ticks into windows of 5.");
    System.out.println("  Each window is folded into VWAP + best bid/ask + volume.\n");

    SimulatedExchangeFeed feed =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
    TickEnricher enricher =
        new TickEnricher(new InMemoryReferenceDataService(0), new InMemoryFxRateService(0), 8);
    RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

    VStream<AggregatedView> views =
        WindowAggregator.aggregate(riskPipeline.assess(enricher.enrich(feed.ticks().take(20))), 5);

    List<AggregatedView> results = views.toList().run();

    System.out.println("  Produced " + results.size() + " aggregated views:");
    for (AggregatedView view : results) {
      System.out.printf(
          "    %-5s vwap=%-10s spread=%-10s vol=%-6s ticks=%-2d maxRisk=%.2f%n",
          view.symbol(),
          view.vwap(),
          view.spread(),
          view.totalVolume(),
          view.tickCount(),
          view.maxRiskScore());
    }
    System.out.println(
        "  Key: chunk + fold gives windowed analytics without materialising all data.\n");
  }

  // =========================================================================
  // Step 6: Anomaly Detection + Scope.allSucceed — Multi-Channel Alerts
  // =========================================================================

  /**
   * Introduces anomaly detection with flatMap and multi-channel alert dispatch using
   * Scope.allSucceed.
   *
   * <p>Each aggregated view is checked for anomalies. Detected anomalies produce alerts that are
   * dispatched to multiple channels concurrently via Scope.allSucceed.
   */
  private static void step6_AnomalyDetectionAndAlerts() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 6: Anomaly Detection + Scope.allSucceed — Alerts          │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  VStream.flatMap produces zero or more alerts per view.");
    System.out.println("  Scope.allSucceed dispatches to all channels concurrently.\n");

    SimulatedExchangeFeed feed =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.01, 42L); // Higher volatility
    TickEnricher enricher =
        new TickEnricher(new InMemoryReferenceDataService(0), new InMemoryFxRateService(0), 8);
    RiskPipeline riskPipeline =
        new RiskPipeline(new RiskCalculator(0.002, 3000), 4); // Lower thresholds

    AnomalyDetector detector = new AnomalyDetector(0.2, 0.005, 15000);

    AtomicInteger logCount = new AtomicInteger();
    AtomicInteger webhookCount = new AtomicInteger();
    AlertDispatcher dispatcher =
        new AlertDispatcher(
            List.of(
                new AlertDispatcher.AlertChannel(
                    "log",
                    alert -> {
                      logCount.incrementAndGet();
                    }),
                new AlertDispatcher.AlertChannel(
                    "webhook",
                    alert -> {
                      webhookCount.incrementAndGet();
                    })));

    VStream<AggregatedView> views =
        WindowAggregator.aggregate(riskPipeline.assess(enricher.enrich(feed.ticks().take(30))), 5);

    VStream<Alert> alertStream = dispatcher.dispatch(detector.detect(views));

    List<Alert> alerts = alertStream.toList().run();

    System.out.println("  Detected " + alerts.size() + " alerts:");
    for (Alert alert : alerts) {
      System.out.printf("    [%s] %s — %s%n", alert.severity(), alert.symbol(), alert.message());
    }
    System.out.println(
        "  Dispatched to: log(" + logCount.get() + "), webhook(" + webhookCount.get() + ")");
    System.out.println("  Key: Scope.allSucceed sends to all channels in parallel.\n");
  }

  // =========================================================================
  // Step 7: VStreamThrottle.throttle — Rate-Limited Publishing
  // =========================================================================

  /**
   * Introduces rate limiting with VStreamThrottle.
   *
   * <p>In production, downstream consumers (dashboards, APIs) may not handle unlimited throughput.
   * VStreamThrottle.throttle(stream, maxElements, window) ensures we don't overwhelm them.
   */
  private static void step7_RateLimitedPublishing() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 7: VStreamThrottle.throttle — Rate-Limited Publishing     │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  throttle(stream, maxElements, window) limits emission rate.");
    System.out.println("  metered(stream, interval) adds fixed delay between elements.\n");

    SimulatedExchangeFeed feed =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);

    // Throttle to 3 ticks per 100ms
    VStream<PriceTick> throttled =
        VStreamThrottle.throttle(feed.ticks(), 3, Duration.ofMillis(100));

    long start = System.currentTimeMillis();
    List<PriceTick> ticks = throttled.take(6).toList().run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("  Produced " + ticks.size() + " ticks in " + elapsed + "ms");
    System.out.println("  (Without throttle, this would be near-instant)");
    System.out.println("  Key: Throttle protects downstream consumers from being overwhelmed.\n");
  }

  // =========================================================================
  // Step 8: CircuitBreaker + Recovery — Resilient Feeds
  // =========================================================================

  /**
   * Introduces resilience patterns: CircuitBreaker for feed protection and VStream.recoverWith for
   * fallback streams.
   *
   * <p>When an exchange feed fails repeatedly, the circuit breaker trips open, preventing further
   * calls. The stream recovers by switching to a fallback feed.
   */
  private static void step8_ResilientFeeds() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 8: CircuitBreaker + Recovery — Resilient Feeds            │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  CircuitBreaker trips after repeated failures.");
    System.out.println("  VStream.recoverWith switches to a fallback stream.\n");

    // Simulate a primary feed that can't connect (fails on first pull).
    // VStream.recoverWith wraps the first pull, so it handles connection failures
    // by seamlessly switching to the fallback feed.
    VStream<PriceTick> primaryFeed =
        VStream.fail(new RuntimeException("NYSE feed: Connection refused"));

    // Fallback feed
    VStream<PriceTick> fallbackFeed =
        new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.002, 99L).ticks();

    // Use resilience wrapper
    FeedResilience resilience = FeedResilience.withDefaults();
    VStream<PriceTick> resilientFeed = resilience.withFallback(primaryFeed, fallbackFeed);

    List<PriceTick> ticks = resilientFeed.take(6).toList().run();

    System.out.println("  Primary feed failed — seamlessly switched to fallback:");
    System.out.println("  Collected " + ticks.size() + " ticks from fallback feed:");
    for (PriceTick tick : ticks) {
      System.out.printf(
          "    %-5s %-6s bid=%-10s%n", tick.symbol(), tick.exchange().name(), tick.bid());
    }
    System.out.println("  Key: recoverWith switches to fallback when the primary feed fails.\n");
  }

  // =========================================================================
  // Step 9: Full Pipeline — End-to-End Integration
  // =========================================================================

  /**
   * Demonstrates the complete end-to-end pipeline combining all previous steps.
   *
   * <p>Data flows: Feeds -> Merge -> Enrich -> Risk -> Aggregate -> Detect -> Dispatch
   */
  private static void step9_FullPipeline() {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Step 9: Full Pipeline — End-to-End Integration                 │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println(
        "  Feeds ──→ Merge ──→ Enrich ──→ Risk ──→ Aggregate ──→ Detect ──→ Dispatch");
    System.out.println(
        "  All stages compose lazily — nothing runs until the terminal operation.\n");

    // Create feeds
    SimulatedExchangeFeed nyse =
        new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.005, 42L);
    SimulatedExchangeFeed lse = new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.005, 99L);
    SimulatedExchangeFeed tse = new SimulatedExchangeFeed(Exchange.TSE, SYMBOLS, 150.0, 0.005, 7L);

    // Create services
    InMemoryReferenceDataService refData = new InMemoryReferenceDataService(0);
    InMemoryFxRateService fxService = new InMemoryFxRateService(0);
    TickEnricher enricher = new TickEnricher(refData, fxService, 8);
    RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(0.003, 4000), 4);
    AnomalyDetector detector = new AnomalyDetector(0.2, 0.005, 20000);

    AtomicInteger alertsDispatched = new AtomicInteger();
    AlertDispatcher dispatcher =
        new AlertDispatcher(
            List.of(
                new AlertDispatcher.AlertChannel("log", a -> alertsDispatched.incrementAndGet()),
                new AlertDispatcher.AlertChannel(
                    "webhook", a -> alertsDispatched.incrementAndGet())));

    PipelineConfig config = new PipelineConfig(8, 4, 5, 30);

    // Build the pipeline
    MarketDataPipeline pipeline =
        new MarketDataPipeline(
            List.of(nyse, lse, tse), enricher, riskPipeline, detector, dispatcher, config);

    // Everything is lazy until now — execute!
    long start = System.currentTimeMillis();
    List<Alert> alerts = pipeline.fullPipeline().toList().run();
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("  Pipeline completed in " + elapsed + "ms");
    System.out.println("  Alerts generated: " + alerts.size());
    System.out.println("  Alert dispatches: " + alertsDispatched.get());

    for (Alert alert : alerts) {
      System.out.printf("    [%s] %s — %s%n", alert.severity(), alert.symbol(), alert.message());
    }

    System.out.println("\n  HKJ features used in this pipeline:");
    System.out.println("    • VStream.unfold         — Simulated tick generation");
    System.out.println("    • VStreamPar.merge       — Concurrent feed merging");
    System.out.println("    • Par.map2               — Parallel ref-data + FX lookups");
    System.out.println("    • VStreamPar.parEvalMap  — Bounded concurrent enrichment");
    System.out.println("    • parEvalMapUnordered    — Unordered parallel risk assessment");
    System.out.println("    • VStream.chunk          — Fixed-size windowed aggregation");
    System.out.println("    • VStream.flatMap        — Zero-or-more alerts per view");
    System.out.println("    • Scope.allSucceed       — Multi-channel alert dispatch");
    System.out.println("    • CircuitBreaker         — Feed failure protection");
    System.out.println("    • VStream.recoverWith    — Fallback stream on failure");
    System.out.println("    • VStream.take           — Safety valve for demo runs");
  }
}
