// Fixture for hkj-book/src/optics/ch7_intro.md
//
// The page's payoff snippet shows the distinction the capability table turns
// on: what a Lens declares, what it does not, and the conversion that closes
// the gap. The record lives here and the annotation processor generates
// OrderLenses during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
record Order(String id, String customer) {}

class Fixture {
  static final Order order = new Order("ORD-1", "Alice");
}
