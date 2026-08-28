// Fixture for hkj-book/src/optics/quickstart.md
//
// The page's section 2 snippets discount every line item through a generated
// navigator, and match one variant of a sealed status. The records live here and
// the annotation processor generates the *Lenses, *Focus and *Prisms companions
// during snippet compilation.
//
// A List field widens through the built-in traversal and hands back a plain
// TraversalPath, so the hop to the element's field is .via(LineItemFocus.price()).
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;

@GeneratePrisms
sealed interface Status permits Status.Pending, Status.Shipped, Status.Cancelled {
  record Pending() implements Status {}

  record Shipped(Instant at) implements Status {}

  record Cancelled(String reason) implements Status {}
}

@GenerateLenses
@GenerateFocus
record LineItem(String sku, BigDecimal price) {}

@GenerateLenses
@GenerateFocus
@GenerateTraversals
record Order(String id, Status status, List<LineItem> items) {}

/** The snippet class extends this, which is what puts {@code order} in scope. */
class Fixture {
  static final Order order = new Order("A-1", new Status.Pending(), List.of());
}
