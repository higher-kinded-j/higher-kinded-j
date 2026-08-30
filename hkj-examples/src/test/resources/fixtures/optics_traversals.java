// Fixture for hkj-book/src/optics/traversals.md
//
// The page's verified snippet shows what the processor generates for a wildcard
// element type, so the fixture supplies the Player record it focuses on.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into.
// Spotless excludes src/test/resources so an "unused import" cleanup cannot
// break fixtures (see build.gradle.kts).

import java.util.Collection;
import java.util.List;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateTraversals;

record Player(String name, int score) {}
