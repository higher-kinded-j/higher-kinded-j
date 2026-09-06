// Fixture for hkj-book/src/optics/prisms.md
//
// The page works one JSON model - a sealed JsonValue with four cases - and composes prisms through
// it. The model is declared here with its generators, so the page's snippets name genuinely
// generated prisms and lenses; the snippet that shows the model shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Traversals;

@GeneratePrisms
sealed interface JsonValue {}

@GenerateLenses
record JsonString(String value) implements JsonValue {}

record JsonNumber(double value) implements JsonValue {}

record JsonBoolean(boolean value) implements JsonValue {}

@GenerateLenses
record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}

@GeneratePrisms
sealed interface DomainError permits ValidationError, NotFoundError {}

@GenerateLenses
record ValidationError(String message) implements DomainError {}

@GenerateLenses
record NotFoundError(String what) implements DomainError {}

class Fixture {

  static final JsonValue jsonValue = new JsonString("hello");

  static final JsonValue value1 = jsonValue;

  static final JsonValue value2 = new JsonNumber(1);

  static final JsonValue value3 = new JsonBoolean(true);

  static final DomainError someError = new ValidationError("bad");

  static Traversal<Map<String, JsonValue>, JsonValue> mapValue(String key) {
    return Traversals.forMap(key);
  }
}
