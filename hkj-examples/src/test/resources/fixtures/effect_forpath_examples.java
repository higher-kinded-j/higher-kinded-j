// Fixture for hkj-book/src/effect/forpath_examples.md
//
// One domain runs through every example: a user with an address, the profile that goes with
// them, and the order they place. The first fence declares User, Address and Order itself, so
// those three are declared here in exactly the same shape - the extractor drops this copy when a
// snippet brings its own, and every helper below has to keep compiling against either.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.trymonad.Try;

record Address(String city) {}

record User(String id, String name, Address address) {}

record Order(String orderId, User user) {}

record Profile(String city) {}

record Validated(String id) {}

record Reserved(String id) {}

record Payment(String transactionId) {}

record Confirmation(String sentAt) {}

record OrderSummary(String orderId, String transactionId, String sentAt) {}

final class UserService {

  User fetch(String id) {
    return new User(id, "Alice", new Address("London"));
  }
}

final class ProfileService {

  Profile fetch(String id) {
    return new Profile("London");
  }
}

class Fixture {

  static final String userId = "user-1";

  static final String profileId = "profile-1";

  static final UserService userService = new UserService();

  static final ProfileService profileService = new ProfileService();

  static final Object order = new Object();

  static Validated validateOrder(Object order) {
    return new Validated("order-123");
  }

  static Reserved reserveInventory(Validated validated) {
    return new Reserved(validated.id());
  }

  static Payment processPayment(Reserved reserved) {
    return new Payment("txn-1");
  }

  static Confirmation sendConfirmation(Payment payment) {
    return new Confirmation("2026-09-06T10:00:00Z");
  }
}
