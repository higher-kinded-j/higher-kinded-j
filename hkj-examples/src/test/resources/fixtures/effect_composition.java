// Fixture for hkj-book/src/effect/composition.md
//
// The composition page builds one order/invoice pipeline up from sequential chaining, independent
// combination, observation and error recovery. The domain behind those examples lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.Nullable;

record User(String name) {

  String getId() {
    return name;
  }

  static User guest() {
    return new User("guest");
  }
}

record Address(String line) {}

record CustomerInfo(String name, String email, Address address) {}

record Cart(List<String> items) {}

record Total(long pence) {}

record Invoice(String id) {}

record Order(String id) {}

record OrderInput(String name, String email, Address address, List<String> items) {}

record Inventory(List<String> available) {}

record Pricing(long pence) {}

record Preferences(String theme) {}

record Profile(User user, Preferences preferences) {}

record Data(String value) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

record Error(String message) {}

record ServiceError(String message, Error cause) {}

record DetailedError(Error cause, String where, Map<String, Object> context) {}

record Result(String value) {}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final String input = "raw";

  static final OrderInput orderInput =
      new OrderInput("Ada", "ada@example.com", new Address("1 Old Street"), List.of("sku-1"));

  static final EitherPath<Error, Data> path = Path.either(Either.right(new Data("d")));

  static final Logger log = new Logger();

  static final ExternalApi externalApi = new ExternalApi();

  static final Service userService = new Service();

  static final Service prefService = new Service();

  // ---- sequential pipeline ------------------------------------------------------------------

  static Either<Error, User> findUser(String userId) {
    return Either.right(new User("Ada"));
  }

  static Either<Error, Cart> getCart(User user) {
    return Either.right(new Cart(List.of()));
  }

  static Either<Error, Total> calculateTotal(Cart cart) {
    return Either.right(new Total(0));
  }

  static Either<Error, Invoice> createInvoice(Total total) {
    return Either.right(new Invoice("i-1"));
  }

  static Either<Error, Invoice> checkout(Cart cart) {
    return Either.right(new Invoice("i-1"));
  }

  // ---- independent validation ----------------------------------------------------------------

  static EitherPath<Error, String> validateName(String name) {
    return Path.right(name);
  }

  static EitherPath<Error, String> validateEmail(String email) {
    return Path.right(email);
  }

  static EitherPath<Error, Address> validateAddress(Address address) {
    return Path.right(address);
  }

  static Either<Error, Inventory> checkInventory(List<String> items) {
    return Either.right(new Inventory(items));
  }

  static Either<Error, Pricing> calculatePricing(Inventory inventory) {
    return Either.right(new Pricing(0));
  }

  static Either<Error, Order> createOrder(CustomerInfo customer, Pricing pricing) {
    return Either.right(new Order("o-1"));
  }

  // ---- observation and recovery ---------------------------------------------------------------

  static Either<Error, String> validateInput(String input) {
    return Either.right(input);
  }

  static Either<Error, User> createUser(String valid) {
    return Either.right(new User("Ada"));
  }

  static Either<Error, String> sendWelcomeEmail(User user) {
    return Either.right(user.name());
  }

  static @Nullable Config loadConfig() {
    return new Config("app");
  }

  /** The lookup that answers with absence, as distinct from the one that answers with an error. */
  static Maybe<User> lookupUser(String id) {
    return Maybe.just(new User("Ada"));
  }

  static Either<Error, Config> loadFromFile() {
    return Either.right(new Config("file"));
  }

  static Either<Error, Config> loadFromEnvironment() {
    return Either.right(new Config("env"));
  }

  static Data fetchData() {
    return new Data("data");
  }

  static final class ExternalApi {

    Either<Error, Data> fetch() {
      return Either.right(new Data("api"));
    }
  }

  static final class Service {

    User get(String id) {
      return new User("Ada");
    }
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    void debug(String format, Object... arguments) {}

    void info(String format, Object... arguments) {}

    void warn(String format, Object... arguments) {}
  }
}
