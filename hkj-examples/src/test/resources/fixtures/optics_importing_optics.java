// Fixture for hkj-book/src/optics/importing_optics.md
//
// The page imports optics for external types. @ImportOptics accepts a package
// or a type, so the fixture carries the import declaration on a holder class
// and the page's snippets use the generated companions.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.time.LocalDate;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.ImportOptics;

@ImportOptics({LocalDate.class})
class OpticsImports {}

@GenerateLenses
record Order(String id, LocalDate orderDate, List<String> lines) {}

class Fixture {
  static final Order order = new Order("ORD-1", LocalDate.of(2026, 3, 14), List.of("widget"));
}
