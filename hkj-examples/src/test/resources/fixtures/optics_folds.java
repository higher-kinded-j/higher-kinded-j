// Fixture for hkj-book/src/optics/folds.md
//
// The page queries one e-commerce model - product, purchase, purchase history - and then reaches for a
// team of employees and a configuration record to show fold combination. All three domains are
// declared here with their generators, so the page's snippets name genuinely generated folds; a
// snippet that shows a model shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.optics.extensions.FoldExtensions.findMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.previewMaybe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
@GenerateFolds
record Product(String name, BigDecimal price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds
@GenerateTraversals
record Purchase(String purchaseId, List<Product> items, String customerName) {}

@GenerateLenses
@GenerateFolds
record PurchaseHistory(List<Purchase> purchases) {}

@GenerateFolds
record ProductCatalog(List<Product> products) {}

record Team(String name, Employee lead, List<Employee> members) {}

record Employee(String name, String email) {}

record Config(String host, Optional<String> port, Optional<String> database) {}

class Fixture {

  static final Product laptop = new Product("Laptop", new BigDecimal("999.99"), "Electronics", true);

  static final Product mouse = new Product("Mouse", new BigDecimal("25.00"), "Electronics", true);

  static final Product desk = new Product("Desk", new BigDecimal("350.00"), "Furniture", false);

  static final Purchase purchase = new Purchase("ORD-123", List.of(laptop, mouse, desk), "Alice");

  static final Purchase purchase1 = purchase;

  static final Purchase purchase2 = new Purchase("ORD-124", List.of(mouse), "Bob");

  static final Purchase purchase3 = new Purchase("ORD-125", List.of(desk), "Carol");

  static final List<Purchase> purchases = List.of(purchase1, purchase2, purchase3);

  static final PurchaseHistory history = new PurchaseHistory(purchases);

  static final Fold<Purchase, Product> itemsFold = PurchaseFolds.items();

  // The BigDecimal sum the page writes in Step 4, which later snippets reuse by name.
  static final Monoid<BigDecimal> sumMonoid =
      new Monoid<>() {
        @Override
        public BigDecimal empty() {
          return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal combine(BigDecimal a, BigDecimal b) {
          return a.add(b);
        }
      };

  static final List<Double> discounts = List.of(0.9, 0.95, 0.85);

  static final Fold<List<Double>, Double> discountsFold = Fold.of(d -> d);

  static final Employee employee = new Employee("Alice", "alice@example.com");

  static final Team team =
      new Team("Core", employee, List.of(new Employee("Bob", "bob@example.com")));

  static final Lens<Employee, String> nameLens =
      Lens.of(Employee::name, (e, v) -> new Employee(v, e.email()));

  static final Lens<Employee, String> emailLens =
      Lens.of(Employee::email, (e, v) -> new Employee(e.name(), v));

  static final Fold<Team, String> teamNameFold = Fold.of(t -> List.of(t.name()));

  static final Fold<Team, String> leadNameFold = Fold.of(t -> List.of(t.lead().name()));

  static final Fold<Team, String> memberNamesFold =
      Fold.of(t -> t.members().stream().map(Employee::name).toList());

  static final Fold<Team, String> leadEmail =
      Fold.of(t -> List.of(t.lead().email()));

  static final Fold<Team, String> memberEmails =
      Fold.of(t -> t.members().stream().map(Employee::email).toList());

  static final Config config = new Config("localhost", Optional.of("8080"), Optional.empty());

  static final Lens<Config, String> hostLens =
      Lens.of(Config::host, (c, v) -> new Config(v, c.port(), c.database()));

  static final Prism<Config, String> portPrism =
      Prism.of(Config::port, p -> new Config("localhost", Optional.of(p), Optional.empty()));

  static final Affine<Config, String> dbAffine =
      Affine.of(Config::database, (c, v) -> new Config(c.host(), c.port(), Optional.of(v)));

  // A stand-in for the reporting the page hands its extracted products to: snippets are compiled,
  // not run.
  static String generateReport(List<Product> products) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
