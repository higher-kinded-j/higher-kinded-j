// Fixture for hkj-book/src/optics/annotations_at_a_glance.md
//
// The page's one worked snippet stacks the generators on an order, so the record's two component
// types are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

record Customer(String id, String name) {}

record LineItem(String sku, int quantity) {}
