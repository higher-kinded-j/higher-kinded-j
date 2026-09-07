// Fixture for hkj-book/src/optics/limiting_traversals.md
//
// The page pages through a product catalogue, and reaches for orders, transactions and log lines
// to show `takingWhile` and `droppingWhile` on other shapes. The models are declared here; the
// snippet that shows them shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.ixed.IxedInstances;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Product(String sku, String name, double price, int stock) {

  Product applyDiscount(double percentage) {
    return new Product(sku, name, price * (1 - percentage), stock);
  }
}

@GenerateLenses
record Catalogue(String name, List<Product> products) {}

@GenerateLenses
record LineItem(Product product, int quantity) {}

@GenerateLenses
@GenerateTraversals
record Order(String id, List<LineItem> items, LocalDateTime created) {}

@GenerateLenses
record SalesMetric(LocalDate date, double revenue, int transactions) {}

record Transaction(LocalDateTime timestamp, String status) {

  Transaction withStatus(String newStatus) {
    return new Transaction(timestamp, newStatus);
  }
}

class Fixture {

  static final List<Product> products =
      List.of(
          new Product("SKU001", "Widget", 10.0, 100),
          new Product("SKU002", "Gadget", 25.0, 50),
          new Product("SKU003", "Gizmo", 15.0, 75),
          new Product("SKU004", "Doohickey", 30.0, 25),
          new Product("SKU005", "Thingamajig", 20.0, 60));

  static final Catalogue catalogue = new Catalogue("Autumn", products);

  static final List<Order> orders =
      List.of(new Order("ORD-1", List.of(new LineItem(products.getFirst(), 2)), LocalDateTime.MIN));

  static final List<Transaction> transactions =
      List.of(new Transaction(LocalDateTime.MIN, "PENDING"));

  static final Traversal<List<Product>, Product> first5 = ListTraversals.taking(5);

  static final int startIndex = 0;

  static final int chunkSize = 10;

  static final int userProvidedIndex = 1;

  static final int totalPages = 3;

  // Stand-ins for the work the page hands each page or product to: snippets are compiled, not run.
  static void processPage(List<Product> page) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void notifyOutOfStock(Product product, int index) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
