// Fixture for hkj-book/src/monads/vstream_performance.md
//
// The optimisation tips compare two pipelines over the same stream; it is declared here so the
// comparison stays two lines.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.vstream.VStream;

class Fixture {

  static final VStream<Integer> stream = VStream.range(1, 100);
}
