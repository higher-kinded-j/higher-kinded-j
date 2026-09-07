// Fixture for hkj-book/src/optics/optics_spec_interfaces.md
//
// The page's worked example is included from the compiled JsonNodeOpticsSpec, JsonPaths and
// JsonApiBook classes, so those types are real. What the page invents for the sake of an
// illustration lives here: `Value`, the check-and-extract API standing in for a library with no
// sealed hierarchy, `Shape`/`Circle`, the hierarchy the @InstanceOf narrowing rules are explained
// on, and `Box`, the generic source type for the spec type-parameter rules.
//
// Two of the page's snippets declare their own `Shape` and `Circle`, which shadow the ones here:
// the narrowing rules turn on whether the base pins the argument, so the page shows both bases.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.example.book.optics.JsonNodeOptics;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.InstanceOf;
import org.higherkindedj.optics.annotations.MatchWhen;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.Wither;
import org.higherkindedj.optics.laws.PrismLaws;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.NumericNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** A library that answers `isX()` and `asX()` rather than exposing subtypes: the @MatchWhen case. */
class Value {

  boolean isObject() {
    return this instanceof ObjectValue;
  }

  ObjectValue asObject() {
    return (ObjectValue) this;
  }

  boolean isString() {
    return false;
  }

  String asString() {
    return "";
  }
}

/** The variant a `Value` carries when it is an object. */
class ObjectValue extends Value {}

/** A base that says nothing about the argument, so an `instanceof Circle` pins nothing. */
class Shape {}

class Circle<X> extends Shape {
  X tag;
}

/** A generic source type, for the rules about which parameters a generated method declares. */
record Box<U>(String label, U content) {

  Box<U> withLabel(String label) {
    return new Box<>(label, content);
  }

  Box<U> withContent(U content) {
    return new Box<>(label, content);
  }
}

class Fixture {

  /** Distinct matching and non-matching sources, which is what the prism laws need. */
  static final JsonNode anObjectNode = new ObjectMapper().readTree("{\"id\": 1}");

  static final JsonNode aStringNode = new ObjectMapper().readTree("\"one\"");
}
