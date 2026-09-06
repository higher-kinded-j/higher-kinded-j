// Fixture for hkj-book/src/monads/vstream.md
//
// The page builds streams from ranges, seeds and a paginated API, then folds and recovers from
// them. Only the paginated API is elided from the snippets; it is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;

class Fixture {

  static String fetchPage(int page) {
    return "page-" + page;
  }
}
