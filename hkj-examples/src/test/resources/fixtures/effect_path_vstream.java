// Fixture for hkj-book/src/effect/path_vstream.md
//
// The page catalogues VStreamPath's constructors, combinators and terminal operations. Most of it
// is self-contained; the paginated-unfold example needs a page source, which lives here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;

record Page(int number, List<String> items) {}

class Fixture {

  static final int lastPage = 5;

  static final VStream<String> myVStream = VStream.of("a", "b", "c");

  static Page fetchPage(int pageNum) {
    return new Page(pageNum, List.of());
  }
}
