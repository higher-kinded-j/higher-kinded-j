// Fixture for hkj-book/src/optics/conversions.md
//
// The page's one snippet composes a generated lens with a list traversal and
// lets andThen choose the result type. The lens is the chapter cast's
// OrderLenses.lines(), which hkj-examples' main sources put on the gate's
// classpath with the Order and LineItem it reads.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
