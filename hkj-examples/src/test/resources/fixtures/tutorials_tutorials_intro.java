// Fixture for hkj-book/src/tutorials/tutorials_intro.md
//
// The tutorials are written as JUnit exercises whose answers the reader fills in, so the page's
// first exercise leans on the `answerRequired()` placeholder the tutorial sources define.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.Test;

class Fixture {

  /** What an unanswered exercise calls; the tutorial sources declare the same helper. */
  static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }
}
