// Fixture for hkj-book/src/examples/examples_market_data.md
//
// The page opens with the one line that runs the whole pipeline. The pipeline and its stages live
// in this module's main sources, which are on the gate's classpath, so the fixture only assembles
// them.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.example.market.alert.AlertDispatcher;
import org.higherkindedj.example.market.alert.AlertDispatcher.AlertChannel;
import org.higherkindedj.example.market.alert.AnomalyDetector;
import org.higherkindedj.example.market.enrichment.InMemoryFxRateService;
import org.higherkindedj.example.market.enrichment.InMemoryReferenceDataService;
import org.higherkindedj.example.market.enrichment.TickEnricher;
import org.higherkindedj.example.market.feed.ExchangeFeed;
import org.higherkindedj.example.market.feed.SimulatedExchangeFeed;
import org.higherkindedj.example.market.model.Alert;
import org.higherkindedj.example.market.model.Exchange;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.example.market.pipeline.MarketDataPipeline;
import org.higherkindedj.example.market.pipeline.PipelineConfig;
import org.higherkindedj.example.market.risk.RiskCalculator;
import org.higherkindedj.example.market.risk.RiskPipeline;

class Fixture {

  static final List<Symbol> symbols = List.of(new Symbol("AAPL"), new Symbol("MSFT"));

  static final List<ExchangeFeed> feeds =
      List.of(
          new SimulatedExchangeFeed(Exchange.NYSE, symbols, 150.0, 0.002, 42L),
          new SimulatedExchangeFeed(Exchange.LSE, symbols, 150.0, 0.002, 99L));

  static final MarketDataPipeline pipeline =
      new MarketDataPipeline(
          feeds,
          new TickEnricher(new InMemoryReferenceDataService(), new InMemoryFxRateService(), 8),
          new RiskPipeline(new RiskCalculator(), 4),
          new AnomalyDetector(),
          new AlertDispatcher(List.of(new AlertChannel("log", _ -> {}))),
          PipelineConfig.defaults());
}
