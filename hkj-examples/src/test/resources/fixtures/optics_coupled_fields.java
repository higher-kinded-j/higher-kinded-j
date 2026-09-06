// Fixture for hkj-book/src/optics/coupled_fields.md
//
// The page's running example is a Range whose constructor rejects a crossed pair, and it reaches
// for a rectangle, a server configuration and a trade to show the same shape elsewhere. Every
// model and every lens it names is declared here; a snippet that shows one shadows this copy,
// which is why the values below are `sample()` stand-ins rather than constructor calls.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.function.BiFunction;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.optics.Lens;
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

record Line(String id, BigDecimal price) {

  Line withPrice(BigDecimal newPrice) {
    return new Line(id, newPrice);
  }
}

record Point(int x, int y) {

  Point translate(int dx, int dy) {
    return new Point(x + dx, y + dy);
  }
}

record Rectangle(Point topLeft, Point bottomRight) {}

record ServerConfig(String host, int minPort, int maxPort) {}

record Config(ServerConfig server) {}

record Triple(int lo, int mid, int hi) {}

record Trade(String currency, BigDecimal amount, int precision) {

  Trade withMoney(String newCurrency, BigDecimal newAmount, int newPrecision) {
    return new Trade(newCurrency, newAmount, newPrecision);
  }
}

class Fixture {

  // A value the page names but does not build. Snippets are compiled, not run, and a snippet that
  // shows a model shadows the one above, so naming a constructor here would tie the fixture to one
  // shape of it.
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

  static final Order order = sample();

  static final String lineId = "L-1";

  static final BigDecimal newPrice = BigDecimal.ONE;

  static long computeChecksum(byte[] data) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

// The order the page contrasts pairing with: its own operation keeps the total consistent, so it
// exposes no lens onto the derived field.
record Order(String id, java.util.List<Line> lines, BigDecimal totalPrice) {

  Order withLine(String lineId, java.util.function.UnaryOperator<Line> change) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
