// Fixture for hkj-book/src/optics/coupled_fields.md
//
// The page's running example is a Range whose constructor rejects a crossed pair, and it reaches
// for a rectangle, a server configuration and a trade to show the same shape elsewhere. Every
// model and every lens it names is declared here; a snippet that shows one shadows this copy,
// which is why the values below are `sample()` stand-ins rather than constructor calls.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.CoupledLenses;

record Range(int lo, int hi) {

  Range {
    if (lo > hi) {
      throw new IllegalArgumentException("lo (" + lo + ") must be <= hi (" + hi + ")");
    }
  }
}

record Transaction(String id, int min, int max, String note) {}

record Packet(byte[] data, long checksum) {}

record Point(int x, int y) {

  Point translate(int dx, int dy) {
    return new Point(x + dx, y + dy);
  }
}

record Rectangle(Point topLeft, Point bottomRight) {}

record ServerConfig(String host, int minPort, int maxPort) {}

record Config(ServerConfig server) {}

// Both carry generated lenses for the closing pair of coupled3 forms, which names them through
// TradeLenses and TripleLenses. The page declares its own Triple, which shadows this copy, and
// describes Trade in a sentence.
@GenerateLenses
record Triple(int lo, int mid, int hi) {}

@GenerateLenses
record Trade(String currency, BigDecimal amount, int precision) {

  Trade withMoney(String newCurrency, BigDecimal newAmount, int newPrecision) {
    return new Trade(newCurrency, newAmount, newPrecision);
  }
}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Range range = sample();

  static final Lens<Range, Integer> loLens = sample();

  static final Lens<Range, Integer> hiLens = sample();

  static final Lens<Range, Pair<Integer, Integer>> boundsLens = sample();

  static final Lens<Transaction, Integer> minLens = sample();

  static final Lens<Transaction, Integer> maxLens = sample();

  static final Rectangle rect = sample();

  static final Lens<Rectangle, Point> topLeftLens = sample();

  static final Lens<Rectangle, Point> bottomRightLens = sample();

  static final int dx = 3;

  static final int dy = 4;

  static final ServerConfig serverConfig = sample();

  static final Lens<ServerConfig, Integer> minPortLens = sample();

  static final Lens<ServerConfig, Integer> maxPortLens = sample();

  static final Config config = sample();

  static final Lens<Config, ServerConfig> configServerLens = sample();

  static final Lens<ServerConfig, Pair<Integer, Integer>> serverPortsLens = sample();

  static final Lens<Trade, String> currencyLens = sample();

  static final Lens<Trade, BigDecimal> amountLens = sample();

  static final Lens<Trade, Integer> precisionLens = sample();

  static final Quotation quotation = sample();

  static final String sku = "LAMP";

  static final BigDecimal newPrice = BigDecimal.ONE;

  static long computeChecksum(byte[] data) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

// The quotation the page contrasts pairing with, beside the chapter's Order: it states the total
// its lines add up to, which an order does not store, so its own operation keeps the two consistent
// and it exposes no lens onto the derived field. Its lines are the chapter cast's LineItem.
record Quotation(List<LineItem> lines, BigDecimal total) {

  Quotation withLine(String sku, UnaryOperator<LineItem> change) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
