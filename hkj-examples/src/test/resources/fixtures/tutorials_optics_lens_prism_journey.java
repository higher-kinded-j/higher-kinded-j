// Fixture for hkj-book/src/tutorials/optics/lens_prism_journey.md
//
// The journey contrasts hand-written copies with lenses and extracts a variant with a prism; the
// records and the generated optics come from here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

@GenerateLenses
record Street(String name, String number) {}

@GenerateLenses
record Address(Street street, String city) {}

@GenerateLenses
record User(String name, String email, Address address) {}

@GeneratePrisms
sealed interface OrderStatus {

  record Pending(String reason) implements OrderStatus {}

  record Shipped(String trackingNumber) implements OrderStatus {}
}

class Fixture {

  static final User user = new User("Ada", "ada@example.test", new Address(new Street("Main", "1"), "London"));

  static final String newEmail = "ada@new.test";

  static final Lens<User, String> userToStreetName =
      UserLenses.address().andThen(AddressLenses.street()).andThen(StreetLenses.name());

  static final OrderStatus orderStatus = new OrderStatus.Shipped("TRK-1");

  static final Prism<OrderStatus, OrderStatus.Shipped> shippedPrism =
      OrderStatusPrisms.shipped();

  static final Lens<OrderStatus.Shipped, String> trackingLens =
      Lens.of(OrderStatus.Shipped::trackingNumber, (s, v) -> new OrderStatus.Shipped(v));
}
