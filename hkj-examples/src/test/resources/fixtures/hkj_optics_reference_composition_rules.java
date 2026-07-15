// Fixture for .claude/skills/hkj-optics/reference/composition-rules.md
//
// The page is a composition matrix, so some of its code is deliberately abstract: `optic1.andThen(
// optic2)` over type variables A, B, C, D. A GENERIC fixture lends those type variables to the
// snippet (the wrapper extends Fixture<A, B, C, D>), which is how the page can show a *shape*
// without inventing a domain for it. Those optics are instance fields for that reason: a static
// member cannot use its class's type parameters.
//
// The concrete patterns at the foot of the page get a real domain, annotated for real, so
// `PaymentPrisms.creditCard()` and `OrderTraversals.lineItems()` are the genuinely generated
// companions, and the composition results the page tabulates (Lens+Prism = Affine, Affine+Traversal
// = Traversal) are checked by javac rather than asserted by a table.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

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

/** The reader's money type. */
record Money(BigDecimal amount) {}

/** A line of an order. */
@GenerateLenses
record LineItem(String sku, Money price) {}

/** The customer sum type the page narrows with a prism. */
@GeneratePrisms
sealed interface Customer permits ActiveCustomer, LapsedCustomer {}

@GenerateLenses
record ActiveCustomer(String email) implements Customer {}

record LapsedCustomer(String email) implements Customer {}

/** The structure the page composes through. `isActive` is what Pattern 3 filters on. */
@GenerateLenses
@GenerateTraversals
record Order(Customer customer, List<LineItem> lineItems) {
  boolean isActive() {
    return true;
  }
}

/** Pattern 1: an optional nested record, reached with Prisms.some(). */
@GenerateLenses
record Address(String city) {}

@GenerateLenses
record User(Optional<Address> address) {}

/** Pattern 2: a payment sum type whose variants carry fields. */
@GeneratePrisms
sealed interface Payment permits CreditCard, BankTransfer {}

@GenerateLenses
record CreditCard(String number) implements Payment {}

record BankTransfer(String iban) implements Payment {}

/** The leaves of the Fold.plus example. */
record Item(String sku) {}

record Employee(String email, String name) {}

record Person(String firstName, String lastName) {}

record Team(Employee lead, List<Employee> members) {}

class Fixture<A, B, C, D> {

  /** The universal fallback is written against type variables: the fixture lends them to it. */
  final Lens<A, B> optic1 = stub();

  final Lens<B, C> optic2 = stub();
  final Lens<C, D> optic3 = stub();

  /** The folds of the parallel-composition example. */
  final Fold<Customer, Order> ordersFold = stub();

  final Fold<Order, Item> itemsFold = stub();
  final Fold<Person, String> firstNameFold = Fold.of(person -> List.of(person.firstName()));
  final Fold<Person, String> lastNameFold = Fold.of(person -> List.of(person.lastName()));
  final Fold<Team, Employee> membersFold = Fold.of(Team::members);

  final Lens<Team, Employee> leadLens =
      Lens.of(Team::lead, (team, lead) -> new Team(lead, team.members()));
  final Lens<Employee, String> emailLens =
      Lens.of(Employee::email, (employee, email) -> new Employee(email, employee.name()));

  /**
   * The reader's own optics, which the page names without ever building. Only their types are
   * load-bearing for a compile gate, so they stand in unimplemented rather than pretending to a
   * behaviour the page never claims.
   */
  static <T> T stub() {
    throw new UnsupportedOperationException("the reader supplies this");
  }
}
