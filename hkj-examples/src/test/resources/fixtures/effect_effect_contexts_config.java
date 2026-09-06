// Fixture for hkj-book/src/effect/effect_contexts_config.md
//
// The ConfigContext page threads several different configuration records through one reporting
// pipeline. Two of its snippets declare their own `AppConfig` with a different shape from the
// others, and those shadow the one here.
//
// Every ConfigContext helper below is built with `ConfigContext.pure`, deliberately: a helper that
// read a field off `AppConfig` would stop compiling underneath whichever snippet shadowed it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.io.IOKind;

record UserId(String value) {}

record User(UserId id, String name) {}

record Order(String id) {}

record Report(String body) {}

record Invoice(String id) {}

record Data(String value) {}

record Result(String value) {}

record Response(String body) {}

/** The configuration records the page projects from. */
record AppConfig(String apiUrl) {

  String name() {
    return "app";
  }
}

record UserConfig(String endpoint) {}

record OrderConfig(String endpoint) {}

record DatabaseConfig(String url) {}

record OrderRequest(String id, List<String> items) {}

record ValidatedRequest(List<String> items) {}

record CheckedRequest(List<String> items) {}

record PaidRequest(String reference) {}

record ReportConfig(String userServiceUrl, String orderServiceUrl, Duration timeout, String format) {}

record DbConfig(String url, String username, String password, int poolSize) {}

record ApiError(String message) {

  static ApiError fromException(Throwable cause) {
    return new ApiError(cause.getMessage());
  }
}

record ApiConfig(String endpoint, Duration timeout) {

  String userEndpoint() {
    return endpoint + "/users";
  }


  ApiConfig withTimeout(Duration timeout) {
    return new ApiConfig(endpoint, timeout);
  }
}

record ServiceConfig(String baseUrl, String validationRules, String inventoryServiceUrl) {

  String endpoint() {
    return baseUrl;
  }

  ServiceConfig withEndpoint(String endpoint) {
    return new ServiceConfig(endpoint, validationRules, inventoryServiceUrl);
  }
}

record AnyConfig(String value) {}

record Config(String name) {}

class Fixture {

  static final UserId userId = new UserId("u-1");

  static final UserId id = new UserId("u-1");

  static final UserRepositoryStub repository = new UserRepositoryStub();

  static final ReportConfig config = new ReportConfig("http://u", "http://o", Duration.ZERO, "PDF");

  static final HttpClient httpClient = new HttpClient();

  static AppConfig appConfig;

  static final ServiceConfig serviceConfig = new ServiceConfig("http://s", "strict", "http://i");

  static final OrderRequest request = new OrderRequest("o-1", List.of("sku-1"));

  static final UserService userService = new UserService();

  static final OrderService orderService = new OrderService();

  static final Validator validator = new Validator();

  static final InventoryClient inventoryClient = new InventoryClient();

  static final String prodPassword = "secret";

  static Report buildReport(String format, User user, List<Order> orders) {
    return new Report(format);
  }

  static AppConfig loadConfig() {
    return appConfig;
  }

  static Connection openConnection(String url) {
    return null;
  }

  static ConfigContext<IOKind.Witness, AppConfig, Report> generateReport() {
    return ConfigContext.pure(new Report("report"));
  }

  static ConfigContext<IOKind.Witness, ServiceConfig, PaidRequest> processPayment(
      CheckedRequest request) {
    return ConfigContext.pure(new PaidRequest("p-1"));
  }

  static ConfigContext<IOKind.Witness, ServiceConfig, Order> saveOrder(PaidRequest request) {
    return ConfigContext.pure(new Order("o-1"));
  }

  static User queryUser(Connection connection, UserId id) {
    return new User(id, "Ada");
  }

  static final UserClient userClient = new UserClient();

  static final OrderClient orderClient = new OrderClient();

  // The ReportConfig pipeline the page opens with.

  static ConfigContext<IOKind.Witness, ReportConfig, User> fetchUser(UserId id) {
    return ConfigContext.pure(new User(id, "Ada"));
  }

  static ConfigContext<IOKind.Witness, ReportConfig, List<Order>> fetchOrders(User user) {
    return ConfigContext.pure(List.of());
  }

  static ConfigContext<IOKind.Witness, ReportConfig, Report> buildReport(List<Order> orders) {
    return ConfigContext.pure(new Report("report"));
  }

  static ConfigContext<IOKind.Witness, AppConfig, Invoice> createInvoice(List<Order> orders) {
    return ConfigContext.pure(new Invoice("i-1"));
  }

  static Invoice createInvoice(User user, Order order) {
    return new Invoice(order.id());
  }

  // The AppConfig pipeline the flatMap example uses.

  static ConfigContext<IOKind.Witness, AppConfig, User> getUser() {
    return ConfigContext.pure(new User(new UserId("u-1"), "Ada"));
  }

  static ConfigContext<IOKind.Witness, AppConfig, List<Order>> getOrders(User user) {
    return ConfigContext.pure(List.of());
  }

  static ConfigContext<IOKind.Witness, AppConfig, Report> generateReport(List<Order> orders) {
    return ConfigContext.pure(new Report("report"));
  }

  // The Config workflow the `then` example sequences.

  static ConfigContext<IOKind.Witness, Config, Unit> logStart() {
    return ConfigContext.pure(Unit.INSTANCE);
  }

  static ConfigContext<IOKind.Witness, Config, Unit> doWork() {
    return ConfigContext.pure(Unit.INSTANCE);
  }

  static ConfigContext<IOKind.Witness, Config, Unit> logComplete() {
    return ConfigContext.pure(Unit.INSTANCE);
  }

  // The `local` examples.

  static ConfigContext<IOKind.Witness, ApiConfig, Data> fetchData() {
    return ConfigContext.pure(new Data("data"));
  }

  static ConfigContext<IOKind.Witness, AppConfig, Result> processData() {
    return ConfigContext.pure(new Result("result"));
  }

  /** The repository the testability section runs against two configurations. */
  static final class UserRepositoryStub {

    ConfigContext<IOKind.Witness, DbConfig, User> findById(UserId id) {
      return ConfigContext.pure(new User(id, "Ada"));
    }
  }

  static final class HttpClient {

    Response get(String endpoint) {
      return new Response("{}");
    }

    Response call(String endpoint) {
      return new Response("{}");
    }

    User getUser(String url) {
      return new User(new UserId("u-1"), "Ada");
    }
  }

  static final class UserService {

    User fetch(String url, Duration timeout, UserId id) {
      return new User(id, "Ada");
    }
  }

  static final class OrderService {

    List<Order> fetch(String url, Duration timeout, User user) {
      return List.of();
    }
  }

  static final class Validator {

    ValidatedRequest validate(OrderRequest request, String rules) {
      return new ValidatedRequest(request.items());
    }
  }

  static final class InventoryClient {

    CheckedRequest check(String url, List<String> items) {
      return new CheckedRequest(items);
    }
  }

  static final class UserClient {

    User fetch(String url, Duration timeout) {
      return new User(new UserId("u-1"), "Ada");
    }
  }

  static final class OrderClient {

    List<Order> fetch(String url, UserId id) {
      return List.of();
    }
  }
}
