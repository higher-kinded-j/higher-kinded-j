// Fixture for .claude/skills/hkj-guide/reference/resilience.md
//
// The page's subject is the resilience combinators, so every snippet elides the call being protected:
// the HTTP client, the remote service, the replicas. Supplying them here means the combinator chains
// - and the railway-aware VResultPath overloads in particular - are compiled against the REAL
// library, so an argument that changes shape fails the build instead of quietly misleading a reader.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.BulkheadConfig;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.CircuitBreakerMetrics;
import org.higherkindedj.hkt.resilience.Resilience;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.resilience.Saga;
import org.higherkindedj.hkt.resilience.SagaBuilder;
import org.higherkindedj.hkt.resilience.SagaError;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;

/** The reader's own domain. */
record Request(String path) {}

record Response(int status) {
  static Response fallback() {
    return new Response(503);
  }
}

record Session(String id) {}

record Result(int rows) {}

record UserProfile(String id) {
  static UserProfile unknown() {
    return new UserProfile("?");
  }
}

/** The reader's own typed failure channel: every failure mode ends up here. */
sealed interface ApiError {
  record Timeout() implements ApiError {}

  record Overloaded() implements ApiError {}

  record Rejected(String why) implements ApiError {}
}

/** The exception the breaker is configured *not* to count: a rejection is not an outage. */
final class BusinessValidationException extends RuntimeException {
  BusinessValidationException(String message) {
    super(message);
  }
}

/** A logger, so the page's `log.warn(...)` calls are real calls. */
final class Log {
  void warn(String message, Object... args) {}
}

final class HttpClient {
  Response get(String url) {
    return new Response(200);
  }
}

final class RemoteService {
  String call() {
    return "ok";
  }
}

final class Database {
  Result query(String sql) {
    return new Result(0);
  }
}

final class ProfileService {
  UserProfile fetch(String id) {
    return new UserProfile(id);
  }
}

class Fixture {

  static final Log log = new Log();

  static final HttpClient httpClient = new HttpClient();
  static final RemoteService service = new RemoteService();
  static final Database database = new Database();
  static final ProfileService profileService = new ProfileService();

  static final String url = "https://example.test/api";
  static final String sql = "SELECT 1";
  static final Request request = new Request("/orders");
  static final List<String> userIds = List.of("u-1", "u-2");

  static final RetryPolicy retryPolicy = RetryPolicy.fixed(3, Duration.ofMillis(10));
  static final CircuitBreakerConfig config = CircuitBreakerConfig.defaults();
  static final CircuitBreaker breaker = CircuitBreaker.withDefaults();
  static final CircuitBreaker serviceBreaker = CircuitBreaker.withDefaults();
  static final Bulkhead bulkhead = Bulkhead.withMaxConcurrent(4);
  static final Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(4);

  static final VTask<String> task = VTask.succeed("ok");

  /** The call the VResultPath sections defer onto a virtual thread. */
  static Either<ApiError, Response> callService(Request request) {
    return Either.right(new Response(200));
  }

  // ----- structured concurrency -----

  static final List<VResultPath<ApiError, Response>> replicas =
      List.of(Path.vresultRight(new Response(200)));

  static final List<VResultPath<ApiError, Response>> tasks =
      List.of(Path.vresultRight(new Response(200)));

  static final VResultPath<ApiError, Session> acquire = Path.vresultRight(new Session("s-1"));

  static final Function<Session, VResultPath<ApiError, Response>> use =
      session -> Path.vresultRight(new Response(200));

  static final BiFunction<Session, Either<ApiError, Response>, VTask<Session>> release =
      (session, outcome) -> VTask.succeed(session);

  static final Function<Throwable, ApiError> onDefect = t -> new ApiError.Rejected(t.getMessage());
}
