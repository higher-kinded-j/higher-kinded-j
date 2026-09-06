// Fixture for hkj-book/src/resilience/bulkhead.md
//
// The page bounds concurrency around one database query. The database, the result and the
// bulkhead the later snippets reuse are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Duration;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.BulkheadConfig;
import org.higherkindedj.hkt.resilience.BulkheadFullException;
import org.higherkindedj.hkt.vtask.VTask;

record Result(String value) {

  static Result fromCache(String sql) {
    return new Result("cached");
  }
}

final class Database {

  Result query(String sql) {
    return new Result("rows");
  }
}

final class SimpleLogger {

  void warn(String message, Object... args) {}
}

record Order(String id) {}

record Reservation(String orderId) {}

record UserProfile(String id) {}

sealed interface OrderError {
  record SystemError(String message) implements OrderError {

    static SystemError fromException(String message, Throwable cause) {
      return new SystemError(message);
    }
  }
}

final class InventoryService {

  Either<OrderError, Reservation> reserve(Order order) {
    return Either.right(new Reservation(order.id()));
  }
}

final class UserService {

  UserProfile fetch(String id) {
    return new UserProfile(id);
  }
}

class Fixture {

  static final String sql = "SELECT 1";

  static final Order order = new Order("order-1");

  static final java.util.List<String> userIds = java.util.List.of("u1", "u2");

  static final InventoryService inventoryService = new InventoryService();

  static final UserService userService = new UserService();

  static final Bulkhead inventoryBulkhead = Bulkhead.withMaxConcurrent(5);

  static EitherPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.either(inventoryService.reserve(order));
  }

  static VResultPath<OrderError, Reservation> reserveInventoryAsync(Order order) {
    return Path.vresultDefer(() -> inventoryService.reserve(order));
  }

  static final Database database = new Database();

  static final SimpleLogger log = new SimpleLogger();

  static final Bulkhead dbBulkhead = Bulkhead.withMaxConcurrent(10);
}
