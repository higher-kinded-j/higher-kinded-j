// Fixture for .claude/skills/hkj-optics/reference/indexed-optics.md
//
// The page teaches indexed optics, so it elides the domain it indexes over: a line item, the order
// that holds them, and the customer whose field an IndexedLens labels. They are the book's order
// service, declared with the book's components. Supplying them here means every IndexedTraversals
// / IndexedFold / IndexedLens / Pair call on the page is compiled against the real API, so a
// renamed method fails the build instead of quietly misleading a reader.
//
// The domain types are TOP-LEVEL: a snippet that declares its own type does not extend Fixture, so
// anything it references has to be a top-level type of the assembled unit.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

/** The book's order line: a SKU, a quantity, and a price the page does arithmetic on. */
record LineItem(String sku, Integer quantity, BigDecimal price) {}

record EmailAddress(String value) {}

/** The book's customer: the record whose field name an IndexedLens carries as its index. */
record Customer(String name, EmailAddress email) {}

enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

/** The book's order: the structure the page composes indexed traversals through. */
record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {}

class Fixture {

  static final List<LineItem> items =
      List.of(
          new LineItem("LAMP", 2, new BigDecimal("9.99")),
          new LineItem("DESK", 1, new BigDecimal("750.00")));

  static final Customer customer =
      new Customer("Alice", new EmailAddress("alice@example.com"));

  static final List<Order> orders =
      List.of(
          new Order(
              UUID.randomUUID(),
              customer,
              items,
              Instant.EPOCH,
              Currency.getInstance("GBP"),
              OrderStatus.NEW));

  static final Map<String, String> metadata = Map.of("delivery.window", "morning");

  static final IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex =
      IndexedTraversals.forList();

  static final IndexedTraversal<String, Map<String, String>, String> metadataTraversal =
      IndexedTraversals.forMap();

  static final Lens<Order, List<LineItem>> linesLens =
      Lens.of(
          Order::lines,
          (order, lines) ->
              new Order(
                  order.id(),
                  order.customer(),
                  lines,
                  order.placedAt(),
                  order.currency(),
                  order.status()));
}
