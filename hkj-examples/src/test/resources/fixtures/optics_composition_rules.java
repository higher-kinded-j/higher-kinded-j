// Fixture for hkj-book/src/optics/composition_rules.md
//
// The page is a table of what composes with what, and each row is worked against whichever model
// makes the point - a configuration, a shape, an order. All of them are declared here; a row that
// shows its model shadows this copy.
//
// The fixture is generic: the page's two summaries state each composition over free A, B, C and
// D, and the wrapper each snippet compiles in passes those on, so the optics they name are
// instance fields typed over them.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

record DatabaseSettings(String host, int port) {}

record Config(Optional<DatabaseSettings> database) {}

@GenerateLenses
record Money(BigDecimal amount) {}

@GenerateLenses
record LineItem(String sku, Money price) {}

@GenerateLenses
record ContactInfo(String email, String phone) {}

@GeneratePrisms
sealed interface Customer permits ActiveCustomer, InactiveCustomer {}

@GenerateLenses
record ActiveCustomer(String email, ContactInfo contact) implements Customer {}

record InactiveCustomer(String reason) implements Customer {}

@GenerateLenses
@GenerateTraversals
record Order(String id, Customer customer, List<LineItem> lineItems) {

  boolean isActive() {
    return true;
  }
}

record Item(String sku) {}

record Person(String firstName, String lastName) {}

record Employee(String name, String email) {}

record Team(String name, Employee lead, List<Employee> members) {}

class Fixture<A, B, C, D> {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <T> T sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Lens<Config, Optional<DatabaseSettings>> databaseLens = sample();

  static final Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

  static final Lens<DatabaseSettings, String> hostLens = sample();

  static final Lens<Order, Customer> orderCustomerLens = sample();

  static final Prism<Customer, ContactInfo> customerContactPrism = sample();

  static final Lens<ContactInfo, String> contactEmailLens = sample();

  static final Fold<Customer, Order> ordersFold = sample();

  static final Fold<Order, Item> itemsFold = sample();

  static final Fold<Person, String> firstNameFold = sample();

  static final Fold<Person, String> lastNameFold = sample();

  static final Lens<Team, Employee> leadLens = sample();

  static final Lens<Employee, String> emailLens = sample();

  static final Fold<Team, Employee> membersFold = sample();

  // The summaries' optics, one per step, over the free A, B, C and D.
  final Lens<A, B> lensAB = sample();

  final Lens<B, C> lensBC = sample();

  final Prism<A, B> prismAB = sample();

  final Prism<B, C> prismBC = sample();

  final Affine<A, B> affineAB = sample();

  final Affine<B, C> affineBC = sample();

  final Traversal<A, B> traversalAB = sample();

  final Traversal<B, C> traversalBC = sample();

  // Three optics of different kinds, for the asTraversal() fallback.
  final Lens<A, B> optic1 = sample();

  final Prism<B, C> optic2 = sample();

  final Affine<C, D> optic3 = sample();
}
