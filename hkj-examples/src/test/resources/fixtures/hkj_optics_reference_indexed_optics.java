// Fixture for .claude/skills/hkj-optics/reference/indexed-optics.md
//
// The page teaches indexed optics, so it elides the domain it indexes over: a line item, the order
// that holds them, and the customer whose field an IndexedLens labels. Supplying them here means
// every IndexedTraversals / IndexedFold / IndexedLens / Pair call on the page is compiled against
// the real API, so a renamed method fails the build instead of quietly misleading a reader.
//
// The domain types are TOP-LEVEL: a snippet that declares its own type does not extend Fixture, so
// anything it references has to be a top-level type of the assembled unit.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Map;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

/** The reader's own line item: a name, a quantity, and a price the page does arithmetic on. */
record LineItem(String productName, int quantity, double price) {}

/** The reader's own order: the structure the page composes indexed traversals through. */
record Order(String id, List<LineItem> items) {}

/** The reader's own customer: the record whose field name an IndexedLens carries as its index. */
record Customer(String name, String email) {}

class Fixture {

  static final List<LineItem> items =
      List.of(new LineItem("Widget", 2, 9.99), new LineItem("Gadget", 1, 750.0));

  static final List<Order> orders = List.of(new Order("o-1", items));

  static final Map<String, String> metadata = Map.of("delivery.window", "morning");

  static final Customer customer = new Customer("Alice", "alice@example.com");

  static final IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex =
      IndexedTraversals.forList();

  static final IndexedTraversal<String, Map<String, String>, String> metadataTraversal =
      IndexedTraversals.forMap();

  static final Lens<Order, List<LineItem>> itemsLens =
      Lens.of(Order::items, (order, updated) -> new Order(order.id(), updated));
}
