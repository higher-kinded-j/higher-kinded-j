// Fixture for hkj-book/src/optics/advanced_prism_patterns.md
//
// The page is a run of independent patterns, each with its own sum type. The models the patterns
// name are declared here, along with the small JSON hierarchy its `Prisms.nearly` and
// `doesNotMatch` snippets read; the snippet that shows a model shadows this copy.
//
// Each pattern goes on in a second class, its "Advanced" block, which declares the prisms it reads,
// so a reader can copy it alone. PatternSupport holds only the services and constants the page
// calls without showing, and is imported statically into every snippet.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static bookverify.PatternSupport.*;
import static java.util.concurrent.CompletableFuture.delayedExecutor;

import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.Traversals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GeneratePrisms
sealed interface JsonValue permits JsonString, JsonNumber {}

record JsonString(String value) implements JsonValue {}

record JsonNumber(double value) implements JsonValue {}

@GeneratePrisms
sealed interface ConfigValue permits StringValue, IntValue, BoolValue, NestedConfig {}

record StringValue(String value) implements ConfigValue {}

record IntValue(int value) implements ConfigValue {}

record BoolValue(boolean value) implements ConfigValue {}

@GenerateLenses
record NestedConfig(Map<String, ConfigValue> values) implements ConfigValue {}

// The responses the traditional handler reads by status code, which the prism solution then
// declares as a sealed ApiResponse. The shared variants carry both, so each snippet compiles.
interface HttpResponse {
  int status();
}

record SuccessResponse(JsonValue data) implements HttpResponse {
  public int status() {
    return 200;
  }
}

@GeneratePrisms
sealed interface ApiResponse
    permits Success, ValidationError, ServerError, RateLimitError, AuthError, NotFoundError {}

record Success(JsonValue data, int statusCode) implements ApiResponse {}

record ValidationError(List<String> errors, String field) implements ApiResponse, HttpResponse {
  public int status() {
    return 400;
  }
}

record ServerError(String message, String traceId) implements ApiResponse, HttpResponse {
  public int status() {
    return 500;
  }
}

record RateLimitError(long retryAfterMs) implements ApiResponse, HttpResponse {
  public int status() {
    return 429;
  }
}

record AuthError(String realm) implements ApiResponse {}

record NotFoundError(String resource) implements ApiResponse {}

/** The client the resilient pipeline calls first. */
interface ApiGateway {
  CompletableFuture<ApiResponse> call(String endpoint);
}

final class ApiException extends RuntimeException {
  ApiException(String message) {
    super(message);
  }
}

record AuditEntry(String type, String id, Instant at) {}

enum Environment {
  DEVELOPMENT,
  PRODUCTION
}

@GeneratePrisms
sealed interface DataValue permits StringData, IntData, DoubleData, NullData {}

record StringData(String value) implements DataValue {}

record IntData(int value) implements DataValue {}

record DoubleData(double value) implements DataValue {}

record NullData() implements DataValue {}

record LineItem(String sku, int quantity) {}

@GeneratePrisms
sealed interface DomainEvent
    permits UserCreated, UserDeleted, UserUpdated, OrderPlaced, OrderCancelled, PaymentProcessed {}

record UserCreated(String userId, String email, Instant timestamp) implements DomainEvent {}

record UserDeleted(String userId, Instant timestamp) implements DomainEvent {}

record UserUpdated(String userId, Map<String, String> changes, Instant timestamp)
    implements DomainEvent {}

record OrderPlaced(String orderId, List<LineItem> items, Instant timestamp)
    implements DomainEvent {}

record OrderCancelled(String orderId, String reason, Instant timestamp) implements DomainEvent {}

record PaymentProcessed(String orderId, BigDecimal amount, Instant timestamp)
    implements DomainEvent {}

@GeneratePrisms
sealed interface OrderState permits Pending, Processing, Shipped, Delivered, Cancelled {}

record Pending(Instant createdAt) implements OrderState {}

record Processing(String transactionId, Instant startedAt) implements OrderState {}

record Shipped(String trackingNumber, Instant shippedAt) implements OrderState {}

record Delivered(Instant deliveredAt) implements OrderState {}

record Cancelled(String reason, Instant cancelledAt) implements OrderState {}

@GeneratePrisms
sealed interface OrderEvent
    permits PaymentReceived, ShippingCompleted, DeliveryConfirmed, CancellationRequested {}

record PaymentReceived(String transactionId) implements OrderEvent {}

record ShippingCompleted(String trackingNumber) implements OrderEvent {}

record DeliveryConfirmed() implements OrderEvent {}

record CancellationRequested(String reason) implements OrderEvent {}

record Order(String id, OrderState state) {

  Order withState(OrderState newState) {
    return new Order(id, newState);
  }
}

// The plugin host the last pattern dispatches over.
record DatabaseConfig(String url) {

  DatabaseConfig withReadReplica() {
    return new DatabaseConfig(url + "?replica=read");
  }
}

record Result(String output) {}

enum FileOperation {
  READ,
  WRITE
}

enum HttpMethod {
  GET,
  POST
}

class DatabaseContext {

  Result executeQuery(String query, DatabaseConfig config) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class FileSystemContext {

  Result performOperation(Path path, FileOperation operation) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class NetworkContext {

  Result makeRequest(URL endpoint, HttpMethod method) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class ComputeContext {

  Result runScript(String script, Runtime runtime) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class ExecutionContext {

  Optional<DatabaseContext> getDatabaseContext() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  Optional<FileSystemContext> getFileSystemContext() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  Optional<NetworkContext> getNetworkContext() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  Optional<ComputeContext> getComputeContext() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

@GeneratePrisms
sealed interface Plugin
    permits DatabasePlugin, FileSystemPlugin, NetworkPlugin, ComputePlugin {}

record DatabasePlugin(String query, DatabaseConfig config) implements Plugin {

  public Result execute(DatabaseContext ctx) {
    return ctx.executeQuery(query, config);
  }
}

record FileSystemPlugin(Path path, FileOperation operation) implements Plugin {

  public Result execute(FileSystemContext ctx) {
    return ctx.performOperation(path, operation);
  }
}

record NetworkPlugin(URL endpoint, HttpMethod method) implements Plugin {

  public Result execute(NetworkContext ctx) {
    return ctx.makeRequest(endpoint, method);
  }
}

record ComputePlugin(String script, Runtime runtime) implements Plugin {

  public Result execute(ComputeContext ctx) {
    return ctx.runScript(script, runtime);
  }
}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final String candidate = "ada@example.com";

  static final List<JsonValue> values =
      List.of(new JsonString("hello"), new JsonNumber(42), new JsonString("world"));

  static final int DEFAULT_POOL_SIZE = 10;

  static final Map<String, Object> row = Map.of();

  static ConfigValue loadConfiguration() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

}

/**
 * What a pattern's first class declares and its continuation reads, and the services the page
 * calls without showing.
 */
final class PatternSupport {

  private PatternSupport() {}

  static final int MAX_STRING_LENGTH = 255;

  static final Logger log = LoggerFactory.getLogger("events");

  static final ApiGateway primaryApi = Fixture.sample();

  static JsonValue callSecondaryApi(String endpoint) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Result processSuccess(SuccessResponse response) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Result handleValidation(ValidationError error) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Result handleServerError(ServerError error) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Result retryWithBackoff(RateLimitError error) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void sendWelcomeEmail(String userId, String email) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void provisionResources(String userId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void cleanupResources(String userId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void archiveData(String userId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void processPayment(String orderId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static void updateInventory(List<LineItem> items) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
