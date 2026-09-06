// Fixture for hkj-book/src/optics/prism_toolkit.md
//
// The page is a tour of the prism toolkit, and each section reaches for whichever sum type suits
// the method it is showing - a JSON value, a domain event, an order status. All of them are
// declared here; a section that shows one shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.ListPrisms;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;

@GeneratePrisms
sealed interface SourceData permits CsvRow, JsonObject, XmlNode {}

@GeneratePrisms
sealed interface JsonValue permits JsonString, JsonNumber, JsonObject {}

record JsonString(String value) implements JsonValue {}

record JsonNumber(double value) implements JsonValue {}

record JsonObject(Map<String, JsonValue> fields) implements JsonValue, SourceData {}

record CsvRow(Map<String, String> columns) implements SourceData {

  String column(String name) {
    return columns.get(name);
  }
}

record XmlNode(String tag) implements SourceData {}

record CustomerRecord(String id, String name, String email) {}

record LineItem(String sku, BigDecimal totalPrice) {}

@GeneratePrisms
sealed interface DomainEvent permits UserEvent, OrderEvent, PaymentEvent, OrderCompleted {}

record UserEvent(String userId) implements DomainEvent {}

record OrderEvent(String orderId) implements DomainEvent {}

record PaymentEvent(String paymentId, BigDecimal amount) implements DomainEvent {}

record OrderCompleted(String orderId, List<LineItem> lineItems) implements DomainEvent {}

@GeneratePrisms
sealed interface ApiResponse permits SuccessResponse, ValidationError, ServerError {}

record SuccessResponse(String data, int status) implements ApiResponse {}

@GenerateLenses
record ValidationError(String code, String message) implements ApiResponse {}

record ServerError(String message) implements ApiResponse {}

@GeneratePrisms
sealed interface ParsedValue permits IntValue, StringValue, InvalidValue {}

record IntValue(int value) implements ParsedValue {}

record StringValue(String value) implements ParsedValue {}

record InvalidValue(String reason) implements ParsedValue {}

@GeneratePrisms
sealed interface ConfigSource permits DatabaseConfig, FileConfig {}

record DatabaseConfig(String host, int port) implements ConfigSource {

  static final DatabaseConfig DEFAULT_POSTGRES = new DatabaseConfig("localhost", 5432);
}

record FileConfig(String path) implements ConfigSource {}

record ApplicationConfig(ConfigSource source) {}

@GeneratePrisms
sealed interface ConfigValue permits StringConfig, IntConfig {}

record StringConfig(String value) implements ConfigValue {}

record IntConfig(int value) implements ConfigValue {}

@GeneratePrisms
sealed interface OrderStatus permits Draft, Submitted, Approved, Rejected {}

record Draft(BigDecimal discount) implements OrderStatus {

  Draft withDiscount(double rate) {
    return new Draft(BigDecimal.valueOf(rate));
  }
}

record Submitted(Instant at) implements OrderStatus {}

record Approved(Instant at, String reason) implements OrderStatus {}

record Rejected(String reason) implements OrderStatus {}

record Order(BigDecimal totalValue) {}

record Data(String value) {}

record User(String name) {}

@GenerateLenses
record Config(String name) {

  static final Config DEFAULT = new Config("default");
}

// The reader's own logger, whatever it is. Named here so the page's snippets can say what they
// would log without the gate carrying a logging framework.
class Log {

  void info(String message, Object... arguments) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Log logger = new Log();

  static final JsonValue jsonValue = new JsonString("hello");

  static final JsonValue value = jsonValue;

  static final List<JsonValue> values = List.of(jsonValue);

  static final ApiResponse response = new SuccessResponse("ok", 200);

  static final ConfigValue configValue = new StringConfig("theme");

  static final Optional<Config> optionalConfig = sample();

  static final Data data = new Data("payload");

  static final String input = "yes";

  static final String statusCode = "200 OK";

  static final List<String> statusCodes = List.of(statusCode);

  static final List<String> names = List.of("Alice", "Bob", "Charlie");

  static Optional<Optional<Config>> loadConfig() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static ParsedValue parseInput(String raw) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Either<ValidationError, Data> validate(Data toCheck) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static @Nullable String getDatabaseValue() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Lens<List<String>, String> listFirstElementLens() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  void processPayment(PaymentEvent event) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
