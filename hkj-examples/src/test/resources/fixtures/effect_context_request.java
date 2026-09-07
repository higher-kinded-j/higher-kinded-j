// Fixture for hkj-book/src/effect/context_request.md
//
// The page threads one HTTP request through trace, correlation, locale, timing and tenant, so the
// domain is one request, one order, and the services around them.
//
// Most snippets on this page declare a class. A snippet's class is a sibling of Fixture rather
// than a subclass, so everything those classes name is declared at top level here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** The inbound request the page reads context out of. */
record HttpRequest(Map<String, String> headers, String host, String path) {

  Optional<String> header(String name) {
    return Optional.ofNullable(headers.get(name));
  }
}

enum OrderStatus {
  NEW,
  PAID
}

record Order(String id, BigDecimal total, LocalDate createdDate, OrderStatus status) {}

record OrderRequest(String customerId) {}

record OrderResponse(String id, String total, String created, String status) {}

record OrderSubmittedEvent(OrderRequest orderRequest, String traceId, String correlationId) {}

record OrderComponent(String detail) {}

record TenantConfig(String databaseUrl, Set<String> featureFlags) {}

record Response(int status, String body) {

  static Response ok(Order order) {
    return new Response(200, order.id());
  }

  static Response badRequest(String message) {
    return new Response(400, message);
  }

  static Response timeout() {
    return new Response(504, "");
  }

  static Response serverError() {
    return new Response(500, "");
  }
}

final class ValidationException extends RuntimeException {

  ValidationException(String message) {
    super(message);
  }
}

final class DeadlineExceededException extends RuntimeException {

  DeadlineExceededException(String message) {
    super(message);
  }
}

/** The scoped values the page binds at the edge. */
final class RequestContext {

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

  static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

  static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  static final ScopedValue<Instant> REQUEST_TIME = ScopedValue.newInstance();

  static final ScopedValue<Instant> DEADLINE = ScopedValue.newInstance();
}

final class TraceIdGenerator {

  static String compact() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}

/** The context-aware logger the page builds, declared here for the snippets that only use it. */
class ContextLogger {

  private final Logger delegate;

  ContextLogger(Class<?> clazz) {
    this.delegate = LoggerFactory.getLogger(clazz);
  }

  void info(String message, Object... args) {
    delegate.info(message, args);
  }

  void warn(String message, Object... args) {
    delegate.warn(message, args);
  }

  void error(String message, Throwable t) {
    delegate.error(message, t);
  }

  void error(String message, Object... args) {
    delegate.error(message, args);
  }
}

final class ResponseFormatter {

  String formatCurrency(java.math.BigDecimal amount) {
    return amount.toPlainString();
  }

  String formatDate(LocalDate date) {
    return date.toString();
  }

  String getMessage(String key, Object... args) {
    return key;
  }
}

final class TenantResolver {

  String resolve(HttpRequest request) {
    return "default";
  }
}

final class Router {

  Response route(HttpRequest request) {
    return new Response(200, "");
  }
}

final class OrderRepository {

  void save(Order order) {}
}

interface RowMapper {

  Object map(Object row);
}

final class JdbcTemplate {

  <T> T queryForObject(String sql, RowMapper mapper, Object... args) {
    return null;
  }

  void save(Object entity) {}
}

final class EventPublisher {

  void publish(OrderSubmittedEvent event) {}
}

final class LegacyService {

  void process() {}
}

final class OrderService {

  Order create(OrderRequest request) {
    return new Order("o-1", BigDecimal.ZERO, LocalDate.EPOCH, OrderStatus.NEW);
  }
}

class Fixture {

  static final HttpRequest request =
      new HttpRequest(Map.of(), "tenant.example.com", "/tenant/acme/orders");

  static final String cartId = "c-1";

  static final String paymentDetails = "card";

  static final OrderRequest orderDetails = new OrderRequest("cust-1");

  static final ContextLogger log = new ContextLogger(Fixture.class);

  static final Router router = new Router();

  static final OrderRepository orderRepository = new OrderRepository();

  static final JdbcTemplate jdbcTemplate = new JdbcTemplate();

  static final EventPublisher eventPublisher = new EventPublisher();

  static final LegacyService legacyService = new LegacyService();

  static final OrderService orderService = new OrderService();

  static final TenantResolver tenantResolver = new TenantResolver();

  static Order buildOrder(OrderRequest request) {
    return new Order("o-1", BigDecimal.ZERO, LocalDate.EPOCH, OrderStatus.NEW);
  }

  static void validateCart(String cartId) {}

  static void processPayment(String paymentDetails) {}

  static void createOrder(OrderRequest orderDetails) {}

  static void processOrder(OrderRequest request) {}

  static Response processWithTimeout(HttpRequest request) {
    return new Response(200, "");
  }

  static <T> T parseBody(HttpRequest request, Class<T> type) {
    return null;
  }

  static <T> T parseResponse(java.net.http.HttpResponse<String> response, Class<T> type) {
    return null;
  }

  static OrderComponent validateInventory(OrderRequest request) {
    return new OrderComponent("inventory");
  }

  static OrderComponent calculatePricing(OrderRequest request) {
    return new OrderComponent("pricing");
  }

  static OrderComponent checkFraudScore(OrderRequest request) {
    return new OrderComponent("fraud");
  }

  static Order assembleOrder(OrderRequest request, java.util.List<OrderComponent> parts) {
    return new Order("o-1", BigDecimal.ZERO, LocalDate.EPOCH, OrderStatus.NEW);
  }
}
