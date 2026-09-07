// Fixture for hkj-book/src/examples/market_building.md
//
// The page walks the market-data pipeline stage by stage, quoting the example's own feed,
// enricher, risk pipeline and aggregator. Those types live in this module's main sources, which
// are on the gate's classpath, so the fixture only supplies the collaborators each quoted method
// reads: the feed's configuration, the two lookup services, the concurrency bound and the risk
// calculator.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.higherkindedj.example.market.enrichment.FxRateService;
import org.higherkindedj.example.market.enrichment.InMemoryFxRateService;
import org.higherkindedj.example.market.enrichment.InMemoryReferenceDataService;
import org.higherkindedj.example.market.enrichment.ReferenceDataService;
import org.higherkindedj.example.market.error.MarketError;
import org.higherkindedj.example.market.feed.ExchangeFeed;
import org.higherkindedj.example.market.feed.FeedMerger;
import org.higherkindedj.example.market.feed.SimulatedExchangeFeed;
import org.higherkindedj.example.market.model.AggregatedView;
import org.higherkindedj.example.market.model.EnrichedTick;
import org.higherkindedj.example.market.model.Exchange;
import org.higherkindedj.example.market.model.Instrument;
import org.higherkindedj.example.market.model.PriceTick;
import org.higherkindedj.example.market.model.RiskAssessment;
import org.higherkindedj.example.market.model.value.Price;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.example.market.model.value.Volume;
import org.higherkindedj.example.market.risk.RiskCalculator;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;

/** The unfold seed the feed threads: a price per symbol, whose turn it is, and the RNG. */
record FeedState(double[] prices, int index, Random random) {}

class Fixture {

  static final List<Symbol> symbols = List.of(new Symbol("AAPL"), new Symbol("MSFT"));

  static final Exchange exchange = Exchange.NYSE;

  static final double basePrice = 150.0;

  static final double volatility = 0.002;

  static final long seed = 42L;

  static final ExchangeFeed feed =
      new SimulatedExchangeFeed(exchange, symbols, basePrice, volatility, seed);

  static final ReferenceDataService refData = new InMemoryReferenceDataService();

  static final FxRateService fxService = new InMemoryFxRateService();

  static final int concurrency = 8;

  static final List<PriceTick> mergedTicks = List.of();

  static final Consumer<MarketError> onEnrichmentError = _ -> {};

  static final RiskCalculator calculator = new RiskCalculator();

  static List<AggregatedView> computeViewsBySymbol(List<RiskAssessment> window) {
    return List.of();
  }
}
