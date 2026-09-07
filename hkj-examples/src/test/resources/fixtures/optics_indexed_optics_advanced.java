// Fixture for hkj-book/src/optics/indexed_optics_advanced.md
//
// The page pairs indices through nested orders and audits a customer's field changes. The line item
// and the customer are declared here; each snippet shows the nesting it works on.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

record LineItem(String productName, int quantity, double price) {}

record Customer(String name, String email) {}

class Fixture {

  static final List<LineItem> items =
      List.of(
          new LineItem("Laptop", 1, 999.99),
          new LineItem("Mouse", 1, 24.99),
          new LineItem("Keyboard", 1, 79.99));
}
