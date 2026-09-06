// Fixture for hkj-book/src/effect/migration_cookbook.md
//
// Each recipe is a before/after pair over the same small domain, so both halves compile against
// the same services. `findUser` and `findAddress` return `Optional` because the recipe they belong
// to is explicitly about migrating Optional chains; `findOrder` returns a nullable because its
// recipe is about null checks.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

record User(String id, List<String> cartItems) {}

@GenerateLenses
record Address(String street, String city, String postcode, String country) {}

@GenerateLenses
record Customer(String id, String name, Address shippingAddress) {}

@GenerateLenses
record Order(String id, Customer customer, List<String> items, String status) {}

record Registration(String name, String email, Integer age) {}

record Confirmation(String reference) {}

record Stock(long total) {}

record Receipt(String id) {}

record Dashboard(Metrics metrics, Alerts alerts) {}

record Metrics(int count) {}

record Alerts(int count) {}

record OrderConfirmation(User user, Stock stock, Receipt receipt) {

  static OrderConfirmation failed(String reason) {
    return new OrderConfirmation(null, null, null);
  }
}

sealed interface AppError {

  record UserNotFound(String id) implements AppError {}

  record NoAddress(String userId) implements AppError {}
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final String DEFAULT_VALUE = "default";

  static final String name = "Ada";

  static final String email = "ada@example.com";

  static final int age = 36;

  static final Semigroup<List<String>> errorSemigroup = Semigroups.list();

  static final Order order = new Order("o-1", new Customer("c-1", "Ada", address()), List.of(), "NEW");

  static final ValidationPath<List<String>, Registration> result =
      Path.valid(new Registration("Ada", "ada@example.com", 36), Semigroups.list());

  static Address address() {
    return new Address("1 Old Street", "London", "N1 1AA", "UK");
  }

  static ValidationPath<List<String>, String> validatePostcode(String postcode) {
    return Path.valid(postcode, Semigroups.list());
  }

  static EitherPath<AppError, String> validatePostcodeE(String postcode) {
    return Path.right(postcode);
  }

  static EitherPath<List<String>, Confirmation> processRegistration(Registration registration) {
    return Path.right(new Confirmation("r-1"));
  }

  static final java.nio.file.Path path = java.nio.file.Path.of("data.txt");

  static final Logger log = new Logger();

  static final UserService userService = new UserService();

  static final InventoryService inventoryService = new InventoryService();

  static final PaymentService paymentService = new PaymentService();

  static String readFile(java.nio.file.Path path) throws IOException {
    return "contents";
  }

  String transform(String raw) {
    return raw.toUpperCase();
  }

  static Optional<User> findUser(String id) {
    return Optional.of(new User(id, List.of()));
  }

  static Optional<Address> findAddress(User user) {
    return Optional.of(address());
  }

  static @Nullable Order findOrder(String id) {
    return new Order("o-1", new Customer("c-1", "Ada", address()), List.of(), "NEW");
  }

  static CompletableFuture<Metrics> fetchMetrics() {
    return CompletableFuture.completedFuture(new Metrics(1));
  }

  static CompletableFuture<Alerts> fetchAlerts() {
    return CompletableFuture.completedFuture(new Alerts(0));
  }

  static final class UserService {

    CompletableFuture<User> findUser(String id) {
      return CompletableFuture.completedFuture(new User(id, List.of()));
    }
  }

  static final class InventoryService {

    CompletableFuture<Stock> checkStock(List<String> items) {
      return CompletableFuture.completedFuture(new Stock(0));
    }
  }

  static final class PaymentService {

    CompletableFuture<Receipt> charge(User user, long total) {
      return CompletableFuture.completedFuture(new Receipt("r-1"));
    }
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    void warn(String message, Throwable cause) {}
  }
}
