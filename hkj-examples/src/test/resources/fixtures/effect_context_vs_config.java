// Fixture for hkj-book/src/effect/context_vs_config.md
//
// The page contrasts ConfigContext (explicit, passed at the edge) with Context over a ScopedValue
// (implicit, inherited by forked virtual threads) on one database lookup and one fan-out. The
// domain behind both lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Duration;
import java.util.List;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

record DatabaseConfig(String connectionString) {}

record User(String id, String name) {}

record RequestInfo(String traceId, String userId) {}

record Data(String value) {}

record Order(String id) {}

record OrderRequest(String sku) {}

record Response(int status) {

  static Response ok(Order order) {
    return new Response(200);
  }
}

record DatabaseUrl(String value) {}

record HttpRequest(String traceId, String userId) {}

record AppConfig(String databaseUrl, String apiBaseUrl, int maxConnections, Duration timeout) {}

/** The scoped values the combined example binds, declared once for the snippets that only read. */
final class RequestContext {

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
}

final class SecurityContext {

  static final ScopedValue<String> PRINCIPAL = ScopedValue.newInstance();
}

final class OrderRepository {

  Order create(OrderRequest request, String userId) {
    return new Order("o-1");
  }
}

final class OrderService {

  OrderService(String databaseUrl) {}

  VTask<Order> createOrder(OrderRequest request) {
    return VTask.succeed(new Order("o-1"));
  }
}

final class Connection {

  User query(String sql, String id) {
    return new User(id, "Ada");
  }
}

final class UserRepo {

  User find(DatabaseConfig config) {
    return new User("u-1", "Ada");
  }
}

final class Logger {

  void info(String format, Object... arguments) {}
}

record PartialResult(String value) {}

record Result(String value) {

  static Result combine(PartialResult first, PartialResult second) {
    return new Result(first.value() + second.value());
  }

  static Result combine(List<PartialResult> parts) {
    return new Result(String.valueOf(parts.size()));
  }

  static Result fromData(List<Data> parts) {
    return new Result(String.valueOf(parts.size()));
  }
}

class Fixture {

  static final String userId = "u-1";

  static final RequestInfo requestInfo = new RequestInfo("t-1", "u-1");

  static final DatabaseConfig config = new DatabaseConfig("jdbc:h2:mem:test");

  static final Logger log = new Logger();

  static final UserRepo userRepo = new UserRepo();

  static final OrderService orderService = new OrderService("jdbc:h2:mem:test");

  static AppConfig loadConfig() {
    return new AppConfig("jdbc:h2:mem:test", "http://api", 10, Duration.ZERO);
  }

  static Connection connect(String url) {
    return new Connection();
  }

  static Data fetch1(RequestInfo info) {
    return new Data("one");
  }

  static Data fetch2(RequestInfo info) {
    return new Data("two");
  }

  static Data fetch3(RequestInfo info) {
    return new Data("three");
  }

  static final DatabaseConfig productionConfig = new DatabaseConfig("jdbc:postgresql://prod/app");

  static final UserRepository userRepository = new UserRepository();

  static PartialResult fetchData() {
    return new PartialResult("data");
  }

  static PartialResult fetchMoreData() {
    return new PartialResult("more");
  }

  static PartialResult fetchData(RequestInfo info) {
    return new PartialResult("data for " + info.traceId());
  }

  static PartialResult fetchMoreData(RequestInfo info) {
    return new PartialResult("more for " + info.traceId());
  }

  static final class UserRepository {

    User findById(String id, String connectionString) {
      return new User(id, "Ada");
    }
  }
}
