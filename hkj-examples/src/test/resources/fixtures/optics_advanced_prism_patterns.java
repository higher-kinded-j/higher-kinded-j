// Fixture for hkj-book/src/optics/advanced_prism_patterns.md
//
// The page's verified snippets demonstrate Prisms.nearly and doesNotMatch over
// a small sealed JSON hierarchy; the sealed interface, its @GeneratePrisms
// companion, and the sample values live here.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Prisms;

@GeneratePrisms
sealed interface JsonValue permits JsonString, JsonNumber {}

record JsonString(String value) implements JsonValue {}

record JsonNumber(double value) implements JsonValue {}

class Fixture {
  static final String candidate = "ada@example.com";
  static final List<JsonValue> values =
      List.of(new JsonString("hello"), new JsonNumber(42), new JsonString("world"));
}
