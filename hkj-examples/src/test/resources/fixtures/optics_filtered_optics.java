// Fixture for hkj-book/src/optics/filtered_optics.md
//
// The page narrows an order's lines, a list of orders and a list of customer histories with
// `filtered` and `filterBy`. The orders are the chapter cast's own, which hkj-examples' main sources
// put on the gate's classpath. The history the page keeps beside them is declared here with the
// generator its lens comes from; the snippet that shows it shadows this copy. `tenPercentOff` is
// the function the page includes from FilteredOpticsBook.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.higherkindedj.example.book.optics.cast.*;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

// A customer's orders, kept beside the cast's Customer with a mark saying whether they are a VIP
@GenerateLenses
record CustomerHistory(Customer customer, List<Order> orders, boolean vip) {}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  // Ten per cent off a line's price, to the penny, as FilteredOpticsBook declares it
  static final UnaryOperator<LineItem> tenPercentOff =
      line -> {
        BigDecimal discounted =
            line.price().multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_EVEN);
        return new LineItem(line.sku(), line.quantity(), discounted);
      };

  static final Order order = sample();

  static final List<Order> orders = List.of();

  static final List<LineItem> lines = List.of();

  static final List<CustomerHistory> histories = List.of();

  static final Traversal<List<LineItem>, LineItem> lineTraversal = Traversals.forList();
}
