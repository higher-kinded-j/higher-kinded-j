// Fixture for hkj-book/src/effect/patterns.md
//
// The patterns page runs through validation, service composition, resource handling, resilience
// and interop, each on a sketch of an application. The sketches share one domain, which lives
// here.
//
// Left ungated on that page, and so absent from here: the Mockito and jqwik test examples (neither
// framework is on this module's test classpath) and the JDBC, file-handle and HTTP-client
// illustrations, which are about third-party resources rather than about HKJ.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.trymonad.Try;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

record User(String name, String email, int age) {}

record Address(String street, String city, String postcode) {}

record Preference(String key) {}

record UserInput(String name, String email, int age) {}

record RegistrationInput(
    UserInput user, String street, String city, String postcode, List<String> preferences) {}

record Item(String sku) {}

record Order(String id) {}

record Reservation(String id, Total total) {}

record Total(long pence) {}

record Payment(String reference) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

record Data(String value) {

  static Data empty() {
    return new Data("");
  }
}

record Report(int rowCount) {}

record ReportRequest(String id) {}

record UserData(String value) {}

record ProductData(String value) {}

record OrderData(String value) {}

record CombinedData(UserData users, ProductData products, OrderData orders) {}

record DetailedError(Error cause, String operation, Map<String, Object> context, Instant at) {}

record Error(String message) {

  String code() {
    return "E1";
  }
}

record DomainError(String code, String message, Error cause) {}

record Response(int status) {}

record ProcessResult(int lines) {}

record Result(String value) {}

record Availability(boolean inStock) {}

record PaymentError(String reason) {}

record OrderRequest(String userId, List<Item> items) {}

final class HttpException extends RuntimeException {

  private final int statusCode;

  HttpException(int statusCode) {
    super("HTTP " + statusCode);
    this.statusCode = statusCode;
  }

  int statusCode() {
    return statusCode;
  }
}

/** Stands in for whatever logger the reader has; the page only ever calls these. */
final class Logger {

  void debug(String format, Object... arguments) {}

  void info(String format, Object... arguments) {}

  void warn(String format, Object... arguments) {}

  void error(String format, Object... arguments) {}
}

final class JdbcTemplate {

  Optional<User> queryForOptional(String sql, String argument) {
    return Optional.of(new User("Ada", "ada@example.com", 36));
  }
}

final class InventoryService {

  EitherPath<String, Reservation> reserve(List<Item> items) {
    return Path.right(new Reservation("r-1", new Total(0)));
  }

  EitherPath<OrderError, Availability> check(List<Item> items) {
    return Path.right(new Availability(true));
  }
}

final class PaymentService {

  EitherPath<PaymentError, Payment> charge(User user, Total total) {
    return Path.right(new Payment("p-1"));
  }
}

/** The service the testing recipes place an order through. */
final class OrderPlacer {

  EitherPath<OrderError, Order> placeOrder(OrderRequest request) {
    return Path.right(new Order("o-1"));
  }
}

final class OrderRepository {

  Order save(Order order) {
    return order;
  }
}

final class UserService {

  EitherPath<UserError, User> getById(String id) {
    return Path.right(new User("Ada", "ada@example.com", 36));
  }
}

final class UserFacade {

  EitherPath<Error, User> getUser(String id) {
    return Path.right(new User("Ada", "ada@example.com", 36));
  }
}

final class PipelineService {

  IOPath<Report> generateReport(ReportRequest request) {
    return Path.io(() -> new Report(0));
  }
}

final class AuditLog {

  void record(User user) {}
}

final class HttpClient {

  Response get(String url) {
    return new Response(200);
  }
}

final class DataSource {

  Connection getConnection() {
    return null;
  }
}

sealed interface UserError {

  record NotFound(String id) implements UserError {}

  default String message() {
    return toString();
  }
}

sealed interface OrderError {

  record UserNotFound(String id) implements OrderError {}

  record InventoryFailed(String detail) implements OrderError {}

  record PaymentFailed(String detail) implements OrderError {}

  static OrderError fromUserError(UserError error) {
    return new UserNotFound(error.message());
  }

  static OrderError fromInventoryError(String error) {
    return new InventoryFailed(error);
  }

  static OrderError fromPaymentError(PaymentError error) {
    return new PaymentFailed(error.reason());
  }
}

record ConfigError(String message) {

  String getMessage() {
    return message;
  }
}

record ServiceError(String message, Throwable cause) {}

final class UserNotFoundException extends RuntimeException {

  UserNotFoundException(String message) {
    super(message);
  }
}

/** The DAO the service section wraps; its own fence stays an illustration of your existing one. */
final class UserRepository {

  Maybe<User> findById(String id) {
    return Maybe.just(new User("Ada", "ada@example.com", 36));
  }

  Maybe<User> findByEmail(String email) {
    return Maybe.just(new User("Ada", "ada@example.com", 36));
  }
}

/** The pre-Path services the interop section wraps. */
final class LegacyService {

  Data fetchData(String id) {
    return new Data("legacy");
  }
}

final class ModernService {

  Optional<User> findUser(String id) {
    return Optional.of(new User("Ada", "ada@example.com", 36));
  }
}

final class PathBasedService {

  MaybePath<User> findUser(String id) {
    return Path.just(new User("Ada", "ada@example.com", 36));
  }

  EitherPath<UserError, User> getUser(String id) {
    return Path.right(new User("Ada", "ada@example.com", 36));
  }
}

class Fixture {

  static final String id = "u-1";

  static final String requestId = "r-1";

  static final UserInput input = new UserInput("Ada", "ada@example.com", 36);

  static final Data fallbackValue = Data.empty();

  static final Semigroup<List<String>> errors = Semigroups.list();

  static final Logger log = new Logger();

  static final Error error = new Error("boom");

  static final String url = "https://example.test/api";

  static final User testUser = new User("Ada", "ada@example.com", 36);

  static final Availability availability = new Availability(true);

  static final OrderRequest request = new OrderRequest("u-1", List.of());

  static final ReportRequest reportRequest = new ReportRequest("r-1");

  static final AuditLog auditLog = new AuditLog();

  static final HttpClient httpClient = new HttpClient();

  static final DataSource dataSource = new DataSource();

  static final JdbcTemplate jdbcTemplate = new JdbcTemplate();

  static final EitherPath<Error, User> path = Path.right(testUser);

  static final IOPath<Response> resilient = Path.io(() -> new Response(200));

  static final UserRepository repository = new UserRepository();

  static final PathBasedService service = new PathBasedService();

  static final OrderRepository orderRepository = new OrderRepository();

  static final InventoryService inventory = new InventoryService();

  static final PaymentService payments = new PaymentService();

  static final OrderPlacer orderService = new OrderPlacer();

  static final UserFacade userService = new UserFacade();

  static final PipelineService pipeline = new PipelineService();

  // ---- validation -------------------------------------------------------------------------

  static EitherPath<String, String> validateName(String name) {
    return Path.right(name);
  }

  EitherPath<String, String> validateEmail(String email) {
    return Path.right(email);
  }

  EitherPath<String, User> validateUser(UserInput input) {
    return Path.right(new User(input.name(), input.email(), input.age()));
  }

  static Maybe<User> findUser(String id) {
    return Maybe.just(new User("Ada", "ada@example.com", 36));
  }

  static EitherPath<OrderError, Order> validateAndProcess(OrderRequest request) {
    return Path.right(new Order("o-1"));
  }

  static BufferedReader openReader(File file) {
    return new BufferedReader(new StringReader(""));
  }

  static ProcessResult processContent(BufferedReader reader) {
    return new ProcessResult(0);
  }

  static Data transform(Data data) {
    return data;
  }

  static Data aggregate(Data data) {
    return data;
  }

  static Report format(Data data) {
    return new Report(0);
  }

  static Data fetchData(ReportRequest request) {
    return new Data("data");
  }

  static EitherPath<String, Integer> validateAge(int age) {
    return Path.right(age);
  }

  static EitherPath<String, String> validateStreet(String street) {
    return Path.right(street);
  }

  static EitherPath<String, String> validateCity(String city) {
    return Path.right(city);
  }

  static EitherPath<String, String> validatePostcode(String postcode) {
    return Path.right(postcode);
  }

  static EitherPath<String, List<Preference>> validatePreferences(List<String> prefs) {
    return Path.right(List.of());
  }

  static ValidationPath<List<String>, String> validateNameV(String name) {
    return Path.valid(name, errors);
  }

  static ValidationPath<List<String>, String> validateEmailV(String email) {
    return Path.valid(email, errors);
  }

  static ValidationPath<List<String>, Integer> validateAgeV(int age) {
    return Path.valid(age, errors);
  }

  static String showAllErrors(List<String> errors) {
    return String.join(", ", errors);
  }

  static String proceed(User user) {
    return user.name();
  }

  // ---- resource and pipeline sketches ------------------------------------------------------

  static Either<ConfigError, Config> loadFromFile() {
    return Either.right(new Config("file"));
  }

  static Either<ConfigError, Config> loadFromEnvironment() {
    return Either.right(new Config("env"));
  }

  static UserData fetchUsers() {
    return new UserData("users");
  }

  static ProductData fetchProducts() {
    return new ProductData("products");
  }

  static OrderData fetchOrders() {
    return new OrderData("orders");
  }

  static IOPath<Data> fetchFromPrimary() {
    return Path.io(() -> new Data("primary"));
  }

  static IOPath<Data> fetchFromBackup() {
    return Path.io(() -> new Data("backup"));
  }

  static Order createOrder(User user, List<Item> items, Payment payment) {
    return new Order("o-1");
  }


}
