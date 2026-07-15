// Fixture for .claude/skills/hkj-guide/reference/migration-recipes.md
//
// Each recipe is a before/after pair, so both halves must compile: the imperative "before" against
// plain Java, and the "after" against the REAL library. The domain both halves share - the lookups,
// the async services, the nested order records - is supplied here so the recipes stay about the
// migration.
//
// The order records carry @GenerateLenses, so `OrderLenses.customer()` in Recipe 6 is the genuinely
// generated optic, not a hand-written stand-in: the recipe is checked against the real processor.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** The reader's own domain: the nested order the lens recipe navigates. */
@GenerateLenses
record Address(String street, String city, String postcode, String country) {}

@GenerateLenses
record Customer(String id, String name, Address shippingAddress) {}

@GenerateLenses
record Order(String id, Customer customer, List<Item> items, Status status) {}

record Item(String sku) {}

enum Status {
  NEW,
  PAID
}

record User(String id, String name, List<Item> cartItems) {}

record Money(long pence) {}

record Stock(List<Item> available) {
  Money total() {
    return new Money(1_000);
  }
}

record Receipt(String id) {}

record OrderConfirmation(User user, Stock stock, Receipt receipt) {
  static OrderConfirmation failed(String reason) {
    return new OrderConfirmation(null, null, null);
  }
}

record Metrics(int hits) {}

record Alerts(int count) {}

record Dashboard(Metrics metrics, Alerts alerts) {}

record Registration(String name, String email, int age) {}

/** The reader's own error type. */
sealed interface AppError {
  record UserNotFound(String id) implements AppError {}

  record NoAddress(String userId) implements AppError {}

  record InvalidPostcode(String postcode) implements AppError {}
}

/** A logger, so the recipes' `log.warn(...)` calls are real calls. */
final class Log {
  void info(String message, Object... args) {}

  void warn(String message, Object... args) {}
}

final class UserAsyncService {
  CompletableFuture<User> findUser(String id) {
    return CompletableFuture.completedFuture(new User(id, "Ada", List.of()));
  }
}

final class InventoryAsyncService {
  CompletableFuture<Stock> checkStock(List<Item> items) {
    return CompletableFuture.completedFuture(new Stock(items));
  }
}

final class PaymentAsyncService {
  CompletableFuture<Receipt> charge(User user, Money amount) {
    return CompletableFuture.completedFuture(new Receipt("r-1"));
  }
}

class Fixture {

  static final Log log = new Log();
  static final String DEFAULT_VALUE = "default";

  static final String path = "/etc/config.txt";
  static final String id = "u-1";

  static final UserAsyncService userService = new UserAsyncService();
  static final InventoryAsyncService inventoryService = new InventoryAsyncService();
  static final PaymentAsyncService paymentService = new PaymentAsyncService();

  static final String userId = "u-1";
  static final String name = "Ada";
  static final String email = "ada@example.com";
  static final int age = 36;

  static final Order order =
      new Order(
          "o-1",
          new Customer("c-1", "Ada", new Address("1 High St", "Bath", "BA1 1AA", "UK")),
          List.of(new Item("sku-1")),
          Status.NEW);

  /** The accumulated result Recipe 5 hands on to the short-circuiting stage. */
  static final ValidationPath<List<String>, Registration> result =
      Path.valid(new Registration("Ada", "ada@example.com", 36), Semigroups.list());

  // ----- Recipe 1: try/catch -----

  static String readFile(String path) throws IOException {
    return "contents";
  }

  /** An instance method, because the recipe writes `this::transform`. */
  String transform(String raw) {
    return raw.strip();
  }

  // ----- Recipes 2 and 3: absence -----

  static Optional<User> findUser(String id) {
    return Optional.of(new User(id, "Ada", List.of()));
  }

  static Optional<Address> findAddress(User user) {
    return Optional.of(new Address("1 High St", "Bath", "BA1 1AA", "UK"));
  }

  static Order findOrder(String id) {
    return order;
  }

  // ----- Recipe 4: async -----

  static CompletableFuture<Metrics> fetchMetrics() {
    return CompletableFuture.completedFuture(new Metrics(1));
  }

  static CompletableFuture<Alerts> fetchAlerts() {
    return CompletableFuture.completedFuture(new Alerts(0));
  }

  // ----- Recipe 5: accumulation -----

  static void badRequest(List<String> errors) {}

  static EitherPath<List<String>, Receipt> processRegistration(Registration registration) {
    return Path.right(new Receipt("r-1"));
  }

  // ----- Recipe 6: lenses -----

  static EitherPath<AppError, String> validatePostcode(String postcode) {
    return Path.right(postcode);
  }
}
