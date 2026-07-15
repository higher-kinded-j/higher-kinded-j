// Fixture for .claude/skills/hkj-guide/reference/patterns.md
//
// The page's patterns are about the shape of a pipeline, so each one elides the domain it runs over:
// the services, the repository, the logger. Supplying them here means the pipelines themselves are
// compiled against the REAL library, so a combinator that is renamed or re-signatured fails the
// build instead of quietly misleading a reader.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;

/** The reader's own domain. */
record User(String name, String email, int age) {}

record Item(String sku) {}

record Money(long pence) {}

record Reservation(String id) {
  Money total() {
    return new Money(1_000);
  }
}

record Payment(String id) {}

record Order(String id) {}

record Response(int status) {}

record RawData(String body) {}

record Rows(int count) {}

record Report(int rowCount) {}

record ReportRequest(String from) {}

record Config(int port) {
  static Config defaults() {
    return new Config(8080);
  }
}

record Data(List<String> rows) {
  static Data empty() {
    return new Data(List.of());
  }
}

/** The reader's own error type, as the enrichment example writes it. */
record Error(String message) {}

record DetailedError(Error cause, String operation, Map<String, Object> context, Instant at) {}

record ConfigError(String message) {}

record ServiceError(String message, Throwable cause) {}

sealed interface UserError {
  String message();

  record NotFound(String id) implements UserError {
    @Override
    public String message() {
      return "no user " + id;
    }
  }
}

/** The unified error the service layer maps everything onto. */
record OrderError(String message) {
  static OrderError fromUserError(UserError e) {
    return new OrderError(e.message());
  }

  static OrderError fromInventoryError(InventoryError e) {
    return new OrderError(e.message());
  }

  static OrderError fromPaymentError(PaymentError e) {
    return new OrderError(e.message());
  }
}

record InventoryError(String message) {}

record PaymentError(String message) {}

/** A logger, so the pages' `log.warn(...)` calls are real calls. */
final class Log {
  void debug(String message, Object... args) {}

  void info(String message, Object... args) {}

  void warn(String message, Object... args) {}
}

/** The repository the service converts at its boundary: absence is normal, so it returns `Maybe`. */
final class UserRepository {
  Maybe<User> findById(String id) {
    return Maybe.just(new User("Ada", "ada@example.com", 36));
  }
}

/** Stands in for Spring's JdbcTemplate: generic in the row type, as the real one is. */
final class JdbcTemplate {
  <T> Optional<T> queryForOptional(String sql, Object... args) {
    return Optional.empty();
  }
}

final class UserService {
  EitherPath<UserError, User> getById(String id) {
    return Path.right(new User("Ada", "ada@example.com", 36));
  }
}

final class InventoryService {
  EitherPath<InventoryError, Reservation> reserve(List<Item> items) {
    return Path.right(new Reservation("r-1"));
  }
}

final class PaymentService {
  EitherPath<PaymentError, Payment> charge(User user, Money amount) {
    return Path.right(new Payment("p-1"));
  }
}

/** The exception-throwing API the TryPath examples wrap. */
final class LegacyApi {
  Data fetchData(String id) {
    return Data.empty();
  }
}

/** The Optional-returning API the MaybePath example wraps. */
final class ModernApi {
  Optional<User> findUser(String id) {
    return Optional.of(new User("Ada", "ada@example.com", 36));
  }
}

final class HttpClient {
  Response get(String url) {
    return new Response(200);
  }
}

/** The service whose deferred pipeline the IOPath example runs. */
final class ReportPipeline {
  IOPath<Report> generateReport(ReportRequest req) {
    return Path.ioPure(new Report(0));
  }
}

class Fixture {

  static final Log log = new Log();

  static final UserRepository repository = new UserRepository();
  static final JdbcTemplate jdbcTemplate = new JdbcTemplate();
  static final UserService users = new UserService();
  static final InventoryService inventory = new InventoryService();
  static final PaymentService payments = new PaymentService();
  static final LegacyApi legacy = new LegacyApi();
  static final ModernApi modern = new ModernApi();
  static final HttpClient httpClient = new HttpClient();
  static final ReportPipeline pipeline = new ReportPipeline();

  static final String url = "https://example.test/api";
  static final ReportRequest req = new ReportRequest("2026-01-01");

  /** The failing path the recovery-with-logging example recovers. */
  static final EitherPath<Error, String> path = Path.left(new Error("boom"));

  static final String fallback = "fallback";

  // ----- the service-layer examples -----

  static Order createOrder(User user, List<Item> items, Payment payment) {
    return new Order("o-1");
  }

  static Either<ConfigError, Config> loadFromFile() {
    return Either.left(new ConfigError("no file"));
  }

  static Either<ConfigError, Config> loadFromEnv() {
    return Either.left(new ConfigError("no env"));
  }

  // ----- the deferred-pipeline example -----

  static RawData fetchData(ReportRequest req) {
    return new RawData("");
  }

  static Rows transform(RawData data) {
    return new Rows(0);
  }

  static Report format(Rows rows) {
    return new Report(rows.count());
  }

  // ----- the retry example -----

  static IOPath<Data> fetchFromPrimary() {
    return Path.ioPure(Data.empty());
  }

  static IOPath<Data> fetchFromBackup() {
    return Path.ioPure(Data.empty());
  }
}
