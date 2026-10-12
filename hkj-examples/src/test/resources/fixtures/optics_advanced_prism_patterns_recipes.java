// Fixture for hkj-book/src/optics/advanced_prism_patterns_recipes.md
//
// The recipes cache a composed optic, batch a prism over a list, and test both. The models they
// read - a settings map, an API response and a configuration value - are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

@GenerateLenses
record SettingsMap(Map<String, Optional<String>> entries) {}

@GeneratePrisms
sealed interface JsonValue permits JsonString, JsonNumber, JsonBoolean, JsonObject {}

record JsonString(String value) implements JsonValue {}

record JsonNumber(double value) implements JsonValue {}

record JsonBoolean(boolean value) implements JsonValue {}

record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}

@GeneratePrisms
sealed interface ApiResponse
    permits Success, ValidationError, ServerError, RateLimitError, AuthError, NotFoundError {}

@GenerateLenses
record Success(JsonValue data, int statusCode) implements ApiResponse {}

record ValidationError(List<String> errors, String field) implements ApiResponse {}

record ServerError(String message, String traceId) implements ApiResponse {}

record RateLimitError(long retryAfterMs) implements ApiResponse {}

record AuthError(String realm) implements ApiResponse {}

record NotFoundError(String resource) implements ApiResponse {}

@GeneratePrisms
sealed interface ConfigValue permits StringValue, IntValue, BoolValue, NestedConfig {}

record StringValue(String value) implements ConfigValue {}

record IntValue(int value) implements ConfigValue {}

record BoolValue(boolean value) implements ConfigValue {}

@GenerateLenses
record NestedConfig(Map<String, ConfigValue> values) implements ConfigValue {}

record HostConfig(String host, int port) {}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final JsonValue jsonData = new JsonObject(Map.of());

  static Prism<HostConfig, String> buildHostPrism() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static HostConfig createValidConfig() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static HostConfig createInvalidConfig() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
