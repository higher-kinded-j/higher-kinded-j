// Fixture for hkj-book/src/optics/conversions.md
//
// The page's one snippet composes a generated lens with a list traversal and
// lets andThen choose the result type. The records live here and the
// annotation processor generates UserLenses during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

record Order(String id) {}

@GenerateLenses
record User(String name, List<Order> orders) {}
