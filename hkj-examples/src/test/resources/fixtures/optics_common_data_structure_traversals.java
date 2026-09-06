// Fixture for hkj-book/src/optics/common_data_structure_traversals.md
//
// The page walks the three container traversals - Optional, Map values and Tuple2 - through a
// configuration model. Every record it names is declared here; the snippet that shows one shadows
// this copy, which is why the values below are `sample()` stand-ins rather than constructor calls.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Lenses;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.util.TupleTraversals;

@GenerateLenses
record ServerConfig(String hostname, Optional<Integer> port, Optional<String> sslCertPath) {}

@GenerateLenses
record ApplicationConfig(String appName, Optional<ServerConfig> server) {}

@GenerateLenses
record FeatureFlags(Map<String, Optional<Boolean>> flags) {}

@GenerateLenses
record DatabaseConfig(Map<String, String> connectionProperties) {}

@GenerateLenses
record ServiceRegistry(Map<String, ServerConfig> services) {}

@GenerateLenses
record Endpoint(String path, Optional<Integer> timeout) {}

@GenerateLenses
record ServiceConfig(String name, Map<String, Integer> ports, Map<String, Endpoint> endpoints) {}

@GenerateLenses
record Location(String name, Tuple2<Double, Double> coordinates) {}

@GenerateLenses
record BoundingBox(Tuple2<Integer, Integer> topLeft, Tuple2<Integer, Integer> bottomRight) {}

class Fixture {

  // A value the page names but does not build. Snippets are compiled, not run, and a snippet that
  // shows a model shadows the one above, so naming a constructor here would tie the fixture to one
  // shape of it.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final ApplicationConfig config = sample();

  static final ServerConfig serverConfig = sample();

  static final ServiceConfig serviceConfig = sample();

  static final ServiceRegistry registry = sample();

  static final BoundingBox box = sample();

  static final Map<String, Double> prices = Map.of("widget", 10.0, "gadget", 25.0, "gizmo", 15.0);

  static final Map<String, Integer> map = Map.of("widget", 10, "gadget", 25);

  static final Optional<String> optional = Optional.of("hello");

  static final Tuple2<Integer, String> mixed = new Tuple2<>(42, "hello");

  static final Tuple2<Integer, String> tuple = mixed;
}
