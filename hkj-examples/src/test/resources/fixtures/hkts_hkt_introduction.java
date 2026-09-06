// Fixture for hkj-book/src/hkts/hkt_introduction.md
//
// The chapter opens with what Java can and cannot say. The three fences that are deliberately not
// Java - the `F<?>` syntax it wishes for, and the container shapes it names in passing - stay
// unmarked; the rest is ordinary code and is gated.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

class Fixture {

  static final List<Integer> numbers = List.of(1, 2, 3);

  static String intToString(int value) {
    return Integer.toString(value);
  }
}
