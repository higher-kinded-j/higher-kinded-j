// Fixture for hkj-book/src/resilience/circuit_breaker.md
//
// The page guards one payment service and one inventory reservation. The services, the domain and
// the breaker the later snippets reuse are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Duration;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.CircuitBreakerMetrics;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;

record Order(String id) {}

record Reservation(String orderId) {}

sealed interface OrderError {
  record SystemError(String code) implements OrderError {

    static SystemError circuitBreakerOpen(String dependency) {
      return new SystemError("CIRCUIT_BREAKER_OPEN");
    }
  }

  record OutOfStock(String sku) implements OrderError {}
}

final class BusinessValidationException extends RuntimeException {}

final class PaymentService {

  String getStatus(String orderId) {
    return "settled";
  }

  BigDecimal getBalance(String accountId) {
    return BigDecimal.TEN;
  }

  String get(String url) {
    return "ok";
  }
}

final class InventoryService {

  Either<OrderError, Reservation> reserve(Order order) {
    return Either.right(new Reservation(order.id()));
  }
}

final class SimpleLogger {

  void info(String message, Object... args) {}

  void warn(String message, Object... args) {}
}

class Fixture {

  static final String orderId = "order-1";

  static final String accountId = "acc-1";

  static final String url = "https://payments.example.com";

  static final Order order = new Order("order-1");

  static final PaymentService paymentService = new PaymentService();

  static final InventoryService inventoryService = new InventoryService();

  static final SimpleLogger log = new SimpleLogger();

  static final CircuitBreakerConfig config = CircuitBreakerConfig.builder().build();

  static final CircuitBreaker breaker = CircuitBreaker.withDefaults();

  static final CircuitBreaker paymentBreaker = CircuitBreaker.withDefaults();

  static final CircuitBreaker inventoryBreaker = CircuitBreaker.withDefaults();

  static String cachedStatus(String orderId) {
    return "cached";
  }

  static EitherPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.either(inventoryService.reserve(order));
  }

  static VResultPath<OrderError, Reservation> reserveInventoryAsync(Order order) {
    return Path.vresultDefer(() -> inventoryService.reserve(order));
  }
}
