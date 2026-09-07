// Fixture for hkj-book/src/effect/path_vtask.md
//
// The page catalogues VTaskPath's constructors, combinators, three execution modes and error
// handling over a small fetch/config sketch. The services behind it live here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Unit;
import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

record Data(String value) {}

record UserId(String value) {}

record User(String name) {}

record Order(String id) {}

record Analytics(String summary) {}

record UserProfile(String user, String profile) {}

record Dashboard(User user, List<Order> orders, Analytics analytics) {

  static Dashboard empty() {
    return new Dashboard(new User("none"), List.of(), new Analytics(""));
  }
}

record Error(String message) {

  static Error from(Throwable cause) {
    return new Error(cause.getMessage());
  }
}

class Fixture {

  static final String url = "https://example.test/data";

  static final String id = "u-1";

  static final UserId userId = new UserId("u-1");

  static final CircuitBreaker serviceBreaker = CircuitBreaker.withDefaults();

  static final Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(8);

  static final UserService userService = new UserService();

  static final OrderService orderService = new OrderService();

  static final AnalyticsService analyticsService = new AnalyticsService();

  static Config loadFromPrimarySource() {
    return new Config("primary");
  }

  static Config loadFromSecondarySource() {
    return new Config("secondary");
  }

  static Config loadFromCache() {
    return new Config("cache");
  }

  static Data fetchData() {
    return new Data("data");
  }

  static String fetchUser(String id) {
    return "user " + id;
  }

  static String fetchProfile(String id) {
    return "profile " + id;
  }

  static String fetchUserData(String id) {
    return "data " + id;
  }

  static String fetchUserProfile(String id) {
    return "profile " + id;
  }

  static String fetchFromServerA() {
    return "a";
  }

  static String fetchFromServerB() {
    return "b";
  }

  static Integer compute(int seed) {
    return seed;
  }

  static VTask<String> validateField1() {
    return VTask.succeed("one");
  }

  static VTask<String> validateField2() {
    return VTask.succeed("two");
  }

  static final Connection connection = new Connection();

  static final DataSource dataSource = new DataSource();

  static final UserDao userDao = new UserDao();

  static final class Connection implements AutoCloseable {

    @Override
    public void close() {}
  }

  static final class DataSource {

    Connection getConnection() {
      return new Connection();
    }
  }

  static final class UserDao {

    List<User> findAll(Connection connection) {
      return List.of();
    }
  }

  static final class UserService {

    User get(UserId id) {
      return new User(id.value());
    }
  }

  static final class OrderService {

    List<Order> recent(UserId id) {
      return List.of();
    }
  }

  static final class AnalyticsService {

    Analytics get(UserId id) {
      return new Analytics("flat");
    }
  }

  static final Logger logger = new Logger();

  static final HttpClient httpClient = new HttpClient();

  static final ConfigService configService = new ConfigService();

  static final VTask<Config> existingVTask = VTask.delay(() -> new Config("app"));

  static Integer compute() {
    return 42;
  }

  static String enrichWithTimestamp(String value) {
    return value + " @ now";
  }

  static Unit initResources() {
    return Unit.INSTANCE;
  }

  static Data loadData() {
    return new Data("data");
  }

  static Config loadFallbackConfig() {
    return new Config("fallback");
  }

  static void handleError(Throwable cause) {}

  static final class HttpClient {

    String get(String url) {
      return "{}";
    }
  }

  static final class ConfigService {

    Config load() {
      return new Config("app");
    }
  }

  /** Stands in for whatever logger the reader has. */
  static final class Logger {

    Unit info(String message) {
      return Unit.INSTANCE;
    }
  }
}
