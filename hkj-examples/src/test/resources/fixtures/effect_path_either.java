// Fixture for hkj-book/src/effect/path_either.md
//
// The page catalogues EitherPath's constructors, combinators, error handling and extractors.
// The domain those examples run on lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Duration;
import java.util.function.Predicate;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;

record User(String name) {}

record Person(String name, Integer age) {}

record Config(String name) {

  static Config defaults() {
    return new Config("defaults");
  }
}

/** The page's untyped error, and the one variant its constructor example names. */
sealed interface Error permits ValidationError, ApiError {}

record ValidationError(String message) implements Error {}

record ApiError(String message) implements Error {

  static DomainError toDomain(ApiError error) {
    return new DomainError(error.message());
  }
}

record DomainError(String message) {}

record ConfigError(String detail) {}

record Reservation(String id) {}

record Receipt(String id) {}

record Status(String state) {}

record Result(String value) {}

record Order(String id) {}

sealed interface OrderError {

  record SystemError(String detail) implements OrderError {

    static SystemError timeout(String step) {
      return new SystemError("timed out: " + step);
    }
  }

  record Unavailable() implements OrderError {}

  record Busy() implements OrderError {}

  static OrderError unavailable() {
    return new Unavailable();
  }

  static OrderError busy() {
    return new Busy();
  }
}

class Fixture {

  static final String input = "raw";

  static final Order order = new Order("o-1");

  static final String orderId = "o-1";

  static final String sql = "select 1";

  static final RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(50));

  static final CircuitBreaker breaker = CircuitBreaker.withDefaults();

  static final Bulkhead bulkhead = Bulkhead.withMaxConcurrent(8);

  static final Predicate<OrderError> isTransient = e -> e instanceof OrderError.SystemError;

  static final EitherPath<OrderError, Order> pipeline = Path.right(new Order("o-1"));

  static EitherPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.right(new Reservation("r-1"));
  }

  static EitherPath<OrderError, Receipt> chargePayment(Order order) {
    return Path.right(new Receipt("c-1"));
  }

  static EitherPath<OrderError, Status> fetchStatus(String orderId) {
    return Path.right(new Status("ok"));
  }

  static EitherPath<OrderError, Result> runQuery(String sql) {
    return Path.right(new Result("rows"));
  }

  static final EitherPath<ApiError, User> apiPath =
      Path.either(Either.right(new User("Ada")));

  static final EitherPath<Error, User> path = Path.either(Either.right(new User("Ada")));

  static Either<Error, User> validateUser(String input) {
    return Either.right(new User("Ada"));
  }

  static Either<String, Config> loadConfig() {
    return Either.right(new Config("file"));
  }

  static Either<String, Config> loadBackupConfig() {
    return Either.right(new Config("backup"));
  }
}
