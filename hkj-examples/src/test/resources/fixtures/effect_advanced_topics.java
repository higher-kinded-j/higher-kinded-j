// Fixture for hkj-book/src/effect/advanced_topics.md
//
// Only the trampoline section is gated. The Free and FreeAp sections build DSLs whose functors,
// witnesses and interpreters the page leaves as `/* ... */` on purpose - they are sketches of a
// shape, not code to copy - so there is nothing for a fixture to supply.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.math.BigInteger;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.TrampolinePath;
