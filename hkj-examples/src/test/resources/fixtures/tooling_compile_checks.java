// Fixture for hkj-book/src/tooling/compile_checks.md
//
// The page shows what the HKJ checker refuses and what it merely nudges about, so its snippets are
// held to the checker's own diagnostics. Only the consumer the discarded-effect example peeks with
// is elided.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.function.Consumer;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

class Fixture {

  static final Consumer<Integer> log = value -> {};
}
