// Fixture for hkj-book/src/optics/ch3_intro.md
//
// The page's payoff snippet filters a generated traversal by predicate; the
// records live here and the annotation processor generates the *Traversals
// and *Lenses companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Item(String name, double price) {}

@GenerateLenses
@GenerateTraversals
record Order(String id, List<Item> items) {}

class Fixture {
  static final Order order =
      new Order(
          "ORD-1",
          List.of(new Item("Laptop", 500.0), new Item("Monitor", 200.0), new Item("Mouse", 25.0)));
}
