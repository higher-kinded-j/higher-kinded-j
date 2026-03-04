// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
import org.higherkindedj.example.market.risk.RiskCalculator;
import org.higherkindedj.example.market.risk.RiskPipeline;
import org.higherkindedj.hkt.vstream.VStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the Market Data Aggregation Pipeline. */
@DisplayName("Market Data Pipeline")
@SuppressWarnings("preview")
class MarketDataPipelineTest {

  private static final List<Symbol> SYMBOLS =
      List.of(new Symbol("AAPL"), new Symbol("GOOGL"), new Symbol("MSFT"));

  private InMemoryReferenceDataService refData;
  private InMemoryFxRateService fxService;

  @BeforeEach
  void setUp() {
    refData = new InMemoryReferenceDataService(0);
    fxService = new InMemoryFxRateService(0);
  }

  @Nested
  @DisplayName("Feed Generation")
  class FeedGenerationTests {

    @Test
    @DisplayName("generates ticks with valid prices")
    void generatesTicksWithValidPrices() {
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);

      List<PriceTick> ticks = feed.ticks().take(10).toList().run();

      assertThat(ticks).hasSize(10);
      for (PriceTick tick : ticks) {
        assertThat(tick.bid().toDouble()).isPositive();
        assertThat(tick.ask().toDouble()).isPositive();
        assertThat(tick.ask().toDouble()).isGreaterThanOrEqualTo(tick.bid().toDouble());
        assertThat(tick.exchange()).isEqualTo(Exchange.NYSE);
      }
    }

    @Test
    @DisplayName("cycles through symbols")
    void cyclesThroughSymbols() {
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);

      List<PriceTick> ticks = feed.ticks().take(6).toList().run();

      // Should cycle: AAPL, GOOGL, MSFT, AAPL, GOOGL, MSFT
      assertThat(ticks.get(0).symbol()).isEqualTo(new Symbol("AAPL"));
      assertThat(ticks.get(1).symbol()).isEqualTo(new Symbol("GOOGL"));
      assertThat(ticks.get(2).symbol()).isEqualTo(new Symbol("MSFT"));
      assertThat(ticks.get(3).symbol()).isEqualTo(new Symbol("AAPL"));
    }

    @Test
    @DisplayName("is deterministic with same seed")
    void isDeterministicWithSameSeed() {
      SimulatedExchangeFeed feed1 =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      SimulatedExchangeFeed feed2 =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);

      List<PriceTick> ticks1 = feed1.ticks().take(5).toList().run();
      List<PriceTick> ticks2 = feed2.ticks().take(5).toList().run();

      for (int i = 0; i < 5; i++) {
        assertThat(ticks1.get(i).bid()).isEqualTo(ticks2.get(i).bid());
        assertThat(ticks1.get(i).ask()).isEqualTo(ticks2.get(i).ask());
      }
    }
  }

  @Nested
  @DisplayName("Feed Merging")
  class FeedMergingTests {

    @Test
    @DisplayName("merges ticks from multiple exchanges")
    void mergesFromMultipleExchanges() {
      // Under CI load, a single take() may not schedule both feeds before the limit is reached.
      // Awaitility retries with fresh feeds until both exchanges appear.
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> {
                SimulatedExchangeFeed nyse =
                    new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
                SimulatedExchangeFeed lse =
                    new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.002, 99L);

                List<PriceTick> merged = FeedMerger.merge(nyse, lse).take(100).toList().run();

                assertThat(merged).hasSize(100);
                Set<String> exchanges =
                    merged.stream()
                        .map(t -> t.exchange().name())
                        .collect(Collectors.toSet());
                assertThat(exchanges).contains("NYSE", "LSE");
              });
    }

    @Test
    @DisplayName("merges three exchange feeds")
    void mergesThreeFeeds() {
      // Under CI load, a single take() may not schedule all three feeds before the limit is
      // reached. Awaitility retries with fresh feeds until all exchanges appear.
      await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> {
                SimulatedExchangeFeed nyse =
                    new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
                SimulatedExchangeFeed lse =
                    new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.002, 99L);
                SimulatedExchangeFeed tse =
                    new SimulatedExchangeFeed(Exchange.TSE, SYMBOLS, 150.0, 0.002, 7L);

                List<PriceTick> merged =
                    FeedMerger.merge(List.of(nyse, lse, tse)).take(300).toList().run();

                assertThat(merged).hasSize(300);
                Set<String> exchanges =
                    merged.stream()
                        .map(t -> t.exchange().name())
                        .collect(Collectors.toSet());
                assertThat(exchanges).contains("NYSE", "LSE", "TSE");
              });
    }
  }

  @Nested
  @DisplayName("Enrichment")
  class EnrichmentTests {

    @Test
    @DisplayName("enriches ticks with instrument and FX data")
    void enrichesWithInstrumentAndFx() {
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      TickEnricher enricher = new TickEnricher(refData, fxService, 4);

      List<EnrichedTick> enriched = enricher.enrich(feed.ticks().take(3)).toList().run();

      assertThat(enriched).hasSize(3);
      for (EnrichedTick et : enriched) {
        assertThat(et.instrument()).isNotNull();
        assertThat(et.fxRate()).isNotNull();
        assertThat(et.midInUsd().toDouble()).isPositive();
      }
    }

    @Test
    @DisplayName("enriches concurrently with bounded parallelism")
    void enrichesConcurrently() {
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      TickEnricher enricher = new TickEnricher(refData, fxService, 8);

      List<EnrichedTick> enriched = enricher.enrich(feed.ticks().take(20)).toList().run();

      assertThat(enriched).hasSize(20);
    }
  }

  @Nested
  @DisplayName("Risk Assessment")
  class RiskAssessmentTests {

    @Test
    @DisplayName("produces risk scores in valid range")
    void producesValidRiskScores() {
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      TickEnricher enricher = new TickEnricher(refData, fxService, 4);
      RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

      List<RiskAssessment> assessed =
          riskPipeline.assess(enricher.enrich(feed.ticks().take(10))).toList().run();

      assertThat(assessed).hasSize(10);
      for (RiskAssessment ra : assessed) {
        assertThat(ra.riskScore()).isBetween(0.0, 1.0);
        assertThat(ra.flags()).isNotNull();
      }
    }
  }

  @Nested
  @DisplayName("Windowed Aggregation")
  class AggregationTests {

    @Test
    @DisplayName("chunks ticks into windows and computes VWAP")
    void chunksAndComputesVwap() {
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      TickEnricher enricher = new TickEnricher(refData, fxService, 4);
      RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

      List<AggregatedView> views =
          WindowAggregator.aggregate(riskPipeline.assess(enricher.enrich(feed.ticks().take(10))), 5)
              .toList()
              .run();

      // 10 ticks across 3 symbols, chunked into windows of 5, then grouped by symbol per chunk
      // Each chunk produces one view per distinct symbol present
      assertThat(views).hasSizeGreaterThanOrEqualTo(2);
      for (AggregatedView view : views) {
        assertThat(view.symbol()).isNotNull();
        assertThat(view.vwap().toDouble()).isPositive();
        assertThat(view.totalVolume().value()).isPositive();
        assertThat(view.tickCount()).isGreaterThanOrEqualTo(1);
      }
    }
  }

  @Nested
  @DisplayName("Anomaly Detection")
  class AnomalyDetectionTests {

    @Test
    @DisplayName("detects anomalies in views")
    void detectsAnomalies() {
      AnomalyDetector detector = new AnomalyDetector(0.0, 0.0, 0); // Everything triggers

      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      TickEnricher enricher = new TickEnricher(refData, fxService, 4);
      RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

      List<Alert> alerts =
          detector
              .detect(
                  WindowAggregator.aggregate(
                      riskPipeline.assess(enricher.enrich(feed.ticks().take(5))), 5))
              .toList()
              .run();

      assertThat(alerts).isNotEmpty();
      for (Alert alert : alerts) {
        assertThat(alert.severity()).isNotNull();
        assertThat(alert.message()).isNotBlank();
      }
    }
  }

  @Nested
  @DisplayName("Alert Dispatch")
  class AlertDispatchTests {

    @Test
    @DisplayName("dispatches to all channels")
    void dispatchesToAllChannels() {
      AtomicInteger channel1 = new AtomicInteger();
      AtomicInteger channel2 = new AtomicInteger();

      AlertDispatcher dispatcher =
          new AlertDispatcher(
              List.of(
                  new AlertDispatcher.AlertChannel("ch1", a -> channel1.incrementAndGet()),
                  new AlertDispatcher.AlertChannel("ch2", a -> channel2.incrementAndGet())));

      AnomalyDetector detector = new AnomalyDetector(0.0, 0.0, 0);
      SimulatedExchangeFeed feed =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      TickEnricher enricher = new TickEnricher(refData, fxService, 4);
      RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

      VStream<Alert> pipeline =
          dispatcher.dispatch(
              detector.detect(
                  WindowAggregator.aggregate(
                      riskPipeline.assess(enricher.enrich(feed.ticks().take(5))), 5)));

      List<Alert> alerts = pipeline.toList().run();

      assertThat(alerts).isNotEmpty();
      // Both channels should have received the same number of alerts
      assertThat(channel1.get()).isEqualTo(alerts.size());
      assertThat(channel2.get()).isEqualTo(alerts.size());
    }
  }

  @Nested
  @DisplayName("Recovery")
  class RecoveryTests {

    @Test
    @DisplayName("recovers from feed failure with fallback stream")
    void recoversWithFallback() {
      // VStream.recoverWith wraps only the first pull, so it handles the case where
      // a feed fails to connect entirely. The fallback stream takes over completely.
      VStream<PriceTick> failingFeed =
          VStream.fail(new RuntimeException("NYSE feed: Connection refused"));

      VStream<PriceTick> fallback =
          new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.002, 99L).ticks();

      VStream<PriceTick> resilient = failingFeed.recoverWith(error -> fallback);

      List<PriceTick> ticks = resilient.take(5).toList().run();

      assertThat(ticks).hasSize(5);
      // All ticks from LSE fallback since primary failed on first pull
      assertThat(ticks).allMatch(t -> t.exchange().equals(Exchange.LSE));
    }
  }

  @Nested
  @DisplayName("Full Pipeline Integration")
  class FullPipelineTests {

    @Test
    @DisplayName("runs complete pipeline end-to-end")
    void runsFullPipeline() {
      SimulatedExchangeFeed nyse =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.005, 42L);
      SimulatedExchangeFeed lse =
          new SimulatedExchangeFeed(Exchange.LSE, SYMBOLS, 150.0, 0.005, 99L);

      TickEnricher enricher = new TickEnricher(refData, fxService, 8);
      RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(0.003, 4000), 4);
      AnomalyDetector detector = new AnomalyDetector(0.2, 0.005, 20000);

      AtomicInteger dispatches = new AtomicInteger();
      AlertDispatcher dispatcher =
          new AlertDispatcher(
              List.of(new AlertDispatcher.AlertChannel("test", a -> dispatches.incrementAndGet())));

      PipelineConfig config = new PipelineConfig(8, 4, 5, 20);
      MarketDataPipeline pipeline =
          new MarketDataPipeline(
              List.of(nyse, lse), enricher, riskPipeline, detector, dispatcher, config);

      // Run the full pipeline
      List<Alert> alerts = pipeline.fullPipeline().toList().run();

      // Pipeline should complete without errors
      assertThat(alerts).isNotNull();
      // Each alert should have been dispatched
      assertThat(dispatches.get()).isEqualTo(alerts.size());
    }

    @Test
    @DisplayName("merged ticks limited by config maxTicks")
    void mergedTicksLimitedByConfig() {
      SimulatedExchangeFeed nyse =
          new SimulatedExchangeFeed(Exchange.NYSE, SYMBOLS, 150.0, 0.002, 42L);
      PipelineConfig config = new PipelineConfig(4, 4, 5, 15);

      TickEnricher enricher = new TickEnricher(refData, fxService, 4);
      RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);
      AnomalyDetector detector = new AnomalyDetector();
      AlertDispatcher dispatcher =
          new AlertDispatcher(List.of(new AlertDispatcher.AlertChannel("test", a -> {})));

      MarketDataPipeline pipeline =
          new MarketDataPipeline(
              List.of(nyse), enricher, riskPipeline, detector, dispatcher, config);

      List<PriceTick> ticks = pipeline.mergedTicks().toList().run();

      assertThat(ticks).hasSize(15);
    }
  }
}
