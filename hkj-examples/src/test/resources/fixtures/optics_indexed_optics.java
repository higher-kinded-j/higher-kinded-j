// Fixture for hkj-book/src/optics/indexed_optics.md
//
// The page numbers the lines of the chapter cast's Order and keys the metadata kept beside it, and
// reaches for a product list to say when the index does not matter. The cast comes from its package,
// through the on-demand import; the product list is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.higherkindedj.example.book.optics.cast.*;
import org.higherkindedj.optics.EachIndexed;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Product(String name, BigDecimal price, String shipping) {

  Product withShipping(String newShipping) {
    return new Product(name, price, newShipping);
  }
}

class Fixture {

  /** A value the page names but does not build: snippets are compiled, never run. */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final List<LineItem> items =
      List.of(
          new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
          new LineItem("MOUSE", 2, new BigDecimal("24.99")),
          new LineItem("KEYBOARD", 1, new BigDecimal("79.99")));

  static final Map<String, String> metadata =
      Map.of("priority", "express", "gift-wrap", "true", "delivery-note", "Leave at door");

  static final Order order = sample();

  static final List<Order> orders = List.of(order);

  static final List<Product> products = List.of(new Product("Laptop", new BigDecimal("999.99"), "standard"));

  static final List<String> list = List.of("a", "b", "c");

  static final IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex =
      IndexedTraversals.forList();

  static final IndexedTraversal<String, Map<String, String>, String> metadataTraversal =
      IndexedTraversals.forMap();

  static final IndexedTraversal<Integer, List<String>, String> indexed =
      IndexedTraversals.forList();

  // Stand-ins for the work the page hands each entry to: snippets are compiled, not run.
  static Entry<String, String> processWithKey(String key, String value) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static LineItem numberItem(int index, LineItem item) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static LineItem process(int index, LineItem item) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
