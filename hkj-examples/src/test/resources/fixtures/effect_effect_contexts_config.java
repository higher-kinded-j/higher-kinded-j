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
import org.higherkindedj.hkt.effect.context.ConfigContext;
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
record AppConfig(String apiUrl) {}

record ReportConfig(String userServiceUrl, String orderServiceUrl, Duration timeout, String format) {}

record DbConfig(String url, String username, String password) {}

record ApiConfig(String endpoint, Duration timeout) {

  ApiConfig withTimeout(Duration timeout) {
    return new ApiConfig(endpoint, timeout);
  }
}

record ServiceConfig(String baseUrl) {}

record AnyConfig(String value) {}

record Config(String name) {}

class Fixture {

  static final UserId userId = new UserId("u-1");

  static final ReportConfig config = new ReportConfig("http://u", "http://o", Duration.ZERO, "PDF");

  static final HttpClient httpClient = new HttpClient();

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

  static final class HttpClient {

    Response get(String endpoint) {
      return new Response("{}");
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
