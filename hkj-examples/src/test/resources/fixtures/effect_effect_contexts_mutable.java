// Fixture for hkj-book/src/effect/effect_contexts_mutable.md
//
// The MutableContext page threads a counter, a statistics record, an accumulator and an id
// generator through state-carrying pipelines. Most snippets declare the state record they use;
// `Counter` is the exception, so the copy here matches the page's declaration exactly - the
// snippet that declares it shadows this one, and the two must agree.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;

record Counter(int value) {

  Counter increment() {
    return new Counter(value + 1);
  }
}

record AnyState(String value) {}

record Request(String body, long id) {

  Request withId(long id) {
    return new Request(body, id);
  }
}

class Fixture {

  static final List<Path> files = List.of();

  static final List<Request> requests = List.of();

  /** The workflow the execution section runs three ways. */
  static final MutableContext<IOKind.Witness, Counter, String> workflow =
      MutableContext.<Counter>get().map(counter -> "Count: " + counter.value());

  static void process(byte[] content) {}
}
