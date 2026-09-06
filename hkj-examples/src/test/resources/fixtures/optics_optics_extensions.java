// Fixture for hkj-book/src/optics/optics_extensions.md
//
// The page reads and writes a user profile through the lens extensions, then an order's items
// through the traversal ones. Both models are declared here, with the generators the snippets'
// companions come from.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.optics.extensions.LensExtensions.getEither;
import static org.higherkindedj.optics.extensions.LensExtensions.getMaybe;
import static org.higherkindedj.optics.extensions.LensExtensions.getValidated;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyEither;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyMaybe;
import static org.higherkindedj.optics.extensions.LensExtensions.modifyTry;
import static org.higherkindedj.optics.extensions.LensExtensions.setIfValid;
import static org.higherkindedj.optics.extensions.TraversalExtensions.collectErrors;
import static org.higherkindedj.optics.extensions.TraversalExtensions.countValid;
import static org.higherkindedj.optics.extensions.TraversalExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllEither;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllMaybe;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyAllValidated;
import static org.higherkindedj.optics.extensions.TraversalExtensions.modifyWherePossible;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record UserProfile(String id, String name, String email, int age, String bio) {}

@GenerateLenses
record OrderItem(String sku, BigDecimal price, int quantity, String status) {}

@GenerateLenses
record Order(String orderId, List<OrderItem> items, String customerEmail) {}

// The reader's own logger, whatever it is. Named here so the page's snippets can say what they
// would log without the gate carrying a logging framework.
class Log {

  void info(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void error(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static final Log logger = new Log();

  static final UserProfile profile =
      new UserProfile("u1", "Alice", "alice@example.com", 30, "Software Engineer");

  static final UserProfile original = profile;

  static final Lens<UserProfile, String> bioLens = UserProfileLenses.bio();

  static final List<OrderItem> items =
      List.of(
          new OrderItem("SKU-1", new BigDecimal("999.99"), 1, "pending"),
          new OrderItem("SKU-2", new BigDecimal("29.99"), 2, "shipped"));

  static final Order order = new Order("ORD-1", items, "alice@example.com");

  static final Traversal<List<OrderItem>, BigDecimal> allPrices =
      Traversals.<OrderItem>forList().andThen(OrderItemLenses.price().asTraversal());

  static String capitalize(String value) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static String updateEmailInDatabase(String email) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
