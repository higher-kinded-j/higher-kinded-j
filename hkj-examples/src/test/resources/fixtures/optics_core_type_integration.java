// Fixture for hkj-book/src/optics/core_type_integration.md
//
// The page pairs the optics with the core effect types, working a user, an order and an API
// response through them. Those records are declared here; a snippet that shows one shadows this
// copy. The user carries the bean-style accessors the page's "before" snippet reads.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.optics.extensions.LensExtensions.getMaybe;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyEither;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllValidated;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.EitherTraversals;
import org.higherkindedj.optics.util.MaybeTraversals;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Profile(String email) {

  Profile withEmail(String newEmail) {
    return new Profile(newEmail);
  }
}

@GenerateLenses
record User(String email, Profile profile) {

  Profile getProfile() {
    return profile;
  }

  User withProfile(Profile newProfile) {
    return new User(email, newProfile);
  }
}

@GenerateLenses
record Customer(String customerId, String name, String email) {}

record OrderItem(String sku, double price) {}

@GenerateLenses
record Order(String orderId, Customer customer, List<OrderItem> items) {}

@GenerateLenses
record ApiResponse(int statusCode, Maybe<Order> data, List<String> warnings) {}

class ValidationException extends RuntimeException {

  ValidationException(String message) {
    super(message);
  }
}

// The reader's own logger, whatever it is. Named here so the page's snippets can say what they
// would log without the gate carrying a logging framework.
class Log {

  void error(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Log log = new Log();

  static final Log logger = log;

  static final User user = sample();

  static final Maybe<User> maybeUser = sample();

  static final Try<Order> tryOrder = sample();

  static final ApiResponse response = sample();

  static final List<Order> orders = List.of();

  static final List<String> userIds = List.of("u1");

  static final Traversal<List<Order>, Double> allPrices = sample();

  static String validateEmailFormat(String email) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Validated<String, Double> validatePrice(Double price) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static List<Try<User>> loadUsersFromDatabase(List<String> ids) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
