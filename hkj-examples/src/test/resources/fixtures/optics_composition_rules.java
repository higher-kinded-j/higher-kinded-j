// Fixture for hkj-book/src/optics/composition_rules.md
//
// The page is a table of what composes with what, and each row is worked against whichever model
// makes the point: a configuration, a shape, an order. The order, its customer and lines, the
// customer profile, the payment and the consignment are the chapter's cast; the others are
// declared here, and a row that shows its model shadows this copy.
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
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.CardFocus;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Returned;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.example.book.optics.cast.Payment;
import org.higherkindedj.example.book.optics.cast.PaymentPrisms;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

record DatabaseSettings(String host, int port) {}

record Config(Optional<DatabaseSettings> database) {}

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

  static final Lens<Consignment, ConsignmentState> consignmentStateLens = sample();

  static final Prism<ConsignmentState, Returned> returnedPrism = sample();

  static final Lens<Returned, String> reasonLens = sample();

  static final Fold<Order, LineItem> linesFold = sample();

  static final Fold<LineItem, String> skuFold = sample();

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
