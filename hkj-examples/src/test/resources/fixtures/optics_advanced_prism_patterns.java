// Fixture for hkj-book/src/optics/advanced_prism_patterns.md
//
// The page is a run of independent patterns, each with its own sum type. The models the patterns
// name are declared here, along with the small JSON hierarchy its `Prisms.nearly` and
// `doesNotMatch` snippets read; the snippet that shows a model shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.higherkindedj.optics.util.Traversals;

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

record ValidationError(String message) {}

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

record PaymentProcessed(String orderId, double amount, Instant timestamp) implements DomainEvent {}

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
record DatabaseConfig(String url) {}

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

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final String candidate = "ada@example.com";

  static final List<JsonValue> values =
      List.of(new JsonString("hello"), new JsonNumber(42), new JsonString("world"));

  static final int DEFAULT_POOL_SIZE = 10;

  static final int MAX_STRING_LENGTH = 255;

  static final Map<String, Object> row = Map.of();

  static ConfigValue loadConfiguration() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void sendWelcomeEmail(String userId, String email) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void provisionResources(String userId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void cleanupResources(String userId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void archiveData(String userId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void processPayment(String orderId) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void updateInventory(List<LineItem> items) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
