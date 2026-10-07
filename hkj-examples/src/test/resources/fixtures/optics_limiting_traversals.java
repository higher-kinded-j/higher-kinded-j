// Fixture for hkj-book/src/optics/limiting_traversals.md
//
// The page pages through a product catalogue, and reaches for orders, transactions and log lines
// to show `takingWhile` and `droppingWhile` on other shapes. The models are declared here; the
// snippet that shows them shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
record Product(String sku, String name, BigDecimal price, int stock) {

  Product applyDiscount(int percent) {
    BigDecimal factor = BigDecimal.valueOf(100 - percent, 2);
    return new Product(sku, name, price.multiply(factor).setScale(2, RoundingMode.HALF_EVEN), stock);
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
record SalesMetric(LocalDate date, BigDecimal revenue, int transactions) {}

record Transaction(LocalDateTime timestamp, String status) {

  Transaction withStatus(String newStatus) {
    return new Transaction(timestamp, newStatus);
  }
}

class Fixture {

  static final List<Product> products =
      List.of(
          new Product("SKU001", "Widget", new BigDecimal("10.00"), 100),
          new Product("SKU002", "Gadget", new BigDecimal("25.00"), 50),
          new Product("SKU003", "Gizmo", new BigDecimal("15.00"), 75),
          new Product("SKU004", "Doohickey", new BigDecimal("30.00"), 25),
          new Product("SKU005", "Thingamajig", new BigDecimal("20.00"), 60));

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
