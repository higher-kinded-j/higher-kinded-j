// Fixture for hkj-book/src/optics/production_readiness.md
//
// The caching snippet stores a generated lens and a composed Focus path as
// constants. The records live here and the annotation processor generates the
// *Lenses and *Focus companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record Company(String name) {}

@GenerateFocus
record Item(String sku, BigDecimal price) {}

@GenerateFocus
record Order(String id, List<Item> items) {}
