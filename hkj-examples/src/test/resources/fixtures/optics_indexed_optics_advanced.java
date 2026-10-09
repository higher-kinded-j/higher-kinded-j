// Fixture for hkj-book/src/optics/indexed_optics_advanced.md
//
// The page includes its examples from IndexedAdvancedBook and OrderFulfilmentDashboard, which take
// the chapter's cast from its package. The one fence left converts a Pair to a Tuple2 and back, and
// needs only these imports.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
