// Fixture for hkj-book/src/examples/market_alerts.md
//
// The page finishes the market-data pipeline: detection, fan-out dispatch, throttling, feed
// failover and the wiring that runs it. The example's own types are on the gate's classpath, so
// the fixture supplies only what each quoted method reads - the channels, the two feeds it fails
// over between, and the stages the assembled pipeline is given.
//
// The wiring snippet quotes the pipeline's stages as bare methods, as the earlier stages on
// this page are quoted; the assembly snippet below it constructs the real class by name.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.market.alert.AlertDispatcher;
import org.higherkindedj.example.market.alert.AlertDispatcher.AlertChannel;
import org.higherkindedj.example.market.alert.AnomalyDetector;
import org.higherkindedj.example.market.aggregation.WindowAggregator;
import org.higherkindedj.example.market.enrichment.InMemoryFxRateService;
import org.higherkindedj.example.market.enrichment.InMemoryReferenceDataService;
import org.higherkindedj.example.market.enrichment.TickEnricher;
import org.higherkindedj.example.market.feed.ExchangeFeed;
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
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamThrottle;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

class Fixture {

  static final List<Symbol> symbols = List.of(new Symbol("AAPL"), new Symbol("MSFT"));

  static final SimulatedExchangeFeed nyse =
      new SimulatedExchangeFeed(Exchange.NYSE, symbols, 150.0, 0.002, 42L);

  static final SimulatedExchangeFeed lse =
      new SimulatedExchangeFeed(Exchange.LSE, symbols, 150.0, 0.002, 99L);

  static final SimulatedExchangeFeed tse =
      new SimulatedExchangeFeed(Exchange.TSE, symbols, 150.0, 0.002, 7L);

  static final ExchangeFeed feed = nyse;

  static final List<ExchangeFeed> feeds = List.of(nyse, lse, tse);

  static final VStream<PriceTick> primaryFeed = nyse.ticks();

  static final VStream<PriceTick> fallbackFeed = lse.ticks();

  static final TickEnricher enricher =
      new TickEnricher(new InMemoryReferenceDataService(), new InMemoryFxRateService(), 8);

  static final RiskPipeline riskPipeline = new RiskPipeline(new RiskCalculator(), 4);

  static final AnomalyDetector anomalyDetector = new AnomalyDetector();

  static final List<AlertChannel> channels =
      List.of(new AlertChannel("log", _ -> {}), new AlertChannel("email", _ -> {}));

  static final AlertDispatcher alertDispatcher = new AlertDispatcher(channels);

  static final List<Alert> dispatchedAlerts = new CopyOnWriteArrayList<>();

  static final PipelineConfig config = PipelineConfig.defaults();

  static final Alert alert =
      new Alert(new Symbol("AAPL"), Alert.Severity.INFO, "quiet", Instant.now());

  static List<Alert> checkView(AggregatedView view) {
    return List.of();
  }
}
