// Fixture for hkj-book/src/effect/effect_path_overview.md
//
// The chapter's entry point walks an order-processing pipeline and then catalogues every Path
// type's constructors, extractors and combinators. The order domain, the services behind it and
// the ready-made paths the catalogue sections operate on live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

@GenerateLenses
@GenerateFocus
record Address(Street street, String postcode) {}

@GenerateLenses
@GenerateFocus
record Street(String name, int number) {}

@GenerateLenses
@GenerateFocus
record User(String name, Optional<String> email, Address address) {

  String getId() {
    return name;
  }

  static User anonymous() {
    return new User("Anonymous", Optional.empty(), new Address(new Street("None", 0), ""));
  }
}

/** The page's untyped error, carried by the `EitherPath<Error, …>` catalogue examples. */
record Error(String message) {}

sealed interface OrderError {

  record UserNotFound(String userId) implements OrderError {}

  record ValidationFailed(String detail) implements OrderError {}

  record InventoryError(String detail) implements OrderError {}

  record PaymentFailed(Throwable cause) implements OrderError {}
}

record OrderRequest(List<String> items) {

  List<String> getItems() {
    return items;
  }
}

record Order(String id) {}

record Invoice(String id) {}

record Cart(List<String> items) {}

record Total(long pence) {}

record InventoryCheck(Total total) {

  boolean isAvailable() {
    return true;
  }

  Total getTotal() {
    return total;
  }
}

record PaymentResult(String reference) {

  boolean isFailed() {
    return false;
  }

  String getFailureReason() {
    return "";
  }
}

record ValidationResult(List<String> errors) {

  boolean isValid() {
    return errors.isEmpty();
  }

  List<String> getErrors() {
    return errors;
  }
}

record OrderResult(String detail) {

  static OrderResult error(String detail) {
    return new OrderResult(detail);
  }

  static OrderResult success(Order order) {
    return new OrderResult(order.id());
  }
}

final class ValidationException extends RuntimeException {

  ValidationException(String message) {
    super(message);
  }
}

final class PaymentException extends RuntimeException {

  PaymentException(String message) {
    super(message);
  }
}

// The collaborators as they were *before* the Path types: absence is null, failure is an
// exception. The functional half of the page uses the fixture's own services, which answer with
// Maybe and Either instead - that difference is the section's whole point.
final class UserLookup {

  @Nullable User findById(String id) {
    return User.anonymous();
  }
}

final class RequestValidator {

  ValidationResult validate(OrderRequest request) {
    return new ValidationResult(List.of());
  }
}

final class StockCheck {

  InventoryCheck check(List<String> items) {
    return new InventoryCheck(new Total(0));
  }
}

final class CardPayments {

  PaymentResult charge(User user, Total total) {
    return new PaymentResult("p-1");
  }
}

record Config(String name) {}

record ValidPostcode(String value) {

  String formatted() {
    return value.toUpperCase();
  }
}

class Fixture {

  static final String id = "u-1";

  static final String userId = "u-1";

  static final String input = "raw input";

  static final java.nio.file.Path configFile = java.nio.file.Path.of("application.conf");

  static final Error noPostcodeError = new Error("No postcode");

  static final MaybePath<String> maybePathOfString = Path.just("hello");

  static final MaybePath<User> maybePath = Path.maybe(User.anonymous());

  static final EitherPath<Error, User> eitherPath = Path.either(Either.right(User.anonymous()));

  static final EitherPath<Error, User> userPath = eitherPath;

  static final IOPath<String> ioPath = Path.io(() -> "contents");

  static final TryPath<Config> tryPath = Path.tryOf(() -> new Config("app"));

  static final Repository repository = new Repository();

  static final Repository userRepository = new Repository();

  static final Validator validator = new Validator();

  static final InventoryService inventoryService = new InventoryService();

  static final PaymentService paymentService = new PaymentService();

  static final Logger log = new Logger();

  static Either<Error, User> findUser(String userId) {
    return Either.right(User.anonymous());
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

  static EitherPath<Error, User> fetchUser(String userId) {
    return Path.either(Either.right(User.anonymous()));
  }

  static EitherPath<Error, ValidPostcode> validatePostcode(String code) {
    return Path.either(Either.right(new ValidPostcode(code)));
  }

  static Either<Error, String> validateInput(String input) {
    return Either.right(input);
  }

  static Either<Error, User> createUser(String valid) {
    return Either.right(User.anonymous());
  }

  static Config loadConfig() {
    return new Config("app");
  }

  static String expensiveDefault() {
    return "computed default";
  }

  static Order createOrder(User user, OrderRequest request, PaymentResult payment) {
    return new Order("o-1");
  }

  static final class Repository {

    Maybe<User> findById(String id) {
      return Maybe.just(User.anonymous());
    }
  }

  static final class Validator {

    Either<String, String> validate(OrderRequest request) {
      return Either.right("validated");
    }
  }

  static final class InventoryService {

    Either<String, InventoryCheck> check(List<String> items) {
      return Either.right(new InventoryCheck(new Total(0)));
    }
  }

  static final class PaymentService {

    PaymentResult charge(User user, Total total) {
      return new PaymentResult("p-1");
    }
  }

  /** Stands in for whatever logger the reader has; the page only ever calls these two. */
  static final class Logger {

    void debug(String format, Object argument) {}

    void info(String format, Object argument) {}
  }
}
