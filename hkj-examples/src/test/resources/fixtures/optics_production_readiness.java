// Fixture for hkj-book/src/optics/production_readiness.md
//
// The caching snippet stores a generated lens and a composed Focus path as
// constants. Company lives here, and the annotation processor generates its
// companions during snippet compilation; Order and LineItem are the chapter's
// cast, whose companions hkj-examples generates.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.focus.TraversalPath;

@GenerateLenses
@GenerateFocus
record Company(String name) {}
