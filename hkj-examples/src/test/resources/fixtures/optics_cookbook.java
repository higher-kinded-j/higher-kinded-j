// Fixture for hkj-book/src/optics/cookbook.md
//
// The cookbook is a page of independent recipes, and most of them declare the model they work on.
// This fixture holds the few types every recipe assumes - money, a customer, a promotion - and the
// optics the recipes name without repeating. Values are `sample()` stand-ins rather than
// constructor calls: a recipe that shows its model shadows the copy here, and may give it a
// different shape.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.maybe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

record Money(double amount) {

  Money multiply(double factor) {
    return new Money(amount * factor);
  }
}

@GenerateLenses
record Promotion(String code, boolean isActive) {}

@GeneratePrisms
sealed interface Customer permits Verified, LoyaltyMember {}

@GenerateLenses
record Verified(String email) implements Customer {}

@GenerateLenses
record LoyaltyMember(String id, List<Promotion> promotions) implements Customer {}

@GenerateLenses
record LineItem(String productId, int quantity, Money price) {}

@GenerateLenses
record Order(String id, List<LineItem> items, Customer customer) {}

@GenerateLenses
record Settings(boolean darkMode, int fontSize) {}

@GenerateLenses
record Profile(String bio, Optional<Settings> settings) {}

@GenerateLenses
record User(String name, Optional<Profile> profile) {}

sealed interface Notification permits Email, SMS, Push {}

record Email(String address, String subject, String body) implements Notification {}

record SMS(String phone, String message) implements Notification {}

record Push(String token, String title) implements Notification {}

@GenerateLenses
record Config(Map<String, String> settings) {}

record Employee(String name, String email) {}

record Team(String name) {}

record Member(String name) {}

record Role(String name, int level) {}

record Department(String name, List<Employee> employees) {}

record Company(List<Department> departments) {}

record Product(String name, Money price, boolean onSale) {}

record Inventory(List<Product> products) {}

enum Status {
  RUNNING,
  STOPPED
}

record Service(String name, Status status) {}

record Server(String hostname, List<Service> services) {}

record Estate(List<Server> servers) {}

class Fixture {

  // A value the page names but does not build. Snippets are compiled, not run, and a recipe that
  // shows its model shadows the one above, so naming a constructor here would tie the fixture to
  // one shape of it.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final User user = sample();

  static final Order order = sample();

  static final Config config = sample();

  static final Company company = sample();

  static final Team team = sample();

  static final Estate estate = sample();

  static final Inventory inventory = sample();

  static final Lens<Config, Optional<Settings>> configLens = sample();

  static final Prism<Optional<Settings>, Settings> settingsPrism = Prisms.some();

  static final Lens<Team, Employee> teamLeadLens = sample();

  static final Lens<Employee, String> employeeEmailLens = sample();

  static final Lens<Employee, String> employeeNameLens = sample();

  static final Lens<User, List<Notification>> userNotificationsLens = sample();

  static final Lens<Email, String> emailAddressLens = sample();

  static final Lens<Email, String> emailSubjectLens = sample();

  static final Lens<Team, Kind<ListKind.Witness, Member>> teamMembersLens = sample();

  static final Lens<Member, Kind<ListKind.Witness, Role>> memberRolesLens = sample();

  static final Lens<Config, String> configApiKeyLens = sample();

  static final Lens<Config, String> configDbUrlLens = sample();

  static final Lens<Order, List<LineItem>> orderItemsLens = sample();

  static final Lens<LineItem, Integer> lineItemQuantityLens = sample();

  static final Lens<LineItem, BigDecimal> lineItemPriceLens = sample();

  static final Lens<Inventory, List<Product>> inventoryProductsLens = sample();
}
