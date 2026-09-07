// Fixture for hkj-book/src/optics/advanced_prism_patterns_recipes.md
//
// The recipes cache a composed optic, batch a prism over a list, and test both. The models they
// read - a settings map, an API response and a configuration value - are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

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
record Settings(Map<String, Optional<String>> entries) {}

record JsonValue(String raw) {}

@GeneratePrisms
sealed interface ApiResponse permits Success, ServerError {}

@GenerateLenses
record Success(JsonValue data, int statusCode) implements ApiResponse {}

record ServerError(String message, String traceId) implements ApiResponse {}

@GeneratePrisms
sealed interface ConfigValue permits StringValue, IntValue {}

record StringValue(String value) implements ConfigValue {}

record IntValue(int value) implements ConfigValue {}

record Config(String host, int port) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final JsonValue jsonData = new JsonValue("{}");

  static Prism<Config, String> buildHostPrism() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Config createValidConfig() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Config createInvalidConfig() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
