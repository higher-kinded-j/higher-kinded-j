// Fixture for hkj-book/src/optics/fluent_api_field_guide.md
//
// The field guide's idioms operate on the same person/team/order domain as the
// Fluent API page; the records live here and the annotation processor
// generates the *Lenses and *Traversals companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Person(String name, int age, String status) {}

@GenerateLenses
record Player(String name, int score, String status) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
record OrderItem(String productId, int quantity, BigDecimal price) {}

@GenerateLenses
@GenerateTraversals
record Order(String orderId, List<OrderItem> items) {}

class Fixture {
  static final Person alice = new Person("Alice", 25, "ACTIVE");

  static final Team team =
      new Team(
          "Wildcats",
          List.of(new Player("Alice", 100, "MEMBER"), new Player("Bob", 40, "MEMBER")));

  static final Order order =
      new Order(
          "ORD-1",
          List.of(
              new OrderItem("WIDGET", 12, new BigDecimal("9.99")),
              new OrderItem("GIZMO", 3, new BigDecimal("24.50"))));

  static final List<Order> orders = List.of(order);

  static final Traversal<Order, BigDecimal> itemPrices =
      OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal());

  static Optional<Person> findPerson(String id) {
    return "alice".equals(id) ? Optional.of(alice) : Optional.empty();
  }
}
