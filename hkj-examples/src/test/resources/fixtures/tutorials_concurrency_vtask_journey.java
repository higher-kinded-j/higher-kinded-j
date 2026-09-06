// Fixture for hkj-book/src/tutorials/concurrency/vtask_journey.md
//
// The journey's pitfalls each contrast a risky line with a better one, so the operation they wrap
// and the data they fetch are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.Duration;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;

record Data(String body) {

  static Data empty() {
    return new Data("");
  }
}

record Pair(String left, String right) {}

class Fixture {

  static final VTask<String> fetchA = VTask.of(() -> "a");

  static final VTask<String> fetchB = VTask.of(() -> "b");

  static Integer dangerousOperation() {
    return 1;
  }

  static Data fetchFromSlowService() {
    return new Data("slow");
  }

  static Integer handleError(Throwable error) {
    return 0;
  }

  static Integer handleSuccess(Integer value) {
    return value;
  }
}
