// Fixture for .claude/skills/hkj-optics/reference/composition-rules.md
//
// The page is a composition matrix, so some of its code is deliberately abstract: `optic1.andThen(
// optic2)` over type variables A, B, C, D. A GENERIC fixture lends those type variables to the
// snippet (the wrapper extends Fixture<A, B, C, D>), which is how the page can show a *shape*
// without inventing a domain for it. Those optics are instance fields for that reason: a static
// member cannot use its class's type parameters.
//
// The concrete patterns at the foot of the page get a real domain, annotated for real, so
// `PaymentPrisms.card()` and `OrderTraversals.lines()` are the genuinely generated companions, and
// the composition results the page tabulates (Lens+Prism = Affine, Affine+Traversal = Traversal)
// are checked by javac rather than asserted by a table. The domain is the book's order service,
// declared with the book's components.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

/** The book's order service: an order, its customer and lines, and its status. */
@GenerateLenses
record EmailAddress(String value) {}

record Customer(String name, EmailAddress email) {}

@GenerateLenses
record LineItem(String sku, Integer quantity, BigDecimal price) {}

enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

/** The structure the page composes through. Pattern 3 filters on its status. */
@GenerateLenses
@GenerateTraversals
record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {}

/** Pattern 1: an optional nested record, reached with Prisms.some(). */
@GenerateLenses
record CustomerProfile(String name, Optional<String> nickname, Optional<EmailAddress> altEmail) {}

/** Pattern 2: a payment sum type whose variants carry fields. */
@GeneratePrisms
sealed interface Payment permits Card, Bank {}

@GenerateLenses
record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

/** The consignment the stored constant narrows: a sum type whose Returned variant has a field. */
record Address(String street, String city, String postcode) {}

@GenerateLenses
record Consignment(UUID orderId, Address to, ConsignmentState state) {}

@GeneratePrisms
sealed interface ConsignmentState
    permits ConsignmentState.Pending, ConsignmentState.Dispatched, ConsignmentState.Returned {
  record Pending() implements ConsignmentState {}

  record Dispatched(Instant at) implements ConsignmentState {}

  @GenerateLenses
  record Returned(String reason) implements ConsignmentState {}
}

/** The leaves of the Fold.plus example. */
record Person(String firstName, String lastName, int age, Address address) {}

record Engineer(String name, String email) {}

record Guild(String name, Engineer lead, List<Engineer> members) {}

class Fixture<A, B, C, D> {

  /** The universal fallback is written against type variables: the fixture lends them to it. */
  final Lens<A, B> optic1 = stub();

  final Lens<B, C> optic2 = stub();
  final Lens<C, D> optic3 = stub();

  /** The folds of the parallel-composition example. */
  final Fold<List<Order>, Order> ordersFold = Fold.of(orders -> orders);

  final Fold<Order, LineItem> linesFold = Fold.of(Order::lines);
  final Fold<Person, String> firstNameFold = Fold.of(person -> List.of(person.firstName()));
  final Fold<Person, String> lastNameFold = Fold.of(person -> List.of(person.lastName()));
  final Fold<Guild, Engineer> membersFold = Fold.of(Guild::members);

  final Lens<Guild, Engineer> leadLens =
      Lens.of(Guild::lead, (guild, lead) -> new Guild(guild.name(), lead, guild.members()));
  final Lens<Engineer, String> emailLens =
      Lens.of(Engineer::email, (engineer, email) -> new Engineer(engineer.name(), email));

  /**
   * The reader's own optics, which the page names without ever building. Only their types are
   * load-bearing for a compile gate, so they stand in unimplemented rather than pretending to a
   * behaviour the page never claims.
   */
  static <T> T stub() {
    throw new UnsupportedOperationException("the reader supplies this");
  }
}
