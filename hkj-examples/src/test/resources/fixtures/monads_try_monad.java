// Fixture for hkj-book/src/monads/try_monad.md
//
// The page's code is included from compiled examples. Its checked fences are import lists,
// and one fence refused on purpose: Try.of given a call that throws a checked exception. This
// fixture supplies the imports that refused snippet names.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.nio.file.Files;
import java.nio.file.Paths;
import org.higherkindedj.hkt.trymonad.Try;
