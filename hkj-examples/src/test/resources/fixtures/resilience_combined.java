// Fixture for hkj-book/src/resilience/combined.md
//
// The page layers the four patterns over one HTTP call, one order workflow and one stream of
// profile lookups. Each of those, and the shared breaker/bulkhead/policy, is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitBreakerConfig;
import org.higherkindedj.hkt.resilience.Resilience;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.VTask;

record Response(String body) {

  static Response fallback() {
    return new Response("fallback");
  }
}

record Order(String id) {}

record OrderRequest(String id) {}

record Shipment(String orderId) {}

record UserProfile(String id) {

  static UserProfile unknown() {
    return new UserProfile("unknown");
  }
}

record Reservation(String orderId) {}

record Payment(String orderId) {}

record PaymentResult(String status) {

  static PaymentResult deferred(String message) {
    return new PaymentResult("DEFERRED");
  }

  static PaymentResult failed(String message) {
    return new PaymentResult("FAILED");
  }
}

sealed interface OrderError {
  record SystemError(String code) implements OrderError {

    static SystemError circuitBreakerOpen(String dependency) {
      return new SystemError("CIRCUIT_BREAKER_OPEN");
    }

    static SystemError timeout(String step, java.time.Duration budget) {
      return new SystemError("TIMEOUT");
    }
  }

  record Invalid(String reason) implements OrderError {}
}

final class PaymentGateway {

  PaymentResult charge(Order order) {
    return new PaymentResult("SETTLED");
  }
}

final class HttpClient {

  Response get(String url) {
    return new Response("ok");
  }
}

final class Service {

  String call() {
    return "ok";
  }
}

final class ProfileService {

  UserProfile fetch(String id) {
    return new UserProfile(id);
  }
}

final class SimpleLogger {

  void warn(String message, Object... args) {}
}

final class Metrics {

  void recordPaymentRetry(Object event) {}
}

class Fixture {

  static final String url = "https://api.example.com";

  static final List<String> userIds = List.of("u1", "u2");

  static final OrderRequest request = new OrderRequest("order-1");

  static final HttpClient httpClient = new HttpClient();

  static final Service service = new Service();

  static final ProfileService profileService = new ProfileService();

  static final SimpleLogger log = new SimpleLogger();

  static final Metrics metrics = new Metrics();

  static final CircuitBreaker serviceBreaker = CircuitBreaker.withDefaults();

  static final Bulkhead serviceBulkhead = Bulkhead.withMaxConcurrent(10);

  static final RetryPolicy retryPolicy =
      RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(200));

  static final PaymentGateway paymentGateway = new PaymentGateway();

  static final Order order = new Order("order-1");

  static EitherPath<OrderError, Order> validateOrder(OrderRequest request) {
    return Path.right(new Order(request.id()));
  }

  static EitherPath<OrderError, Reservation> reserveInventory(Order order) {
    return Path.right(new Reservation(order.id()));
  }

  static EitherPath<OrderError, Payment> chargePayment(Reservation reservation) {
    return Path.right(new Payment(reservation.orderId()));
  }

  static EitherPath<OrderError, Shipment> createShipment(Payment payment) {
    return Path.right(new Shipment(payment.orderId()));
  }
}
