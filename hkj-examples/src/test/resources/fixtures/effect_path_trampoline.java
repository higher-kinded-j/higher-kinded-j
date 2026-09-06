// Fixture for hkj-book/src/effect/path_trampoline.md
//
// The page's creation section lifts an existing Trampoline, so one has to be in scope.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.trampoline.Trampoline;

class Fixture {

  static final Trampoline<Integer> trampoline = Trampoline.done(42);
}
