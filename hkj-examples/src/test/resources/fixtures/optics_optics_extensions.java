// Fixture for hkj-book/src/optics/optics_extensions.md
//
// The page reads and writes an affiliate through the lens extensions, then an order's lines
// through the traversal ones. The affiliate is the page's supporting type beside the cast, declared
// here as ExtensionsBook declares it, with the generator its companion comes from; the order and
// its lines are the chapter's cast.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.optics.extensions.LensExtensions.getEither;
import static org.higherkindedj.optics.extensions.LensExtensions.getMaybe;
import static org.higherkindedj.optics.extensions.LensExtensions.getValidated;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyEither;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyMaybe;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyTry;
import static org.higherkindedj.optics.extensions.LensExtensions.setIfValid;
import static org.higherkindedj.optics.extensions.TraversalExtensions.collectErrors;
import static org.higherkindedj.optics.extensions.TraversalExtensions.countValid;
import static org.higherkindedj.optics.extensions.TraversalExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllEither;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllMaybe;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllValidated;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyWherePossible;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Affiliate(String id, String name, String email, Integer commission, String bio) {}

// The reader's own logger, whatever it is. Named here so the page's snippets can say what they
// would log without the gate carrying a logging framework.
class Log {

  void info(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void error(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, so the orders a
   * snippet ships are named here rather than assembled.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Log logger = new Log();

  static final Affiliate affiliate =
      new Affiliate("a1", "Alice", "alice@example.com", 10, "Lighting blogger");

  static final Affiliate original = affiliate;

  static final Lens<Affiliate, String> bioLens = AffiliateLenses.bio();

  static final List<LineItem> items =
      List.of(
          new LineItem("SKU-1", 1, new BigDecimal("999.99")),
          new LineItem("SKU-2", 2, new BigDecimal("29.99")));

  static final List<Order> orders = sample();

  static final Traversal<List<LineItem>, BigDecimal> allPrices =
      Traversals.<LineItem>forList().andThen(LineItemLenses.price().asTraversal());

  static String capitalize(String value) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String updateEmailInDatabase(String email) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
