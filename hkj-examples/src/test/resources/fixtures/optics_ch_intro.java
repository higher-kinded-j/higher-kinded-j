// Fixture for hkj-book/src/optics/ch_intro.md
//
// The page shows its before and after as includes from IntroBook; its one snippet
// declares the three annotated records, and this fixture supplies the imports.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

class Fixture {}
