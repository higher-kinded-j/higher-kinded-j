// Fixture for hkj-book/src/functional/bifunctor.md
//
// The page shows Bifunctor's declaration. These imports let that snippet compile; the page's other
// code is included from compiled examples.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
