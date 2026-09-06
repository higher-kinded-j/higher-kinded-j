// Fixture for hkj-book/src/resilience/retry.md
//
// The page retries one HTTP call and one inventory reservation under every policy shape. The
// clients, the logger and the domain are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;

record Response(int statusCode, String body) {}

record Order(String id) {}

record Reservation(String orderId) {}

record Data(String value) {

  static Data empty() {
    return new Data("");
  }
}

sealed interface OrderError {
  record SystemError(String detail) implements OrderError {}

  record OutOfStock(String sku) implements OrderError {}
}

final class HttpException extends RuntimeException {

  private final int statusCode;

  HttpException(int statusCode) {
    this.statusCode = statusCode;
  }

  int statusCode() {
    return statusCode;
  }
}

final class HttpClient {

  String get(String url) {
    return "ok";
  }
}

final class InventoryService {

  Either<OrderError, Reservation> reserve(Order order) {
    return Either.right(new Reservation(order.id()));
  }
}

final class DataSource {

  Data fetch() {
    return new Data("payload");
  }
}

final class SimpleLogger {

  void warn(String message, Object... args) {}

  void error(String message, Object... args) {}
}

final class Metrics {

  void incrementRetryCount(int attempt) {}
}

class Fixture {

  static final String url = "https://api.example.com/orders";

  static final Order order = new Order("order-1");

  static final HttpClient httpClient = new HttpClient();

  static final HttpClient primaryService = new HttpClient();

  static final HttpClient backupService = new HttpClient();

  static final InventoryService inventoryService = new InventoryService();

  static final DataSource primarySource = new DataSource();

  static final DataSource backupSource = new DataSource();

  static final SimpleLogger log = new SimpleLogger();

  static final Metrics metrics = new Metrics();

  static final RetryPolicy policy = RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1));

  static final VTask<String> resilient =
      Retry.retryTask(VTask.of(() -> httpClient.get("https://api.example.com")), policy);

  static EitherPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.either(inventoryService.reserve(order));
  }

  static VResultPath<OrderError, Reservation> reserveInventoryAsync(Order order) {
    return Path.vresultDefer(() -> inventoryService.reserve(order));
  }

  static EitherPath<OrderError, Order> pipeline() {
    return Path.right(new Order("order-1"));
  }
}
